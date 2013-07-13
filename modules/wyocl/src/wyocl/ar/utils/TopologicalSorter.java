package wyocl.ar.utils;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class TopologicalSorter {
	private final static boolean DEBUG = false;
	
	public interface DAGSortNode {
		public Collection<DAGSortNode> getNextNodesForSorting(); 
	}
	
	public static <T extends DAGSortNode> List<T> sort(Collection<T> nodes) throws NotADAGException {
		List<T> l = new LinkedList<T>();
		Set<T> unmarked = new HashSet<T>(nodes);
		Set<T> temporaryMarked = new HashSet<T>();
		Set<T> allNodes = new HashSet<T>(nodes);
		
		if(DEBUG) { System.err.println("------- Sorting ------"); }
		
		while(unmarked.size() > 0) {
			T anObject = unmarked.iterator().next();
			
			if(allNodes.contains(anObject)) {
				visit(anObject, allNodes, l, unmarked, temporaryMarked);
			}
		}
		
		return l;
	}
	
	@SuppressWarnings("unchecked")
	private static <T extends DAGSortNode> void visit(T node, Set<T> allNodes, List<T> l, Set<T> unmarked, Set<T> temporaryMarked) throws NotADAGException {
		if(temporaryMarked.contains(node)) {
			throw new NotADAGException();
		}
		
		if(unmarked.contains(node)) {
			if(DEBUG) { System.err.println("Visiting: "+node); }
			
			temporaryMarked.add(node);
			for(DAGSortNode n : node.getNextNodesForSorting()) {
				if(allNodes.contains(n)) {
					visit((T)n, allNodes, l, unmarked, temporaryMarked);
				}
			}
			temporaryMarked.remove(node);
			unmarked.remove(node);
			l.add(0, node);
			
			if(DEBUG) { System.err.println("Leaving: "+node + " Array: " + l); }
		}
	}
}
