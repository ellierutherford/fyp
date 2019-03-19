package ie.tcd.fyp.retrieval;


public class DBSlice {

	String id;
	double scoreEntry;
	int numOfSlices = 0;
	int sizeOfDoc = 0; //in sentences
	boolean normalize;
	
	public DBSlice (String id, double scoreEntry, int numOfSlices, int sizeOfDoc) {
		this.id = id;
		this.scoreEntry = scoreEntry;
		this.numOfSlices = numOfSlices;
		this.sizeOfDoc = sizeOfDoc;
		this.normalize = false;
	}
	
	public String getId() {
		return id;
	}
	
	public double getScoreEntry() {
		return scoreEntry;
	}
	
	public int getNumOfSlices() {
		return numOfSlices;
	}
	
	public int getSizeOfDoc() {
		return sizeOfDoc;
	}
	
	public boolean getNormalization() {
		return this.normalize;
	}
	
	public void setNormalization(boolean norm) {
		this.normalize = norm;
	}
	
	
}
