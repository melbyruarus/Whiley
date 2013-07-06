package wyocl.ar;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import wybs.util.Pair;
import wyil.lang.Constant;
import wyil.lang.Type;
import wyocl.ar.DFGNode.DFGNodeCause;
import wyocl.ar.utils.TopologicalSorter;
import wyocl.ar.utils.TopologicalSorter.DAGSortNode;

public abstract class CFGNode implements TopologicalSorter.DAGSortNode {
	public Set<CFGNode> previous = new HashSet<CFGNode>();
	public int identifier;
	protected Map<Integer, Pair<Type, Set<DFGNode>>> startRegisterInfo;
	protected Map<Integer, Pair<Type, Set<DFGNode>>> endRegisterInfo;

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
	
	public abstract void propogateRegisterInfo(Map<Integer, Pair<Type, Set<DFGNode>>> info);
	
	public Map<Integer, Pair<Type, Set<DFGNode>>> getStartTypes() {
		return startRegisterInfo;
	}

	public Map<Integer, Pair<Type, Set<DFGNode>>> getEndRegisterInfo() {
		return endRegisterInfo;
	}
	
	public static class VanillaCFGNode extends CFGNode {
		public CFGNode.Block body = new Block();
		public CFGNode next;
		
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
		public void propogateRegisterInfo(Map<Integer, Pair<Type, Set<DFGNode>>> info) {
			startRegisterInfo = DFGGenerator.mergeRegisterInfo(startRegisterInfo, info);
			endRegisterInfo = DFGGenerator.propogateTypesThroughBytecodes(body.instructions, startRegisterInfo);
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
			for(LoopBreakNode n : breakNodes) {
				nodes.add(n.next);
			}
		}
		
		@Override
		public void getNestedNextNodes(Set<CFGNode> nodes) {
			nodes.add(body);
		}
		
		@Override
		public void propogateRegisterInfo(Map<Integer, Pair<Type, Set<DFGNode>>> info) {
			startRegisterInfo = DFGGenerator.mergeRegisterInfo(startRegisterInfo, info);
			endRegisterInfo = new HashMap<Integer, Pair<Type, Set<DFGNode>>>(startRegisterInfo);
			updateEndRegisterInfoWithLoopRegisters(endRegisterInfo);
			
			Set<CFGNode> endNodes = new HashSet<CFGNode>();
			endNodes.add(endNode);
			endNodes.addAll(breakNodes);
			
			DFGGenerator.populateDFG(body, endRegisterInfo, endNodes);
			
			boolean success = false;
			for(int n=0;n<5;n++) {
				Map<Integer, Pair<Type, Set<DFGNode>>> oldInfo = endRegisterInfo;
				endRegisterInfo = DFGGenerator.mergeRegisterInfo(endRegisterInfo, endNode.getEndRegisterInfo());
				if(oldInfo.equals(endRegisterInfo)) {
					success = true;
					break;
				}
				DFGGenerator.populateDFG(body, endRegisterInfo, endNodes);
			}
			
			if(!success) {
				throw new RuntimeException("Loop Info Not Stabalising");
			}
		}

		protected abstract void updateEndRegisterInfoWithLoopRegisters(Map<Integer, Pair<Type, Set<DFGNode>>> registerInfo);
	}
	
	public static class ForLoopNode extends CFGNode.LoopNode implements DFGNodeCause {
		private final Bytecode.For bytecode;
		public DFGNode indexDFGNode;
		public DFGNode sourceDFGNode;
		
		public ForLoopNode(Bytecode.For bytecode, int startIndex, int endIndex) {super(bytecode, startIndex, endIndex);this.bytecode = bytecode;}

		@Override
		protected void updateEndRegisterInfoWithLoopRegisters(Map<Integer, Pair<Type, Set<DFGNode>>> registerInfo) {
			int indexRegister = bytecode.getIndexRegister();
			Type indexType = bytecode.getIndexType();
			int sourceRegister = bytecode.getSourceRegister();
			Type sourceType = bytecode.getSourceType();
			
			Set<DFGNode> dfgNodeSet = new HashSet<DFGNode>();
			
			if(indexDFGNode == null) {
				Pair<Type, Set<DFGNode>> state = registerInfo.get(indexRegister);
				if(state == null) {
					throw new RuntimeException("Internal Inconsistancy");
				}
				
				indexDFGNode = new DFGNode(this, indexRegister);
				indexDFGNode.lastModified.addAll(state.second());
			}
			
			indexDFGNode.type = DFGGenerator.mergeTypes(indexDFGNode.type, indexType);
			
			dfgNodeSet.add(indexDFGNode);
			
			if(sourceDFGNode == null) {
				Pair<Type, Set<DFGNode>> state = registerInfo.get(sourceRegister);
				if(state == null) {
					throw new RuntimeException("Internal Inconsistancy");
				}
				
				sourceDFGNode = new DFGNode(this, sourceRegister);
				sourceDFGNode.type = indexType;
				sourceDFGNode.lastModified.addAll(state.second());
				
			}
			
			sourceDFGNode.type = DFGGenerator.mergeTypes(sourceDFGNode.type, indexType);
			
			registerInfo.put(indexRegister, new Pair<Type, Set<DFGNode>>(indexType, dfgNodeSet));
		}
	}
	
	public static class WhileLoopNode extends CFGNode.LoopNode {
		private final Bytecode.While bytecode;
		
		public WhileLoopNode(Bytecode.While bytecode, int startIndex, int endIndex) {super(bytecode, startIndex, endIndex);this.bytecode = bytecode;}

		@Override
		protected void updateEndRegisterInfoWithLoopRegisters(Map<Integer, Pair<Type, Set<DFGNode>>> registerInfo) {
		}
	}
	
	public static class LoopBreakNode extends CFGNode {
		private final CFGNode.LoopNode loop;
		
		public CFGNode next;
		
		LoopBreakNode(CFGNode.LoopNode loop) {this.loop = loop;}
		
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
		public void propogateRegisterInfo(Map<Integer, Pair<Type, Set<DFGNode>>> info) {
			endRegisterInfo = startRegisterInfo = DFGGenerator.mergeRegisterInfo(startRegisterInfo, info);
		}
	}
	
	public static class LoopEndNode extends CFGNode {
		private final CFGNode.LoopNode loop;
		
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
		public void propogateRegisterInfo(Map<Integer, Pair<Type, Set<DFGNode>>> info) {
			endRegisterInfo = startRegisterInfo = DFGGenerator.mergeRegisterInfo(startRegisterInfo, info);
		}
	}
	
	/**
	 * A node which represents a multi conditional jump. May be able to be converted to a switch statement.
	 * 
	 * 
	 * @author melby
	 *
	 */
	public static class MultiConditionalJumpNode extends CFGNode implements DFGNodeCause {
		private final Bytecode.Switch switchBytecode;
		
		public List<Pair<Constant, CFGNode>> branches = new ArrayList<Pair<Constant, CFGNode>>();
		public DFGNode dfgNode;
		public CFGNode defaultBranch;
		
		public MultiConditionalJumpNode(Bytecode.Switch switchBytecode) {this.switchBytecode = switchBytecode;}
		
		public List<Pair<Constant, String>> branchTargets() {
			return switchBytecode.branchTargets();
		}
		
		public String defaultTarget() {
			return switchBytecode.defaultTarget();
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
		public void propogateRegisterInfo(Map<Integer, Pair<Type, Set<DFGNode>>> info) {
			endRegisterInfo = startRegisterInfo = DFGGenerator.mergeRegisterInfo(startRegisterInfo, info);
			
			int register = switchBytecode.getCheckedRegister();
			
			if(dfgNode == null) {
				dfgNode = new DFGNode(this, register);
			}
			
			Pair<Type, Set<DFGNode>> state = endRegisterInfo.get(register);
			dfgNode.lastModified.addAll(state.second());
			dfgNode.type = DFGGenerator.mergeTypes(dfgNode.type, state.first());
		}
	}
	
	/**
	 * A node which represents a conditional jump. May be able to be converted to an if/else statement
	 * 
	 * @author melby
	 * 
	 */
	public static class ConditionalJumpNode extends CFGNode implements DFGNodeCause {
		private final Bytecode.ConditionalJump conditionalJump;
		
		public CFGNode conditionMet;
		public CFGNode conditionUnmet;
		
		public Map<Integer, DFGNode> dfgNodes = new HashMap<Integer, DFGNode>();
		
		public ConditionalJumpNode(Bytecode.ConditionalJump conditionalJump) {this.conditionalJump = conditionalJump;}

		public String conditionMetTarget() {
			return conditionalJump.conditionMetTarget();
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
		public void propogateRegisterInfo(Map<Integer, Pair<Type, Set<DFGNode>>> info) {
			endRegisterInfo = startRegisterInfo = DFGGenerator.mergeRegisterInfo(startRegisterInfo, info);
			
			Set<Integer> checkedRegisters = new HashSet<Integer>();
			conditionalJump.getCheckedRegisters(checkedRegisters);
			
			for(int reg : checkedRegisters) {
				DFGNode dfgNode = dfgNodes.get(reg);
				
				if(dfgNode == null) {
					dfgNode = new DFGNode(this, reg);
					dfgNodes.put(reg, dfgNode);
				}
				
				Pair<Type, Set<DFGNode>> state = endRegisterInfo.get(reg);
				dfgNode.lastModified.addAll(state.second());
				dfgNode.type = DFGGenerator.mergeTypes(dfgNode.type, state.first());
			}
		}
	}
	
	public static class ReturnNode extends CFGNode {
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
		public void propogateRegisterInfo(Map<Integer, Pair<Type, Set<DFGNode>>> info) {
			endRegisterInfo = startRegisterInfo = DFGGenerator.mergeRegisterInfo(startRegisterInfo, info);
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
		public void propogateRegisterInfo(Map<Integer, Pair<Type, Set<DFGNode>>> info) {
			endRegisterInfo = startRegisterInfo = DFGGenerator.mergeRegisterInfo(startRegisterInfo, info);
		}
	}
}