package wyocl.filter.optimizer.stages;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import wyocl.ar.ARPrinter;
import wyocl.ar.Bytecode;
import wyocl.ar.CFGGenerator;
import wyocl.ar.CFGNode;
import wyocl.ar.CFGNode.DummyNode;
import wyocl.ar.CFGNode.ForLoopNode;
import wyocl.ar.DFGNode;
import wyocl.ar.utils.CFGIterator;
import wyocl.ar.utils.CFGIterator.Entry;
import wyocl.ar.utils.DFGIterator;
import wyocl.ar.utils.NotADAGException;
import wyocl.filter.CFGCompatabilityAnalyser.LoopAnalyserResult;
import wyocl.filter.LoopType;

public class MultidimensionalLoopFlatteningStage {
	private static final boolean DEBUG = false;
	
	public static void process(final DummyNode dummyNode, final LoopAnalyserResult analyserResult, Map<Integer, DFGNode> argumentRegisters) {
		try {
			if(DEBUG) {
				System.err.println("------before-------");
				try {
					ARPrinter.print(dummyNode, false);
				} catch(NotADAGException e) {
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
				} catch(NotADAGException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		} catch(NotADAGException e) {
			throw new RuntimeException(e); // Should never happen
		}
	}
	
	private static void recursivlySearchForAndFlattenImplicitLoops(LoopAnalyserResult analyserResult, List<Entry> nestedRepresentation) {
		for(int i = 0; i < nestedRepresentation.size(); i++) {
			Entry e = nestedRepresentation.get(i);
			
			if(e.node != null) {
				if(e.node instanceof CFGNode.ForLoopNode) {
					if(analyserResult.loopCompatabilities.get(e.node) == LoopType.GPU_IMPLICIT) {
						recursivlyFlattenLoop(analyserResult, (CFGNode.ForLoopNode)e.node, nestedRepresentation.get(i + 1).scope, 1);
						i++; // Skip nested content
					}
				}
			} else {
				recursivlySearchForAndFlattenImplicitLoops(analyserResult, e.scope);
			}
		}
	}
	
	private static void recursivlyFlattenLoop(LoopAnalyserResult analyserResult, CFGNode.ForLoopNode loop, List<Entry> body, int depth) {
		if(loop.getIndexes().size() >= 3) {
			return;
		}
		
		// FIXME: Check to see the inner loop isn't depended on
		// FIXME: Check the start/end registers for the inner loop are defined
		// outside of the outer loop
		
		// Find the biggest (super rough) inner loop
		int max = -1;
		int size = 0;
		
		for(int i = 0; i < body.size(); i++) {
			Entry e = body.get(i);
			
			if(e.node != null) {
				if(e.node instanceof CFGNode.ForLoopNode) {
					CFGNode.ForLoopNode innerLoop = (CFGNode.ForLoopNode)e.node;
					
					if(areLoopsIndependant(loop, innerLoop) && canBytecodesBeReshuffled(loop, innerLoop, getFirstLevelBytecodes(body, i))) {
						LoopType compat = analyserResult.loopCompatabilities.get(innerLoop);
						if(compat == LoopType.GPU_IMPLICIT || compat == LoopType.GPU_IMPLICIT_INNER) {
							if(body.get(i + 1).scope.size() > size) {
								max = i;
							}
						}
					}
				}
			}
		}
		
		if(max == -1) {
			return;
		}
		
		CFGNode.ForLoopNode innerLoop = (CFGNode.ForLoopNode)body.get(max).node;
		
		// Move the inner index bytecodes outside the outer loop
		CFGNode.VanillaCFGNode newHeader = new CFGNode.VanillaCFGNode();
		
		List<Bytecode> extractedBytecodes = getDependantBytecodes(getFirstLevelBytecodes(body, max), loop, innerLoop);
		for(Bytecode b : extractedBytecodes) {
			((CFGNode.VanillaCFGNode)b.cfgNode).body.instructions.remove(b);
			b.cfgNode = newHeader;
			newHeader.body.instructions.add(b);
		}
		
		for(CFGNode c : loop.previous) {
			c.retargetNext(loop, newHeader);
			newHeader.previous.add(c);
		}
		loop.previous.clear();
		loop.previous.add(newHeader);
		newHeader.next = loop;
		
		// Flatten the inner loop into the outer loop
		loop.getIndexes().addAll(innerLoop.getIndexes());
		innerLoop.getIndexes().clear();
		
		innerLoop.body.previous.clear();
		for(CFGNode pre : innerLoop.previous) {
			pre.retargetNext(innerLoop, innerLoop.body);
			innerLoop.body.previous.add(pre);
		}
		innerLoop.previous.clear();
		innerLoop.body = null;
		
		innerLoop.endNode.next.previous.clear();
		for(CFGNode pre : innerLoop.endNode.previous) {
			pre.retargetNext(innerLoop.endNode, innerLoop.endNode.next);
			innerLoop.endNode.next.previous.add(pre);
		}
		innerLoop.endNode.previous.clear();
		innerLoop.endNode.next = null;
	}
	
	private static List<Bytecode> getFirstLevelBytecodes(List<Entry> body, int upTo) {
		List<Bytecode> ret = new ArrayList<Bytecode>();
		
		for(int i = 0; i < upTo; i++) {
			if(body.get(i).node instanceof CFGNode.VanillaCFGNode) {
				ret.addAll(((CFGNode.VanillaCFGNode)body.get(i).node).body.instructions);
			}
		}
		
		return ret;
	}
	
	private static boolean areLoopsIndependant(ForLoopNode outerLoop, ForLoopNode innerLoop) {
		Set<DFGNode> startNodes = new HashSet<DFGNode>();
		innerLoop.getSourceDFGNodes(startNodes);
		
		Set<DFGNode> exclusiveNodes = new HashSet<DFGNode>();
		outerLoop.getIndexDFGNodes(exclusiveNodes);
		
		return !DFGIterator.doNodesDependUpon(startNodes, exclusiveNodes);
	}
	
	private static boolean canBytecodesBeReshuffled(ForLoopNode outerLoop, ForLoopNode innerLoop, List<Bytecode> firstLevelBytecodes) {
		Set<DFGNode> startNodes = new HashSet<DFGNode>();
		innerLoop.getSourceDFGNodes(startNodes);
		
		Set<DFGNode> dependantNodes = new HashSet<DFGNode>();
		
		DFGIterator.gatherDependantNodes(startNodes, dependantNodes);
		
		for(DFGNode n : dependantNodes) {
			if(DFGIterator.doesDFGNodeOccurBetween(outerLoop, innerLoop, n) && !firstLevelBytecodes.contains(n.cause)) {
				return false;
			}
		}
		
		return true;
	}
	
	private static List<Bytecode> getDependantBytecodes(final List<Bytecode> body, ForLoopNode outerLoop, ForLoopNode innerLoop) {
		Set<DFGNode> startNodes = new HashSet<DFGNode>();
		innerLoop.getSourceDFGNodes(startNodes);
		
		Set<DFGNode> dependantNodes = new HashSet<DFGNode>();
		DFGIterator.gatherDependantNodes(startNodes, dependantNodes);
		
		Set<Bytecode> ret = new HashSet<Bytecode>();
		
		for(DFGNode n : dependantNodes) {			
			if(DFGIterator.doesDFGNodeOccurBetween(outerLoop, innerLoop, n)) {
				ret.add((Bytecode)n.cause);
			}
		}
		
		List<Bytecode> list = new ArrayList<Bytecode>(ret);
		
		Collections.sort(list, new Comparator<Bytecode>() {
			@Override
			public int compare(Bytecode o1, Bytecode o2) {
				return body.indexOf(o1) - body.indexOf(o2);
			}
		});
		
		return list;
	}
}
