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

import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import wybs.util.Pair;
import wyil.lang.Block.Entry;
import wyil.lang.Code;
import wyil.lang.Constant;
import wyil.lang.Type;
import wyocl.ar.ARGenerator.Bytecode.Loop;

public class ARGenerator {
	public static abstract class RegisterNode {
		public Set<RegisterNode> previous = new HashSet<RegisterNode>();
		public RegisterNode next;
		public Bytecode modifiedBytecode;
		public int register;
		public Type type;
	}
	
	public static abstract class Bytecode {
		public Set<RegisterNode> modifiedRegisters = new HashSet<RegisterNode>();
		public Set<RegisterNode> registersDependedUpon = new HashSet<RegisterNode>();
		public CFGNode cfgNode;
		
		public static interface Data {
		}
		
		public static interface Control {
		}
		
		public static interface Loop extends Control {
			String loopEndLabel();
		}
		
		public static interface Jump extends Control {
		}
		
		public static interface Target {
			public String name();
		}
		
		public static class Unary extends Bytecode implements Data {
			private final Code.UnArithOp code;
			
			public Unary(Code.UnArithOp code) { this.code = code; }
		}
		
		public static class Binary extends Bytecode implements Data {
			private final Code.BinArithOp code;
			
			public Binary(Code.BinArithOp code) { this.code = code; }
		}
		
		public static class ConstLoad extends Bytecode implements Data {
			private final Code.Const code;
			
			public ConstLoad(Code.Const code) { this.code = code; }
		}
		
		public static class Assign extends Bytecode implements Data {
			private final Code.Assign code;
			
			public Assign(Code.Assign code) { this.code = code; }
		}
		
		public static class Move extends Bytecode implements Data {
			private final Code.Move code;
			
			public Move(Code.Move code) { this.code = code; }
		}
		
		public static class Convert extends Bytecode implements Data {
			private final Code.Convert code;
			
			public Convert(Code.Convert code) { this.code = code; }
		}
		
		public static class Load extends Bytecode implements Data {
			private final Code.IndexOf code;
			
			public Load(Code.IndexOf code) { this.code = code; }
		}
		
		public static class LengthOf extends Bytecode implements Data {
			private final Code.LengthOf code;
			
			public LengthOf(Code.LengthOf code) { this.code = code; }
		}
		
		public static class TupleLoad extends Bytecode implements Data {
			private final Code.TupleLoad code;
			
			public TupleLoad(Code.TupleLoad code) { this.code = code; }
		}
		
		public static class Update extends Bytecode implements Data {
			private final Code.Update code;
			
			public Update(Code.Update code) { this.code = code; }
		}
		
		public static class For extends Bytecode implements Loop {
			private final Code.ForAll code;
			
			public For(Code.ForAll code) { this.code = code; }

			@Override
			public String loopEndLabel() {
				return code.target;
			}
		}
		
		public static class While extends Bytecode implements Loop {
			private final Code.Loop code;
			
			public While(Code.Loop code) { this.code = code; }
			
			@Override
			public String loopEndLabel() {
				return code.target;
			}
		}
		
		public static class UnconditionalJump extends Bytecode implements Jump {
			private final Code.Goto code;
			
			public UnconditionalJump(Code.Goto code) { this.code = code; }

			public String target() {
				return code.target;
			}
		}
		
		public static class ConditionalJump extends Bytecode implements Jump {
			private final Code.If code;
			
			public ConditionalJump(Code.If code) { this.code = code; }

			public String conditionMetTarget() {
				return code.target;
			}
		}
		
		public static class Switch extends Bytecode implements Jump {
			private final Code.Switch code;
			
			public Switch(Code.Switch code) { this.code = code; }
			
			public List<Pair<Constant, String>> branchTargets() {
				return code.branches;
			}

			public String defaultTarget() {
				return code.defaultTarget;
			}
		}
		
		public static class Label extends Bytecode implements Control, Target {
			private final Code.Label code;
			
			public Label(Code.Label code) { this.code = code; }

			@Override
			public String name() {
				return code.label;
			}
		}
		
		public static class LoopEnd extends Bytecode implements Control, Target {
			private final Code.LoopEnd code;
			
			public LoopEnd(Code.LoopEnd code) { this.code = code; }
			
			@Override
			public String name() {
				return code.label;
			}
		}
		
		public static class Return extends Bytecode implements Jump {
			private final Code.Return code;
			
			public Return(Code.Return code) { this.code = code; }
		}
		
		public static class Invoke extends Bytecode {
			private final Code.Invoke code;
			
			public Invoke(Code.Invoke code) { this.code = code; }
		}
	}
	
	public static abstract class CFGNode {
		public Set<CFGNode> previous = new HashSet<CFGNode>();
	}
	
	public static class VanillaCFGNode extends CFGNode {
		public Block body = new Block();
		public CFGNode next;
	}
		
	public static class Block {
		public List<Bytecode> instructions = new ArrayList<Bytecode>();
	}
	
	public static abstract class LoopNode extends CFGNode {
		private final Bytecode.Loop abstractLoopBytecode;
		
		public CFGNode body;
		public CFGNode after;
		public Set<LoopBreakNode> breakNodes = new HashSet<LoopBreakNode>();
		public final int startIndex;
		public final int endIndex;
		public LoopEndNode endNode = new LoopEndNode(this);
		
		public LoopNode(Bytecode.Loop loop, int startIndex, int endIndex) {this.abstractLoopBytecode = loop;this.startIndex = startIndex;this.endIndex = endIndex;}
		
		public String loopEndLabel() {
			return abstractLoopBytecode.loopEndLabel();
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
	}
	
	public static class LoopEndNode extends CFGNode {
		private final LoopNode loop;
		
		LoopEndNode(LoopNode loop) {this.loop = loop;}
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
	}
	
	public static CFGNode processEntries(List<Entry> entries, Set<CFGNode> exitPoints) {
		CFGNode rootNode = recursivlyConstructRoughCFG(entries, indexLabels(entries), new HashMap<Integer, CFGNode>(), new HashMap<String, LoopNode>(), 0, exitPoints);
		
		return rootNode;
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

	private static CFGNode recursivlyConstructRoughCFG(List<Entry> entries, Map<String, Integer> labelIndexes, Map<Integer, CFGNode> alreadyProcessedEntries, Map<String, LoopNode> loopEndIndex, int processingIndex,  Set<CFGNode> exitPoints) {
		System.err.println("Recursing to "+processingIndex);
		if(alreadyProcessedEntries.containsKey(processingIndex)) {
			System.err.println("Nothing to do");
			return alreadyProcessedEntries.get(processingIndex);
		}
		else {
			VanillaCFGNode node = new VanillaCFGNode();
			alreadyProcessedEntries.put(processingIndex, node);
			System.err.println("Creating node "+node);
			
			for(int i=processingIndex;i<entries.size();i++) {
				Bytecode bytecode = processCode(entries.get(i).code);
				
				System.err.println("Processing code "+bytecode);
				
				if(bytecode instanceof Bytecode.Control) {
					if(bytecode instanceof Bytecode.LoopEnd) {
						CFGNode next = loopEndIndex.get(((Bytecode.LoopEnd)bytecode).name()).endNode;
						node.next = next;
						next.previous.add(node);
						return node;
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
						if(bytecode instanceof Bytecode.UnconditionalJump) {
							Bytecode.UnconditionalJump jump = (Bytecode.UnconditionalJump)bytecode;
							int target = labelIndexes.get(jump.target());
							CFGNode next = createLoopBreaks(loopEndIndex, i, target, recursivlyConstructRoughCFG(entries, labelIndexes, alreadyProcessedEntries, loopEndIndex, target, exitPoints));
							node.next = next;
							next.previous.add(node);
							return node;
						}
						else if(bytecode instanceof Bytecode.ConditionalJump) {
							ConditionalJumpNode next = new ConditionalJumpNode((Bytecode.ConditionalJump)bytecode);
							System.err.println("Creating node "+next);
							int conditionMetTarget = labelIndexes.get(next.conditionMetTarget());
							next.conditionMet = createLoopBreaks(loopEndIndex, i, conditionMetTarget, recursivlyConstructRoughCFG(entries, labelIndexes, alreadyProcessedEntries, loopEndIndex, conditionMetTarget, exitPoints));
							next.conditionMet.previous.add(next);
							int conditionUnmetTarget = i+1;
							next.conditionUnmet = createLoopBreaks(loopEndIndex, i, conditionUnmetTarget, recursivlyConstructRoughCFG(entries, labelIndexes, alreadyProcessedEntries, loopEndIndex, conditionUnmetTarget, exitPoints));
							next.conditionUnmet.previous.add(next);
							node.next = next;
							next.previous.add(node);
							return node;
						}
						else if(bytecode instanceof Bytecode.Return) {
							node.next = null;
							exitPoints.add(node);
							return node;
						}
						else if(bytecode instanceof Bytecode.Switch) {
							MultiConditionalJumpNode next = new MultiConditionalJumpNode((Bytecode.Switch)bytecode);
							System.err.println("Creating node "+next);
							for(Pair<Constant, String> branch : next.branchTargets()) {
								int target = labelIndexes.get(branch.second());
								Pair<Constant, CFGNode> branchMatchedPair = new Pair<Constant, CFGNode>(branch.first(), createLoopBreaks(loopEndIndex, i, target, recursivlyConstructRoughCFG(entries, labelIndexes, alreadyProcessedEntries, loopEndIndex, target, exitPoints)));
								branchMatchedPair.second().previous.add(next);
							}
							int target = labelIndexes.get(next.defaultBranch);
							next.defaultBranch = createLoopBreaks(loopEndIndex, i, target, recursivlyConstructRoughCFG(entries, labelIndexes, alreadyProcessedEntries, loopEndIndex, target, exitPoints));
							next.defaultBranch.previous.add(next);
							node.next = next;
							next.previous.add(node);
							return node;
						}
					}
					else if(bytecode instanceof Bytecode.Loop) {
						Bytecode.Loop loopBytecode = (Bytecode.Loop)bytecode;
						LoopNode next;
						if(bytecode instanceof Bytecode.For){ 
							next = new ForLoopNode((Bytecode.For)loopBytecode, i, labelIndexes.get(loopBytecode.loopEndLabel()));
						}
						else { 
							next = new WhileLoopNode((Bytecode.While)loopBytecode, i, labelIndexes.get(loopBytecode.loopEndLabel()));
						}
						
						System.err.println("Creating node "+next);
						
						alreadyProcessedEntries.put(processingIndex, next.endNode);
						loopEndIndex.put(loopBytecode.loopEndLabel(), next);
						
						next.body = recursivlyConstructRoughCFG(entries, labelIndexes, alreadyProcessedEntries, loopEndIndex, i+1, exitPoints);
						next.after = recursivlyConstructRoughCFG(entries, labelIndexes, alreadyProcessedEntries, loopEndIndex, labelIndexes.get(next.loopEndLabel())+1, exitPoints);
						node.next = next;
						next.previous.add(node);
						return next;
					}
				}
				else {
					node.body.instructions.add(bytecode);
				}
			}
			
			exitPoints.add(node);
			return node;
		}
	}

	private static CFGNode createLoopBreaks(Map<String, LoopNode> loopEndIndex, int jumpFromIndex, int jumpToIndex, CFGNode cfgAfter) {
		System.err.println("Checking for loop breaks");
		Collection<LoopNode> loops = loopEndIndex.values();
		List<LoopNode> loopsBreakingFrom = new ArrayList<LoopNode>();
		
		for(LoopNode n : loops) {
			if(jumpFromIndex > n.startIndex && jumpToIndex > n.endIndex) {
				loopsBreakingFrom.add(n);
			}
		}
		
		if(loopsBreakingFrom.size() == 0) {
			System.err.println("Didn't find any");
			return cfgAfter;
		}
		else {
			System.err.println("Found "+loopsBreakingFrom);
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

	private static Bytecode processCode(Code code) {
		if(code instanceof Code.ForAll) {
			return new Bytecode.For((Code.ForAll)code);
		}
		else if(code instanceof Code.Loop) { // Must be after Code.ForAll
			return new Bytecode.While((Code.Loop)code);
		}
		else if(code instanceof Code.Nop) {
			return null;
		}
		else if(code instanceof Code.TryCatch) {
			throw new NotImplementedException();
		}
		else if(code instanceof Code.Const) {
			return new Bytecode.ConstLoad((Code.Const)code);
		}
		else if(code instanceof Code.Goto) {
			return new Bytecode.UnconditionalJump((Code.Goto)code);
		}
		else if(code instanceof Code.TryEnd) { // Must be before Code.Label
			throw new NotImplementedException();
		}
		else if(code instanceof Code.LoopEnd) {
			return new Bytecode.LoopEnd((Code.LoopEnd)code);
		}
		else if(code instanceof Code.Label) {
			return new Bytecode.Label((Code.Label)code);
		}
		else if(code instanceof Code.Assert) {
			return null; // FIXME: this should probably do something else
		}
		else if(code instanceof Code.Assign) {
			return new Bytecode.Assign((Code.Assign)code);
		}
		else if(code instanceof Code.Convert) {
			return new Bytecode.Convert((Code.Convert)code);
		}
		else if(code instanceof Code.Debug) {
			throw new NotImplementedException();
		}
		else if(code instanceof Code.Dereference) {
			throw new NotImplementedException();
		}
		else if(code instanceof Code.Assume) {
			return null; // FIXME: this should probably do something else
		}
		else if(code instanceof Code.FieldLoad) {
			throw new NotImplementedException();
		}
		else if(code instanceof Code.If) {
			return  new Bytecode.ConditionalJump((Code.If)code);
		}
		else if(code instanceof Code.IfIs) {
			throw new NotImplementedException();
		}
		else if(code instanceof Code.IndexOf) {
			return new Bytecode.Load((Code.IndexOf)code);
		}
		else if(code instanceof Code.IndirectInvoke) {
			throw new NotImplementedException();
		}
		else if(code instanceof Code.Invert) {
			throw new NotImplementedException();
		}
		else if(code instanceof Code.Invoke) {
			return new Bytecode.Invoke((Code.Invoke)code);
		}
		else if(code instanceof Code.Lambda) {
			throw new NotImplementedException();
		}
		else if(code instanceof Code.LengthOf) {
			return new Bytecode.LengthOf((Code.LengthOf)code);
		}
		else if(code instanceof Code.Move) {
			return new Bytecode.Move((Code.Move)code);
		}
		else if(code instanceof Code.NewList) {
			throw new NotImplementedException();
		}
		else if(code instanceof Code.NewMap) {
			throw new NotImplementedException();
		}
		else if(code instanceof Code.NewObject) {
			throw new NotImplementedException();
		}
		else if(code instanceof Code.NewRecord) {
			throw new NotImplementedException();
		}
		else if(code instanceof Code.NewSet) {
			throw new NotImplementedException();
		}
		else if(code instanceof Code.NewTuple) {
			throw new NotImplementedException();
		}
		else if(code instanceof Code.Not) {
			throw new NotImplementedException();
		}
		else if(code instanceof Code.Return) {
			return new Bytecode.Return((Code.Return)code);
		}
		else if(code instanceof Code.SubList) {
			throw new NotImplementedException();
		}
		else if(code instanceof Code.SubString) {
			throw new NotImplementedException();
		}
		else if(code instanceof Code.Switch) {
			return new Bytecode.Switch((Code.Switch)code);
		}
		else if(code instanceof Code.Throw) {
			throw new NotImplementedException();
		}
		else if(code instanceof Code.TupleLoad) {
			return new Bytecode.TupleLoad((Code.TupleLoad)code);
		}
		else if(code instanceof Code.Update) {
			return new Bytecode.Update((Code.Update)code);
		}
		else if(code instanceof Code.Void) {
			throw new NotImplementedException();
		}
		else if(code instanceof Code.BinArithOp) {
			return new Bytecode.Binary((Code.BinArithOp)code);
		}
		else if(code instanceof Code.BinListOp) {
			throw new NotImplementedException();
		}
		else if(code instanceof Code.BinSetOp) {
			throw new NotImplementedException();
		}
		else if(code instanceof Code.BinStringOp) {
			throw new NotImplementedException();
		}
		else if(code instanceof Code.UnArithOp) {
			return new Bytecode.Unary((Code.UnArithOp)code);
		}
		else {
			throw new RuntimeException("Unknown bytecode encountered: "+code+" ("+code.getClass()+")");
		}
	}
}
