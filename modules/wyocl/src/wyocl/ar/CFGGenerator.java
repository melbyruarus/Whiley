package wyocl.ar;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import wybs.util.Pair;
import wyil.lang.Block.Entry;
import wyil.lang.Code;
import wyil.lang.Constant;

public class CFGGenerator {
	private static boolean DEBUG = false;
	
	public static CFGNode processEntries(List<Entry> entries, Set<CFGNode.ReturnNode> exitPoints, Set<CFGNode.UnresolvedTargetNode> unresolvedTargets) {
		if(exitPoints == null) {
			exitPoints = new HashSet<CFGNode.ReturnNode>();
		}
		if(unresolvedTargets == null) {
			unresolvedTargets = new HashSet<CFGNode.UnresolvedTargetNode>();
		}
		
		CFGNode rootNode = recursivlyConstructRoughCFG(entries, indexLabels(entries), new HashMap<Integer, CFGNode>(), new HashMap<String, CFGNode.LoopNode>(), 0, exitPoints, unresolvedTargets);
		populateIdentifiers(rootNode, 0);
		
		return rootNode;
	}
	
	private static int populateIdentifiers(CFGNode node, int id) {
		node.identifier = id;
		id++;
		Set<CFGNode> nodes = new HashSet<CFGNode>();
		node.getFlowNextNodes(nodes);
		for(CFGNode n : nodes) {
			id = populateIdentifiers(n, id);
		}
		return id;
	}

	private static Map<String, Integer> indexLabels(List<Entry> entries) {
		Map<String, Integer> map = new HashMap<String, Integer>();
		
		int index = 0;
		for(Entry e : entries) {
			if(e.code instanceof Code.Label) {
				Code.Label l = (Code.Label)e.code;
				map.put(l.label, index);
			}
			index++;
		}
		
		return map;
	}

	private static CFGNode recursivlyConstructRoughCFG(List<Entry> entries, Map<String, Integer> labelIndexes, Map<Integer, CFGNode> alreadyProcessedEntries, Map<String, CFGNode.LoopNode> loopEndIndex, int processingIndex,  Set<CFGNode.ReturnNode> exitPoints, Set<CFGNode.UnresolvedTargetNode> unresolvedTargets) {
		if(DEBUG) {System.err.println("Recursing to "+processingIndex);}
		if(alreadyProcessedEntries.containsKey(processingIndex)) {
			if(DEBUG) {System.err.println("Nothing to do");}
			return alreadyProcessedEntries.get(processingIndex);
		}
		else {
			CFGNode.VanillaCFGNode node = new CFGNode.VanillaCFGNode();
			if(DEBUG) {System.err.println("Creating node "+node);}
			
			for(int i=processingIndex;i<entries.size();i++) {
				if(node.body.instructions.size() > 0) {
					alreadyProcessedEntries.put(processingIndex, node);
				}
				
				Bytecode bytecode = Bytecode.bytecodeForCode(entries.get(i).code);
				
				if(DEBUG) {System.err.println("Processing code "+bytecode);}
				
				if(bytecode != null) {
					if(bytecode instanceof Bytecode.Control) {
						if(bytecode instanceof Bytecode.LoopEnd) {
							CFGNode.LoopEndNode end = loopEndIndex.get(((Bytecode.LoopEnd)bytecode).name()).endNode;
							if(node.body.instructions.size() == 0) {
								return end;
							}
							else {
								node.next = end;
								end.previous.add(node);
								return node;
							}
						}
						else if(bytecode instanceof Bytecode.Label) {
							if(node.body.instructions.size() == 0) {
								// Do nothing
							}
							else {
								CFGNode next = recursivlyConstructRoughCFG(entries, labelIndexes, alreadyProcessedEntries, loopEndIndex, i, exitPoints, unresolvedTargets);
								node.next = next;
								next.previous.add(node);
								return node;
							}
						}
						else if(bytecode instanceof Bytecode.Jump) {
							CFGNode nextNode = null;
							
							if(bytecode instanceof Bytecode.UnconditionalJump) {
								Bytecode.UnconditionalJump jump = (Bytecode.UnconditionalJump)bytecode;
								Integer target = labelIndexes.get(jump.getTargetLabel());
								if(target != null) {
									nextNode = createLoopBreaks(loopEndIndex, i, target, recursivlyConstructRoughCFG(entries, labelIndexes, alreadyProcessedEntries, loopEndIndex, target, exitPoints, unresolvedTargets));
								}
								else {
									nextNode = new CFGNode.UnresolvedTargetNode(jump.getTargetLabel());
									unresolvedTargets.add((CFGNode.UnresolvedTargetNode)nextNode);
								}
							}
							else if(bytecode instanceof Bytecode.ConditionalJump) {
								CFGNode.ConditionalJumpNode jumpNode = new CFGNode.ConditionalJumpNode((Bytecode.ConditionalJump)bytecode);
								if(DEBUG) {System.err.println("Creating node "+jumpNode);}
								Integer conditionMetTarget = labelIndexes.get(jumpNode.conditionMetTarget());
								if(conditionMetTarget != null) {
									jumpNode.conditionMet = createLoopBreaks(loopEndIndex, i, conditionMetTarget, recursivlyConstructRoughCFG(entries, labelIndexes, alreadyProcessedEntries, loopEndIndex, conditionMetTarget, exitPoints, unresolvedTargets));
								}
								else {
									jumpNode.conditionMet = new CFGNode.UnresolvedTargetNode(jumpNode.conditionMetTarget());
									unresolvedTargets.add((CFGNode.UnresolvedTargetNode)jumpNode.conditionMet);
								}
								jumpNode.conditionMet.previous.add(jumpNode);
								int conditionUnmetTarget = i+1;
								if(conditionUnmetTarget < entries.size()) {
									jumpNode.conditionUnmet = createLoopBreaks(loopEndIndex, i, conditionUnmetTarget, recursivlyConstructRoughCFG(entries, labelIndexes, alreadyProcessedEntries, loopEndIndex, conditionUnmetTarget, exitPoints, unresolvedTargets));
								}
								else {
									jumpNode.conditionUnmet = new CFGNode.UnresolvedTargetNode(conditionUnmetTarget);
									unresolvedTargets.add((CFGNode.UnresolvedTargetNode)jumpNode.conditionUnmet);
								}
								jumpNode.conditionUnmet.previous.add(jumpNode);
								
								nextNode = jumpNode;
							}
							else if(bytecode instanceof Bytecode.Return) {
								CFGNode.ReturnNode jumpNode = new CFGNode.ReturnNode((Bytecode.Return)bytecode);
								exitPoints.add(jumpNode);
								
								nextNode = jumpNode;
							}
							else if(bytecode instanceof Bytecode.Switch) {
								CFGNode.MultiConditionalJumpNode jumpNode = new CFGNode.MultiConditionalJumpNode((Bytecode.Switch)bytecode);
								if(DEBUG) {System.err.println("Creating node "+jumpNode);}
								for(Pair<Constant, String> branch : jumpNode.getBranchTargets()) {
									Integer target = labelIndexes.get(branch.second());
									CFGNode targetNode;
									if(target != null) {
										targetNode = createLoopBreaks(loopEndIndex, i, target, recursivlyConstructRoughCFG(entries, labelIndexes, alreadyProcessedEntries, loopEndIndex, target, exitPoints, unresolvedTargets));
									}
									else {
										targetNode = new CFGNode.UnresolvedTargetNode(branch.second());
										unresolvedTargets.add((CFGNode.UnresolvedTargetNode)targetNode);
									}
									targetNode.previous.add(jumpNode);								
									jumpNode.branches.add(new Pair<Constant, CFGNode>(branch.first(), targetNode));
								}
								Integer target = labelIndexes.get(jumpNode.getDefaultTarget());
								if(target != null) {
									jumpNode.defaultBranch = createLoopBreaks(loopEndIndex, i, target, recursivlyConstructRoughCFG(entries, labelIndexes, alreadyProcessedEntries, loopEndIndex, target, exitPoints, unresolvedTargets));
								}
								else {
									jumpNode.defaultBranch = new CFGNode.UnresolvedTargetNode(jumpNode.getDefaultTarget());
									unresolvedTargets.add((CFGNode.UnresolvedTargetNode)jumpNode.defaultBranch);
								}
								jumpNode.defaultBranch.previous.add(jumpNode);
								
								nextNode = jumpNode;
							}
							
							if(node.body.instructions.size() == 0) {
								return nextNode;
							}
							else {
								node.next = nextNode;
								nextNode.previous.add(node);
								return node;
							}
						}
						else if(bytecode instanceof Bytecode.Loop) {
							Bytecode.Loop loopBytecode = (Bytecode.Loop)bytecode;
							CFGNode.LoopNode loopNode;
							Integer index = labelIndexes.get(loopBytecode.loopEndLabel());
							int end = 0;
							if(index != null) {
								end = index;
							}
							else {
								end = Integer.MAX_VALUE;
							}
							
							if(bytecode instanceof Bytecode.For){ 
								loopNode = new CFGNode.ForLoopNode((Bytecode.For)loopBytecode, i, end);
							}
							else { 
								loopNode = new CFGNode.WhileLoopNode((Bytecode.While)loopBytecode, i, end);
							}
							
							if(DEBUG) {System.err.println("Creating node "+loopNode);}
							
							alreadyProcessedEntries.put(end, loopNode.endNode);
							loopEndIndex.put(loopBytecode.loopEndLabel(), loopNode);
							
							if(i+1 < entries.size()) {
								loopNode.body = recursivlyConstructRoughCFG(entries, labelIndexes, alreadyProcessedEntries, loopEndIndex, i+1, exitPoints, unresolvedTargets);
							}
							else {
								loopNode.body = new CFGNode.UnresolvedTargetNode(i+1);
								unresolvedTargets.add((CFGNode.UnresolvedTargetNode)loopNode.body);
							}
							loopNode.body.previous.add(loopNode);
							Integer target = labelIndexes.get(loopNode.loopEndLabel());
							if(target != null) {
								target++;
								if(target < entries.size()) {
									loopNode.endNode.next = recursivlyConstructRoughCFG(entries, labelIndexes, alreadyProcessedEntries, loopEndIndex, target, exitPoints, unresolvedTargets);
								}
								else {
									loopNode.endNode.next = new CFGNode.ReturnNode(null);
									exitPoints.add((CFGNode.ReturnNode)loopNode.endNode.next);
								}
							}
							else {
								loopNode.endNode.next = new CFGNode.UnresolvedTargetNode(loopNode.loopEndLabel());
								unresolvedTargets.add((CFGNode.UnresolvedTargetNode)loopNode.endNode.next);
							}
							loopNode.endNode.next.previous.add(loopNode.endNode);
							
							if(node.body.instructions.size() == 0) {
								return loopNode;
							}
							else {
								node.next = loopNode;
								loopNode.previous.add(node);
								return node;
							}
						}
					}
					else {
						node.body.instructions.add(bytecode);
						bytecode.cfgNode = node;
					}
				}
			}
			
			CFGNode.ReturnNode end = new CFGNode.ReturnNode(null);
			exitPoints.add(end);
			
			if(node.body.instructions.size() > 0) {
				node.next = end;
				end.previous.add(node);
				
				return node;
			}
			else {
				return end;
			}
		}
	}

	private static CFGNode createLoopBreaks(Map<String, CFGNode.LoopNode> loopEndIndex, int jumpFromIndex, int jumpToIndex, CFGNode cfgAfter) {
		if(DEBUG) {System.err.println("Checking for loop breaks");}
		Collection<CFGNode.LoopNode> loops = loopEndIndex.values();
		List<CFGNode.LoopNode> loopsBreakingFrom = new ArrayList<CFGNode.LoopNode>();
		
		for(CFGNode.LoopNode n : loops) {
			if(jumpFromIndex > n.startIndex && jumpFromIndex < n.endIndex && jumpToIndex > n.endIndex) {
				loopsBreakingFrom.add(n);
			}
		}
		
		if(loopsBreakingFrom.size() == 0) {
			if(DEBUG) {System.err.println("Didn't find any");}
			return cfgAfter;
		}
		else {
			if(DEBUG) {System.err.println("Found "+loopsBreakingFrom);}
			Collections.sort(loopsBreakingFrom, new Comparator<CFGNode.LoopNode>() {
				@Override
				public int compare(CFGNode.LoopNode arg0, CFGNode.LoopNode arg1) {
					return arg1.endIndex - arg0.endIndex;
				}
			});
			
			CFGNode.LoopBreakNode last = null;
			for(CFGNode.LoopNode n : loopsBreakingFrom) {
				CFGNode.LoopBreakNode loopBreakNode = new CFGNode.LoopBreakNode(n);
				if(last == null) {
					loopBreakNode.next = cfgAfter;
					cfgAfter.previous.add(loopBreakNode);
				}
				else {
					loopBreakNode.next = last;
					last.previous.add(loopBreakNode);
				}
				last = loopBreakNode;
				n.breakNodes.add(loopBreakNode);
			}
			
			return last;
		}
	}
}
