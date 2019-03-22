package ie.tcd.fyp.trec;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;



public class TrecQueryParser {
	
	BufferedReader reader;
	
	public TrecQueryParser(BufferedReader reader) {
		this.reader = reader;

	}
	
	public ArrayList<TrecQuery> parseQueryFile(){
		ArrayList<TrecQuery> queries = new ArrayList<TrecQuery>();
		
		try {
			queries = parse();
		}
		catch (IOException e1) {
			System.err.println(e1.getMessage());
		}
		
		return queries;
	}
	
	public ArrayList<TrecQuery> parse() throws IOException {
		ArrayList<TrecQuery> queries = new ArrayList<TrecQuery>();
		String currentLine = reader.readLine();
		int id=0;
		String title="";
		String description="";
		String narrative="";
		
		while(currentLine!=null) {
			
			if(currentLine.contains("<num>")) {
				id = Integer.parseInt(currentLine.substring((currentLine.indexOf("Number:")+7),currentLine.length()).trim());
				currentLine = reader.readLine();
			}
			else if(currentLine.contains("<title>")) {
				title = currentLine.substring((currentLine.indexOf("<title>")+7),currentLine.length());
				currentLine = reader.readLine();
				if(title.contentEquals("")) {
					while(!currentLine.contains("<desc>")) {
						if(!currentLine.contains("<title>"))
							title += currentLine;
						currentLine = reader.readLine();
					}
				}
					
			}
			else if(currentLine.contains("<desc>")) {
				while(!currentLine.contains("<narr>")) {
					if(!currentLine.contains("<desc>"))
						description += currentLine;
					currentLine = reader.readLine();
					
				}
			}
			else if(currentLine.contains("<narr>")) {
				while(!currentLine.contains("</top>")) {
					if(!currentLine.contains("<narr>"))
						narrative += currentLine;
					currentLine = reader.readLine();
					
				}
			}
			else if(currentLine.contains("</top>")) {
				queries.add(new TrecQuery(id,title,description,narrative,""));
				title = "";
				description = "";
				narrative = "";
				currentLine = reader.readLine();
			}
			else {
				currentLine = reader.readLine();
			}
			
		}
		return queries;
		
	}
}
