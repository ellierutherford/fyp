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
	
	/**
	 * 
	 * @param queryFilePath - the path to the file containing the queries (Trec topics)
	 * @param dbName - the name of the database to be used during indexing (a new db will be created if
	 * the one specified doesn't exist
	 * @param documentCollectionPath - the file path to the DIRECTORY where the documents to be indexed live
	 * This directory can contain as many sub directories as you like
	 * A CHTSDocumentIndexer is created based on the params specified above 
	 */
	
	public Retriever(String queryFilePath, String dbName, String documentCollectionPath) {
		this.queryFilePath = queryFilePath;
		this.dbName = dbName;
		this.documentCollectionPath = documentCollectionPath;
		this.indexer = new CHTSDocumentIndexer(this.dbName,this.documentCollectionPath);
	}
	
	public static void main(String [] args) throws FileNotFoundException, IOException  {
		Retriever r = new Retriever("/home/eleanor/Senior_Soph/fyp/trec8_topics/topics.401-450",
				"fyp_test","/home/eleanor/Senior_Soph/fyp/test_dir_normalization");
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
		indexer.runTrec();
		//indexer.initDB();//this line is only necessary if we are NOT indexing but just experimenting with queries
		getCollection();
		ArrayList<TrecQuery> queries = processQueryFile();
		for(TrecQuery q : queries) {
			rankQuery(q); //does this belong here?
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
	 * @throws IOException
	 * get the centroid vector for the query we're currently looking at
	 * for each concept in the query's centroid vector, find the documents relevant to that concept
	 * and calculate the association scores between each document relevant to the concept, and the query
	 * Once this process has been carried out for all queries, print the queries + document scores to a file
	 * according to the format needed for trec_eval
	 */
	private static void rankQuery(TrecQuery currentQuery) throws IOException {
		 
		for (Map.Entry<Integer, Double> entry : currentQuery.queryAsSlice.topConceptsVector.entrySet()) {
			int conceptId = entry.getKey();
			Document dbEntryForCurrentConcept = collection.find(eq("_id",conceptId)).first();
			//as long as the concept actually is in the database
			if(dbEntryForCurrentConcept!=null) {
				currentQuery.computeDocumentAssociationScoresForQuery(dbEntryForCurrentConcept, entry);
				//currentQuery.addBestSlicesToMap();
				//currentQuery.addUpSliceScores();
				//currentQuery.rankDocs();
			}
		}
		currentQuery.printDocAssociationScoresForQueryToFile("/home/eleanor/Senior_Soph/fyp/results_la_docs_only_NEW_QUERY_no_tfidf_nrm_last");
	}
}
