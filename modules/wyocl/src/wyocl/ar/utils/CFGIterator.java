package wyocl.ar.utils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import wyocl.ar.CFGNode;

public class CFGIterator {
	public interface CFGNodeCallback {
		/**
		 * Process an individual node in the CFG
		 * @param node The node at the current position in the iteration
		 * 
		 * @return true to continue iterating, false otherwise
		 */
		boolean process(CFGNode node);
	}
	
	public static void iterateCFGFlow(CFGNodeCallback callback, CFGNode start, Set<CFGNode> terminate) {
		Set<CFGNode> fringe = new HashSet<CFGNode>();
		Set<CFGNode> processed = new HashSet<CFGNode>();
		if(terminate != null) {
			processed.addAll(terminate);
		}
		
		fringe.add(start);
				
		while(!fringe.isEmpty()) {
			CFGNode node = fringe.iterator().next();
			processed.add(node);
			fringe.remove(node);
						
			if(!callback.process(node)) {
				return;
			}
			
			Set<CFGNode> next = new HashSet<CFGNode>();
			node.getFlowNextNodes(next);
			next.removeAll(processed);
			fringe.addAll(next);
		}
	}
	
	public static void iterateCFGScope(CFGNodeCallback callback, CFGNode start, Set<CFGNode> terminate) {
		Set<CFGNode> fringe = new HashSet<CFGNode>();
		Set<CFGNode> processed = new HashSet<CFGNode>();
		if(terminate != null) {
			processed.addAll(terminate);
		}
		
		fringe.add(start);
		
		while(!fringe.isEmpty()) {
			CFGNode node = fringe.iterator().next();
			processed.add(node);
			fringe.remove(node);
						
			if(!callback.process(node)) {
				return;
			}
			
			Set<CFGNode> next = new HashSet<CFGNode>();
			node.getScopeNextNodes(next);
			next.removeAll(processed);
			fringe.addAll(next);
		}
	}
	
	/**
	 * Determine if one node occurs on the path between two others
	 * 
	 * @param node
	 * @param one
	 * @param two
	 * @return Whether node occurs on a path between one and two
	 */
	public static boolean doesNodeDependUpon(final CFGNode node, final CFGNode one, final CFGNode two) {
		final boolean inBetween[] = new boolean[1];
		inBetween[0] = false;
		
		Set<CFGNode> terminate = new HashSet<CFGNode>();
		terminate.add(two);
		
		iterateCFGFlow(new CFGNodeCallback() {
			@Override
			public boolean process(CFGNode aNode) {
				if(aNode == node) {
					inBetween[0] = true;
					return false;
				}
				return true;
			}
		}, one, terminate);
		
		return inBetween[0];
	}

	public static class Entry {
		public final CFGNode node;
		public final List<Entry> scope;
		
		public Entry(CFGNode node) {
			this.node = node;
			this.scope = null;
		}
		
		public Entry(List<Entry> scope) {
			this.scope = scope;
			this.node = null;
		}
	}
	
	public static interface CFGNestedRepresentationVisitor {
		public void enteredNestedNode(CFGNode node, List<Entry> nestedEntries);
		public void exitedNestedNode(CFGNode node);
		public void visitNonNestedNode(CFGNode node);
	}
	
	public static void traverseNestedRepresentation(List<Entry> nodesList, CFGNestedRepresentationVisitor visitor) {
		traverseNestedRepresentation(nodesList, null, visitor);
	}
	
	private static void traverseNestedRepresentation(List<Entry> nodesList, CFGNode parent, CFGNestedRepresentationVisitor visitor) {
		CFGNode last = null;
		for(Entry e : nodesList) {
			if(e.node != null) {
				visitor.visitNonNestedNode(e.node);
				last = e.node;
			}
			else {
				visitor.enteredNestedNode(last, e.scope);
				traverseNestedRepresentation(e.scope, last, visitor);
				visitor.exitedNestedNode(last);
			}
		}
	}

	public static List<Entry> createNestedRepresentation(CFGNode node) throws NotADAGException {
		Set<CFGNode> nodesSet = new HashSet<CFGNode>();
		recursivlyAdd(node, nodesSet);
		
		List<CFGNode> nodesList = TopologicalSorter.sort(nodesSet);
		List<Entry> ret = new ArrayList<Entry>();
		
		for(CFGNode n : nodesList) {
			ret.add(new Entry(n));
			
			Set<CFGNode> nested = new HashSet<CFGNode>();
			n.getNestedNextNodes(nested);
			
			for(CFGNode inner : nested) {
				ret.add(new Entry(createNestedRepresentation(inner)));
			}
		}
		
		return ret;
	}

	private static void recursivlyAdd(CFGNode node, Set<CFGNode> nodes) {
		if(node != null) {
			nodes.add(node);
			
			Set<CFGNode> subnodes = new HashSet<CFGNode>();
			node.getScopeNextNodes(subnodes);
			for(CFGNode n : subnodes) {
				recursivlyAdd(n, nodes);
			}
		}
	}

	public static Set<CFGNode> getRoots(CFGNode node) {
		Set<CFGNode> previous = new HashSet<CFGNode>(node.previous);
		
		if(previous.isEmpty()) {
			previous.add(node);
			return previous;
		}
		
		while(true) {
			Set<CFGNode> fringe = new HashSet<CFGNode>();
			boolean more = false;
			for(CFGNode n : previous) {
				if(n.previous.isEmpty()) {
					fringe.add(n);
				}
				else {
					more = true;
					fringe.addAll(n.previous);
				}
			}
			if(!more) {
				return fringe;
			}
			else {
				previous = fringe;
			}
		}
	}

	public static void iterateSortedCFGScope(CFGNodeCallback callback, CFGNode start, Set<CFGNode> terminate) throws NotADAGException {
		// TODO Auto-generated method stub
		final Set<CFGNode> nodesSet = new HashSet<CFGNode>();
		
		iterateCFGScope(new CFGNodeCallback() {
			
			@Override
			public boolean process(CFGNode node) {
				nodesSet.add(node);
				return true;
			}
		}, start, terminate);
		
		List<CFGNode> nodesList = TopologicalSorter.sort(nodesSet);
		
		for(CFGNode n : nodesList) {
			callback.process(n);
		}
	}
}
