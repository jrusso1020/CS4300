public class Document {
	
	private String name;
	private double rank;
	
	public Document(String n, double r) {
		name = n;
		rank = r;
	}
	
	public double getRank() {
		return this.rank;
	}
	
	public String getName() {
		return this.name;
	}
}
