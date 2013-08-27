package wyocl.filter.optimizer.stages;

import java.util.List;
import java.util.Map;

import wyocl.ar.ARPrinter;
import wyocl.ar.CFGGenerator;
import wyocl.ar.CFGNode;
import wyocl.ar.CFGNode.DummyNode;
import wyocl.ar.DFGNode;
import wyocl.ar.utils.CFGIterator;
import wyocl.ar.utils.CFGIterator.CFGNestedRepresentationVisitor;
import wyocl.ar.utils.CFGIterator.Entry;
import wyocl.ar.utils.NotADAGException;
import wyocl.filter.CFGCompatabilityAnalyser.LoopAnalyserResult;

public class MultidimensionalLoopFlatteningStage {
	private static final boolean DEBUG = false;

	public static void process(final DummyNode dummyNode, final LoopAnalyserResult analyserResult, Map<Integer, DFGNode> argumentRegisters) {
		try {
			if(DEBUG) {
				System.err.println("------before-------");
				try {
					ARPrinter.print(dummyNode, false);
				} catch (NotADAGException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			
			CFGIterator.traverseNestedRepresentation(CFGIterator.createNestedRepresentation(dummyNode), new CFGNestedRepresentationVisitor() {
				@Override
				public void enteredNestedNode(CFGNode node, List<Entry> nestedEntries) {
					// TODO Auto-generated method stub
					
				}

				@Override
				public void exitedNestedNode(CFGNode node) {
					// TODO Auto-generated method stub
					
				}

				@Override
				public void visitNonNestedNode(CFGNode node) {
					// TODO Auto-generated method stub
					
				}
			});
			
			CFGGenerator.populateIdentifiers(dummyNode, 0);

			if(DEBUG) {
				System.err.println("------after-------");
				try {
					ARPrinter.print(dummyNode, false);
				} catch (NotADAGException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		} catch (NotADAGException e) {
			throw new RuntimeException(e); // Should never happen
		}
	}
}
