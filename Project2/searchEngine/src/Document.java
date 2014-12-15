public class Document {
	
	private String name;
	private double rank;
	private double clusterRank;
	
	public Document(String n, double r) {
		this.name = n;
		this.rank = r;
		//if -1, it is not in a cluster
		this.clusterRank = -1;
	}
	
	public double getRank() {
		return this.rank;
	}
	
	public double getClusterRank() {
		if (this.clusterRank == -1) {
			return this.rank;
		}
		else {
			return this.clusterRank;
		}
	}
	
	public void setClusterRank(double r) {
		this.clusterRank = r;
	}
	
	public String getName() {
		return this.name;
	}
}
