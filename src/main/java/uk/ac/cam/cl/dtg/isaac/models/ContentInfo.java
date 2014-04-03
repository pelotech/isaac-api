package uk.ac.cam.cl.dtg.isaac.models;

/**
 * DTO represents high level information about a piece of content
 * 
 * This should be a light weight object used for presenting search results etc.
 *
 */
public class ContentInfo {

	private String id;

	private String title;
	
	private String type;

	private String url;

	public ContentInfo(String id, String title, String type, String url) {
		super();
		this.id = id;
		this.type = type;
		this.title = title;
		this.url = url;
	}

	public String getId() {
		return id;
	}

	public String getType() {
		return type;
	}

	public String getTitle() {
		return title;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}
}