package trecParserTest;

import static org.junit.Assert.assertEquals;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import ie.tcd.fyp.trec.TrecQuery;

public class sortingTest {

	@Test
	public void testSorter() {
		Map<String,Double> slicesInDoc = new HashMap<String,Double>();
		slicesInDoc.put("a",1.0);
		slicesInDoc.put("b", 2.5);
		slicesInDoc.put("c",120.2);
		slicesInDoc.put("d",12.0);
		slicesInDoc.put("e",-1.2);
		slicesInDoc.put("f",10.2);
		
		

	}
}
