package wyocl.ar.utils;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import wyocl.ar.Bytecode;
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
	 * @param startNodes
	 * @param exclusiveNodes
	 * @return Whether one depends upon two
	 */
	public static boolean doNodesDependUpon(Set<DFGNode> startNodes, final Set<DFGNode> exclusiveNodes) {
		Set<DFGNode> dependantNodes = new HashSet<DFGNode>();
		gatherDependantNodes(startNodes, dependantNodes);
		
		dependantNodes.retainAll(exclusiveNodes);
		
		return !dependantNodes.isEmpty();
	}
	
	public static void gatherDependantNodes(Set<DFGNode> startNodes, Set<DFGNode> dependantNodes) {
		Set<DFGNode> fringe = new HashSet<DFGNode>();
		Set<DFGNode> temp = new HashSet<DFGNode>();
		fringe.addAll(startNodes);
		
		while(!fringe.isEmpty()) {
			DFGNode node = fringe.iterator().next();
			dependantNodes.add(node);
			fringe.remove(node);
			
			temp.clear();
			if(node.cause != null) {
				node.cause.gatherReadDFGNodes(temp);
			}

			for(DFGNode next : temp) {
				if(!dependantNodes.contains(next.lastModified)) {
					fringe.addAll(next.lastModified);
				}

				dependantNodes.add(next);
			}
		}
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

	public static boolean doesDFGNodeOccurBetween(CFGNode startNode, CFGNode endNode, DFGNode dfgNode) {
		CFGNode node = null;
		if(dfgNode.cause instanceof Bytecode) {
			Bytecode bytecode = (Bytecode)dfgNode.cause;
			node = bytecode.cfgNode;
		}
		else if(dfgNode.cause instanceof CFGNode) {
			node = (CFGNode)dfgNode.cause;
		}
		else if(dfgNode.cause == null) { // This can only occur for registers defined outside this function, i.e. argument
			return false;
		}
		else {
			throw new InternalError("Unknown DFGNode cause: " + dfgNode.cause);
		}
													
		return CFGIterator.doesNodeDependUpon(node, startNode, endNode);
	}
}
