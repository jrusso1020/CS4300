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
	private HashMap<String, HashMap<String,Double>> tfidf;
	private HashMap<String, HashMap<String,Double>> failureAnalysis;
	private HashMap<String, HashMap<String,Double>> normalizedDocs;
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
	
	public HashMap<String,Integer> tokenize(Reader reader) {
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
	
	static LinkedList<String> priority(Document[] docs, int k){
		DocumentComparator dc = new DocumentComparator();
		
		//Sorting O(n * log(n))
		Arrays.sort(docs, dc);
		
		LinkedList<String> resultDocuments = new LinkedList<String>();
		for(int j=0; j<Math.min(k, docs.length); j++){
			resultDocuments.add(docs[j].getName());
		}

		return resultDocuments;
	}
	
	HashMap<String, Double> getRanks(String query, char q0, char q1, char q2) {
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
		
			return ranks;
	}
	
	LinkedList<String> getResults(String query, char q0, char q1, char q2, int k) {
		HashMap<String, Double> ranks = getRanks(query, q0, q1, q2);
		
		Document[] docs = new Document[ranks.size()];
		int i = 0;
		for (String doc : ranks.keySet()) {
			Document d = new Document(doc,ranks.get(doc).doubleValue());
			docs[i] = d;
			i++;
		}
		
		LinkedList<String> top = priority(docs, k);
		
		return top;
	}
	
	public void loadIndex(String indexDir, String docDir) {
		File f = new File(indexDir + "/index");
		if(f.exists() && !f.isDirectory() && docPath!=docDir) {
			try(
					InputStream file = new FileInputStream(indexDir + "/index");
					InputStream buffer = new BufferedInputStream(file);
					ObjectInput input = new ObjectInputStream (buffer);
				){
					System.out.println("Loading index...");
					//deserialize the List
					HashMap<String, HashMap<String,Integer>> temp = (HashMap<String, HashMap<String,Integer>>)input.readObject();
					//display its data
					if (temp != null) {
						index = temp;
						docPath = docDir;
					}
				}
			catch(ClassNotFoundException ex){
			}
			catch(IOException ex){
			}
		}
		
		if  (docPath!=docDir) {
			System.out.println("Building index...");
			//Build the index
			buildIndex(docDir, indexDir);
		}
	}
	
	private HashMap<String,HashMap<String,Double>> getDocVector(char d0, char d1) {
		HashMap<String,HashMap<String,Double>> docVector = new HashMap<String,HashMap<String,Double>>();
		for (String doc: index.keySet()) {
			HashMap<String,Integer> terms = index.get(doc);
			for (String t: terms.keySet()) {
				double dtf = tf(t, terms, d0);
				double didf = idf(t, d1);
				
				if (!docVector.containsKey(doc)) {
					docVector.put(doc, new HashMap<String,Double>());
				}
				HashMap<String,Double> vec = docVector.get(doc);
				vec.put(t, new Double(dtf*didf));
			}
		}
		
		return docVector;
	}
	
	public LinkedList<Cluster> completeLinkCluster(List<String> results, int K) {
		//calculate distances
		double[][] distances = new double[results.size()][results.size()];
		for (int i=0; i<results.size(); i++) {
			distances[i][i] = 1;
			
			for (int j=i+1; j<results.size(); j++) {
				String doci = results.get(i);
				String docj = results.get(j);
				
				HashMap<String,Double> termsi = normalizedDocs.get(doci);
				HashMap<String,Double> termsj = normalizedDocs.get(docj);
				
				// calculate dot product
				double d = 0.0;
				for (String t: termsi.keySet()) {
					if (termsj.containsKey(t)) {
						double d1 = termsi.get(t).doubleValue();
						double d2 = termsj.get(t).doubleValue();
						
						d += d1*d2;
					}
				}
				
				// 1 / dot product
				if (d != 0.0){
					d = 1 / d;
				}
				else {
					d = Double.MAX_VALUE;
				}
				
				distances[i][j] = d;
				distances[j][i] = d;
			}
		}
		
		LinkedList<Cluster> clusters = new LinkedList<Cluster>();
		
		//make a new cluster for each document
		for (int i=0; i<results.size(); i++) {
			LinkedList<String> d = new LinkedList<String>();
			d.add(results.get(i));
			clusters.add(new Cluster(d, distances[i], i));
		}
		
		//merge clusters
		while (clusters.size() > K) {
			double smallest = Double.MAX_VALUE;
			int c1 = 0;
			int c2 = 0;
			
			for (int i=0; i<clusters.size(); i++) {
				double s = clusters.get(i).getSmallestDistance();
				if (s < smallest) {
					c1 = i;
					smallest = s;
					int index = clusters.get(i).getIndexOfSmallestDistance();
					c2 = index;
				}
			}
			
			clusters.get(c1).completeLinkMerge(clusters.get(c2));
			
			for (int i=0; i<clusters.size(); i++) {
				clusters.get(i).removeDistance(c2);
				if (i > c2) {
					clusters.get(i).setIndex(i - 1);
				}
			}
			clusters.remove(c2);
		}
		
		return clusters;
	}
	
	public static void problem2(OurSearchEngine ose, String docDir, String indexDir,
	           String queryFile, String answerFile, int numResults, char[] weights, int K, boolean highest, boolean print) {
		//get weights
		char d0 = weights[0];
		char d1 = weights[1];
		char d2 = weights[2];
		char q0 = weights[3];
		char q1 = weights[4];
		char q2 = weights[5];
		
		System.out.println("" + d0 + d1  + d2 + "." + q0 + q1 + q2);
		System.out.println("K: " + K);
		if (highest) {
			System.out.println("Highest");
		}
		else {
			System.out.println("Average");
		}
		
		//Load index
		ose.loadIndex(indexDir, docDir);
		
		// load queries and answer
		System.out.println("Loading queries and answers...");
		Map<Integer, String> queries = EvaluateQueries.loadQueries(queryFile);
		Map<Integer, HashSet<String>> queryAnswers = EvaluateQueries.loadAnswers(answerFile);
		
		//calculating tfidf for documents
		System.out.println("Calculating tfidf for documents...");
		HashMap<String,HashMap<String,Double>> docVector = ose.getDocVector(d0, d1);
		
		//normalize the documents
		System.out.println("Normalize documents...");
		ose.normalizedDocs = ose.normalizeIndex(d2, docVector);
		
		// Search and evaluate
		System.out.println("Evaluating...");
		double averageSumOld = 0.0;
		double averageSumNew = 0.0;
		int better = 0;
		int worse = 0;
		
		for (Integer i : queries.keySet()) {
			LinkedList<String> oldResults = ose.getResults(queries.get(i), q0, q1,q2, numResults);
			HashMap<String, Double> ranks = ose.getRanks(queries.get(i), q0, q1, q2);
			
			//cluster
			LinkedList<Cluster> clusters = ose.completeLinkCluster(oldResults.subList(0, 30), K);
			
			//update ranks
			HashMap<String, Double> clusterRanks = new HashMap<String, Double>();
			for (Cluster c: clusters) {
				LinkedList<String> docs = c.getDocs();
				double count = 0.0;
				for (String d: docs) {
					double r = ranks.get(d).doubleValue();
					//largest
					if (highest && r > count) {
						count = r;
					}
					
					//average
					if (!highest) {
						count += r;
					}
				}
				
				//if average
				if (!highest) {
					count = count / docs.size();
				}
				
				//update
				for (String d: docs) {
					clusterRanks.put(d, new Double(count));
				}
			}
			
			//prioritize
			Document[] docs = new Document[ranks.size()];
			int j = 0;
			for (String doc : ranks.keySet()) {
				Document d = new Document(doc,ranks.get(doc).doubleValue());
				if (clusterRanks.containsKey(doc)) {
					d.setClusterRank(clusterRanks.get(doc).doubleValue());
				}
				docs[j] = d;
				j++;
			}
			
			LinkedList<String> newResults = priority(docs, numResults);
			double apOld = EvaluateQueries.averagePrecision(queryAnswers.get(i), oldResults);
			double apNew = EvaluateQueries.averagePrecision(queryAnswers.get(i), newResults);
			
			if (apOld < apNew) {
				better++;
			}
			if (apOld > apNew) {
				worse++;
			}
			
			averageSumOld += apOld;
			averageSumNew += apNew;
			
			if (print) {
				if ((apNew - apOld) > .1){
					System.out.println(queries.get(i));
					System.out.println(apNew - apOld);
					System.out.println("Old Ranks");
					int k = 0;
					for (String r: oldResults) {
						System.out.print(r + " ");
						k++;
						if (k == 30) {
							break;
						}
					}
					System.out.println("");
					
					System.out.println("\nClusters");
					for (Cluster c: clusters) {
						LinkedList<String> documents = c.getDocs();
						double count = 0.0;
						for (String doc: documents) {
							System.out.print(doc + " ");
							
							double r = ranks.get(doc).doubleValue();
							//largest
							if (highest && r > count) {
								count = r;
							}
							
							//average
							if (!highest) {
								count += r;
							}
						}
						
						//if average
						if (!highest) {
							count = count / documents.size();
						}
						
						System.out.println(count);
					}
					
					System.out.println("\nNew Ranks");
					k = 0;
					for (String r: newResults) {
						System.out.print(r + " ");
						k++;
						if (k == 30) {
							break;
						}
					}
					System.out.println("");
				}
			}
				
		}
		
		System.out.printf("Old MAP: %f\n", averageSumOld / queries.size());
		System.out.printf("New MAP: %f\n", averageSumNew / queries.size());
		System.out.printf("Improved: %d\n", better);
		System.out.printf("Worse: %d\n\n", worse);
	}
	
	public static HashMap<String, Double> qprime(OurSearchEngine ose, LinkedList<String> rel, HashMap<String, Double> qj, double A, double B, double C){
		HashMap<String, Double> qPrime= new HashMap<String, Double>();
		for(String t: qj.keySet()){
			double dRel=0;
			for(String doc: rel){
				if(ose.normalizedDocs.get(doc).containsKey(t)){
					dRel+= ose.normalizedDocs.get(doc).get(t);
				}
			}
			/*double dNrel=0;
			for(String doc: ose.normalizedDocs.keySet()){
				if(rel.contains(doc)==false){
					if(ose.normalizedDocs.get(doc).containsKey(t)){
							dNrel+= ose.normalizedDocs.get(doc).get(t);
						}
					else{
						dNrel+=0.5*ose.idf(t,'t');
					}
				}
			}*/
			double qjPrime= 
			A*qj.get(t)+B*(1.0/(double)rel.size())*dRel; //-C*(1.0/(double)(ose.normalizedDocs.size()-rel.size()))*dNrel;
			qPrime.put(t, new Double(qjPrime));
		}
		return qPrime;
	}
	
	//gets query every token of a query and its tf-idf weight
	HashMap<String, Double> getRanksQuery(String query, char q0, char q1, char q2) {
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
				return normalize(q2, queryVector);
	}
	
	public static void rocchio(OurSearchEngine ose, String docDir, String indexDir,
	           String queryFile, String answerFile, int numResults, char[] weights, int K, double A, double B, double C ){
		double max=0;
		Integer query= new Integer(0);
		//get weights
		char d0 = weights[0];
		char d1 = weights[1];
		char d2 = weights[2];
		char q0 = weights[3];
		char q1 = weights[4];
		char q2 = weights[5];
		
		
		System.out.println("" + d0 + d1  + d2 + "." + q0 + q1 + q2);
		
		//Load index
		ose.loadIndex(indexDir, docDir);
		
		// load queries and answer
		System.out.println("Loading queries and answers...");
		Map<Integer, String> queries = EvaluateQueries.loadQueries(queryFile);
		Map<Integer, HashSet<String>> queryAnswers = EvaluateQueries.loadAnswers(answerFile);
		
		//calculating tfidf for documents
		System.out.println("Calculating tfidf for documents...");
		HashMap<String,HashMap<String,Double>> docVector = ose.getDocVector(d0, d1);
		
		//normalize the documents
		System.out.println("Normalize documents...");
		ose.normalizedDocs = ose.normalizeIndex(d2, docVector);
		
		System.out.println("Evaluating...");
		double averageSumOld = 0.0;
		double averageSumNew = 0.0;
		int better = 0;
		int worse = 0;
		for (Integer i : queries.keySet()) {
			int bigK=K;
			LinkedList<String> prevR = ose.getResults(queries.get(i), q0, q1,q2, 100);
			LinkedList<String> results = ose.getResults(queries.get(i), q0, q1,q2, numResults);
			HashMap<String, Double> ranks = ose.getRanksQuery(queries.get(i), q0, q1, q2);
			HashMap<String, Double> orig=ose.getRanksQuery(queries.get(i), q0, q1, q2);;
			HashMap<String, Double> newQuer= new HashMap<String, Double>(); //will contain each query token and its query idf-tf
			//gets each term in the corpus
			for(String s: results){
				for(String t: ose.index.get(s).keySet()){
					if(newQuer.containsKey(t)==false){
						newQuer.put(t, new Double(0));
					}
				}
			}
			//takes query tf-idf and assigns to the tokens that also occur in the rel docs
			for(String t: ranks.keySet()){
					newQuer.put(t, ranks.get(t));
			}
			//call outside method for q'j for each token
			HashMap<String, Double>qPrime= qprime(ose, results, newQuer, A, B, C);
			
			Document[] arr = new Document[qPrime.size()];
			int count=0;
			
			for(String t: qPrime.keySet()){
				Document d = new Document(t, qPrime.get(t));
				arr[count]=d;
				count++;
			}
			LinkedList<String> top= priority(arr, arr.length);
			for(String t: ranks.keySet()){
				ranks.put(t, qPrime.get(t));
			}
			
			for(String t: top){
				if(ranks.containsKey(t)==false){
					ranks.put(t, qPrime.get(t));
					bigK--;
					if(bigK==0){
						break;
					}
				}
			}
			
			
			//scores
			HashMap<String, Double> order = new HashMap<String, Double>();
			
			//get scores for all documents with expanded query
			for (String t : ranks.keySet()) {
				Double qScore = ranks.get(t);
				if (qScore != null) {
					for (String d : ose.normalizedDocs.keySet()) {
						HashMap<String,Double> scores = ose.normalizedDocs.get(d);
						if (!order.containsKey(d)) {
							order.put(d, new Double(0));
						}
								
						Double s = scores.get(t);
						if (s != null) {
							Double r = order.get(d);
							double r2 = 0;
							if (r != null) {
								r2 = r.doubleValue();
							}
							order.put(d, new Double(r2 + s.doubleValue()*qScore.doubleValue()));
						}
					}
				}
			}
			Document[] arr2 = new Document[order.size()];
			count=0;
			for(String t: order.keySet()){
				Document d = new Document(t, order.get(t));
				arr2[count]=d;
				count++;
			}
			LinkedList<String> top2= priority(arr2, 100);
			double apOld = EvaluateQueries.averagePrecision(queryAnswers.get(i), prevR);
			double apNew = EvaluateQueries.averagePrecision(queryAnswers.get(i), top2);
			
			if (apOld < apNew) {
				better++;
			}
			if (apOld > apNew) {
				worse++;
			}
			if(Math.abs(apOld-apNew)>max){
				query=i;
			}
			
			averageSumOld += apOld;
			averageSumNew += apNew;
			if(i.equals(new Integer(30)) && indexDir.equals("data/index/med2")){
				System.out.println("Original Query:");
				for(String a: orig.keySet()){
					System.out.print(" " + a + ":" + orig.get(a) + " ");
				}
				System.out.println();
				System.out.println("Expanded Query:");
				for(String a: ranks.keySet()){
					System.out.print(" " + a + ":" + ranks.get(a) + " ");
				}
				System.out.println();
				System.out.println("Old:" + apOld + " " + "New: " +  apNew);
			}
		}
		
		System.out.println();
		System.out.printf("Old MAP: %f\n", averageSumOld / queries.size());
		System.out.printf("New MAP: %f\n", averageSumNew / queries.size());
		System.out.printf("Improved: %d\n", better);
		System.out.printf("Worse: %d\n\n", worse);
		System.out.println("Query that changed the most:" + query);
		
	}
	
	public static HashMap<String, Double> qprime2(OurSearchEngine ose, String rel, String nRel, HashMap<String, Double> qj, double A, double B, double C){
		HashMap<String, Double> qPrime= new HashMap<String, Double>();
		double rl=1.0;
		double nl=1.0;
		for(String t: qj.keySet()){
			double dRel=0;
			if(!rel.equals("")){
				if(ose.normalizedDocs.get(rel).containsKey(t)){
					dRel+= ose.normalizedDocs.get(rel).get(t);
				}
			}
			double dNrel=0;
			if(!nRel.equals("")){
				if(ose.normalizedDocs.get(nRel).containsKey(t)){
					dNrel+= ose.normalizedDocs.get(nRel).get(t);
				}
			}
			if(rel.equals("")){
				rl=0.0;
			}
			if(nRel.equals("")){
				nl=0.0;
			}
			double qjPrime=0.0;
			if(C!=0){
				qjPrime= A*qj.get(t)+B*(rl)*dRel-C*(nl)*dNrel;
			}
			else{
				qjPrime= A*qj.get(t)+B*(rl)*dRel;
			}
			qPrime.put(t, new Double(qjPrime));
		}
		return qPrime;
	}
	
	public static void rocchio2(OurSearchEngine ose, String docDir, String indexDir,
	           String queryFile, String answerFile, int numResults, char[] weights, int K, double A, double B, double C ){
		double max=0;
		Integer query= new Integer(0);
		//get weights
		char d0 = weights[0];
		char d1 = weights[1];
		char d2 = weights[2];
		char q0 = weights[3];
		char q1 = weights[4];
		char q2 = weights[5];
		
		
		System.out.println("" + d0 + d1  + d2 + "." + q0 + q1 + q2);
		
		//Load index
		ose.loadIndex(indexDir, docDir);
		
		// load queries and answer
		System.out.println("Loading queries and answers...");
		Map<Integer, String> queries = EvaluateQueries.loadQueries(queryFile);
		Map<Integer, HashSet<String>> queryAnswers = EvaluateQueries.loadAnswers(answerFile);
		
		//calculating tfidf for documents
		System.out.println("Calculating tfidf for documents...");
		HashMap<String,HashMap<String,Double>> docVector = ose.getDocVector(d0, d1);
		
		//normalize the documents
		System.out.println("Normalize documents...");
		ose.normalizedDocs = ose.normalizeIndex(d2, docVector);
		HashMap<Integer, ArrayList<String>> relDocs= new HashMap<Integer, ArrayList<String>>();
		try {
			BufferedReader swr = new BufferedReader(new FileReader(answerFile));
			String line = null;
			while ((line = swr.readLine()) != null) {
				Integer num= new Integer(line.substring(0, line.indexOf(" ")));
				line=line.substring(line.indexOf(" ")+1);
				ArrayList<String> words=new ArrayList<String>(Arrays.asList(line.split(" ")));
				relDocs.put(num,words);				
			}
			swr.close();
		} catch(IOException e) {
			System.out.println("Could not read the stop word file");
		}
		
		System.out.println("Evaluating...");
		double averageSumOld = 0.0;
		double averageSumNew = 0.0;
		int better = 0;
		int worse = 0;
		for (Integer i : queries.keySet()) {
			int bigK=K;
			LinkedList<String> prevR = ose.getResults(queries.get(i), q0, q1,q2, 100);
			LinkedList<String> results = ose.getResults(queries.get(i), q0, q1,q2, numResults);
			String topRel="";
			String topNrel="";
			for(String st: results){
				if(relDocs.get(i).contains(st)){
					topRel=st;
					break;
				}
			}
			for(String st: results){
				if(relDocs.get(i).contains(st)==false){
					topNrel=st;
					break;
				}
			}
			HashMap<String, Double> ranks = ose.getRanksQuery(queries.get(i), q0, q1, q2);
			HashMap<String, Double> orig=ose.getRanksQuery(queries.get(i), q0, q1, q2);
			HashMap<String, Double> newQuer= new HashMap<String, Double>(); //will contain each query token and its query idf-tf
			//gets each term in the corpus
			if(!topRel.equals("")){
				for(String t: ose.index.get(topRel).keySet()){
					if(newQuer.containsKey(t)==false){
						newQuer.put(t, new Double(0));
					}
				}
			}
			
			if(C!=0){
				if(!topNrel.equals("")){
					for(String t: ose.index.get(topNrel).keySet()){
						if(newQuer.containsKey(t)==false){
							newQuer.put(t, new Double(0));
						}
					}
				}
			}
			//takes query tf-idf and assigns to the words in corpus that are in the query
			for(String t: ranks.keySet()){
					newQuer.put(t, ranks.get(t));
			}
			//call outside method for q'j for each token
			HashMap<String, Double>qPrime= qprime2(ose, topRel, topNrel, newQuer, A, B, C);
			Document[] arr = new Document[qPrime.size()];
			int count=0;
			for(String t: qPrime.keySet()){
				Document d = new Document(t, qPrime.get(t));
				arr[count]=d;
				count++;
			}
			LinkedList<String> top= priority(arr, arr.length);
			for(String t: ranks.keySet()){
				ranks.put(t, qPrime.get(t));
			}
			for(String t: top){
				if(ranks.containsKey(t)==false){
					ranks.put(t, qPrime.get(t));
					bigK--;
					if(bigK==0){
						break;
					}
				}
			}
			//scores
			HashMap<String, Double> order = new HashMap<String, Double>();
			
			//get scores for all documents with expanded query
			for (String t : ranks.keySet()) {
				Double qScore = ranks.get(t);
				if (qScore != null) {
					for (String d : ose.normalizedDocs.keySet()) {
						HashMap<String,Double> scores = ose.normalizedDocs.get(d);
						if (!order.containsKey(d)) {
							order.put(d, new Double(0));
						}
								
						Double s = scores.get(t);
						if (s != null) {
							Double r = order.get(d);
							double r2 = 0;
							if (r != null) {
								r2 = r.doubleValue();
							}
							order.put(d, new Double(r2 + s.doubleValue()*qScore.doubleValue()));
						}
					}
				}
			}
			Document[] arr2 = new Document[order.size()];
			count=0;
			for(String t: order.keySet()){
				Document d = new Document(t, order.get(t));
				arr2[count]=d;
				count++;
			}
			LinkedList<String> top2= priority(arr2, 100);
			double apOld = EvaluateQueries.averagePrecision(queryAnswers.get(i), prevR);
			double apNew = EvaluateQueries.averagePrecision(queryAnswers.get(i), top2);
			
			if (apOld < apNew) {
				better++;
			}
			if (apOld > apNew) {
				worse++;
			}
			
			if(Math.abs(apOld-apNew)>max){
				query=i;
			}
			averageSumOld += apOld;
			averageSumNew += apNew;
			
			if(i.equals(new Integer(48)) && indexDir.equals("data/index/cacm2")){
				System.out.println("Original Query:");
				for(String a: orig.keySet()){
					System.out.print(" " + a + ":" + orig.get(a) + " ");
				}
				System.out.println();
				System.out.println("Expanded Query:");
				for(String a: ranks.keySet()){
					System.out.print(" " + a + ":" + ranks.get(a) + " ");
				}
				
				System.out.println();
				System.out.println("Old:" + apOld + " " + "New: " +  apNew);
			}
		}
		System.out.println();
		System.out.printf("Old MAP: %f\n", averageSumOld / queries.size());
		System.out.printf("New MAP: %f\n", averageSumNew / queries.size());
		System.out.printf("Improved: %d\n", better);
		System.out.printf("Worse: %d\n\n", worse);
		System.out.println("Query that changed the most:" + query);
		
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
		char[] weights = {'a','t','c','a','t','c'};
		
		OurSearchEngine ose = new OurSearchEngine();
		
		int numResults = 100;
		
		System.out.println("PROBLEM 1");
		System.out.println("PART A");
		System.out.println("CACM");
		rocchio(ose, cacmDocsDir, cacmIndexDir, cacmQueries, cacmAnswers, 7, weights, 5, 4, 8, 0);
		System.out.println("MED");
		rocchio(ose, medDocsDir, medIndexDir, medQueries, medAnswers, 7, weights, 5, 4, 8, 0);
		
		System.out.println("PART B");
		System.out.println("CACM");
		rocchio(ose, cacmDocsDir, cacmIndexDir, cacmQueries, cacmAnswers, 7, weights, 10, 4, 16, 0);
		System.out.println("MED");
		rocchio(ose, medDocsDir, medIndexDir, medQueries, medAnswers, 7, weights, 10, 4, 16, 0);
		
		System.out.println("PROBLEM 2");
		System.out.println("CACM");
		problem2(ose, cacmDocsDir, cacmIndexDir, cacmQueries, cacmAnswers, numResults, weights, 20, true, false);
		problem2(ose, cacmDocsDir, cacmIndexDir, cacmQueries, cacmAnswers, numResults, weights, 10, false, false);
		
		System.out.println("MED");
		problem2(ose, medDocsDir, medIndexDir, medQueries, medAnswers, numResults, weights, 20, true, true);
		problem2(ose, medDocsDir, medIndexDir, medQueries, medAnswers, numResults, weights, 10, false, false);
		
		System.out.println("PROBLEM 3");
		System.out.println("PART A");
		System.out.println("CACM");
		rocchio2(ose, cacmDocsDir, cacmIndexDir, cacmQueries, cacmAnswers, 7, weights, 5, 4, 8, 4);
		System.out.println("MED");
		rocchio2(ose, medDocsDir, medIndexDir, medQueries, medAnswers, 7, weights, 5, 4, 8, 4);
		
		System.out.println("PART B");
		System.out.println("CACM");
		rocchio2(ose, cacmDocsDir, cacmIndexDir, cacmQueries, cacmAnswers, 7, weights, 5, 4, 16, 0);
		System.out.println("MED");
		rocchio2(ose, medDocsDir, medIndexDir, medQueries, medAnswers, 7, weights, 5, 4, 16, 0);
		
		System.out.println("done");
;	}
}
