package ie.tcd.fyp.trec;

public class TrecDocument {
	String id;
	String textContent;

	
	public TrecDocument(String id, String textContent) {
		this.id = id;
		this.textContent = textContent;
	}
	

	public String getId () {
		return this.id;
	}
	
	public String getText() {
		return this.textContent;
	}
}
