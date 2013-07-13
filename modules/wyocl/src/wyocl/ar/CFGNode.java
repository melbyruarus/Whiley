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
import wyocl.ar.Bytecode.ConditionalJump;
import wyocl.ar.Bytecode.Return;
import wyocl.ar.DFGGenerator.DFGReadWriteTracking;
import wyocl.ar.utils.TopologicalSorter;
import wyocl.ar.utils.TopologicalSorter.DAGSortNode;

public abstract class CFGNode implements TopologicalSorter.DAGSortNode {
	public interface GPUSupportedNode {
		public boolean isGPUSupported();
	}
	
	public interface PassThroughNode {
		public CFGNode finalTarget();
	}

	public Set<CFGNode> previous = new HashSet<CFGNode>();
	public int identifier;
	protected DFGReadWriteTracking startRegisterInfo;
	protected DFGReadWriteTracking endRegisterInfo;

	/**
	 * Get the set of next nodes from the perspective of control flow.
	 * 
	 * For example for a loop this will be the body, and for a conditional
	 * will be the different branches. For a normal node this will be the
	 * following node.
	 * @param nodes
	 */
	public abstract void getFlowNextNodes(Set<CFGNode> nodes);
	/**
	 * Get the set of next nodes from the perspective of scope.
	 * 
	 * For example for a loop this will be the node after the end of the loop
	 * and for a conditional will be the node at which the different branches meet.
	 * For a normal node this will be the following node.
	 * @param nodes
	 */
	public abstract void getScopeNextNodes(Set<CFGNode> nodes);
	/**
	 * Get the set of nested nodes
	 * 
	 * For example for a loop this will be the body, and for a conditional
	 * will be the different branches. For a normal node this will be empty.
	 * @param nodes
	 */
	public abstract void getNestedNextNodes(Set<CFGNode> nodes);
	
	@Override
	public String toString() {
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		
		pw.print(this.hashCode());
		pw.print(' ');
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
		this.getFlowNextNodes(nodes);
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
		this.getScopeNextNodes(nodes);
		return (Set<DAGSortNode>)((Object)nodes);
	}
	
	/**
	 * This method should only ever be called directly by DFGGenerator.
	 * 
	 * This method should only be called for CFGNodes at exactly the same scope level,
	 * this method will automatically recurse for nested CFGNodes. i.e. getScopeNextNodes
	 * should be used when recursing.
	 * 
	 * @param info
	 */
	public abstract void propogateRegisterInfo(DFGReadWriteTracking info);
	
	public abstract void gatherDFGNodesInto(Set<DFGNode> allDFGNodes);
	
	public DFGReadWriteTracking getStartTypes() {
		return startRegisterInfo;
	}

	public DFGReadWriteTracking getEndRegisterInfo() {
		return endRegisterInfo;
	}
	
	public static class VanillaCFGNode extends CFGNode implements GPUSupportedNode {
		public CFGNode.Block body = new Block();
		public CFGNode next;
		private Set<DFGNode> cachedBytecodeDFGNodes;
		
		@Override
		public void getFlowNextNodes(Set<CFGNode> nodes) {
			nodes.add(next);
		}
		
		@Override
		public void getScopeNextNodes(Set<CFGNode> nodes) {
			nodes.add(next);
		}
		
		@Override
		public void getNestedNextNodes(Set<CFGNode> nodes) {
		}

		@Override
		public void propogateRegisterInfo(DFGReadWriteTracking info) {
			startRegisterInfo = DFGGenerator.mergeRegisterInfo(startRegisterInfo, info);
			cachedBytecodeDFGNodes = new HashSet<DFGNode>();
			endRegisterInfo = DFGGenerator.propogateDFGThroughBytecodes(body.instructions, startRegisterInfo, cachedBytecodeDFGNodes);
		}
		
		@Override
		public void gatherDFGNodesInto(Set<DFGNode> allDFGNodes) {
			allDFGNodes.addAll(cachedBytecodeDFGNodes);
		}

		@Override
		public boolean isGPUSupported() {
			return true;
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
		public void getFlowNextNodes(Set<CFGNode> nodes) {
			nodes.add(body);
		}
		
		@Override
		public void getScopeNextNodes(Set<CFGNode> nodes) {
			nodes.add(this.endNode.next);
			for(CFGNode.LoopBreakNode n : breakNodes) {
				nodes.add(n.next);
			}
		}
		
		@Override
		public void getNestedNextNodes(Set<CFGNode> nodes) {
			nodes.add(body);
		}
		
		@Override
		public void propogateRegisterInfo(DFGReadWriteTracking info) {
			startRegisterInfo = DFGGenerator.mergeRegisterInfo(startRegisterInfo, info);
			endRegisterInfo = DFGGenerator.propogateDFGThroughBytecode((Bytecode)abstractLoopBytecode, startRegisterInfo);
			
			Set<CFGNode> endNodes = new HashSet<CFGNode>();
			endNodes.add(endNode);
			endNodes.addAll(breakNodes);
			
			DFGGenerator.populatePartialDFGFromRecursion(body, endRegisterInfo, endNodes);
			
			boolean success = false;
			for(int n=0;n<5;n++) {
				DFGReadWriteTracking oldInfo = endRegisterInfo;
				endRegisterInfo = DFGGenerator.mergeRegisterInfo(endRegisterInfo, endNode.getEndRegisterInfo());
				if(oldInfo.equals(endRegisterInfo)) {
					success = true;
					break;
				}
				DFGGenerator.populatePartialDFGFromRecursion(body, endRegisterInfo, endNodes);
			}
			
			if(!success) {
				throw new RuntimeException("Loop Info Not Stabalising");
			}
		}

		@Override
		public void gatherDFGNodesInto(Set<DFGNode> allDFGNodes) {
			allDFGNodes.addAll(((Bytecode)abstractLoopBytecode).readDFGNodes.values());
			allDFGNodes.addAll(((Bytecode)abstractLoopBytecode).writtenDFGNodes.values());
		}
	}
	
	public static class ForLoopNode extends CFGNode.LoopNode implements GPUSupportedNode {
		private final Bytecode.For bytecode;
		
		public ForLoopNode(Bytecode.For bytecode, int startIndex, int endIndex) {super(bytecode, startIndex, endIndex);this.bytecode = bytecode;}

		public Bytecode.For getBytecode() {
			return bytecode;
		}
		
		@Override
		public boolean isGPUSupported() {
			return true;
		}
	}
	
	public static class WhileLoopNode extends CFGNode.LoopNode  implements GPUSupportedNode {
		private final Bytecode.While bytecode;
		
		public WhileLoopNode(Bytecode.While bytecode, int startIndex, int endIndex) {super(bytecode, startIndex, endIndex);this.bytecode = bytecode;}

		@Override
		public boolean isGPUSupported() {
			return true;
		}

		public Bytecode.While getBytecode() {
			return bytecode;
		}
	}
	
	public static class LoopBreakNode extends CFGNode implements GPUSupportedNode, PassThroughNode {
		public final CFGNode.LoopNode loop;
		
		public CFGNode next;
		
		LoopBreakNode(CFGNode.LoopNode loop) {this.loop = loop;}
		
		@Override
		public void getFlowNextNodes(Set<CFGNode> nodes) {
			nodes.add(next);
		}

		@Override
		public void getScopeNextNodes(Set<CFGNode> nodes) {
			nodes.add(loop.endNode); // Sorting only, not control flow
		}
		
		@Override
		public void getNestedNextNodes(Set<CFGNode> nodes) {
		}

		@Override
		public void propogateRegisterInfo(DFGReadWriteTracking info) {
			endRegisterInfo = startRegisterInfo = DFGGenerator.mergeRegisterInfo(startRegisterInfo, info);
		}
		
		@Override
		public void gatherDFGNodesInto(Set<DFGNode> allDFGNodes) {
		}

		@Override
		public boolean isGPUSupported() {
			return true;
		}
		
		@Override
		public CFGNode finalTarget() {
			if(next instanceof PassThroughNode) {
				return ((PassThroughNode) next).finalTarget();
			}
			else {
				return next;
			}
		}
	}
	
	public static class LoopEndNode extends CFGNode implements GPUSupportedNode {
		public final CFGNode.LoopNode loop;
		
		public CFGNode next;
		
		LoopEndNode(CFGNode.LoopNode loop) {this.loop = loop;}
		
		@Override
		public void getFlowNextNodes(Set<CFGNode> nodes) {
			nodes.add(next);
		}

		@Override
		public void getScopeNextNodes(Set<CFGNode> nodes) {
		}
		
		@Override
		public void getNestedNextNodes(Set<CFGNode> nodes) {
		}

		@Override
		public void propogateRegisterInfo(DFGReadWriteTracking info) {
			endRegisterInfo = startRegisterInfo = DFGGenerator.mergeRegisterInfo(startRegisterInfo, info);
		}
		
		@Override
		public void gatherDFGNodesInto(Set<DFGNode> allDFGNodes) {
		}

		@Override
		public boolean isGPUSupported() {
			return true;
		}
	}
	
	/**
	 * A node which represents a multi conditional jump. May be able to be converted to a switch statement.
	 * 
	 * 
	 * @author melby
	 *
	 */
	public static class MultiConditionalJumpNode extends CFGNode implements GPUSupportedNode {
		private final Bytecode.Switch switchBytecode;
		
		public List<Pair<Constant, CFGNode>> branches = new ArrayList<Pair<Constant, CFGNode>>();
		public CFGNode defaultBranch;
		
		public MultiConditionalJumpNode(Bytecode.Switch switchBytecode) {this.switchBytecode = switchBytecode;}
		
		public List<Pair<Constant, String>> getBranchTargets() {
			return switchBytecode.getBranchTargets();
		}
		
		public String getDefaultTarget() {
			return switchBytecode.getDefaultTargetLabel();
		}
		
		public List<Pair<Constant, CFGNode>> getBranches() {
			return branches;
		}
		
		public CFGNode getDefaultBranch() {
			return defaultBranch;
		}
		
		@Override
		public void getFlowNextNodes(Set<CFGNode> nodes) {
			nodes.add(defaultBranch);
			for(Pair<Constant, CFGNode> p : branches) {
				nodes.add(p.second());
			}
		}

		@Override
		public void getScopeNextNodes(Set<CFGNode> nodes) {
			getFlowNextNodes(nodes);
		}
		
		@Override
		public void getNestedNextNodes(Set<CFGNode> nodes) {
		}

		@Override
		public void propogateRegisterInfo(DFGReadWriteTracking info) {
			startRegisterInfo = DFGGenerator.mergeRegisterInfo(startRegisterInfo, info);
			endRegisterInfo = DFGGenerator.propogateDFGThroughBytecode(switchBytecode, startRegisterInfo);
		}
		
		@Override
		public void gatherDFGNodesInto(Set<DFGNode> allDFGNodes) {
			allDFGNodes.addAll(switchBytecode.readDFGNodes.values());
			allDFGNodes.addAll(switchBytecode.writtenDFGNodes.values());
		}

		@Override
		public boolean isGPUSupported() {
			return true;
		}

		public Bytecode getBytecode() {
			return switchBytecode;
		}

		public int getCheckedRegister() {
			return switchBytecode.getCheckedRegister();
		}
	}
	
	/**
	 * A node which represents a conditional jump. May be able to be converted to an if/else statement
	 * 
	 * @author melby
	 * 
	 */
	public static class ConditionalJumpNode extends CFGNode implements GPUSupportedNode {
		private final Bytecode.ConditionalJump conditionalJump;
		
		public CFGNode conditionMet;
		public CFGNode conditionUnmet;
				
		public ConditionalJumpNode(Bytecode.ConditionalJump conditionalJump) {this.conditionalJump = conditionalJump;}

		public String conditionMetTarget() {
			return conditionalJump.getConditionMetTargetLabel();
		}
		
		@Override
		public void getFlowNextNodes(Set<CFGNode> nodes) {
			nodes.add(conditionMet);
			nodes.add(conditionUnmet);
		}
		
		@Override
		public void getScopeNextNodes(Set<CFGNode> nodes) {
			getFlowNextNodes(nodes);
		}
		
		@Override
		public void getNestedNextNodes(Set<CFGNode> nodes) {
		}
		
		@Override
		public void propogateRegisterInfo(DFGReadWriteTracking info) {
			startRegisterInfo = DFGGenerator.mergeRegisterInfo(startRegisterInfo, info);
			endRegisterInfo = DFGGenerator.propogateDFGThroughBytecode((Bytecode)conditionalJump, startRegisterInfo);
		}
		
		@Override
		public void gatherDFGNodesInto(Set<DFGNode> allDFGNodes) {
			allDFGNodes.addAll(((Bytecode)conditionalJump).readDFGNodes.values());
			allDFGNodes.addAll(((Bytecode)conditionalJump).writtenDFGNodes.values());
		}

		@Override
		public boolean isGPUSupported() {
			return conditionalJump instanceof Bytecode.GPUSupportedBytecode;
		}

		public ConditionalJump getBytecode() {
			return conditionalJump;
		}
	}
	
	public static class ReturnNode extends CFGNode implements GPUSupportedNode {
		private final Bytecode.Return bytecode;
				
		public ReturnNode(Bytecode.Return bytecode) {
			this.bytecode = bytecode;
		}
		
		@Override
		public void getFlowNextNodes(Set<CFGNode> nodes) {
		}

		@Override
		public void getScopeNextNodes(Set<CFGNode> nodes) {
		}
		
		@Override
		public void getNestedNextNodes(Set<CFGNode> nodes) {
		}

		@Override
		public void propogateRegisterInfo(DFGReadWriteTracking info) {
			startRegisterInfo = DFGGenerator.mergeRegisterInfo(startRegisterInfo, info);
			endRegisterInfo = DFGGenerator.propogateDFGThroughBytecode(bytecode, startRegisterInfo);
		}
		
		@Override
		public void gatherDFGNodesInto(Set<DFGNode> allDFGNodes) {
			allDFGNodes.addAll(bytecode.readDFGNodes.values());
			allDFGNodes.addAll(bytecode.writtenDFGNodes.values());
		}

		@Override
		public boolean isGPUSupported() {
			return true;
		}

		public Return getBytecode() {
			return bytecode;
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
		public void getNestedNextNodes(Set<CFGNode> nodes) {
		}

		@Override
		public void getFlowNextNodes(Set<CFGNode> nodes) {
		}
		
		@Override
		public void getScopeNextNodes(Set<CFGNode> nodes) {
		}
		
		@Override
		public String toString() {
			return super.toString() + " " + (isUnresolvedLabel ? targetLabel : targetLine);
		}

		@Override
		public void propogateRegisterInfo(DFGReadWriteTracking info) {
			endRegisterInfo = startRegisterInfo = DFGGenerator.mergeRegisterInfo(startRegisterInfo, info);
		}
		
		@Override
		public void gatherDFGNodesInto(Set<DFGNode> allDFGNodes) {
		}
	}
}