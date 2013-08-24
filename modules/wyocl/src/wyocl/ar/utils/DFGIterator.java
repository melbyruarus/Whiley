package wyocl.ar.utils;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import wyocl.ar.CFGNode;
import wyocl.ar.DFGNode;
import wyocl.ar.utils.CFGIterator.CFGNodeCallback;

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
	
	public static void iterateDFGAlongLastModified(DFGNodeCallback callback, Collection<DFGNode> start, Collection<DFGNode> end) {
		Set<DFGNode> fringe = new HashSet<DFGNode>();
		Set<DFGNode> processed = new HashSet<DFGNode>();
		
		fringe.addAll(start);
		processed.addAll(end);
		
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
	
	public static void iterateDFGAlongLastRead(DFGNodeCallback callback, DFGNode start) {
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
			
			for(DFGNode next : node.lastRead) {
				if(!processed.contains(next)) {
					fringe.add(next);
				}
			}
		}
	}
	
	public static void iterateDFGAlongLastRead(DFGNodeCallback callback, Collection<DFGNode> start, Collection<DFGNode> end) {
		Set<DFGNode> fringe = new HashSet<DFGNode>();
		Set<DFGNode> processed = new HashSet<DFGNode>();
		
		fringe.addAll(start);
		processed.addAll(end);
		
		while(!fringe.isEmpty()) {
			DFGNode node = fringe.iterator().next();
			processed.add(node);
			fringe.remove(node);
			
			if(!callback.process(node)) {
				return;
			}
			
			for(DFGNode next : node.lastRead) {
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

	public static int maxUsedRegister(CFGNode node) {
		Set<CFGNode> roots = CFGIterator.getRoots(node);
		if(roots.size() != 1) {
			throw new InternalError("CFGNode "+node+" has more than one root");
		}
		
		final Set<DFGNode> dfgNodes = new HashSet<DFGNode>();
		
		CFGIterator.iterateCFGFlow(new CFGNodeCallback() {
			@Override
			public boolean process(CFGNode node) {
				node.gatherDFGNodesInto(dfgNodes);
				return true;
			}
		}, roots.iterator().next(), null);
		
		int max = 0;
		for(DFGNode n : dfgNodes) {
			if(n.register > max) {
				max = n.register;
			}
		}
		
		return max;
	}
}
