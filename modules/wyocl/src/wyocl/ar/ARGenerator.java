package wyocl.ar;

import java.io.PrintWriter;
import java.io.StringWriter;
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
import wyil.lang.Type;
import wyocl.ar.utils.NotADAGException;
import wyocl.ar.utils.TopologicalSorter;
import wyocl.ar.utils.TopologicalSorter.DAGSortNode;

public class ARGenerator {
	private static boolean DEBUG = false;
	
	public static abstract class DFGNode {
		public Set<DFGNode> lastModified = new HashSet<DFGNode>();
		public Bytecode bytecode;
		public int register;
		public Type type;
	}
	
	public static abstract class CFGNode implements TopologicalSorter.DAGSortNode {
		public Set<CFGNode> previous = new HashSet<CFGNode>();
		public int identifier;

		public abstract void getNextNodes(Set<CFGNode> nodes);
		
		@Override
		public String toString() {
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			
			pw.print(this.getClass().getSimpleName());
			pw.print('(');
			pw.print(identifier);
			pw.print(") previous:{");
			
			String sep = "";
			for(CFGNode n : this.previous) {
				if(n != null) {
					pw.print(sep);
					pw.print(n.identifier);
					sep = ", ";
				}
			}
			
			pw.print("} next:{");
			
			sep = "";
			Set<CFGNode> nodes = new HashSet<CFGNode>();
			this.getNextNodes(nodes);
			for(CFGNode n : nodes) {
				if(n != null) {
					pw.print(sep);
					pw.print(n.identifier);
					sep = ", ";
				}
			}
			
			pw.print('}');
			
			return sw.toString();
		}
		
		@SuppressWarnings("unchecked")
		@Override
		public Collection<DAGSortNode> getNextNodesForSorting() {
			Set<CFGNode> nodes = new HashSet<CFGNode>();
			this.getNextNodes(nodes);
			return (Set<DAGSortNode>)((Object)nodes);
		}
	}
	
	public static class VanillaCFGNode extends CFGNode {
		public Block body = new Block();
		public CFGNode next;
		
		@Override
		public void getNextNodes(Set<CFGNode> nodes) {
			nodes.add(next);
		}
	}
		
	public static class Block {
		public List<Bytecode> instructions = new ArrayList<Bytecode>();
	}
	
	public static abstract class LoopNode extends CFGNode {
		private final Bytecode.Loop abstractLoopBytecode;
		
		public CFGNode body;
		public Set<LoopBreakNode> breakNodes = new HashSet<LoopBreakNode>();
		public final int startIndex;
		public final int endIndex;
		public LoopEndNode endNode = new LoopEndNode(this);
		
		public LoopNode(Bytecode.Loop loop, int startIndex, int endIndex) {this.abstractLoopBytecode = loop;this.startIndex = startIndex;this.endIndex = endIndex;}
		
		public String loopEndLabel() {
			return abstractLoopBytecode.loopEndLabel();
		}
		
		@Override
		public void getNextNodes(Set<CFGNode> nodes) {
			nodes.add(body);
		}
	}
	
	public static class ForLoopNode extends LoopNode {
		private final Bytecode.For bytecode;
		
		public ForLoopNode(Bytecode.For bytecode, int startIndex, int endIndex) {super(bytecode, startIndex, endIndex);this.bytecode = bytecode;}
	}
	
	public static class WhileLoopNode extends LoopNode {
		private final Bytecode.While bytecode;
		
		public WhileLoopNode(Bytecode.While bytecode, int startIndex, int endIndex) {super(bytecode, startIndex, endIndex);this.bytecode = bytecode;}
	}
	
	public static class LoopBreakNode extends CFGNode {
		private final LoopNode loop;
		
		public CFGNode next;
		
		LoopBreakNode(LoopNode loop) {this.loop = loop;}
		
		@Override
		public void getNextNodes(Set<CFGNode> nodes) {
			nodes.add(next);
		}
	}
	
	public static class LoopEndNode extends CFGNode {
		private final LoopNode loop;
		
		public CFGNode next;
		
		LoopEndNode(LoopNode loop) {this.loop = loop;}
		
		@Override
		public void getNextNodes(Set<CFGNode> nodes) {
			nodes.add(next);
		}
	}
	
	/**
	 * A node which represents a multi conditional jump. May be able to be converted to a switch statement.
	 * 
	 * 
	 * @author melby
	 *
	 */
	public static class MultiConditionalJumpNode extends CFGNode {
		private final Bytecode.Switch switchBytecode;
		
		public List<Pair<Constant, CFGNode>> branches = new ArrayList<Pair<Constant, CFGNode>>();
		public CFGNode defaultBranch;
		
		public MultiConditionalJumpNode(Bytecode.Switch switchBytecode) {this.switchBytecode = switchBytecode;}
		
		public List<Pair<Constant, String>> branchTargets() {
			return switchBytecode.branchTargets();
		}
		
		public String defaultTarget() {
			return switchBytecode.defaultTarget();
		}
		
		@Override
		public void getNextNodes(Set<CFGNode> nodes) {
			nodes.add(defaultBranch);
			for(Pair<Constant, CFGNode> p : branches) {
				nodes.add(p.second());
			}
		}
	}
	
	/**
	 * A node which represents a conditional jump. May be able to be converted to an if/else statement
	 * 
	 * @author melby
	 * 
	 */
	public static class ConditionalJumpNode extends CFGNode {
		private final Bytecode.ConditionalJump conditionalJump;
		
		public CFGNode conditionMet;
		public CFGNode conditionUnmet;
		
		public ConditionalJumpNode(Bytecode.ConditionalJump conditionalJump) {this.conditionalJump = conditionalJump;}

		public String conditionMetTarget() {
			return conditionalJump.conditionMetTarget();
		}
		
		@Override
		public void getNextNodes(Set<CFGNode> nodes) {
			nodes.add(conditionMet);
			nodes.add(conditionUnmet);
		}
	}
	
	public static class ReturnNode extends CFGNode {
		@Override
		public void getNextNodes(Set<CFGNode> nodes) {
		}
	}
	
	public static CFGNode processEntries(List<Entry> entries, Set<ReturnNode> exitPoints) {
		CFGNode rootNode = recursivlyConstructRoughCFG(entries, indexLabels(entries), new HashMap<Integer, CFGNode>(), new HashMap<String, LoopNode>(), 0, exitPoints);
		populateIdentifiers(rootNode, 0);
		
		try {
			ARPrinter.print(rootNode);
		} catch (NotADAGException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return rootNode;
	}
	
	private static int populateIdentifiers(CFGNode node, int id) {
		node.identifier = id;
		id++;
		Set<CFGNode> nodes = new HashSet<CFGNode>();
		node.getNextNodes(nodes);
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

	private static CFGNode recursivlyConstructRoughCFG(List<Entry> entries, Map<String, Integer> labelIndexes, Map<Integer, CFGNode> alreadyProcessedEntries, Map<String, LoopNode> loopEndIndex, int processingIndex,  Set<ReturnNode> exitPoints) {
		if(DEBUG) {System.err.println("Recursing to "+processingIndex);}
		if(alreadyProcessedEntries.containsKey(processingIndex)) {
			if(DEBUG) {System.err.println("Nothing to do");}
			return alreadyProcessedEntries.get(processingIndex);
		}
		else {
			VanillaCFGNode node = new VanillaCFGNode();
			if(DEBUG) {System.err.println("Creating node "+node);}
			
			for(int i=processingIndex;i<entries.size();i++) {
				if(i > processingIndex) {
					alreadyProcessedEntries.put(processingIndex, node);
				}
				
				Bytecode bytecode = Bytecode.bytecodeForCode(entries.get(i).code);
				
				if(DEBUG) {System.err.println("Processing code "+bytecode);}
				
				if(bytecode instanceof Bytecode.Control) {
					if(bytecode instanceof Bytecode.LoopEnd) {
						LoopEndNode end = loopEndIndex.get(((Bytecode.LoopEnd)bytecode).name()).endNode;
						if(i == processingIndex) {
							return end;
						}
						else {
							node.next = end;
							end.previous.add(node);
							return node;
						}
					}
					else if(bytecode instanceof Bytecode.Label) {
						if(i == processingIndex) {
							// Do nothing
						}
						else {
							CFGNode next = recursivlyConstructRoughCFG(entries, labelIndexes, alreadyProcessedEntries, loopEndIndex, i, exitPoints);
							node.next = next;
							next.previous.add(node);
							return node;
						}
					}
					else if(bytecode instanceof Bytecode.Jump) {
						CFGNode nextNode = null;
						
						if(bytecode instanceof Bytecode.UnconditionalJump) {
							Bytecode.UnconditionalJump jump = (Bytecode.UnconditionalJump)bytecode;
							int target = labelIndexes.get(jump.target());
							nextNode = createLoopBreaks(loopEndIndex, i, target, recursivlyConstructRoughCFG(entries, labelIndexes, alreadyProcessedEntries, loopEndIndex, target, exitPoints));
						}
						else if(bytecode instanceof Bytecode.ConditionalJump) {
							ConditionalJumpNode jumpNode = new ConditionalJumpNode((Bytecode.ConditionalJump)bytecode);
							if(DEBUG) {System.err.println("Creating node "+jumpNode);}
							int conditionMetTarget = labelIndexes.get(jumpNode.conditionMetTarget());
							jumpNode.conditionMet = createLoopBreaks(loopEndIndex, i, conditionMetTarget, recursivlyConstructRoughCFG(entries, labelIndexes, alreadyProcessedEntries, loopEndIndex, conditionMetTarget, exitPoints));
							jumpNode.conditionMet.previous.add(jumpNode);
							int conditionUnmetTarget = i+1;
							jumpNode.conditionUnmet = createLoopBreaks(loopEndIndex, i, conditionUnmetTarget, recursivlyConstructRoughCFG(entries, labelIndexes, alreadyProcessedEntries, loopEndIndex, conditionUnmetTarget, exitPoints));
							jumpNode.conditionUnmet.previous.add(jumpNode);
							
							nextNode = jumpNode;
						}
						else if(bytecode instanceof Bytecode.Return) {
							ReturnNode jumpNode = new ReturnNode();
							exitPoints.add(jumpNode);
							
							nextNode = jumpNode;
						}
						else if(bytecode instanceof Bytecode.Switch) {
							MultiConditionalJumpNode jumpNode = new MultiConditionalJumpNode((Bytecode.Switch)bytecode);
							if(DEBUG) {System.err.println("Creating node "+jumpNode);}
							for(Pair<Constant, String> branch : jumpNode.branchTargets()) {
								int target = labelIndexes.get(branch.second());
								Pair<Constant, CFGNode> branchMatchedPair = new Pair<Constant, CFGNode>(branch.first(), createLoopBreaks(loopEndIndex, i, target, recursivlyConstructRoughCFG(entries, labelIndexes, alreadyProcessedEntries, loopEndIndex, target, exitPoints)));
								branchMatchedPair.second().previous.add(jumpNode);
							}
							int target = labelIndexes.get(jumpNode.defaultBranch);
							jumpNode.defaultBranch = createLoopBreaks(loopEndIndex, i, target, recursivlyConstructRoughCFG(entries, labelIndexes, alreadyProcessedEntries, loopEndIndex, target, exitPoints));
							jumpNode.defaultBranch.previous.add(jumpNode);
							
							nextNode = jumpNode;
						}
						
						if(i == processingIndex) {
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
						LoopNode loopNode;
						if(bytecode instanceof Bytecode.For){ 
							loopNode = new ForLoopNode((Bytecode.For)loopBytecode, i, labelIndexes.get(loopBytecode.loopEndLabel()));
						}
						else { 
							loopNode = new WhileLoopNode((Bytecode.While)loopBytecode, i, labelIndexes.get(loopBytecode.loopEndLabel()));
						}
						
						if(DEBUG) {System.err.println("Creating node "+loopNode);}
						
						alreadyProcessedEntries.put(processingIndex, loopNode.endNode);
						loopEndIndex.put(loopBytecode.loopEndLabel(), loopNode);
						
						loopNode.body = recursivlyConstructRoughCFG(entries, labelIndexes, alreadyProcessedEntries, loopEndIndex, i+1, exitPoints);
						loopNode.body.previous.add(loopNode);
						loopNode.endNode.next = recursivlyConstructRoughCFG(entries, labelIndexes, alreadyProcessedEntries, loopEndIndex, labelIndexes.get(loopNode.loopEndLabel())+1, exitPoints);
						loopNode.endNode.next.previous.add(loopNode.endNode);
						
						if(i == processingIndex) {
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
			
			ReturnNode end = new ReturnNode();
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

	private static CFGNode createLoopBreaks(Map<String, LoopNode> loopEndIndex, int jumpFromIndex, int jumpToIndex, CFGNode cfgAfter) {
		if(DEBUG) {System.err.println("Checking for loop breaks");}
		Collection<LoopNode> loops = loopEndIndex.values();
		List<LoopNode> loopsBreakingFrom = new ArrayList<LoopNode>();
		
		for(LoopNode n : loops) {
			if(jumpFromIndex > n.startIndex && jumpToIndex > n.endIndex) {
				loopsBreakingFrom.add(n);
			}
		}
		
		if(loopsBreakingFrom.size() == 0) {
			if(DEBUG) {System.err.println("Didn't find any");}
			return cfgAfter;
		}
		else {
			if(DEBUG) {System.err.println("Found "+loopsBreakingFrom);}
			Collections.sort(loopsBreakingFrom, new Comparator<LoopNode>() {
				@Override
				public int compare(LoopNode arg0, LoopNode arg1) {
					return arg1.endIndex - arg0.endIndex;
				}
			});
			
			LoopBreakNode last = null;
			for(LoopNode n : loopsBreakingFrom) {
				LoopBreakNode loopBreakNode = new LoopBreakNode(n);
				if(last == null) {
					loopBreakNode.next = cfgAfter;
					cfgAfter.previous.add(loopBreakNode);
				}
				else {
					loopBreakNode.next = last;
					last.previous.add(loopBreakNode);
				}
				last = loopBreakNode;
			}
			
			return last;
		}
	}
}
