import java.util.Comparator;


public class DocumentComparator implements Comparator<Document> {

	@Override
	public int compare(Document o1, Document o2) {
		if (o1.getRank() < o2.getRank()) {
			return 1;
		}
		if (o2.getRank() < o1.getRank()) {
			return -1;
		}
		return 0;
	}

}
