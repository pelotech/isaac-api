package uk.ac.cam.cl.dtg.clojure;

public interface DatomicLogger {
	
	/*
	 * Logs a JSON event from the specified browser session.
	 * Returns true if we were able to store the event. 
	 */
	public boolean logEvent(String sessionId, String cookieId, String eventJson);

}
