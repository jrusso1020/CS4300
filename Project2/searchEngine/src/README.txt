Please make sure you have the necessary jars for the project.

MINI
Problem 1 - 3:
To run the code for Problems 1 - 3, please ensure that the following date is in the correct location:
		String cacmDocsDir = "data/cacm"; // directory containing CACM documents
		String cacmIndexDir = "data/index/cacm2"; // the directory where CACM index is written into
		
		String medDocsDir = "data/med"; // directory containing MED documents
		String medIndexDir = "data/index/med2"; // the directory where MED index is written into
		String cacmQueries = "data/cacm_processed.query"; //file containing CACM queries
		String medQueries = "data/med_processed.query"; //file containing MED queries
		String cacmAnswers = "data/cacm_processed.rel"; //file containing CACM relevant documents
		String medAnswers = "data/med_processed.rel"; //file containing MED relevant documents

Then open OurSearchEngine.java and press run.  This program with create the index if you do not have a version already and then do parts 1 - 3.

Problem 4:
Our version of a disk search engine can be found in DiskSearchEngine.java

Cluster.java: the cluster object we use when clustering
DiskSearchEngine.java: our attempt at a search engine that uses the disk
Document.java: an object used when ranking the documents
DocumentComparator.java: The comparator used to rank the documents
OurSearchEngine.java: file containing code for indexing and problems 1 - 3

The remaining files are ones provided to us.
