package ie.tcd.fyp.retrieval;

import static com.mongodb.client.model.Filters.eq;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;

import org.bson.Document;

import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

import ie.adaptcentre.tcd.phd.indexer.CHTSDocumentIndexer;
import ie.tcd.fyp.trec.TrecQuery;
import ie.tcd.fyp.trec.TrecQueryParser;

/**
 * 
 * @author Eleanor Rutherford
 *
 */
public class Retriever {
	
	static MongoCollection<Document> collection;
	CHTSDocumentIndexer indexer;
	String queryFilePath;
	String dbName;
	String documentCollectionPath;
	boolean index;
	
	/**
	 * 
	 * @param queryFilePath - the path to the file containing the queries (Trec topics)
	 * @param dbName - the name of the database to be used during indexing (a new db will be created if
	 * the one specified doesn't exist
	 * @param documentCollectionPath - the file path to the DIRECTORY where the documents to be indexed live.
	 * This directory can contain as many sub directories as you like
	 * @param index - this will be either true or false - should we index the collection or not.
	 * index should be false if we're just doing query experiments.
	 * 
	 *A CHTSDocumentIndexer is created based on the params specified above. 
	 */
	
	public Retriever(String queryFilePath, String dbName, String documentCollectionPath, String index) {
		this.queryFilePath = queryFilePath;
		this.dbName = dbName;
		this.documentCollectionPath = documentCollectionPath;
		this.indexer = new CHTSDocumentIndexer(this.dbName,this.documentCollectionPath);
		if(index.contentEquals("true"))
			this.index = true;
	}
	
	public static void main(String [] args) throws FileNotFoundException, IOException  {
		String topicsFilePath = args[0];
		String dbName = args[1];
		String dataDir = args[2];
		String index = args[3];
		Retriever r = new Retriever(topicsFilePath,dbName,dataDir,index);
		r.performRetrieval();
	}
	
	/**
	 * performs indexing for files specified in directory when creating Retriever object
	 * processes (i.e. parses) queries found in file, also specified during Retriever object creation
	 * ranks the queries 
	 * @throws IOException
	 * @throws FileNotFoundException
	 */
	public void performRetrieval () throws IOException, FileNotFoundException {
		if(index)
			indexer.runTrec();
		else
			indexer.initDB();//this line is only necessary if we are NOT indexing but just experimenting with queries
		getCollection();
		ArrayList<TrecQuery> queries = processQueryFile();
		ArrayList<String> experiments = new ArrayList<String>();
		
		//all following queries are title only, with 20 components in query vector
		//0: docs only = d
		//1: all passages only (normalized according to doc size) = all_p_norm
		//2: docs + all passages (normalized according to doc size) = d_all_p_norm
		//3: passages only (using filter method) = p_f (so added up)
		//4: passages only (using filter method) = p_f_norm (so added up & normalized)
		//5: docs + passages using filter method = d_p_f
		//6: passages only (gran = 2) = p_g_2
		//7: docs + passages, (gran = 2) = d_p_g_2
		//8: docs + best passage (out of all passages) = d_b_p_all_p
		//9: docs + best passage (out of all passages, normalized) = d_b_p_all_p_norm
		
		experiments.add(0,"d");
		experiments.add(1,"all_p_norm");
		experiments.add(2,"d_all_p_norm");
		experiments.add(3,"p_f");
		experiments.add(4,"p_f_norm");
		experiments.add(5,"d_p_f");
		experiments.add(6,"p_g_2");
		experiments.add(7,"d_p_g_2");
		experiments.add(8,"d_b_p_all_p");
		experiments.add(9,"d_b_p_all_p_norm");
		
		//TODO write methods for the following experiments
		//tenth exp: docs + best passage (out of passages with gran=2) d_b_p_g_2
		//eleventh exp: docs + best passages (out of all passages using filter method) d_b_p_f
		//twelvth exp: passages only (using filter method, normalized according to number of passages) p_f_norm
		//experiments.add(9,"d_b_p_all_p");
		//experiments.add(10,"d_b_p_all_p_norm");
		//experiments.add(11,"d_b_p_g_2");
		//experiments.add(12,"d_b_p_f");
		
		
		for(int i=0;i<experiments.size();i++) {
			String expId = experiments.get(i);
			
			for(TrecQuery q : queries) {
				q.fileToPrintTo = "/home/eleanor/exp_results/" + expId; //we set this too many times..
				rankQuery(q, expId); //does this belong here?
			}
		}
	}
	
	//helper function to ensure we access the same database as the one used for indexing
	private void getCollection() {
		MongoDatabase database = indexer.getMongoClient().getDatabase(indexer.getDBName());
		collection = database.getCollection("concepts");		
	}
	
	/**
	 * 
	 * @return an array list containing query objects which represent the parsed Trec topics
	 * @throws FileNotFoundException
	 */
	private ArrayList<TrecQuery> processQueryFile() throws FileNotFoundException {
		BufferedReader queryReader = new BufferedReader(new FileReader(queryFilePath)); 
		TrecQueryParser queryParser = new TrecQueryParser(queryReader);
		ArrayList<TrecQuery> queries = new ArrayList<TrecQuery>();
		queries = queryParser.parseQueryFile();
		return queries;
	}

	/**
	 * 
	 * @param currentQuery - the query we're currently ranking
	 * @param expId - the id of the experiment we are currently running
	 * @throws IOException
	 * get the centroid vector for the query we're currently looking at
	 * for each concept in the query's centroid vector, find the documents relevant to that concept
	 * and calculate the association scores between each document relevant to the concept, and the query
	 * Once this process has been carried out for all queries, print the queries + document scores to a file
	 * according to the format needed for trec_eval
	 */
	private static void rankQuery(TrecQuery currentQuery, String expId) throws IOException {
		
		//for all concepts in query centroid vector
		for (Map.Entry<Integer, Double> entry : currentQuery.queryAsSlice.topConceptsVector.entrySet()) {
			int conceptId = entry.getKey();
			double queryConceptScore = entry.getValue();
			//find all the slices and docs relevant to the concept
			Document dbEntryForCurrentConcept = collection.find(eq("_id",conceptId)).first();
			//as long as the concept actually is in the database
			if(dbEntryForCurrentConcept!=null) {
				@SuppressWarnings("unchecked")
				ArrayList<org.bson.Document> sliceList =  (ArrayList<org.bson.Document>) dbEntryForCurrentConcept.get("slices");
				//for that concept, record document-query scores and slice-query scores
				switch(expId) {
				case "d" : 
					docsOnly(sliceList, currentQuery, queryConceptScore);
					break;
				case "all_p_norm" :
					allPassages(sliceList,currentQuery,queryConceptScore,true);
					currentQuery.rankBySumOfPassages();
					break;
				case "d_all_p_norm":
					docsOnly(sliceList, currentQuery, queryConceptScore);
					allPassages(sliceList,currentQuery,queryConceptScore,true);
					currentQuery.rankBySumOfPassages();
					break;
				case "p_f" :
					filteredPassages(sliceList,currentQuery,queryConceptScore,false);
					currentQuery.rankBySumOfPassages();
					break;
				case "p_f_norm":
					filteredPassages(sliceList,currentQuery,queryConceptScore,true);
					currentQuery.rankBySumOfPassages();
					break;
				case "d_p_f" :
					docsOnly(sliceList, currentQuery, queryConceptScore);
					allPassages(sliceList,currentQuery,queryConceptScore,true);
					currentQuery.rankBySumOfPassages();
					break;
				case "p_g_2" :
					passagesAtSetGranLevel(sliceList,currentQuery,queryConceptScore,2);
					currentQuery.rankBySumOfPassages();
					break;
				case "d_p_g_2" :
					docsOnly(sliceList, currentQuery, queryConceptScore);
					passagesAtSetGranLevel(sliceList,currentQuery,queryConceptScore,2);
					currentQuery.rankBySumOfPassages();
					break;
				case "d_b_p_all_p":
					docsOnly(sliceList, currentQuery, queryConceptScore);
					allPassages(sliceList,currentQuery,queryConceptScore,false);
					currentQuery.addBestSlicesToMap();
					break;
				case "d_b_p_all_p_norm":
					docsOnly(sliceList, currentQuery, queryConceptScore);
					allPassages(sliceList,currentQuery,queryConceptScore,true);
					currentQuery.addBestSlicesToMap();
					break;
				}
					
			}
		}
		//once we've dealt with all concepts in the query vector, print the scores for each doc
		//this will print to a file with its experiment id as the filename 
		currentQuery.printDocAssociationScoresForQueryToFile();
	}
	
	
	/*The following are all helper methods for experiments
	 * */
	/**
	 * 
	 * @param sliceList
	 * @param currentQuery
	 * @param queryConceptScore
	 */
	private static void docsOnly(ArrayList<org.bson.Document> sliceList, TrecQuery currentQuery, double queryConceptScore) {
		for (org.bson.Document slice : sliceList) {
			String idEntry = (String) slice.get("id");
			Double scoreEntry = (Double) slice.get("score");
			currentQuery.docsOnly(idEntry,scoreEntry,queryConceptScore);
		}
	}
	
	private static void allPassages(ArrayList<org.bson.Document> sliceList, TrecQuery currentQuery, double queryConceptScore, boolean norm) {
		for (org.bson.Document slice : sliceList) {
			   String idEntry = (String) slice.get("id");
			   Double scoreEntry = (Double) slice.get("score");
			   int numOfSlices = 0;
			   int sizeOfDoc = 0; //in sentences
			   if(!idEntry.contains("DOC")) {
				   numOfSlices = (Integer) slice.get("currentNumberOfSlices");
				   sizeOfDoc = (Integer) slice.get("doc_size");   
			   }
			   DBSlice sliceEntry = new DBSlice(idEntry,scoreEntry,numOfSlices,sizeOfDoc);
		
			   if(norm) {
				   currentQuery.normalizePassages();
				   currentQuery.allPassages(sliceEntry,queryConceptScore);
			   }
			   else 
				   currentQuery.allPassages(sliceEntry, queryConceptScore);
		}
	}
	

	private static void filteredPassages(ArrayList<org.bson.Document> sliceList, TrecQuery currentQuery, double queryConceptScore, boolean norm) {
		for (org.bson.Document slice : sliceList) {
			   String idEntry = (String) slice.get("id");
			   Double scoreEntry = (Double) slice.get("score");
			   int numOfSlices = 0;
			   int sizeOfDoc = 0; //in sentences
			   if(!idEntry.contains("DOC")) {
				   numOfSlices = (Integer) slice.get("currentNumberOfSlices");
				   sizeOfDoc = (Integer) slice.get("doc_size");   
			   }
			   DBSlice sliceEntry = new DBSlice(idEntry,scoreEntry,numOfSlices,sizeOfDoc);
			   if(norm) {
				   //is this the correct method of normalization?
				   currentQuery.normalizePassages();
				   currentQuery.passagesAtMultipleGranLevels(sliceEntry,queryConceptScore);
			   }
			   else 
				   currentQuery.passagesAtMultipleGranLevels(sliceEntry,queryConceptScore);
		}
	}
	
	private static void passagesAtSetGranLevel(ArrayList<org.bson.Document> sliceList, TrecQuery currentQuery, double queryConceptScore, int granLevel) {
		for (org.bson.Document slice : sliceList) {
			   String idEntry = (String) slice.get("id");
			   Double scoreEntry = (Double) slice.get("score");
			   int numOfSlices = 0;
			   int sizeOfDoc = 0; //in sentences
			   if(!idEntry.contains("DOC")) {
				   numOfSlices = (Integer) slice.get("currentNumberOfSlices");
				   sizeOfDoc = (Integer) slice.get("doc_size");   
			   }
			   DBSlice sliceEntry = new DBSlice(idEntry,scoreEntry,numOfSlices,sizeOfDoc);
			   currentQuery.passagesAtSetGranLevel(sliceEntry,queryConceptScore,granLevel);
		}
	}

}
