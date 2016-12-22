package analysis;

import info.debatty.java.stringsimilarity.NormalizedLevenshtein;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import twitter.Element;
import twitter.Events;
import twitter.download;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.*;
import java.util.Map.Entry;

public class functions {

    private static NormalizedLevenshtein l = new NormalizedLevenshtein();
    private static Logger log = LoggerFactory.getLogger(functions.class);

    public static JSONArray getKeywords(String body) throws IOException, JSONException {
        String stuff = "outputMode=json&text="+ URLEncoder.encode(body, "UTF-8");
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
        print("About to do alchemypostrequest");
        print(word);
        String stuff = "outputMode=json&text="+ URLEncoder.encode(body, "UTF-8")+"&targets="+word;
        print(stuff);
        String answer = download.alchemyPostRequest("https://gateway-a.watsonplatform.net/calls/text/TextGetTargetedSentiment",stuff);
        print(new JSONObject(answer).getString("status"));
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
            if (object == entry.getValue()) {
                return entry.getKey();
            }
        }
        return null;
    }

    public static double getEventScore(Events event, HashMap<String, Element> positive, HashMap<String, Element> negative, Date currentDate) {
        double result = 0;
        double score;
        System.out.println("Getting score");
        System.out.println(positive.size());
        System.out.println(negative.size());
        for (int i=0;i<event.keywords.size();i++) {
            for (String key : positive.keySet()) {
                System.out.println(key+" vs "+event.keywords.get(i));
                score = Word2vec.getScore(key,event.keywords.get(i));
                System.out.println("pos score is "+score);
                long diff = (1+currentDate.getTime()-positive.get(key).date.getTime())/(1000*60*60*24);
                result += (positive.get(key).sentiment)*(score)/diff;
                //result += (positive.get(key).sentiment)*(1-l.distance(key, event.keywords.get(i)))/diff;
            }
            for (String key : negative.keySet()) {
                System.out.println(key+" vs "+event.keywords.get(i));
                score = Word2vec.getScore(key,event.keywords.get(i));
                System.out.println("neg score is "+score);
                long diff = (1+currentDate.getTime()-positive.get(key).date.getTime())/(1000*60*60*24);
                result += (negative.get(key).sentiment)*(score)/diff;
                //result += (negative.get(key).sentiment)*(1-l.distance(key, event.keywords.get(i)))/diff;
            }
        }
        return result;
    }

    public static String getBestEvent(List<Events> events, HashMap<String, Element> positive, HashMap<String, Element> negative, Date currentDate) {
        String best = events.get(0).name; double top = getEventScore(events.get(0),positive,negative,currentDate);
        print(events.get(0).name+" : "+top);
        for (int i=1; i<events.size();i++) {
            print("calculating score for "+events.get(i).name);
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