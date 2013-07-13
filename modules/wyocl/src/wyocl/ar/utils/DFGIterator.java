package wyocl.ar.utils;

import java.util.HashSet;
import java.util.Set;

import wyocl.ar.DFGNode;

public class DFGIterator {
	public interface DFGNodeCallback {
		/**
		 * Process an individual node in the DFG
		 * @param node The node at the current position in the iteration
		 * 
		 * @return true to continue iterating, false otherwise
		 */
		boolean process(DFGNode node);
	}
	
	public static void iterateDFGAlongLastModified(DFGNodeCallback callback, DFGNode start) {
		Set<DFGNode> fringe = new HashSet<DFGNode>();
		Set<DFGNode> processed = new HashSet<DFGNode>();
		
		fringe.add(start);
		
		while(!fringe.isEmpty()) {
			DFGNode node = fringe.iterator().next();
			processed.add(node);
			fringe.remove(node);
			
			if(!callback.process(node)) {
				return;
			}
			
			for(DFGNode next : node.lastModified) {
				if(!processed.contains(next)) {
					fringe.add(next);
				}
			}
		}
	}
	
	/**
	 * Determine if one node depends upon an other
	 * 
	 * @param one
	 * @param two
	 * @return Whether one depends upon two
	 */
	public static boolean doesNodeDependUpon(DFGNode one, final DFGNode two) {
		final boolean doesDepend[] = new boolean[1];
		doesDepend[0] = false;
		
		iterateDFGAlongLastModified(new DFGNodeCallback() {
			@Override
			public boolean process(DFGNode node) {
				if(node == two) {
					doesDepend[0] = true;
					return false;
				}
				return true;
			}
		}, one);
		
		return doesDepend[0];
	}
}
