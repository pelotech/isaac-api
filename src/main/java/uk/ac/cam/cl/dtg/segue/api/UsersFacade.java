/**
 * Copyright 2014 Stephen Cummins
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at
 * 		http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package uk.ac.cam.cl.dtg.segue.api;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.commons.lang3.Validate;
import org.jboss.resteasy.annotations.GZIP;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.cam.cl.dtg.segue.api.managers.UserManager;
import uk.ac.cam.cl.dtg.segue.auth.exceptions.AuthenticationProviderMappingException;
import uk.ac.cam.cl.dtg.segue.auth.exceptions.DuplicateAccountException;
import uk.ac.cam.cl.dtg.segue.auth.exceptions.FailedToHashPasswordException;
import uk.ac.cam.cl.dtg.segue.auth.exceptions.InvalidPasswordException;
import uk.ac.cam.cl.dtg.segue.auth.exceptions.InvalidTokenException;
import uk.ac.cam.cl.dtg.segue.auth.exceptions.MissingRequiredFieldException;
import uk.ac.cam.cl.dtg.segue.auth.exceptions.NoUserException;
import uk.ac.cam.cl.dtg.segue.auth.exceptions.NoUserLoggedInException;
import uk.ac.cam.cl.dtg.segue.comm.CommunicationException;
import uk.ac.cam.cl.dtg.segue.dao.SegueDatabaseException;
import uk.ac.cam.cl.dtg.segue.dos.users.RegisteredUser;
import uk.ac.cam.cl.dtg.segue.dos.users.Role;
import uk.ac.cam.cl.dtg.segue.dto.SegueErrorResponse;
import uk.ac.cam.cl.dtg.segue.dto.users.RegisteredUserDTO;
import uk.ac.cam.cl.dtg.util.PropertiesLoader;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;

/**
 * User facade.
 * @author Stephen Cummins
 * 
 */
@Path("/")
public class UsersFacade extends AbstractSegueFacade {
	private static final Logger log = LoggerFactory.getLogger(UsersFacade.class);

	private final UserManager userManager;

	/**
	 * Construct an instance of the UsersFacade.
	 * 
	 * @param properties
	 *            - properties loader for the application
	 * @param userManager
	 *            - user manager for the application
	 */
	@Inject
	public UsersFacade(final PropertiesLoader properties, final UserManager userManager) {
		super(properties);
		this.userManager = userManager;
	}

	/**
	 * Get the details of the currently logged in user.
	 * 
	 * @param request
	 *            - request information used for caching.
	 * @param httpServletRequest
	 *            - the request which may contain session information.
	 * @return Returns the current user DTO if we can get it or null response if
	 *         we can't. It will be a 204 No Content
	 */
	@GET
	@Path("users/current_user")
	@Produces(MediaType.APPLICATION_JSON)
	@GZIP
	public Response getCurrentUserEndpoint(@Context final Request request,
			@Context final HttpServletRequest httpServletRequest) {
		try {
			RegisteredUserDTO currentUser = userManager.getCurrentRegisteredUser(httpServletRequest);

			// Calculate the ETag based on User we just retrieved from the DB
			EntityTag etag = new EntityTag("currentUser".hashCode() + currentUser.hashCode() + "");
			Response cachedResponse = generateCachedResponse(request, etag,
					Constants.NEVER_CACHE_WITHOUT_ETAG_CHECK);
			if (cachedResponse != null) {
				return cachedResponse;
			}

			return Response.ok(currentUser).tag(etag)
					.cacheControl(getCacheControl(Constants.NEVER_CACHE_WITHOUT_ETAG_CHECK)).build();
		} catch (NoUserLoggedInException e) {
			return new SegueErrorResponse(Status.UNAUTHORIZED,
					"Unable to retrieve the current user as no user is currently logged in.").toResponse();
		}
	}

	/**
	 * This method allows users to create a local account or update their
	 * settings.
	 * 
	 * It will also allow administrators to change any user settings.
	 * 
	 * @param request
	 *            - the http request of the user wishing to authenticate
	 * @param userObjectString
	 *            - object containing all user account information including
	 *            passwords.
	 * @return the updated users object.
	 */
	@POST
	@Path("users")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	@GZIP
	public final Response createOrUpdateUserSettings(@Context final HttpServletRequest request,
			final String userObjectString) {

		RegisteredUser userObjectFromClient;
		try {
			ObjectMapper tempObjectMapper = new ObjectMapper();
			tempObjectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

			userObjectFromClient = tempObjectMapper.readValue(userObjectString, RegisteredUser.class);

			if (null == userObjectFromClient) {
				return new SegueErrorResponse(Status.BAD_REQUEST, "No user settings provided.").toResponse();
			}
		} catch (IOException e1) {
			return new SegueErrorResponse(Status.BAD_REQUEST,
					"Unable to parse the user object you provided.", e1).toResponse();
		}

		// determine if this is intended to be an update or create operation.
		if (userObjectFromClient.getDbId() != null) {
			return this.updateUserObject(request, userObjectFromClient);
		} else {
			return this.createUserObject(request, userObjectFromClient);
		}
	}

	/**
	 * End point that allows a local user to generate a password reset request.
	 * 
	 * Step 1 of password reset process - send user an e-mail
	 * 
	 * @param userObject
	 *            - A user object containing the email of the user requesting a
	 *            reset
	 * @return a successful response regardless of whether the email exists or
	 *         an error code if there is a technical fault
	 */
	@POST
	@Path("users/resetpassword")
	@Consumes(MediaType.APPLICATION_JSON)
	@GZIP
	public final Response generatePasswordResetToken(final RegisteredUserDTO userObject) {
		if (null == userObject) {
			log.debug("User is null");
			return new SegueErrorResponse(Status.BAD_REQUEST, "No user settings provided.").toResponse();
		}

		try {
			userManager.resetPasswordRequest(userObject);

			return Response.ok().build();
		} catch (CommunicationException e) {
			SegueErrorResponse error = new SegueErrorResponse(Status.INTERNAL_SERVER_ERROR,
					"Error sending reset message.", e);
			log.error(error.getErrorMessage(), e);
			return error.toResponse();
		} catch (Exception e) {
			SegueErrorResponse error = new SegueErrorResponse(Status.INTERNAL_SERVER_ERROR,
					"Error generate password reset token.", e);
			log.error(error.getErrorMessage(), e);
			return error.toResponse();
		}
	}

	/**
	 * End point that verifies whether or not a password reset token is valid.
	 * 
	 * Optional Step 2 - validate token is correct
	 * 
	 * @param token
	 *            - A password reset token
	 * @return Success if the token is valid, otherwise returns not found
	 */
	@GET
	@Path("users/resetpassword/{token}")
	@Produces(MediaType.APPLICATION_JSON)
	@GZIP
	public final Response validatePasswordResetRequest(@PathParam("token") final String token) {
		try {
			if (userManager.validatePasswordResetToken(token)) {
				return Response.ok().build();
			}
		} catch (SegueDatabaseException e) {
			log.error("Internal database error, while validating Password Reset Request.", e);
			SegueErrorResponse error = new SegueErrorResponse(Status.INTERNAL_SERVER_ERROR,
					"Database error has occurred. Unable to access token list.");
			return error.toResponse();
		}

		SegueErrorResponse error = new SegueErrorResponse(Status.NOT_FOUND, "Invalid password reset token.");
		log.debug(String.format("Invalid password reset token: %s", token));
		return error.toResponse();
	}

	/**
	 * Final step of password reset process. Change password.
	 * 
	 * @param token
	 *            - A password reset token
	 * @param userObject
	 *            - A user object containing password information.
	 * @return successful response.
	 */
	@POST
	@Path("users/resetpassword/{token}")
	@Consumes(MediaType.APPLICATION_JSON)
	@GZIP
	public final Response resetPassword(@PathParam("token") final String token,
			final RegisteredUser userObject) {
		try {
			userManager.resetPassword(token, userObject);
		} catch (InvalidTokenException e) {
			SegueErrorResponse error = new SegueErrorResponse(Status.BAD_REQUEST,
					"Invalid password reset token.");
			return error.toResponse();
		} catch (InvalidPasswordException e) {
			SegueErrorResponse error = new SegueErrorResponse(Status.BAD_REQUEST, "No password supplied.");
			return error.toResponse();
		} catch (SegueDatabaseException e) {
			String errorMsg = "Database error has occurred during reset password process. Please try again later";
			log.error(errorMsg, e);
			SegueErrorResponse error = new SegueErrorResponse(Status.INTERNAL_SERVER_ERROR, errorMsg);
			return error.toResponse();
		}

		return Response.ok().build();
	}

	/**
	 * Update a user object.
	 * 
	 * This method does all of the necessary security checks to determine who is
	 * allowed to edit what.
	 * 
	 * @param request
	 *            - so that we can identify the user
	 * @param userObjectFromClient
	 *            - the new user object from the clients perspective.
	 * @return the updated user object.
	 */
	private Response updateUserObject(final HttpServletRequest request,
			final RegisteredUser userObjectFromClient) {
		Validate.notBlank(userObjectFromClient.getDbId());

		// this is an update as the user has an id
		// security checks
		try {
			// check that the current user has permissions to change this users
			// details.
			RegisteredUserDTO currentlyLoggedInUser = this.userManager.getCurrentRegisteredUser(request);
			if (!currentlyLoggedInUser.getDbId().equals(userObjectFromClient.getDbId())
					&& currentlyLoggedInUser.getRole() != Role.ADMIN) {
				return new SegueErrorResponse(Status.FORBIDDEN,
						"You cannot change someone elses' user settings.").toResponse();
			}

			// check that any changes to protected fields being made are
			// allowed.
			RegisteredUserDTO existingUserFromDb = this.userManager.getUserDTOById(userObjectFromClient
					.getDbId());
			// check that the user is allowed to change the role of another user
			// if that is what they are doing.
			if (currentlyLoggedInUser.getRole() != Role.ADMIN && userObjectFromClient.getRole() != null
					&& !userObjectFromClient.getRole().equals(existingUserFromDb.getRole())) {
				return new SegueErrorResponse(Status.FORBIDDEN,
						"You do not have permission to change a users role.").toResponse();
			}

			RegisteredUserDTO updatedUser = userManager.updateUserObject(userObjectFromClient);

			return Response.ok(updatedUser).build();
		} catch (NoUserLoggedInException e) {
			return new SegueErrorResponse(Status.UNAUTHORIZED,
					"You must be logged in to change your user settings.").toResponse();
		} catch (NoUserException e) {
			return new SegueErrorResponse(Status.NOT_FOUND, "The user specified does not exist.")
					.toResponse();
		} catch (DuplicateAccountException e) {
			return new SegueErrorResponse(Status.BAD_REQUEST,
					"An account already exists with the e-mail address specified.").toResponse();
		} catch (SegueDatabaseException e) {
			log.error("Unable to modify user", e);
			return new SegueErrorResponse(Status.INTERNAL_SERVER_ERROR, "Error while modifying the user")
					.toResponse();
		} catch (InvalidPasswordException e) {
			return new SegueErrorResponse(Status.BAD_REQUEST,
					"Invalid password. You cannot have an empty password.").toResponse();
		} catch (MissingRequiredFieldException e) {
			log.warn("Missing field during update operation. ", e);
			return new SegueErrorResponse(Status.BAD_REQUEST, "You are missing a required field. "
					+ "Please make sure you have specified all mandatory fields in your response.")
					.toResponse();
		} catch (AuthenticationProviderMappingException e) {
			return new SegueErrorResponse(Status.INTERNAL_SERVER_ERROR,
					"Unable to map to a known authenticator. The provider: is unknown").toResponse();
		}
	}

	/**
	 * Create a user object. This method allows new user objects to be created.
	 * 
	 * @param request
	 *            - so that we can identify the user
	 * @param userObjectFromClient
	 *            - the new user object from the clients perspective.
	 * @return the updated user object.
	 */
	private Response createUserObject(final HttpServletRequest request,
			final RegisteredUser userObjectFromClient) {
		try {
			RegisteredUserDTO savedUser = userManager.createUserObject(userObjectFromClient);

			// we need to tell segue that the user who we just created is the
			// one that is logged in.
			this.userManager.createSession(request, savedUser.getDbId());

			return Response.ok(savedUser).build();
		} catch (InvalidPasswordException e) {
			return new SegueErrorResponse(Status.BAD_REQUEST,
					"Invalid password. You cannot have an empty password.").toResponse();
		} catch (FailedToHashPasswordException e) {
			return new SegueErrorResponse(Status.INTERNAL_SERVER_ERROR, "Unable to set a password.")
					.toResponse();
		} catch (MissingRequiredFieldException e) {
			log.warn("Missing field during update operation. ", e);
			return new SegueErrorResponse(Status.BAD_REQUEST, "You are missing a required field. "
					+ "Please make sure you have specified all mandatory fields in your response.")
					.toResponse();
		} catch (DuplicateAccountException e) {
			return new SegueErrorResponse(Status.BAD_REQUEST,
					"An account already exists with the e-mail address specified.").toResponse();
		} catch (SegueDatabaseException e) {
			String errorMsg = "Unable to set a password, due to an internal database error.";
			log.error(errorMsg, e);
			return new SegueErrorResponse(Status.INTERNAL_SERVER_ERROR, errorMsg).toResponse();
		} catch (AuthenticationProviderMappingException e) {
			return new SegueErrorResponse(Status.INTERNAL_SERVER_ERROR,
					"Unable to map to a known authenticator. The provider: is unknown").toResponse();
		}
	}
}