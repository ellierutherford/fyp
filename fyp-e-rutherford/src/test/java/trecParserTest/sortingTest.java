package trecParserTest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;

import ie.tcd.fyp.trec.TrecQuery;

public class sortingTest {

	@Test
	public void testSorter() {
		HashMap<String,Double> slicesInDoc = new HashMap<String,Double>();
		slicesInDoc.put("a",1.0);
		slicesInDoc.put("b", 2.5);
		slicesInDoc.put("c",120.2);
		slicesInDoc.put("d",12.0);
		slicesInDoc.put("e",-1.2);
		slicesInDoc.put("f",10.2);
		LinkedHashMap<String,Double> map = (LinkedHashMap<String, Double>) TrecQuery.sortMap(slicesInDoc);
		LinkedHashMap<String,Double> testyMap = new LinkedHashMap<String,Double>();
		testyMap.put("c",120.2);
		testyMap.put("d",12.0);
		testyMap.put("f",10.2);
		testyMap.put("b", 2.5);
		testyMap.put("a",1.0);
		testyMap.put("e",-1.2);
		assertTrue(testyMap.equals(slicesInDoc));
		

	}
	
	
}
