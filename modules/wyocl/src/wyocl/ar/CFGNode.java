package wyocl.ar;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import wybs.util.Pair;
import wyil.lang.Constant;
import wyocl.ar.utils.TopologicalSorter;
import wyocl.ar.utils.TopologicalSorter.DAGSortNode;

public abstract class CFGNode implements TopologicalSorter.DAGSortNode {
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
	
	public static class VanillaCFGNode extends CFGNode {
		public CFGNode.Block body = new Block();
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
		public Set<CFGNode.LoopBreakNode> breakNodes = new HashSet<CFGNode.LoopBreakNode>();
		public final int startIndex;
		public final int endIndex;
		public CFGNode.LoopEndNode endNode = new LoopEndNode(this);
		
		public LoopNode(Bytecode.Loop loop, int startIndex, int endIndex) {this.abstractLoopBytecode = loop;this.startIndex = startIndex;this.endIndex = endIndex;}
		
		public String loopEndLabel() {
			return abstractLoopBytecode.loopEndLabel();
		}
		
		@Override
		public void getNextNodes(Set<CFGNode> nodes) {
			nodes.add(body);
		}
	}
	
	public static class ForLoopNode extends CFGNode.LoopNode {
		private final Bytecode.For bytecode;
		
		public ForLoopNode(Bytecode.For bytecode, int startIndex, int endIndex) {super(bytecode, startIndex, endIndex);this.bytecode = bytecode;}
	}
	
	public static class WhileLoopNode extends CFGNode.LoopNode {
		private final Bytecode.While bytecode;
		
		public WhileLoopNode(Bytecode.While bytecode, int startIndex, int endIndex) {super(bytecode, startIndex, endIndex);this.bytecode = bytecode;}
	}
	
	public static class LoopBreakNode extends CFGNode {
		private final CFGNode.LoopNode loop;
		
		public CFGNode next;
		
		LoopBreakNode(CFGNode.LoopNode loop) {this.loop = loop;}
		
		@Override
		public void getNextNodes(Set<CFGNode> nodes) {
			nodes.add(next);
		}
	}
	
	public static class LoopEndNode extends CFGNode {
		private final CFGNode.LoopNode loop;
		
		public CFGNode next;
		
		LoopEndNode(CFGNode.LoopNode loop) {this.loop = loop;}
		
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
	
	public static class UnresolvedTargetNode extends CFGNode {
		private final boolean isUnresolvedLabel;
		private final String targetLabel;
		private final int targetLine;
		
		public UnresolvedTargetNode(String target) {
			isUnresolvedLabel = true;
			targetLabel = target;
			targetLine = 0;
		}

		public UnresolvedTargetNode(int target) {
			isUnresolvedLabel = false;
			targetLabel = null;
			targetLine = target;
		}
		
		public boolean isUnresolvedLabel() {
			return isUnresolvedLabel;
		}
		
		public String getUnresolvedLabel() {
			return targetLabel;
		}
		
		public int getUnresolvedLine() {
			return targetLine;
		}

		@Override
		public void getNextNodes(Set<CFGNode> nodes) {
		}
		
		@Override
		public String toString() {
			return super.toString() + " " + (isUnresolvedLabel ? targetLabel : targetLine);
		}
	}
}