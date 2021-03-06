import java.util.Comparator;


public class DocumentComparator implements Comparator<Document> {

	@Override
	public int compare(Document o1, Document o2) {
		//check cluster rank
		if (o1.getClusterRank() < o2.getClusterRank()) {
			return 1;
		}
		if (o2.getClusterRank() < o1.getClusterRank()) {
			return -1;
		}
		
		//check regular ranks
		if (o1.getRank() < o2.getRank()) {
			return 1;
		}
		if (o2.getRank() < o1.getRank()) {
			return -1;
		}
		return 0;
	}

}
