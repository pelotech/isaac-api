package uk.ac.cam.cl.dtg.segue.auth.exceptions;

/**
 * An exception which indicates that there are no credentials configured for the
 * user so we cannot authenticate using this provider.
 * 
 * @author Stephen Cummins
 * 
 */
public class NoCredentialsAvailableException extends Exception {
	private static final long serialVersionUID = -2703137641897650020L;

	/**
	 * Creates a NoCredentialsAvailableException.
	 * 
	 * @param message
	 *            - to accompany the exception.
	 */
	public NoCredentialsAvailableException(final String message) {
		super(message);
	}
}