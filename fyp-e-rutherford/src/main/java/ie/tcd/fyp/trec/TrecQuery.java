package ie.tcd.fyp.trec;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.Map.Entry;

import edu.wiki.search.MongoESASearcher;
import ie.adaptcentre.tcd.phd.nlp.pooling.TermsPool;
import ie.adaptcentre.tcd.phd.nlp.segmenter.Slice;
import ie.adaptcentre.tcd.phd.nlp.segmenter.StringProcessing;
import ie.tcd.fyp.retrieval.DBSlice;


/**
 * Class for query objects
 * @author Eleanor Rutherford
 *
 */
public class TrecQuery {
	
	boolean normalizePassages = false;
	boolean useOneGranLevel = false;
	boolean useFilterGranLevel = false;
	boolean useDocs = false;
	boolean useAllPassages = false;
	int granularityLevelForPassages;
	int id;
	String title;
	String description;
	String narrative;
	String chosenQueryContent;
	String trecCollection;
	MongoESASearcher esa;
	public String fileToPrintTo;
	public Slice queryAsSlice;
	public HashMap<String, Double> weightedScoresForDocsAssociatedWithQuery = new HashMap<String, Double>();
	//the key string in the map is the doc id, the key string in the second map is the slice id and the double is the slice score
	Map<String,Map<String,Double>> slices = new HashMap<String,Map<String,Double>>();
	
	/**
	 * 
	 * @param id - the topic/query id
	 * @param title - the title of the topic
	 * @param description - the content of the description field
	 * @param narrative - the content of the narrative field
	 * @param trecCollection - 
	 * this constructor assigns the above variables, sets the content of the query according to one of the 
	 * helper functions setQueryContentTo... and converts the query to a "slice" (so that we can get the concept vector for it)
	 * @throws IOException
	 */
	public TrecQuery(int id, String title, String description, String narrative, String trecCollection) throws IOException {
		this.id = id;
		this.title = title;
		this.description = description;
		this.narrative = narrative;
		this.trecCollection = trecCollection;
		setQueryContentToDesc();//change this depending on what content you want in your query
		this.queryAsSlice = convertQueryToSlice();
	}
	
	public void setQueryContentToTitle() {
		this.chosenQueryContent = this.title;
	}
	
	public void setQueryContentToDesc() {
		this.chosenQueryContent = this.description;
	}
	
	public void setQueryContentToNarr() {
		this.chosenQueryContent = this.narrative;
	}
	
	public void normalizePassages() {
		normalizePassages = true;
	}
	
	public void oneGranLevel(int granularityLevelForPassages) {
		useOneGranLevel = true;
		this.granularityLevelForPassages = granularityLevelForPassages;
		System.out.println("the slices present at the gran. level s.t the number of slices is " + 
				this.granularityLevelForPassages + " were used in retrieval." + " the slices were normalized: "
				+ this.normalizePassages);
	}
	
	public void multipleGranLevels() {
		useFilterGranLevel = true;
	}
	

	/**
	 * the purpose of this method is to get the centroid vector for the query - the method for doing so
	 * requires a slice so first, we have to wrap the query in a slice object
	 * @return the query as a slice
	 * @throws IOException
	 */
	private Slice convertQueryToSlice() throws IOException {
		
		StringProcessing sp = new StringProcessing();
		//this is a bit redundant when we're just using short queries, but it lets us frontload the work in case we want to experiment with longer ones later on
		ArrayList<String> queryAsStrings = sp.splitTextIntoSentences(this.chosenQueryContent); 
		ArrayList<ArrayList<String>> querySentencesAsTerms = sp.buildSentencesAsTermsForQuery(queryAsStrings);
		ArrayList<String> uniqueTermsInQuery = sp.getDocumentUniqueTerms(querySentencesAsTerms);
		
		TermsPool termsPoolInQuery = new TermsPool(uniqueTermsInQuery);
		ArrayList <String> terms = sp.getTerms(this.chosenQueryContent);
		
		Slice querySlice = new Slice(0,terms,this.id,false);

		//this is using the method i use to get centroid vector for slices and docSlice
		//the method getConceptVector for QUERY relies on database accesses - i'm sticking with term pool instead
		try {
			esa = new MongoESASearcher();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
		esa.getConceptVector(querySlice,termsPoolInQuery);
		
		return querySlice;
	
	}
	
	//these methods are for the various experiments - which docs/passages do we take into account and how
	public void docsOnly(String idEntry, double scoreEntry, double queryConceptScore) {
		if(idEntry.contains("DOC"))
			dealWithDocs(idEntry,scoreEntry,queryConceptScore);	
	}
	
	public void allPassages(DBSlice sliceEntry,double queryConceptScore) {
		if(!sliceEntry.getId().contains("DOC")) {
			dealWithSlices(sliceEntry,queryConceptScore,sliceEntry.getSizeOfDoc());
		}	
	}
	
	public void passagesAtSetGranLevel(DBSlice sliceEntry, double queryConceptScore, int granLevel) {
		if(!sliceEntry.getId().contains("DOC")) {
			slicesAtOneGranularityLevel(sliceEntry,queryConceptScore,granLevel);
		}		
	}
	
	public void passagesAtMultipleGranLevels(DBSlice sliceEntry, double queryConceptScore) {
		if(!sliceEntry.getId().contains("DOC")) {
			slicesAtAllGranularityLevels(sliceEntry,queryConceptScore);
		}
	}
		
	
	private void slicesAtOneGranularityLevel(DBSlice slice,double queryConceptScore,int granularityLevel) {
		if(slice.getNumOfSlices()==granularityLevel) {
		   dealWithSlices(slice,queryConceptScore,slice.getNumOfSlices());
	   }
		
	}

	private void slicesAtAllGranularityLevels(DBSlice slice,double queryConceptScore) {
		int sizeOfDoc = slice.getSizeOfDoc();
		int numOfSlices = slice.getNumOfSlices();
		
		if(sizeOfDoc>700) {
			if(numOfSlices<=10)
				dealWithSlices(slice,queryConceptScore,numOfSlices);
		}
		else if(sizeOfDoc<700 && sizeOfDoc>=400) {
			if(numOfSlices<=6)
				dealWithSlices(slice,queryConceptScore,numOfSlices);
		}
		else if(sizeOfDoc<400 && sizeOfDoc>=100) {
			if(numOfSlices<=3)
				dealWithSlices(slice,queryConceptScore,numOfSlices);
		}
		else if(sizeOfDoc<100 && sizeOfDoc>0) {
			if(numOfSlices==2)
				dealWithSlices(slice,queryConceptScore,numOfSlices);
		}

	}

	private void dealWithDocs(String idEntry, double scoreEntry, double queryConceptScore) {
		String docId = getDocId(idEntry);
		Double entireDocScore = scoreEntry;
		entireDocScore = entireDocScore*queryConceptScore; 
		addScoreToQueryMap(docId, entireDocScore,weightedScoresForDocsAssociatedWithQuery);
		
	}
	
	private void addScoreToQueryMap(String docId, Double score, HashMap<String,Double> mapToAddTo) {
		if(mapToAddTo.containsKey(docId)) {
			Double currentScore = mapToAddTo.get(docId);
			mapToAddTo.put(docId, score+currentScore);
		}
		else {	
			mapToAddTo.put(docId,score);
		}	
	}

	private void dealWithSlices(DBSlice slice, double queryConceptScore, double normalizationFactor) {
		String docId = getDocIdForSlice(slice.getId()); 
		String sliceId = slice.getId();
		Double sliceScore = slice.getScoreEntry();
		sliceScore = sliceScore * queryConceptScore;
		Map<String,Double> slicesWithScores = (Map<String,Double>) slices.get(docId);
		if(this.normalizePassages)
			sliceScore = sliceScore/normalizationFactor;
		if(slicesWithScores!=null) {
			slicesWithScores.put(sliceId,sliceScore);
		}
		else {
			slicesWithScores = new HashMap<String, Double>();
			slicesWithScores.put(sliceId,sliceScore);
		}
		slices.put(docId,slicesWithScores);
	
	}
	
	//TODO: redo this method
	//this will add only the best slice for each doc to the map 
	/*public void addBestSlicesToMap() {
		//iterate thru slice map
		//sorts each doc entry in reverse order - the first element is the highest passage score for the doc
		for (Map.Entry<String, Map<String,Double>> entry : slices.entrySet()) {
			Map <String,Double> slicesInDoc = entry.getValue();
			slicesInDoc = sortByValue(slicesInDoc);
			Entry<String,Double> bestSlice = slicesInDoc.entrySet().stream().findFirst().get();
			String docId = entry.getKey();
			//multiply by 7 for a different weighting scheme
		
			addScoreToQueryMap(docId,bestSlice.getValue());
		}	
		
	}*/
	
	public void rankBySumOfPassages() {
		for (Map.Entry<String, Map<String,Double>> documentEntry : slices.entrySet()) {
			Map <String,Double> slicesInDoc = documentEntry.getValue();
			double scoreForPassage = addUpSliceScores(slicesInDoc);
			String docId = documentEntry.getKey();
			addScoreToQueryMap(docId,scoreForPassage,weightedScoresForDocsAssociatedWithQuery);//removed normalization, see notes
			}
		//System.out.println("A summation of all passage scores were used to rank documents "
			//	+ "according to their combined passage score");
	}
	
	public static double addUpSliceScores(Map<String,Double> sliceScoresForDoc) {
		double score=0;
		for (Entry<String, Double> entry : sliceScoresForDoc.entrySet()) {
			score += entry.getValue();
		}	
		return score; 
	}
	
	//TODO: this one too
	//method code from https://dzone.com/articles/how-to-sort-a-map-by-value-in-java-8
    /*public static HashMap<String, Double> sortByValue(HashMap<String, Double> slices) {

        return slices.entrySet()

                .stream()

                .sorted((Map.Entry.<String, Double>comparingByValue().reversed()))

                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));

    }*/

   private static String getDocIdForSlice(String sliceKey) {
	   int end = sliceKey.indexOf("#");
	   return sliceKey.substring(0,end);
   }
   
   /*private static String getSliceId(String sliceKey) {
	   int end = sliceKey.indexOf("iter");
	   return sliceKey.substring(0,end);
   }*/
   
   private static String getDocId(String sliceKey){
	   int end = sliceKey.indexOf("_DOC");
	   return sliceKey.substring(0,end);
   }
		
	
   	//question about hashmap ordering - is this actually printing the top 1000 or A one thousand?
	public void printDocAssociationScoresForQueryToFile() throws IOException {
		
		FileWriter fileWriter = new FileWriter(fileToPrintTo, true);
		BufferedWriter bufferedFileWriter = new BufferedWriter(fileWriter);
		//weightedScoresForDocsAssociatedWithQuery = sortByValue(weightedScoresForDocsAssociatedWithQuery);
		int counter = 1000;
		for (HashMap.Entry<String, Double> documentInSet : weightedScoresForDocsAssociatedWithQuery.entrySet()) {
			//if(counter==0)
				//break;
			String docId = documentInSet.getKey();
			double docQueryScore = documentInSet.getValue();
			
			bufferedFileWriter.write(this.id + " 0 " + docId + " 0 " + docQueryScore + " 0 ");
			bufferedFileWriter.newLine();
			//counter--;
		}
		bufferedFileWriter.flush();
		bufferedFileWriter.close();
		
	}
}
