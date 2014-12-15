import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.RandomAccessFile;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Analyzer.TokenStreamComponents;
import org.apache.lucene.analysis.en.PorterStemFilter;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.util.CharArraySet;

public class DiskSearchEngine {
	
	private RandomAccessFile direct;
	private RandomAccessFile variable;
	private HashMap<String, Integer> vocabulary;
	private MyAnalyzer analyzer;
	private String docPath;
	private String directFile = "/direct.txt";
	private String variableFile = "/variable.txt";
	private int currentWord = 0;
	private int currentDoc = 0;
	private int offset = 0;
	
	public DiskSearchEngine(){
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
		vocabulary = new HashMap<String, Integer>();
	}
	
	private HashMap<Integer, Integer> getTermsDocID(int docnumber) throws IOException {
		//direct = id * 2 sizeof(Integer)
				int offset = docnumber * 2 * 4;
				direct.seek(offset);
				byte[] bytes = new byte[2];
				direct.read(bytes);

				//byte offset, length
				String data = new String(bytes);
				String elements[] = data.split(",");
				offset = Integer.parseInt(elements[0]);
				int length = Integer.parseInt(elements[1]);
				bytes = new byte[length * 2 * 4];
				variable.seek(offset);
				variable.read(bytes);
				data = new String(bytes);
				String[] terms = data.split(" ");
				
				HashMap<Integer, Integer> vector = new HashMap<Integer, Integer>();
				
				for (String term : terms) {
					elements = term.split(",");
					int termid = Integer.parseInt(elements[0]);
					int count = Integer.parseInt(elements[1]);
					
					vector.put(termid, count);
				}
				return vector;
	}
	
	public HashMap<Integer,Integer> getTerms(String doc) throws IOException {
		//convert doc name to doc id
		//Grabs 0001 from CACM-0001
		String num = doc.substring(doc.length()-4); 
		// Turns 0001 into an int, need to do minus 1 to start at 0
		int docnumber = Integer.parseInt(num)-1;
		return getTermsDocID(docnumber);
	}
	
	public ArrayList<HashMap<Integer,Integer>> iterate() {
		int i = 0;
		ArrayList<HashMap<Integer,Integer>> col = new ArrayList<HashMap<Integer,Integer>>();
		while(true) {
			try {
				HashMap<Integer, Integer> terms = getTermsDocID(i);
				col.add(terms);
				i++;
			}
			catch (IOException e) {
				break;
			}
		}
		return col;
	}
	
	public void loadIndex(String indexDir, String docDir) {
		try {
			//load old index
			direct = new RandomAccessFile(indexDir + directFile, "r");
			variable = new RandomAccessFile(indexDir + variableFile, "r");
			
			//load vocabulary
			File f = new File(indexDir + "/vocab");
			if(f.exists() && !f.isDirectory() && docPath!=docDir) {
				try(
						InputStream file = new FileInputStream(indexDir + "/vocab");
						InputStream buffer = new BufferedInputStream(file);
						ObjectInput input = new ObjectInputStream (buffer);
					){
						System.out.println("Loading vocab...");
						//deserialize the List
						HashMap<String,Integer> temp = (HashMap<String,Integer>)input.readObject();
						//display its data
						if (temp != null) {
							vocabulary = temp;
						}
					}
				catch(ClassNotFoundException ex){
				}
				catch(IOException ex){
				}
			}
			docPath = docDir;
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			
			//index
			if  (docPath!=docDir) {
				System.out.println("Building index...");
				//Build the index
				buildIndex(docDir, indexDir);
			}
		}
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

		// Check whether the directory is readable
		final File docDir = new File(docsPath);
		if (!docDir.exists() || !docDir.canRead()) {
			System.exit(1);
		}
		
		try {
			direct = new RandomAccessFile(indexPath + directFile, "rw");
			variable = new RandomAccessFile(indexPath + variableFile, "rw");
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		System.out.println("Document directory '" +docDir.getAbsolutePath());
		Date start = new Date();
		this.indexDocs(docDir);
		
		analyzer.close();
		
		//save vocabulary
		File iFile = new File(indexPath + "/vocab");
        FileOutputStream f;
        ObjectOutputStream s;
		try {
			f = new FileOutputStream(iFile);
			s = new ObjectOutputStream(f);
			s.writeObject(vocabulary);
	        s.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		Date end = new Date();
		System.out.println(end.getTime() - start.getTime() + " total milliseconds");
		
		this.docPath = docsPath;
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
					
					//convert to a string
					String serialized = "";
					for (String term : terms.keySet()) {
						//term already exists in our vocabulary
						int id = 0;
						if (vocabulary.containsKey(term)) {
							id = vocabulary.get(term).intValue();
						}
						else {
							currentWord++;
							id = currentWord;
						}
						
						int count = terms.get(term).intValue();
						
						serialized += " " + id + "," + count;
					}
					
					//convert string to bytes
					byte[] bVector = serialized.getBytes();
					
					serialized = "";
					
					serialized = offset + "," + terms.size();
					byte[] bDirect = serialized.getBytes();
					
					try {
						//write to direct
						direct.seek(currentDoc * 2 * 4);
						direct.write(bDirect);
						currentDoc++;
						
						//write to variable
						variable.seek(offset);
						variable.write(bVector);
						offset += terms.size() * 2 * 4;
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
		}
	}
}
