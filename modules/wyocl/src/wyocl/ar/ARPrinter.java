package wyocl.ar;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import wyocl.ar.utils.NotADAGException;
import wyocl.ar.utils.TopologicalSorter;

public class ARPrinter {
	public static void print(CFGNode rootNode) throws NotADAGException {
		Set<CFGNode> nodesSet = new HashSet<CFGNode>();
		recursivlyAdd(rootNode, nodesSet);
		
		List<CFGNode> nodesList = TopologicalSorter.sort(nodesSet);
		
		for(CFGNode n : nodesList) {
			System.err.println(n);
		}
	}

	private static void recursivlyAdd(CFGNode node, Set<CFGNode> nodes) {
		if(node != null) {
			nodes.add(node);
			
			Set<CFGNode> subnodes = new HashSet<CFGNode>();
			node.getNextNodes(subnodes);
			for(CFGNode n : subnodes) {
				recursivlyAdd(n, nodes);
			}
		}
	}
}