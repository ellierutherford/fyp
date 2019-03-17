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


/**
 * Class for query objects
 * @author Eleanor Rutherford
 *
 */
public class TrecQuery {
	
	int id;
	String title;
	String description;
	String narrative;
	String chosenQueryContent;
	String trecCollection;
	MongoESASearcher esa;
	public Slice queryAsSlice;
	public HashMap<String, Double> weightedScoresForDocsAssociatedWithQuery = new HashMap<String, Double>();
	HashMap<String,Double> scoresForNormalizationDocs = new HashMap<String,Double>();
	double scoresForNormalizationQueries=0;
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
	
	public void setQueryContentToTitle() {
		this.chosenQueryContent = this.title;
	}
	
	public void setQueryContentToDesc() {
		this.chosenQueryContent = this.description;
	}
	
	public void setQueryContentToNarr() {
		this.chosenQueryContent = this.narrative;
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
	
public void computeDocumentAssociationScoresForQuery(org.bson.Document dbEntryForConcept, Entry<Integer, Double> conceptFromQueryCentroid) {
		
	   ArrayList<org.bson.Document> sliceList =  (ArrayList<org.bson.Document>) dbEntryForConcept.get("slices");
	   double queryRelevanceScore = conceptFromQueryCentroid.getValue();
	   scoresForNormalizationQueries+=(queryRelevanceScore*queryRelevanceScore);

	   for (org.bson.Document slice : sliceList) {
		  
		   String idEntry = (String) slice.get("id");
		   Double scoreEntry = (Double) slice.get("score");
		   int numOfSlices = 0;
		   int sizeOfDoc = 0;
		   if(!idEntry.contains("DOC")) {
			   numOfSlices = (Integer) slice.get("currentNumberOfSlices");
			   sizeOfDoc = (Integer) slice.get("doc_size");
		   }
		   
		   /*if(numOfSlices==3) {
			   dealWithSlices(idEntry,scoreEntry, conceptFromQueryCentroid);
		   }
		   //TODO another method here for experimenting with document length & no. of passages
		   else if(idEntry.contains("DOC")) {
			   dealWithDocs(idEntry,scoreEntry,conceptFromQueryCentroid);
		   }  */
		   if(idEntry.contains("DOC")) {
			   dealWithDocs(idEntry,scoreEntry,conceptFromQueryCentroid);
		   }  
		  
		   else {
			   //if(numOfSlices==2)
				   //dealWithSlices(idEntry,scoreEntry, conceptFromQueryCentroid);
		   }
	   }
	   
	   //normalizeAllScores();
	   
	}

	private void normalizeAllScores() {
		for (Map.Entry<String,Double> document : weightedScoresForDocsAssociatedWithQuery.entrySet()) {
			normalizeScoreForDoc(document.getKey());
		}
	}
	
	private void normalizePassages() {
		
	}
	
	//not entirely sure about this ....
	private void normalizeScoreForDoc(String docId) {
		double normOfDoc = Math.sqrt(weightedScoresForDocsAssociatedWithQuery.get(docId));
		double normOfQuery = Math.sqrt(scoresForNormalizationQueries);
		double normFactor = normOfDoc * normOfQuery;
		weightedScoresForDocsAssociatedWithQuery.put(docId,weightedScoresForDocsAssociatedWithQuery.get(docId)/normFactor); 
	}

	private void dealWithDocs(String idEntry, Double scoreEntry, Entry<Integer, Double> conceptFromQueryCentroid) {
		String docId = getDocId(idEntry);
		Double entireDocScore = scoreEntry/**scoreEntry*/;
		//addScoreToQueryMap(docId,entireDocScore,scoresForNormalizationDocs);
		entireDocScore = entireDocScore*conceptFromQueryCentroid.getValue(); 
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

	private void dealWithSlices(String idEntry, Double scoreEntry, Entry<Integer, Double> queryScoreWithConcept) {
		String docId = getDocIdForSlice(idEntry); 
		String sliceId = idEntry;
		Double sliceScore = scoreEntry;
		sliceScore = sliceScore * queryScoreWithConcept.getValue();
		Map<String,Double> slicesWithScores = (Map<String,Double>) slices.get(docId);
		if(slicesWithScores!=null) {
			slicesWithScores.put(sliceId,sliceScore);
		}
		else {
			slicesWithScores = new HashMap<String, Double>();
			slicesWithScores.put(sliceId,sliceScore);
		}
		slices.put(docId,slicesWithScores);
	
	}
	
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
	
	public void rankDocs() {
		for (Map.Entry<String, Map<String,Double>> documentEntry : slices.entrySet()) {
			Map <String,Double> slicesInDoc = documentEntry.getValue();
			double scoreForPassage = addUpSliceScores(slicesInDoc);
			String docId = documentEntry.getKey();
			addScoreToQueryMap(docId,scoreForPassage,weightedScoresForDocsAssociatedWithQuery);//removed normalization, see notes
			}
		
	}
	
	public static double addUpSliceScores(Map<String,Double> sliceScoresForDoc) {
		double score=0;
		for (Entry<String, Double> entry : sliceScoresForDoc.entrySet()) {
			score += entry.getValue();
		}	
		return score;
	}
	
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
	public void printDocAssociationScoresForQueryToFile(String outFile) throws IOException {
		
		FileWriter fileWriter = new FileWriter(outFile, true);
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
