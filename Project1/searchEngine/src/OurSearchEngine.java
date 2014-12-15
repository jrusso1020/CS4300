import java.io.*;
import java.util.*;

import org.apache.lucene.analysis.Analyzer.TokenStreamComponents;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.util.CharArraySet;
import org.apache.lucene.analysis.en.PorterStemFilter;

public class OurSearchEngine {
	
	private HashMap<String,HashMap<String,Integer>> index;
	private String indexfile;
	private MyAnalyzer analyzer;
	private String docPath;
	private HashMap<String,HashMap<String,Double>> tfidf;
	private HashMap<String, HashMap<String,Double>> failureAnalysis;
	boolean print;
	
	public OurSearchEngine() {
		index = new HashMap<String,HashMap<String,Integer>>();
		
		//need stopwords
		CharArraySet stopwords = new CharArraySet(0, false);
		String stopwordfile = "data/stopwords/stopwords_indri.txt";
		try {
			BufferedReader swr = new BufferedReader(new FileReader(stopwordfile));
			String line = null;
			while ((line = swr.readLine()) != null) {
				stopwords.add(line);
			}
			swr.close();
		} catch(IOException e) {
			System.out.println("Could not read the stop word file");
		}
				
		analyzer = new MyAnalyzer(stopwords);
		failureAnalysis = new HashMap<String, HashMap<String,Double>>();
	}
	
	public HashMap<String,Integer> getTerms(String doc) {
		return index.get(doc);
	}
	
	public Collection<HashMap<String,Integer>> iterate(){
		return index.values();
	}
	
	public void buildIndex(String docsPath, String indexPath) {
		// Check whether docsPath is valid
		if (docsPath == null || docsPath.isEmpty()) {
			System.err.println("Document directory cannot be null");
			System.exit(1);
		}
		
		//already indexed this corpus
		if (this.docPath == docsPath) {
			return;
		}
		else {
			this.docPath = docsPath;
		}

		// Check whether the directory is readable
		final File docDir = new File(docsPath);
		if (!docDir.exists() || !docDir.canRead()) {
			System.out.println("Document directory '" +docDir.getAbsolutePath()+ "' does not exist or is not readable, please check the path");
			System.exit(1);
		}
		
		indexfile = indexPath + "/index";

		System.out.println("Document directory '" +docDir.getAbsolutePath());
		Date start = new Date();
		index = new HashMap<String,HashMap<String,Integer>>();
		this.indexDocs(docDir);
		
		analyzer.close();
		
		//save index
		File iFile = new File(indexfile);
        FileOutputStream f;
        ObjectOutputStream s;
		try {
			f = new FileOutputStream(iFile);
			s = new ObjectOutputStream(f);
			s.writeObject(index);
	        s.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		Date end = new Date();
		System.out.println(end.getTime() - start.getTime() + " total milliseconds");

	}
	
	HashMap<String,Integer> tokenize(Reader reader) {
		// make a new, empty document
		HashMap<String,Integer> terms = new HashMap<String,Integer>();
		
		//tokenize, remove stop words, and stem
		TokenStreamComponents tsc = analyzer.createComponents("contents",reader);
		TokenStream ts = tsc.getTokenStream();
		PorterStemFilter psf = new PorterStemFilter(ts);
		CharTermAttribute cattr = psf.addAttribute(CharTermAttribute.class);
		try {
			psf.reset();
		} catch (IOException e) {
			e.printStackTrace();
		}
		try {
			while (psf.incrementToken()) {
			  String t = cattr.toString();
			  Integer v = terms.get(t);
			  if (v!=null) {
				  terms.put(t, new Integer(v.intValue() + 1));
			  }
			  else {
				  terms.put(t, new Integer(1));
			  }
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		try {
			psf.end();
		} catch (IOException e) {
			e.printStackTrace();
		}
		try {
			psf.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return terms;
	}
	
	
	void indexDocs(File file)  {
		if (file.canRead()) {
			if (file.isDirectory()) {
				String[] files = file.list();
				if (files != null) {
					for (int i = 0; i < files.length; i++) {
						indexDocs(new File(file, files[i]));
					}
				}
			} else {
				// make a new, empty document
				String doc = file.getName();
				FileInputStream fis = null;
				
				if (doc.endsWith(".txt")){
					doc = doc.substring(0, doc.length()-".txt".length());
					
					try {
						fis = new FileInputStream(file);
					} catch (FileNotFoundException e) {
						System.out.println(" caught a " + e.getClass() + "\n with message: " + e.getMessage());
					}
					
					BufferedReader reader = new BufferedReader(new InputStreamReader(fis));

					HashMap<String,Integer> terms = tokenize(reader);
						
					index.put(doc, terms);
				}
			}
		}
	}
	
	HashMap<String,Double> normalize(char q2, HashMap<String,Double> tokens) {
		//normalize tokens based on q2 and return
		switch(q2) {
			case 'n':
				return tokens;
			case 'c':
                // Loop through the weights for all the terms in the 
				// document. Add their sums and then square root the result
				if (tokens != null){
					double length = 0;
					for (Double db : tokens.values()) {
						double v = db.doubleValue();
						length += Math.pow(v, 2);
					}
					
					length = Math.sqrt(length);
					
					HashMap<String, Double> ntokens = new HashMap<String, Double>();
					for (String k: tokens.keySet()){
						Double d = tokens.get(k);
						double v = d.doubleValue();
						double nsquare = v/length;
						ntokens.put(k, new Double(nsquare));	
					}
					return ntokens;
				}		
		}
		return null;
	}
	
	HashMap<String,HashMap<String,Double>> normalizeIndex(char d2, HashMap<String,HashMap<String,Double>> vec) {
		HashMap<String,HashMap<String,Double>> normalized = new HashMap<String,HashMap<String,Double>>();
		//for each document in the index, call normalize
		for (String document : vec.keySet()) {
			HashMap<String,Double> normalizedTokens = normalize(d2, vec.get(document));
			normalized.put(document, normalizedTokens);
		}
		return normalized;
	}
	
	double raw_tf(String term, HashMap<String,Integer> terms) {
		if (terms != null) {
			Integer v = terms.get(term);
			if (v != null) {
				double freq = v.doubleValue();
				double total = 0.0;
				for (Integer t : terms.values()) {
					total += t.doubleValue();
				}
				if (total != 0) {
					return freq / total;
				}
			}
		}
		return 0.0;
	}
	
	double tf(String term, HashMap<String,Integer> terms, char type) {
		double raw = raw_tf(term, terms);
		switch(type) {
			case 'n':
				return raw;
			case 'l':
				return 1 + Math.log(raw);
			case 'a':
				//get the most common term
				String mostCommonTerm = "";
				int count = 0;
				for (String t : terms.keySet()) {
					if (terms.get(t).intValue() > count) {
						mostCommonTerm = t;
						count = terms.get(t).intValue();
					}
				}
				
				//get tf of most common term
				double maxtf = raw_tf(mostCommonTerm, terms);
				
				return 0.5 + 0.5 * raw / maxtf;
			case 'b':
				if (raw > 0) {
					return 1.0;
				}
				else {
					return 0.0;
				}
		}
		return 0.0;
	}
	
	double idf(String term, char type) {
		double total = index.size();
		double match = 0.0;
		for (HashMap <String,Integer> doc : index.values()) {
			if (doc.containsKey(term)) {
				match++;
			}
		}
		
		switch(type){
			case 'n':
				return 1;
			case 't':
				if (match != 0.0) {
					return Math.log(total / match);
				}
				break;
			case 'p':
				if (match != 0.0) {
					return Math.max(0.0, Math.log((total - match) / match));
				}	
		}
		return 0.0;
	}
	
	static LinkedList<String> priority(HashMap<String, Double> ranks, int k){
		DocumentComparator dc = new DocumentComparator();
		//Our original attempt with the priority queue to get O(n * log(k))
		/*PriorityQueue<Document> results = new PriorityQueue<Document>(100, dc);
		for (String doc : ranks.keySet()) {
			double r = ranks.get(doc).doubleValue();
			if (results.size() >= k){
				if (r > results.peek().getRank()){
					results.poll();
					results.add(new Document(doc, r));
				}
			}
			else {
				results.add(new Document(doc, r));
			}
		}
		
		LinkedList<String> resultDocuments = new LinkedList<String>();
		while(!results.isEmpty()) {
			Document d = results.remove();
			resultDocuments.add(d.getName());
		}*/
		
		//Sorting O(n * log(n))
		Document[] docs = new Document[ranks.size()];
		int i = 0;
		for (String doc : ranks.keySet()) {
			Document d = new Document(doc,ranks.get(doc).doubleValue());
			docs[i] = d;
			i++;
		}
		Arrays.sort(docs, dc);
		
		LinkedList<String> resultDocuments = new LinkedList<String>();
		for(int j=0; j<Math.min(k, docs.length); j++){
			resultDocuments.add(docs[j].getName());
		}

		return resultDocuments;
	}
	
	LinkedList<String> getResults(String query, HashMap<String,HashMap<String,Double>> normalizedDocs, char q0, char q1, char q2, int k) {
		//stemming and stopping
		StringReader reader = new StringReader(query);
		HashMap<String,Integer> tokens = tokenize(reader);
		
		//calculate query vector
		HashMap<String, Double> queryVector = new HashMap<String,Double>();
		for (String t : tokens.keySet()) {
			double qtf = tf(t, tokens, q0);
			double qidf = idf(t, q1);
			queryVector.put(t, new Double(qtf*qidf));
		}
		
		//normalization
		HashMap<String,Double> normalizedQueries = normalize(q2, queryVector);
		
		//scores
		HashMap<String, Double> ranks = new HashMap<String, Double>();
		
		//comment out to run with inverted tfidf
		for (String t : normalizedQueries.keySet()) {
			Double qScore = normalizedQueries.get(t);
			if (qScore != null) {
				for (String d : normalizedDocs.keySet()) {
					HashMap<String,Double> scores = normalizedDocs.get(d);
					if (!ranks.containsKey(d)) {
						ranks.put(d, new Double(0));
					}
							
					Double s = scores.get(t);
					if (s != null) {
						Double r = ranks.get(d);
						double r2 = 0;
						if (r != null) {
							r2 = r.doubleValue();
						}
						ranks.put(d, new Double(r2 + s.doubleValue()*qScore.doubleValue()));
					}
				}
			}
		}
		
		//uses the inverted index
		//uncomment out to run inverted index
		/*for (String t : normalizedQueries.keySet()) {
			Double qScore = normalizedQueries.get(t);
			HashMap<String, Double> docs = tfidf.get(t);
			if (docs != null){
				for (String d : docs.keySet()) {
					Double dScore = docs.get(d);
					Double r = ranks.get(d);
					double r2 = 0;
					if (r != null) {
						r2 = r.doubleValue();
					}
					ranks.put(d, new Double(r2 + dScore.doubleValue()*qScore.doubleValue()));
				}
			}
		}*/
		
		LinkedList<String> top = priority(ranks, k);
		
		//Failure Analysis
		/*if (print) {
			System.out.println("Query Vector:");
			for (String t : normalizedQueries.keySet()) {
				System.out.print(t + "-" + normalizedQueries.get(t).doubleValue() + " ");
			}
			System.out.println("");
			System.out.println("Results:");
			for (String doc : top) {
				System.out.print(doc + ":" + ranks.get(doc).doubleValue() + " ");
			}
			System.out.println("");
			String topDoc = top.getFirst();
			if (topDoc !=null) {
				System.out.println("Top Doc Vector:");
				HashMap<String,Double> words = normalizedDocs.get(topDoc);
				for (String t : words.keySet()) {
					System.out.print(t + "-" + words.get(t).doubleValue() + " ");
				}
			}
		}*/
		return top;
	}
	
	public static void evaluate(OurSearchEngine ose, String docDir, String indexDir,
			           String queryFile, String answerFile, int numResults, char[] weights) {
		//get weights
		char d0 = weights[0];
		char d1 = weights[1];
		char d2 = weights[2];
		char q0 = weights[3];
		char q1 = weights[4];
		char q2 = weights[5];
		
		System.out.println("" + d0 + d1  + d2 + "." + q0 + q1 + q2);
		
		//Try to load index
		File f = new File(indexDir + "/index");
		if(f.exists() && !f.isDirectory() && ose.docPath!=docDir) {
			try(
					InputStream file = new FileInputStream(indexDir + "/index");
					InputStream buffer = new BufferedInputStream(file);
					ObjectInput input = new ObjectInputStream (buffer);
				){
					System.out.println("Loading index...");
					//deserialize the List
					HashMap<String, HashMap<String,Integer>> index = (HashMap<String, HashMap<String,Integer>>)input.readObject();
					//display its data
					if (index != null) {
						ose.index = index;
						ose.docPath = docDir;
					}
				}
			catch(ClassNotFoundException ex){
			}
			catch(IOException ex){
			}
		}
		
		if  (ose.docPath!=docDir) {
			System.out.println("Building index...");
			//Build the index
			ose.buildIndex(docDir, indexDir);
		}
		
		// load queries and answer
		System.out.println("Loading queries and answers...");
		Map<Integer, String> queries = EvaluateQueries.loadQueries(queryFile);
		Map<Integer, HashSet<String>> queryAnswers = EvaluateQueries.loadAnswers(answerFile);
		
		//calculating tfidf for documents
		System.out.println("Calculating tfidf for documents...");
		HashMap<String,HashMap<String,Double>> docVector = new HashMap<String,HashMap<String,Double>>();
		for (String doc: ose.index.keySet()) {
			HashMap<String,Integer> terms = ose.index.get(doc);
			for (String t: terms.keySet()) {
				double dtf = ose.tf(t, terms, d0);
				double didf = ose.idf(t, d1);
				
				if (!docVector.containsKey(doc)) {
					docVector.put(doc, new HashMap<String,Double>());
				}
				HashMap<String,Double> vec = docVector.get(doc);
				vec.put(t, new Double(dtf*didf));
			}
		}
		
		//normalize the documents
		System.out.println("Normalize documents...");
		HashMap<String,HashMap<String,Double>> normalizedDocs = ose.normalizeIndex(d2, docVector);
		
		//invert index to speed up idf calculations for queries
		//This part is not necessary.  We have commented out code in getResults() that will run with this
		//If you uncomment it, it should speed up query time processing
		System.out.println("Inverting index...");
		HashMap<String,HashMap<String,Double>> tfidf = new HashMap<String,HashMap<String,Double>>();
		for (String doc: normalizedDocs.keySet()) {
			HashMap<String,Double> terms = normalizedDocs.get(doc);
			for (String t : terms.keySet()) {
				if (!tfidf.containsKey(t)) {
					tfidf.put(t, new HashMap<String,Double>());
				}
				
				HashMap<String, Double> docs = tfidf.get(t);
				docs.put(doc, terms.get(t));
			}
		}
		ose.tfidf = tfidf;
		
		// Search and evaluate
		System.out.println("Evaluating...");
		double averageSum = 0;
		for (Integer i : queries.keySet()) {
			String type = new String(weights);
			//For failure analysis
			if ((type.equals("atnatn") && i.intValue()==16 || (type.equals("annbpn") && i.intValue()==20))) {
				ose.print = true;
			}
			LinkedList<String> results = ose.getResults(queries.get(i), normalizedDocs, q0, q1,q2, numResults);
			double ap = EvaluateQueries.averagePrecision(queryAnswers.get(i), results);
			averageSum += ap;
			
			//For failure analysis
			if (!ose.failureAnalysis.containsKey(queries.get(i))) {
				ose.failureAnalysis.put(queries.get(i), new HashMap<String,Double>());
			}
			HashMap<String,Double> failureValues = ose.failureAnalysis.get(queries.get(i));
			failureValues.put(type, new Double(ap));
			ose.print = false;
		}
				
		System.out.printf("MAP: %f\n\n", averageSum / queries.size());
	}

	public static void bm25(OurSearchEngine ose, String docDir, String indexDir, String query, String answerfile){
		System.out.println("Starting bm25...");
		//Try to load index
				File f = new File(indexDir + "/index");
				if(f.exists() && !f.isDirectory() && ose.docPath!=docDir) {
					try(
							InputStream file = new FileInputStream(indexDir + "/index");
							InputStream buffer = new BufferedInputStream(file);
							ObjectInput input = new ObjectInputStream (buffer);
						){
							System.out.println("Loading index...");
							//deserialize the List
							HashMap<String, HashMap<String,Integer>> index = (HashMap<String, HashMap<String,Integer>>)input.readObject();
							//display its data
							if (index != null) {
								ose.index = index;
								ose.docPath = docDir;
							}
						}
					catch(ClassNotFoundException ex){
					}
					catch(IOException ex){
					}
				}
				
				if  (ose.docPath!=docDir) {
					System.out.println("Building index...");
					//Build the index
					ose.buildIndex(docDir, indexDir);
				}
				
		//load queries from doc
		System.out.println("Loading queries and answers...");
		Map<Integer, String> queries = EvaluateQueries.loadQueries(query);
		Map<Integer, HashSet<String>> queryAnswers = EvaluateQueries.loadAnswers(answerfile);
		
		//tokenize each query
		HashMap<Integer, HashMap<String,Integer>> tokq = new HashMap<Integer, HashMap<String,Integer>>();
		for(Integer i : queries.keySet()){
			StringReader reader = new StringReader(queries.get(i));
			tokq.put(i, ose.tokenize(reader));
		}
		System.out.println("Computing Avg Doc Length...");
		//computing avg doc length
		double avgdl=0.0;
		for(String s: ose.index.keySet()){
			for(String t: ose.index.get(s).keySet()){
				avgdl+= ose.index.get(s).get(t);
			}

		}
		avgdl=avgdl/ose.index.size();
		System.out.println("Average document length: " + avgdl);
		//given constants
		double b=0.75;
		double k1=1.2;
		double k2=100;
		double big_n=(double)ose.index.size();
		double bigr=0.0;
		double r=0.0;
		//constants to be computing for each query
		double ni=0.0;
		double fi=0.0; //given in index hashMap
		double qfi=0.0; //given in tokq hashMap
		double bigk=0.0;
		double docl=0.0;
		double bmsum=0;
		
		System.out.println("Computing BM25 Score...");
		HashMap<Integer, HashMap<String, Double>> bm = new HashMap<Integer, HashMap<String,Double>>();
		//for each query go through each document and compute bm25 similarity 
		for(Integer i : tokq.keySet()){
			//hashMap that will score each doc and its bm25 score for a query
			HashMap<String, Double> inner = new HashMap<String, Double>();
			//going through each doc
			for(String doc: ose.index.keySet()){
				//resetting constants
				bmsum=0;
				docl=0;
				bigk=0;
				//computing doc length
				for(String t: ose.index.get(doc).keySet()){
					docl+=(double) ose.index.get(doc).get(t);
				}
				//calculating big K for document doc
				bigk=k1*((1-b)+(b*(docl/avgdl)));
				//going through each term of the query
				for(String qtok: tokq.get(i).keySet()){
					ni=0;
					fi=0;
					qfi=tokq.get(i).get(qtok); //frequency of term in query
					//get frequency of term in document if it exists
					if(ose.index.get(doc).containsKey(qtok)){
						fi=ose.index.get(doc).get(qtok);
					}
					//going through each document to see if the term is in it to find number of docs term is in
					for(String s : ose.index.keySet()){
						if(ose.index.get(s).containsKey(qtok)){
							ni+=1;
						}
					}
					//adding BM25 for each term in the query
					bmsum+=Math.log( ( ((r+0.5)/(bigr-r+0.5)) /((ni-r+0.5) / ( big_n-ni-bigr+r+0.5 )))) * (((k1+1)*fi) / (bigk+fi)) * ( ((k2+1)*qfi) / (k2+qfi)) ;
				}
				//adding each doc and its bm25 score to a hashMap
				inner.put(doc, Double.valueOf(bmsum));
			}
			//adding each query and its hashMap of docs and bm25 scores to a hashMap
			bm.put(i, inner);
		}
		
		System.out.println("Computing BM25 MAP...");
		double averageSum = 0;
		//computing averagePrecision and summing
		for (Integer i : bm.keySet()) {
			LinkedList<String> top = priority(bm.get(i), 100);
			double ap = EvaluateQueries.averagePrecision( queryAnswers.get(i) , top);
			averageSum += ap;
			
			//for failure analysis
			if (!ose.failureAnalysis.containsKey(queries.get(i))) {
				ose.failureAnalysis.put(queries.get(i), new HashMap<String,Double>());
			}
			HashMap<String, Double> failureValues = ose.failureAnalysis.get(queries.get(i));
			failureValues.put("bm25", new Double(ap));
			//Failure Analysis
			/*if(i.intValue()==16 || i.intValue()==20) {
				System.out.println("Query: " + i);
				System.out.println("Results:");
				for (String doc : top) {
					System.out.print(doc + ":" + bm.get(i).get(doc).doubleValue() + " ");
				}
			}*/
		}
		System.out.println("BM25 MAP: " + (averageSum / (double) queries.size()) + "\n");
		
	}
	public static void main(String[] arg) {
		String cacmDocsDir = "data/cacm"; // directory containing CACM documents
		String cacmIndexDir = "data/index/cacm2"; // the directory where index is written into
		
		String medDocsDir = "data/med"; // directory containing MED documents
		String medIndexDir = "data/index/med2"; // the directory where index is written into
		String cacmQueries = "data/cacm_processed.query";
		String medQueries = "data/med_processed.query";
		String cacmAnswers = "data/cacm_processed.rel";
		String medAnswers = "data/med_processed.rel";
		
		//SMART
		char[] s1 = {'a','t','c','a','t','c'};
		char[] s2 = {'a','t','n','a','t','n'};
		char[] s3 = {'a','n','n','b','p','n'};
		char[] s4 = {'l','n','c','l','t','c'};
		
		OurSearchEngine ose = new OurSearchEngine();
		
		
		System.out.println("CACM");
		evaluate(ose, cacmDocsDir, cacmIndexDir, cacmQueries, cacmAnswers, 100, s1);
		evaluate(ose, cacmDocsDir, cacmIndexDir, cacmQueries, cacmAnswers, 100, s2);
		evaluate(ose, cacmDocsDir, cacmIndexDir, cacmQueries, cacmAnswers, 100, s3);
		evaluate(ose, cacmDocsDir, cacmIndexDir, cacmQueries, cacmAnswers, 100, s4);
		
		//bm25
		bm25(ose,cacmDocsDir, cacmIndexDir, cacmQueries, cacmAnswers);
	   
		System.out.println("MED");
		evaluate(ose, medDocsDir, medIndexDir, medQueries, medAnswers, 100, s1);
		evaluate(ose, medDocsDir, medIndexDir, medQueries, medAnswers, 100, s2);
		evaluate(ose, medDocsDir, medIndexDir, medQueries, medAnswers, 100, s3);
		evaluate(ose, medDocsDir, medIndexDir, medQueries, medAnswers, 100, s4);
		
		//bm25
		bm25(ose,medDocsDir, medIndexDir, medQueries, medAnswers);
		
		//FAILURE ANALYSIS
		/*double best = 0.0;
		String bestQuery = ""; 
		String bestType = "";
		
		double worst = 0.0;
		String worstQuery = ""; 
		String worstType = "";
		for (String query : ose.failureAnalysis.keySet()) {
			double min = 0;
			double max = 0;
			String minType = "";
			String maxType = "";
			HashMap<String, Double> apValues = ose.failureAnalysis.get(query);
			double bm25 = apValues.get("bm25");
			
			for (String type : apValues.keySet()) {
				if (type == "bm25") {
					continue;
				}
				double ap = apValues.get(type).doubleValue();
				if (ap <= min || minType=="") {
					min = ap;
					minType = type;
				}
				if (ap >= max) {
					max = ap;
					maxType = type;
				}
			}
			
			if ((max - bm25) > worst) {
				worst = max - bm25;
				worstQuery = query;
				worstType = maxType;
			}
			
			if ((bm25 - min) > best) {
				best = bm25 - min;
				bestQuery = query;
				bestType = maxType;
			}
		}
		
		System.out.println("Best Query: " + bestQuery);
		System.out.println("Diff: " + best);
		HashMap<String, Double> aps = ose.failureAnalysis.get(bestQuery);
		System.out.println("bm25: " + aps.get("bm25").doubleValue());
		System.out.println(bestType + ": " + aps.get(bestType).doubleValue() + "\n");
		
		System.out.println("Worst Query: " + worstQuery);
		System.out.println("Diff: " + worst);
		aps = ose.failureAnalysis.get(worstQuery);
		System.out.println(worstType + ": " + aps.get(worstType).doubleValue());
		System.out.println("bm25: " + aps.get("bm25").doubleValue());*/
		
		System.out.println("done");
;	}
}
