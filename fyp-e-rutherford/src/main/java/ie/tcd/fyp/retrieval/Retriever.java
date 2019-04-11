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
	static String outFile;
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
	
	public Retriever(String queryFilePath, String dbName, String documentCollectionPath, String outFile, String index) {
		this.queryFilePath = queryFilePath;
		this.dbName = dbName;
		this.documentCollectionPath = documentCollectionPath;
		this.indexer = new CHTSDocumentIndexer(this.dbName,this.documentCollectionPath);
		this.outFile = outFile;
		if(index.contentEquals("true"))
			this.index = true;
	}
	
	public static void main(String [] args) throws FileNotFoundException, IOException  {
		String topicsFilePath = args[0];
		String dbName = args[1];
		String dataDir = args[2];
		String outFile = args[3];
		String index = args[4];
		Retriever r = new Retriever(topicsFilePath,dbName,dataDir,outFile,index);
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
	
		
	//for every query, run all experiments
		
		for(TrecQuery q : queries) {
			System.out.println(q.getQueryContent());
			rankQuery(q); 
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
	private static void rankQuery(TrecQuery currentQuery) throws IOException {
		
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
				allPassages(sliceList,currentQuery,queryConceptScore,true);
			}
		}
		
		
		//once we've dealt with all concepts in the query vector, we can calculate the final scores and 
		//print the scores for each doc
		currentQuery.rankBySumOfPassages();
		
		currentQuery.printDocAssociationScoresForQueryToFile(outFile);
	}
	
	
	/*The following are all helper methods for experiments
	 * */
	/**
	 * 
	 * @param sliceList
	 * @param currentQuery
	 * @param queryConceptScore
	 */
	
	private static void allPassages(ArrayList<org.bson.Document> sliceList, TrecQuery currentQuery, double queryConceptScore, boolean norm) {
		for (org.bson.Document slice : sliceList) {
			   String idEntry = (String) slice.get("id");
			   Double scoreEntry = (Double) slice.get("score");
			   int numOfSlices = 0;
			   int sizeOfDoc = 0; //in sentences
			   int sizeOfSlice = 0;
			   if(!idEntry.contains("DOC")) {
				   numOfSlices = (Integer) slice.get("currentNumberOfSlices");
				   sizeOfDoc = (Integer) slice.get("doc_size");  
				   sizeOfSlice = (Integer) slice.get("size");
			   }
			   DBSlice sliceEntry = new DBSlice(idEntry,scoreEntry,numOfSlices,sizeOfDoc,sizeOfSlice);
		
			   if(norm) {
				   currentQuery.normalizePassages();
				   currentQuery.allPassages(sliceEntry,queryConceptScore);
			   }
			   else 
				   currentQuery.allPassages(sliceEntry, queryConceptScore);
		}
	}
	

}
