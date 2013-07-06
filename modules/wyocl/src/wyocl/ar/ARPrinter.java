package wyocl.ar;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import wyocl.ar.utils.NotADAGException;
import wyocl.ar.utils.TopologicalSorter;

public class ARPrinter {
	private static class Entry {
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
	
	public static void print(CFGNode rootNode) throws NotADAGException {
		List<Entry> nodesList = createList(rootNode);
		printList(nodesList, 0);
	}
	
	private static void printList(List<Entry> nodesList, int indent) {
		for(Entry e : nodesList) {
			if(e.node != null) {
				for(int i=0;i<indent;i++) {
					System.err.print('\t');
				}
				System.err.println(e.node);
			}
			else {
				printList(e.scope, indent+1);
			}
		}
	}

	public static List<Entry> createList(CFGNode node) throws NotADAGException {
		Set<CFGNode> nodesSet = new HashSet<CFGNode>();
		recursivlyAdd(node, nodesSet);
		
		List<CFGNode> nodesList = TopologicalSorter.sort(nodesSet);
		List<Entry> ret = new ArrayList<Entry>();
		
		for(CFGNode n : nodesList) {
			ret.add(new Entry(n));
			
			Set<CFGNode> nested = new HashSet<CFGNode>();
			n.getNestedNextNodes(nested);
			
			for(CFGNode inner : nested) {
				ret.add(new Entry(createList(inner)));
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
}