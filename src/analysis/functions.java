package analysis;

import info.debatty.java.stringsimilarity.NormalizedLevenshtein;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.Map.Entry;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import twitter.Element;
import twitter.Events;
import twitter.download;

public class functions {
	
	private static NormalizedLevenshtein l = new NormalizedLevenshtein();
	
	public static JSONArray getKeywords(String body) throws IOException, JSONException {
		String stuff = "outputMode=json&text="+URLEncoder.encode(body, "UTF-8");
		String answer = download.alchemyPostRequest("https://gateway-a.watsonplatform.net/calls/text/TextGetRankedKeywords",stuff);
		JSONObject ans = new JSONObject(answer);
		//System.out.println(ans);
		if (ans.has("keywords")) {
			JSONArray keywords = ans.getJSONArray("keywords");
			return keywords;
		}
		return null;
	}
	
	public static HashMap<String,Double> getSentiment(JSONArray keywords, String body) throws IOException, JSONException {
		HashMap<String,Double> sentiments = new HashMap<String,Double>();
		if (keywords == null || keywords.length() == 0) {
			print("NO KEYWORDS");
			return sentiments;
		}
		String word = "";
		for (int i=0;i<keywords.length();i++) {
			word += keywords.getJSONObject(i).getString("text").replaceAll("[^A-Za-z0-9 ]", "")+"|";
		}
		//print(word);
		String stuff = "outputMode=json&text="+URLEncoder.encode(body, "UTF-8")+"&targets="+word;
		//print(stuff);
		String answer = download.alchemyPostRequest("https://gateway-a.watsonplatform.net/calls/text/TextGetTargetedSentiment",stuff);
		//print(new JSONObject(answer).getString("status"));
		if (new JSONObject(answer).has("statusInfo")) return sentiments;
		JSONArray sents = new JSONObject(answer).getJSONArray("results");
		for (int i=0;i<sents.length();i++) {
			double score = 0;
			if (sents.getJSONObject(i).getJSONObject("sentiment").has("score")) {
				score = sents.getJSONObject(i).getJSONObject("sentiment").getDouble("score");
			}
			sentiments.put(sents.getJSONObject(i).getString("text"), score);
		}
		return sentiments;
	}
	
	public static List<HashMap<String, Element>> positivenegative(HashMap<String,Element> result) {
		SortedSet<Element> values = new TreeSet<Element>(new SentimentComparator());
		for (Entry<String, Element> entry : result.entrySet()) {
	        values.add((Element) entry.getValue());
	    }
		HashMap<String,Element> positive = new HashMap<String,Element>();
		HashMap<String,Element> negative = new HashMap<String,Element>();
		for (int j=0;j<5;j++){
			Element lastpos = (Element) values.toArray()[values.toArray().length-1-j];
			String positiv = getKeyByValue(result,lastpos);
			Element firstpos = (Element) values.toArray()[j];
			String negativ = getKeyByValue(result,firstpos);
			positive.put(positiv, lastpos);
			negative.put(negativ, firstpos);
		}
		List<HashMap<String, Element>> myData = new ArrayList<HashMap<String, Element>>();
		myData.add(positive);
		myData.add(negative);
		return myData;
	}
	
	public static <T, E> String getKeyByValue(HashMap<String, Element> result2, Element object) {
	    for (Entry<String, Element> entry : result2.entrySet()) {
	        if (Objects.equals(object, entry.getValue())) {
	            return entry.getKey();
	        }
	    }
	    return null;
	}
	
	public static double getEventScore(Events event,HashMap<String, Element> positive, HashMap<String, Element> negative, Date currentDate) {
		double result = 0;
		for (int i=0;i<event.keywords.size();i++) {
			for (String key : positive.keySet()) {
				long diff = (currentDate.getTime()-positive.get(key).date.getTime())/(1000*60*60*24);
				result += (positive.get(key).sentiment)*(1-l.distance(key, event.keywords.get(i)))/diff;
			}
			for (String key : negative.keySet()) {
				long diff = currentDate.getTime()-negative.get(key).date.getTime()/(1000*60*60*24);
				result += (negative.get(key).sentiment)*(1-l.distance(key, event.keywords.get(i)))/diff;
			}
		}
		return result;
	}
	
	public static String getBestEvent(List<Events> events,HashMap<String, Element> positive, HashMap<String, Element> negative, Date currentDate) {
		String best = events.get(0).name; double top = getEventScore(events.get(0),positive,negative,currentDate);
		print(events.get(0).name+" : "+top);
		for (int i=1; i<events.size();i++) {
			double score = getEventScore(events.get(i),positive,negative,currentDate);
			print(events.get(i).name+" : "+score);
			if (score>top) {
				top = score; best = events.get(i).name;
			}
		}
		return best;
	}

	public static void print(String str) {
		System.out.println(str);
	}
	public static void print(double str) {
		System.out.println(str);
	}
	public static void print(@SuppressWarnings("rawtypes") List str) {
		System.out.println(str.toString());
	}

}

class SentimentComparator implements Comparator<Element> {
    public int compare(Element p1, Element p2){
    	if ((p1.sentiment-p2.sentiment)>=0) {
    		return 1;
    	} else {
    		return -1;
    	}
    }
}
