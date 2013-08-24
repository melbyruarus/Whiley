package wyocl.ar;

import java.util.List;

import wyocl.ar.utils.CFGIterator;
import wyocl.ar.utils.CFGIterator.Entry;
import wyocl.ar.utils.NotADAGException;

public class ARPrinter {
	public static void print(CFGNode rootNode, final boolean withDFG) throws NotADAGException {
		print(CFGIterator.createNestedRepresentation(rootNode), withDFG);
	}

	public static void print(List<Entry> sortedCFG, final boolean withDFG) {
		final int[] indent = new int[1];
		indent[0] = 0;
		
		CFGIterator.traverseNestedRepresentation(sortedCFG, new CFGIterator.CFGNestedRepresentationVisitor() {
			@Override
			public void visitNonNestedNode(CFGNode node) {
				for(int i=0;i<indent[0];i++) {
					System.err.print('\t');
				}
				System.err.println(node);
				if(node instanceof CFGNode.VanillaCFGNode) {
					for(Bytecode b : ((CFGNode.VanillaCFGNode) node).body.instructions) {
						for(int i=0;i<indent[0]+1;i++) {
							System.err.print('\t');
						}
						
						if(withDFG) {
							System.err.print(b);
							System.err.print(" ");
							System.err.println(b.getDFGNodeSummaryString());
						}
						else {
							System.err.println(b);
						}
					}
				}
			}
			
			@Override
			public void exitedNestedNode(CFGNode node) {
				indent[0]--;
			}
			
			@Override
			public void enteredNestedNode(CFGNode node, List<Entry> nestedEntries) {
				indent[0]++;
			}
		});
	}
}