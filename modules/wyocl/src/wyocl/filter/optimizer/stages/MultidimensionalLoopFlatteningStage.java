package wyocl.filter.optimizer.stages;

import java.util.List;
import java.util.Map;

import wyocl.ar.ARPrinter;
import wyocl.ar.CFGGenerator;
import wyocl.ar.CFGNode;
import wyocl.ar.CFGNode.DummyNode;
import wyocl.ar.DFGNode;
import wyocl.ar.utils.CFGIterator;
import wyocl.ar.utils.CFGIterator.Entry;
import wyocl.ar.utils.NotADAGException;
import wyocl.filter.CFGCompatabilityAnalyser.LoopAnalyserResult;
import wyocl.filter.LoopType;

public class MultidimensionalLoopFlatteningStage {
	private static final boolean DEBUG = true;

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
			
			List<Entry> nestedRepresentation = CFGIterator.createNestedRepresentation(dummyNode);
			recursivlySearchForAndFlattenImplicitLoops(analyserResult, nestedRepresentation);
			
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

	private static void recursivlySearchForAndFlattenImplicitLoops(LoopAnalyserResult analyserResult, List<Entry> nestedRepresentation) {
		for(int i=0;i<nestedRepresentation.size();i++) {
			Entry e = nestedRepresentation.get(i);
			
			if(e.node != null) {
				if(e.node instanceof CFGNode.ForLoopNode) {
					if(analyserResult.loopCompatabilities.get(e.node) == LoopType.GPU_IMPLICIT) {
						recursivlyFlattenLoop(analyserResult, (CFGNode.ForLoopNode)e.node, nestedRepresentation.get(i+1).scope, 1);
						i++; // Skip nested content
					}
				}
			}
			else {
				recursivlySearchForAndFlattenImplicitLoops(analyserResult, e.scope);
			}
		}
	}

	private static void recursivlyFlattenLoop(LoopAnalyserResult analyserResult, CFGNode.ForLoopNode loop, List<Entry> body, int depth) {		
		if(depth > 3) {
			return;
		}
		
		// FIXME: Check to see the inner loop isn't depended on
		// FIXME: Check the start/end registers for the inner loop are defined outside of the outer loop
		
		// Find the biggest (super rough) inner loop
		int max = -1;
		int size = 0;
		
		for(int i=0;i<body.size();i++) {
			Entry e = body.get(i);
			
			if(e.node != null) {
				if(e.node instanceof CFGNode.ForLoopNode) {
					LoopType compat = analyserResult.loopCompatabilities.get(e.node);
					if(compat == LoopType.GPU_IMPLICIT || compat == LoopType.GPU_IMPLICIT_INNER) {
						if(body.get(i+1).scope.size() > size) {
							max = i;
						}
					}
				}
			}
		}
		
		if(max == -1) {
			return;
		}
		
		CFGNode.ForLoopNode otherLoop = (CFGNode.ForLoopNode)body.get(max).node;
		
		// Check to see if the inner loop can also be flattened
		recursivlyFlattenLoop(analyserResult, otherLoop, body.get(max+1).scope, depth+1);
		
		// Flatten the inner loop into the outer loop
		loop.getIndexes().addAll(otherLoop.getIndexes());
		otherLoop.getIndexes().clear();
		
		otherLoop.body.previous.clear();
		for(CFGNode pre : otherLoop.previous) {
			pre.retargetNext(otherLoop, otherLoop.body);
			otherLoop.body.previous.add(pre);
		}
		otherLoop.previous.clear();
		otherLoop.body = null;
		
		otherLoop.endNode.next.previous.clear();
		for(CFGNode pre : otherLoop.endNode.previous) {
			pre.retargetNext(otherLoop.endNode, otherLoop.endNode.next);
			otherLoop.endNode.next.previous.add(pre);
		}
		otherLoop.endNode.previous.clear();
		otherLoop.endNode.next = null;
	}
}
