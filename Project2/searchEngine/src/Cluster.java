import java.util.ArrayList;
import java.util.LinkedList;

public class Cluster {
	private LinkedList<String> docs;
	private ArrayList<Double> distances;
	private int index;
	
	public Cluster(LinkedList<String> doc, double[] dist, int i) {
		this.docs = doc;
		this.distances = new ArrayList<Double>();
		this.index = i;
		
		for (double d : dist) {
			this.distances.add(new Double(d));
		}
	}
	
	public void setIndex(int i) {
		index = i;
	}
	
	public LinkedList<String> getDocs() {
		return docs;
	}
	
	public double getDistance(int i) {
		return distances.get(i).doubleValue();
	}
	
	public double getSmallestDistance() {
		double smallest = Double.MAX_VALUE;
		
		for (int j=0; j < distances.size(); j++) {
			if (j != index) {
				if (distances.get(j).doubleValue() <= smallest) {
					smallest = distances.get(j).doubleValue();
				}
			}
		}
		
		return smallest;
	}
	
	public int getIndexOfSmallestDistance() {
		double smallest = Double.MAX_VALUE;
		int i = -1;
		
		for (int j=0; j < distances.size(); j++) {
			if (j != index) {
				if (distances.get(j).doubleValue() <= smallest || i == -1) {
					smallest = distances.get(j).doubleValue();
					i = j;
				}
			}
		}
		
		return i;
	}
	
	public void completeLinkMerge(Cluster c) {
		//update cluster
		docs.addAll(c.docs);
				
		//update distances
		for (int i=0; i<distances.size(); i++) {
			double mine = distances.get(i).doubleValue();
			double theirs = c.distances.get(i).doubleValue();
			
			if (theirs > mine) {
				distances.remove(i);
				distances.add(i, new Double(theirs));
			}
		}
	}
	
	public void removeDistance(int i) {
		distances.remove(i);
	}

}
