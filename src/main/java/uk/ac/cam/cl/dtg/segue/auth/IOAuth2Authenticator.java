package uk.ac.cam.cl.dtg.segue.auth;

import java.io.IOException;

/**
 * This interface defines the required methods for an oauth 2.0 provider.
 * 
 * @author sac92
 */
public interface IOAuth2Authenticator extends IOAuthAuthenticator {
	/**
	 * Step 1 of OAUTH2 - Request authorisation URL. Request an authorisation
	 * url which will allow the user to be logged in with their oauth provider.
	 * 
	 * @param antiForgeryStateToken
	 *            - A unique token to protect against CSRF attacks
	 * @return String - A url which should be fully formed and ready for the
	 *         user to login with - this should result in a callback to a
	 *         prearranged api endpoint if successful.
	 * @throws IOException
	 *             - if there is a problem with the end point.
	 */
	String getAuthorizationUrl(final String antiForgeryStateToken) throws IOException;

	/**
	 * This method generates an anti CSRF token to be included in the
	 * authorisation step (step 1).
	 * 
	 * @return SecureRandom token.
	 */
	String getAntiForgeryStateToken();
}
