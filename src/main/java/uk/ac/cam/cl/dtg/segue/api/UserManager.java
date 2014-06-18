package uk.ac.cam.cl.dtg.segue.api;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.security.GeneralSecurityException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.Validate;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.google.inject.name.Named;

import uk.ac.cam.cl.dtg.segue.auth.AuthenticatorSecurityException;
import uk.ac.cam.cl.dtg.segue.auth.CodeExchangeException;
import uk.ac.cam.cl.dtg.segue.auth.IFederatedAuthenticator;
import uk.ac.cam.cl.dtg.segue.auth.IOAuth2Authenticator;
import uk.ac.cam.cl.dtg.segue.auth.NoUserIdException;
import uk.ac.cam.cl.dtg.segue.dao.IUserDataManager;
import uk.ac.cam.cl.dtg.segue.dto.SegueErrorResponse;
import uk.ac.cam.cl.dtg.segue.dto.users.User;

/**
 * This class is responsible for all low level user management actions e.g. authentication and registration.
 * TODO: Split authentication functionality into another class and let this one focus on maintaining our segue user state.
 */
public class UserManager{
	private static final Logger log = LoggerFactory.getLogger(UserManager.class);

	private static final String HMAC_SHA_ALGORITHM = "HmacSHA1";
	private static final String DATE_FORMAT = "EEE, d MMM yyyy HH:mm:ss z";
	public enum AuthenticationProvider {GOOGLE, FACEBOOK, RAVEN;};
	
	private final IUserDataManager database;
	private final String hmacSalt;
	private final Map<AuthenticationProvider, IFederatedAuthenticator> registeredAuthProviders;
	
	@Inject
	public UserManager(IUserDataManager database, @Named(Constants.HMAC_SALT) String hmacSalt, Map<AuthenticationProvider, IFederatedAuthenticator> providersToRegister){
		Validate.notNull(database);
		Validate.notNull(hmacSalt);
		Validate.notNull(providersToRegister);
		this.database = database;
		this.hmacSalt = hmacSalt;
		this.registeredAuthProviders = providersToRegister;
	}
	
	/**
	 * This method will attempt to authenticate the user and provide a user object back to the caller.
	 * 
	 * @param request
	 * @param provider
	 * @return A response containing a user object or a redirect URI to the authentication provider if authorization / login is required.
	 */
	public Response authenticate(HttpServletRequest request, String provider){
		// get the current user based on their session id information.
		User currentUser = getCurrentUser(request);
		if(null != currentUser){
			return Response.ok().entity(currentUser).build();
		}

		// Ok we don't have a current user so now we have to go ahead and try and authenticate them.
		IFederatedAuthenticator federatedAuthenticator = null;
		
		try{
			federatedAuthenticator = mapToProvider(provider);
			
			// if we are an OAuth2Provider redirect to the provider authorization url.	
			if(federatedAuthenticator instanceof IOAuth2Authenticator){
				IOAuth2Authenticator oauthProvider = (IOAuth2Authenticator) federatedAuthenticator;

				URI redirectLink = URI.create(oauthProvider.getAuthorizationUrl());
				List<NameValuePair> urlParams = URLEncodedUtils.parse(redirectLink.toString(), Charset.defaultCharset());

				// to deal with cross site request forgery (Provider should embed a state param in the uri returned in the redirectLink)
				String antiForgeryTokenFromProvider =  null;
				
				for(NameValuePair param : urlParams){
					if(param.getName().equals("state")){
						antiForgeryTokenFromProvider = param.getValue();
					}
				}
				
				if(null == antiForgeryTokenFromProvider){
					SegueErrorResponse error = new SegueErrorResponse(Status.INTERNAL_SERVER_ERROR, "Anti forgery authenitication error. Please contact server admin.");
					log.error("Unable to extract Anti Forgery Token from Authentication provider");
					return error.toResponse();					
				}

				// Store antiForgeryToken in the users session.
				request.getSession().setAttribute("state", antiForgeryTokenFromProvider);
				
				return Response.temporaryRedirect(redirectLink).entity(redirectLink).build();
			}
			else
			{
				SegueErrorResponse error = new SegueErrorResponse(Status.INTERNAL_SERVER_ERROR, "Unable to map to a known authenticator. The provider: " + provider + " is unknown");
				log.error(error.getErrorMessage());
				return error.toResponse();
			}
		}
		catch(IllegalArgumentException e){
			SegueErrorResponse error = new SegueErrorResponse(Status.BAD_REQUEST, "Error mapping to a known authenticator. The provider: " + provider + " is unknown");
			log.error(error.getErrorMessage(), e);
			return error.toResponse();
		}
		catch (IOException e) {
			SegueErrorResponse error = new SegueErrorResponse(Status.INTERNAL_SERVER_ERROR, "IOException when trying to redirect to OAuth provider", e);
			log.error(error.getErrorMessage(), e);
			return error.toResponse();
		}
	}

	/**
	 * Authenticate Callback will receive the authentication information from the different provider types. 
	 * (e.g. OAuth 2.0 (IOAuth2Authenticator) or bespoke)
	 * 
	 * TODO: Refactor this to be a bit more sensible.
	 * 
	 * @param request
	 * @param response
	 * @param provider
	 * @return Response containing the populated user DTO.
	 */
	public Response authenticateCallback(HttpServletRequest request, HttpServletResponse response, String provider){
		User currentUser = getCurrentUser(request);

		if(null != currentUser){
			log.warn("We already have a cookie set with a valid user. We won't proceed with authentication callback logic.");
			return Response.ok().entity(currentUser).build();
		}

		// Ok we don't have a current user so now we have to go ahead and try and authenticate them.
		IFederatedAuthenticator federatedAuthenticator = null;
		
		try{
			federatedAuthenticator = mapToProvider(provider);
		}
		catch(IllegalArgumentException e){
			SegueErrorResponse error = new SegueErrorResponse(Status.BAD_REQUEST, "Unable to map to a known authenticator. The provider: " + provider + " is unknown");
			log.warn(error.getErrorMessage());
			return error.toResponse();
		}

		// if we are an OAuth2Provider complete next steps of oauth
		if(federatedAuthenticator instanceof IOAuth2Authenticator){
			IOAuth2Authenticator oauthProvider = (IOAuth2Authenticator) federatedAuthenticator;

			// verify there is no cross site request forgery going on.
			if(request.getQueryString() == null || !ensureNoCSRF(request)){
				SegueErrorResponse error = new SegueErrorResponse(Status.UNAUTHORIZED, "CSRF check failed.");
				log.error(error.getErrorMessage());
				return error.toResponse();
			}

			// this will have our authorization code within it.
			StringBuffer fullUrlBuf = request.getRequestURL();
			fullUrlBuf.append('?').append(request.getQueryString());

			try{
				// extract auth code from string buffer
				String authCode = oauthProvider.extractAuthCode(fullUrlBuf.toString());

				if (authCode == null) {
					SegueErrorResponse error = new SegueErrorResponse(Status.UNAUTHORIZED, "User denied access to our app.");
					log.info("Provider failed to give us an authorization code.");
					return error.toResponse();					
				} else {   
					log.debug("User granted access to our app");

					String internalReference = oauthProvider.exchangeCode(authCode);

					// get user info from provider
					// note the userid field in this object will contain the providers user id not ours.
					User userFromProvider = federatedAuthenticator.getUserInfo(internalReference);
					
					if(null == userFromProvider)
						return Response.noContent().entity("Can't create user").build();

					log.debug("User with name " + userFromProvider.getEmail() + " retrieved");

					// this is the providers unique id for the user we should store it for now
					String providerId = userFromProvider.getDbId();
					
					// clear user object id so that it is ready to receive our local one.
					userFromProvider.setDbId(null);

					AuthenticationProvider providerReference = AuthenticationProvider.valueOf(provider.toUpperCase());					
					User localUserInformation = this.getUserFromLinkedAccount(providerReference, providerId);

					// decide if we need to register a new user or link to an existing account
					if(null == localUserInformation){
						log.info("New registration - User does not already exist.");
						// register user
						String localUserId = registerUser(userFromProvider, providerReference, providerId);
						localUserInformation = this.database.getById(localUserId);
						
						if(null == localUserInformation){
							// we just put it in so something has gone very wrong.
							log.error("Failed to retreive user even though we just put it in the database.");
							throw new NoUserIdException();
						}
					}
					else{
						log.debug("Returning user detected" + localUserInformation.getEmail());
					}
					
					// create a signed session for this user so that we don't need to do this again for a while.
					this.createSession(request, localUserInformation.getDbId());
					
					return Response.ok(localUserInformation).build();
				}				
			}
			catch(IOException e){
				SegueErrorResponse error = new SegueErrorResponse(Status.UNAUTHORIZED, "Exception while trying to authenticate a user - during callback step", e);
				log.error(error.getErrorMessage(), e);
				return error.toResponse();				
			} catch (NoUserIdException e) {
				SegueErrorResponse error = new SegueErrorResponse(Status.UNAUTHORIZED, "Unable to locate user information.");
				log.error("No userID exception received. Unable to locate user.", e);
				return error.toResponse();						
			} catch (CodeExchangeException e) {
				SegueErrorResponse error = new SegueErrorResponse(Status.UNAUTHORIZED, "Security code exchange failed.");
				log.error("Unable to verify security code.", e);
				return error.toResponse();
			} catch (AuthenticatorSecurityException e) {
				SegueErrorResponse error = new SegueErrorResponse(Status.UNAUTHORIZED, "Error during security checks.");
				log.error(error.getErrorMessage(), e);
				return error.toResponse();
			}
		}
		else{
			// We should never see this if a correct provider has been given
			SegueErrorResponse error = new SegueErrorResponse(Status.INTERNAL_SERVER_ERROR, "Unable to map to a known authenticator. The provider: " + provider + " is unknown");
			log.error(error.getErrorMessage());
			return error.toResponse();	
		}
	}
	
	/**
	 * This method will attempt to find a segue user using a 3rd party provider and a unique id that identifies the user to the provider. 
	 * 
	 * @param provider - the provider that we originally validated with
	 * @param providerId - the unique ID of the user as given to us from the provider.
	 * @return A user object or null if we were unable to find the user with the information provided.
	 */
	public User getUserFromLinkedAccount(AuthenticationProvider provider, String providerId){
		Validate.notNull(provider);
		Validate.notBlank(providerId);
		
		User user = database.getByLinkedAccount(provider, providerId);
		if(null == user){
			log.info("Unable to locate user based on provider information provided.");			
		}
		return user;
	}

	/**
	 * Get the details of the currently logged in user
	 * 
	 * @return Returns the current user DTO if we can get it or null if user is not currently logged in
	 */
	public User getCurrentUser(HttpServletRequest request){
		Validate.notNull(request);
		
		// get the current user based on their session id information.
		String currentUserId = (String) request.getSession().getAttribute(Constants.SESSION_USER_ID);
		
		if(null == currentUserId){
			log.debug("Current userID is null. Assume they are not logged in.");
			return null;
		}

		// check if the users session is validated using our credentials.
		if(!this.validateUsersSession(request)){
			log.info("User session has failed validation. Assume they are not logged in.");
			return null;
		}

		// retrieve the user from database.
		return database.getById(currentUserId);
	}

	/**
	 * This method destroys the users current session and may do other clean up activities.
	 * 
	 * @param request - from the current user
	 */
	public void logUserOut(HttpServletRequest request){
		Validate.notNull(request);
		try{
			request.getSession().invalidate();			
		}
		catch(IllegalStateException e){
			log.info("The session has already been invalidated. Unable to logout again...");
		}
	}
	
	/**
	 * Creates a signed session for the user so we know that it is them when we check them.
	 * 
	 * @param request
	 * @param userId
	 */
	public void createSession(HttpServletRequest request, String userId){		
		Validate.notNull(request);
		Validate.notBlank(userId);
		
		String currentDate = new SimpleDateFormat(DATE_FORMAT).format(new Date());
		String sessionId =  request.getSession().getId();
		String sessionHMAC = this.calculateHMAC(hmacSalt+userId + sessionId + currentDate, userId + sessionId + currentDate);
		
		request.getSession().setAttribute(Constants.SESSION_USER_ID, userId);
		request.getSession().setAttribute(Constants.SESSION_ID, sessionId);
		request.getSession().setAttribute(Constants.DATE_SIGNED, currentDate);
		request.getSession().setAttribute(Constants.HMAC, sessionHMAC);
	}

	/**
	 * Verifies that the signed session is valid 
	 * Currently only confirms the signature.
	 * 
	 * @param request
	 * @return True if we are happy, false if we are not.
	 */
	public boolean validateUsersSession(HttpServletRequest request){
		Validate.notNull(request);
		
		String userId = (String) request.getSession().getAttribute(Constants.SESSION_USER_ID);
		String currentDate = (String) request.getSession().getAttribute(Constants.DATE_SIGNED);
		String sessionId = (String) request.getSession().getAttribute(Constants.SESSION_ID);
		String sessionHMAC = (String) request.getSession().getAttribute(Constants.HMAC);
		
		String ourHMAC = this.calculateHMAC(hmacSalt+userId + sessionId + currentDate, userId + sessionId + currentDate);
		
		if(null == userId){
			log.debug("No session set so not validating user identity.");
			return false;
		}
		
		if(ourHMAC.equals(sessionHMAC)){
			log.debug("Valid user session continuing...");
			return true;	
		}
		else
		{
			log.info("Invalid user session detected");
			return false;
		}
	}
	
	private String calculateHMAC(String key, String dataToSign){
		Validate.notEmpty(key, "Signing key cannot be blank.");
		Validate.notEmpty(dataToSign, "Data to sign cannot be blank.");
		
		try {
			SecretKeySpec signingKey = new SecretKeySpec(key.getBytes(), HMAC_SHA_ALGORITHM);
			Mac mac = Mac.getInstance(HMAC_SHA_ALGORITHM);
			mac.init(signingKey);
			
			byte[] rawHmac = mac.doFinal(dataToSign.getBytes());
			
			String result = new String(Base64.encodeBase64(rawHmac));
			return result;
		} catch (GeneralSecurityException e) {
			log.warn("Unexpected error while creating hash: " + e.getMessage(),	e);
			throw new IllegalArgumentException();
		}		
	}

	private IFederatedAuthenticator mapToProvider(String provider){
		Validate.notEmpty(provider, "Provider name must not be empty or null if we are going to map it to an implementation.");
		
		AuthenticationProvider enumProvider = null;
		try{
			enumProvider = AuthenticationProvider.valueOf(provider.toUpperCase());
		}
		catch(IllegalArgumentException e){
			log.error("The provider requested is invalid and not a known AuthenticationProvider: " + provider);
			throw new IllegalArgumentException();
		}
		
		if(!registeredAuthProviders.containsKey(enumProvider)){
			log.error("This authentication provider has not been registered / implemented yet: " + provider);
			throw new IllegalArgumentException();
		}
		
		log.debug("Mapping provider: " + provider + " to " + enumProvider);
		
		return this.registeredAuthProviders.get(enumProvider);
	}
	
	/**
	 * This method will compare the state in the users cookie with the response from the provider.
	 * 
	 * @param request
	 * @return True if we are satisfied that they match and false if we think there is a problem.
	 */
	private boolean ensureNoCSRF(HttpServletRequest request){
		Validate.notNull(request);
		
		// to deal with cross site request forgery
		String csrfTokenFromUser = (String) request.getSession().getAttribute("state");
		String csrfTokenFromProvider = request.getParameter("state");

		if(null == csrfTokenFromUser || null == csrfTokenFromProvider ||!csrfTokenFromUser.equals(csrfTokenFromProvider)){
			log.error("Invalid state parameter - Provider said: " + request.getParameter("state") + " Session said: " + request.getSession().getAttribute("state"));
			return false;
		}
		else
		{
			log.debug("State parameter matches - Provider said: " + request.getParameter("state") + " Session said: " + request.getSession().getAttribute("state"));
			return true;
		}
	}

	/**
	 * This method should handle the situation where we haven't seen a user before.
	 * 
	 * @param user from authentication provider
	 * @param provider information
	 * @param unique reference for this user held by the authentication provider.
	 * @return The localUser account user id of the user after registration.
	 */
	private String registerUser(User user, AuthenticationProvider provider, String providerId){
		String userId = database.register(user, provider, providerId);
		return userId;
	}
}
