Please make sure you have the necessary jars for the project.

LUCENE
1. To run the code for problem 1, open EvaluateQueries.java and press run.  Our code will print the MAP scores for the two sets.

MINI
2. To run the code for problem 2, please ensure that the following data is in the correct location: 
		String cacmDocsDir = "data/cacm"; // directory containing CACM documents
		String cacmIndexDir = "data/index/cacm2"; // the directory where CACM index is written into
		
		String medDocsDir = "data/med"; // directory containing MED documents
		String medIndexDir = "data/index/med2"; // the directory where MED index is written into
		String cacmQueries = "data/cacm_processed.query"; //file containing CACM queries
		String medQueries = "data/med_processed.query"; //file containing MED queries
		String cacmAnswers = "data/cacm_processed.rel"; //file containing CACM relevant documents
		String medAnswers = "data/med_processed.rel"; //file containing MED relevant documents

Then open OurSearchEngine.java and press run.  This program will create the index and continue on to do parts 3 and 4.

EVALUATOR
3 and 4. To run the code for problem 3 and 4, open OurSearchEngine.java.  Please make sure that the data for problem 2 is in the same location.  Press run.  

We have commented out our code that uses an inverted index and the code for our failure analysis.  Please feel free to uncomment it and use it.
We have also commented out our code to do a more efficient retrieval of the top K documents which uses a Priority Queue.  However this has a bug and does not run correctly.  It was just an experiment to reach O(n * log(k)) instead of O(n * log(n)).
