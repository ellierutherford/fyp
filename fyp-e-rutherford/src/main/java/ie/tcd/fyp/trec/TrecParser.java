package ie.tcd.fyp.trec;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TrecParser {
String fileToBeRead;
	
	public TrecParser(String fileName) {
		this.fileToBeRead = fileName;
			
	}
	
	public ArrayList<TrecDocument> parseFile(){
		ArrayList<TrecDocument> docs = new ArrayList<TrecDocument>();
		try {
			docs = parse();
		}
		catch (FileNotFoundException e) {
	        System.err.println(e.getMessage());
		}
		catch (IOException e1) {
			System.err.println(e1.getMessage());
		}
		
		return docs;
	}
	
	private ArrayList<TrecDocument> parse() throws FileNotFoundException, IOException{
		BufferedReader fileR = new BufferedReader(new FileReader(this.fileToBeRead));
		ArrayList<TrecDocument> docs = new ArrayList<TrecDocument>();
		String currentLine = "";
		String docId= "";
		String textContent = "";
		String tagContent = "";
		Pattern openTagPattern = Pattern.compile("<[A-Z0-9]+>");
		Pattern closedTagPattern = Pattern.compile("</[A-Z0-9]+>");
		 while((currentLine=fileR.readLine())!=null){
			tagContent = "";
			Matcher openTag = openTagPattern.matcher(currentLine);
			Matcher closeTag = closedTagPattern.matcher(currentLine);
			boolean containsOpenTag = openTag.find();
			boolean containsCloseTag = closeTag.find();
			if(currentLine.contains("<DOCNO>")||currentLine.contains("<TEXT>") || currentLine.contains("</DOC>")) {
				//tag content spans multiple lines 
				if(containsOpenTag && !containsCloseTag) {
					while(!containsCloseTag) {
						tagContent+=currentLine;
						currentLine = fileR.readLine();
						closeTag = closedTagPattern.matcher(currentLine);
						containsCloseTag = closeTag.find();
					}
					tagContent+=closeTag.group(0);
				}
				//tag content is on same line
				else if(containsOpenTag && containsCloseTag) {
					tagContent+=currentLine;
				}
				
				String currentTag = closeTag.group(0);
				String contentBetweenTags = "";
				if(!currentTag.contains("</DOC>")) {
					contentBetweenTags = getStringBetweenTags(tagContent);
				}
				if(currentTag.contentEquals("</DOCNO>")) {
					docId = contentBetweenTags;
				}
				else if(currentTag.contentEquals("</TEXT>")) {
					textContent = contentBetweenTags;
				}
				else if(currentLine.contains("<GRAPHIC>")) {
					textContent += getStringBetweenTags(getCurrentTagContent("</GRAPHIC>",currentLine),"<GRAPHIC>","</GRAPHIC>");
				}
				else if(currentTag.contentEquals("</DOC>")) {
					TrecDocument d = new TrecDocument(docId,textContent);
					docs.add(d);
				}
			}				

		}
		fileR.close();
		return docs;
		
	}
	private String getStringBetweenTags(String lineIncludingTags) {
		int start = lineIncludingTags.indexOf(">");
		int finish = lineIncludingTags.indexOf("<", lineIncludingTags.indexOf("<") + 1);
		return lineIncludingTags.substring(start+1,finish).trim();
	}
}
