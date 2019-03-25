package ie.tcd.fyp.trec;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
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
		setQueryContentToTitle();//change this depending on what content you want in your query
		this.queryAsSlice = convertQueryToSlice();
	}
	
	public void cleanQuery() {
		this.weightedScoresForDocsAssociatedWithQuery = new HashMap<String,Double>();
		this.slices = new HashMap<String,Map<String,Double>>();
		this.normalizePassages = false;
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
		if(uniqueTermsInQuery==null)
			System.out.println("stop");
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
		esa.getConceptVector(querySlice,termsPoolInQuery,20);//limit for number of concepts in query vec
		
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
			//where granLevel is the number of slices in the doc at a level of granularity e.g. if
			//granLevel = 2, that means there are two slices for the doc at this level
			if(sliceEntry.getNumOfSlices()==granLevel) {
				   dealWithSlices(sliceEntry,queryConceptScore,sliceEntry.getNumOfSlices());
			}
		}		
	}
	
	public void passagesAtMultipleGranLevels(DBSlice sliceEntry, double queryConceptScore) {
		int numOfSlices = sliceEntry.getNumOfSlices();
		int sizeOfSlice = sliceEntry.getSizeOfSlice();
		if(!sliceEntry.getId().contains("DOC")) {
			if(sizeOfSlice>=1 && sizeOfSlice<=3)
				dealWithSlices(sliceEntry,queryConceptScore,1);
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
		Map<String,Double> slicesForDoc = (Map<String,Double>) slices.get(docId);
		if(this.normalizePassages)
			sliceScore = sliceScore/normalizationFactor;
		if(slicesForDoc!=null) {
			Double currentScoreForPassage = slicesForDoc.get(sliceId);
			//if the slice already has an entry in the map
			if(currentScoreForPassage!=null)
			    slicesForDoc.put(sliceId, currentScoreForPassage+sliceScore);
			else 
			    slicesForDoc.put(sliceId,sliceScore);
		}
		else {
			slicesForDoc = new HashMap<String, Double>();
			slicesForDoc.put(sliceId,sliceScore);
		}
		slices.put(docId,slicesForDoc);
	
	}
	
	public void addBestSliceToMap() {
		
		for (Map.Entry<String, Map<String,Double>> entry : slices.entrySet()) {
			Map <String,Double> slicesInDoc = entry.getValue();
			Entry<String,Double> bestSlice = maxValueInMap(slicesInDoc);
			String docId = entry.getKey();
			addScoreToQueryMap(docId,bestSlice.getValue(),weightedScoresForDocsAssociatedWithQuery);
		}	
		
	}
	
	
	public void rankBySumOfPassages() {
		for (Map.Entry<String, Map<String,Double>> documentEntry : slices.entrySet()) {
			Map <String,Double> slicesInDoc = documentEntry.getValue();
			double scoreForPassage = addUpSliceScores(slicesInDoc);
			String docId = documentEntry.getKey();
			addScoreToQueryMap(docId,scoreForPassage,weightedScoresForDocsAssociatedWithQuery);
			}
	}
	
	public static double addUpSliceScores(Map<String,Double> sliceScoresForDoc) {
		double score=0;
		for (Entry<String, Double> entry : sliceScoresForDoc.entrySet()) {
			score += entry.getValue();
		}	
		return score; 
	}
	
	//method code modified from https://www.mkyong.com/java/how-to-sort-a-map-in-java/
	public static Entry<String,Double> maxValueInMap(Map<String, Double> slicesInDoc) {

		Map.Entry<String, Double> maxEntry = null;
		
		for (Map.Entry<String, Double> entry : slicesInDoc.entrySet())
		{
		    if (maxEntry == null || entry.getValue().compareTo(maxEntry.getValue()) > 0)
		    {
		        maxEntry = entry;
		    }
		}
		return maxEntry;
    }

   private static String getDocIdForSlice(String sliceKey) {
	   int end = sliceKey.indexOf("#");
	   return sliceKey.substring(0,end);
   }
   
 
   private static String getDocId(String sliceKey){
	   int end = sliceKey.indexOf("_DOC");
	   return sliceKey.substring(0,end);
   }
		
   //code for sort method from https://www.mkyong.com/java/how-to-sort-a-map-in-java/
   public static Map<String,Double> sortMap(HashMap<String,Double> mapToSort){
	   List<Map.Entry<String,Double>> entries = new ArrayList<>(mapToSort.entrySet());
	   Collections.sort(entries, new Comparator<Map.Entry<String, Double>>() {
           public int compare(Map.Entry<String, Double> o1,
                              Map.Entry<String, Double> o2) {
               return (o2.getValue()).compareTo(o1.getValue());
           }
       });
	
	   Map<String, Double> sortedMap = new LinkedHashMap<String, Double>();
       for (Map.Entry<String, Double> entry : entries) {
           sortedMap.put(entry.getKey(), entry.getValue());
       }
       
       return sortedMap;
   }
	
	public void printDocAssociationScoresForQueryToFile() throws IOException {
		
		FileWriter fileWriter = new FileWriter(fileToPrintTo, true);
		BufferedWriter bufferedFileWriter = new BufferedWriter(fileWriter);
		int counter = 1000;
		
		//sort score map by value
		LinkedHashMap<String,Double> linkedMap = (LinkedHashMap<String, Double>) sortMap(weightedScoresForDocsAssociatedWithQuery);
		
		for (Entry<String, Double> documentInSet : linkedMap.entrySet()) {
			if(counter==0)
				break;
			String docId = documentInSet.getKey();
			double docQueryScore = documentInSet.getValue();
			
			bufferedFileWriter.write(this.id + " 0 " + docId + " 0 " + docQueryScore + " 0 ");
			bufferedFileWriter.newLine();
			counter--;
		}
		bufferedFileWriter.flush();
		bufferedFileWriter.close();
		
	}

}
