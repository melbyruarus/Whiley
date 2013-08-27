package wyocl.ar;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import wybs.util.Pair;
import wyil.lang.Code;
import wyil.lang.Constant;
import wyil.lang.Type;
import wyocl.ar.Bytecode.ConditionalJump;
import wyocl.ar.Bytecode.Return;
import wyocl.ar.DFGGenerator.DFGReadWriteTracking;
import wyocl.ar.utils.CFGIterator;
import wyocl.ar.utils.CFGIterator.CFGNodeCallback;
import wyocl.ar.utils.NotADAGException;
import wyocl.ar.utils.TopologicalSorter;
import wyocl.ar.utils.TopologicalSorter.DAGSortNode;

public abstract class CFGNode implements TopologicalSorter.DAGSortNode, DFGNode.DFGNodeCause {
	private static final String LABEL_PREFIX = "wyocl_wyil_cfg_wyil_conversion_label";
	
	public static interface BytecodeVisitor {
		public void visit(Bytecode b);
		public boolean shouldVisitNode(CFGNode node);
	}

	public interface GPUSupportedNode {
		public boolean isGPUSupported();
	}

	public interface PassThroughNode {
		public CFGNode finalTarget();
	}

	public Set<CFGNode> previous = new HashSet<CFGNode>();
	public int identifier = -1;
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
	protected abstract void propogateRegisterInfo(DFGReadWriteTracking info);

	public abstract void gatherDFGNodesInto(Set<DFGNode> allDFGNodes);

	protected abstract void clearDFGNodes();

	public abstract void retargetNext(CFGNode oldNode, CFGNode newNode);
	
	public abstract boolean isEmpty();
	
	public abstract void disconnect();
	
	/**
	 * Visit all the bytecodes in this node in order
	 * 
	 * This will be a recursive process, so there is no need for the caller to manually
	 * traverse into nested nodes, i.e. for the body of a loop
	 * 
	 * @param bytecodeVisitor
	 */
	public abstract void forBytecode(BytecodeVisitor bytecodeVisitor);

	public static String calculateLabel(CFGNode nextNode) {
		if(nextNode.identifier < 0) {
			throw new InternalError("Nodes must have ids propogated before calling forBytecode()");
		}
		
		return LABEL_PREFIX + nextNode.identifier;
	}
	
	private static void checkIfNeedingLabel(BytecodeVisitor bytecodeVisitor, CFGNode node) {
		if(node.identifier < 0) {
			throw new InternalError("Nodes must have ids propogated before calling forBytecode()");
		}
		
		bytecodeVisitor.visit(new Bytecode.Label(Code.Label(LABEL_PREFIX+node.identifier)));
	}
	
	public void gotoLabel(String label, BytecodeVisitor bytecodeVisitor) {
		bytecodeVisitor.visit(new Bytecode.UnconditionalJump(Code.Goto(label)));
	}
	
	public DFGReadWriteTracking getStartRegisterInfo() {
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
		protected void propogateRegisterInfo(DFGReadWriteTracking info) {
			startRegisterInfo = DFGGenerator.mergeRegisterInfo(startRegisterInfo, info);
			cachedBytecodeDFGNodes = new HashSet<DFGNode>();
			endRegisterInfo = DFGGenerator.propogateDFGThroughBytecodes(body.instructions, startRegisterInfo, cachedBytecodeDFGNodes);
		}

		@Override
		public void gatherDFGNodesInto(Set<DFGNode> allDFGNodes) {
			if(cachedBytecodeDFGNodes != null) {
				allDFGNodes.addAll(cachedBytecodeDFGNodes);
			}
		}

		@Override
		protected void clearDFGNodes() {
			startRegisterInfo = null;
			endRegisterInfo = null;
			
			cachedBytecodeDFGNodes = null;
			for(Bytecode b : this.body.instructions) {
				DFGGenerator.clearDFG(b);
			}
		}

		@Override
		public boolean isGPUSupported() {
			return true;
		}

		@Override
		public void retargetNext(CFGNode oldNode, CFGNode newNode) {
			if(next == oldNode) {
				next = newNode;
			}
		}

		@Override
		public void forBytecode(BytecodeVisitor bytecodeVisitor) {
			checkIfNeedingLabel(bytecodeVisitor, this);
			
			for(Bytecode b : body.instructions) {
				bytecodeVisitor.visit(b);
			}
			
			gotoLabel(calculateLabel(next), bytecodeVisitor);
		}

		@Override
		public boolean isEmpty() {
			return body.instructions.isEmpty();
		}

		@Override
		public void disconnect() {
			for(CFGNode n : previous) {
				n.retargetNext(this, next);
			}
			next.previous.remove(this);
			next.previous.addAll(previous);
			previous.clear();
			next = null;
		}
	}

	public static class Block {
		public List<Bytecode> instructions = new ArrayList<Bytecode>();
	}

	public static abstract class LoopNode extends CFGNode {
		private final String loopEndLabel;
		private final Code causialLoopBytecode; // FIXME: remove once performing bytecode-cfg-bytecode conversions

		public CFGNode body;
		public Set<CFGNode.LoopBreakNode> breakNodes = new HashSet<CFGNode.LoopBreakNode>();
		public final int startIndex;
		public final int endIndex;
		public CFGNode.LoopEndNode endNode = new LoopEndNode(this);

		public LoopNode(String loopEndLabel, int startIndex, int endIndex, Code causialLoopBytecode) {
			this.loopEndLabel = loopEndLabel;
			this.startIndex = startIndex;
			this.endIndex = endIndex;
			this.causialLoopBytecode = causialLoopBytecode;
		}

		public String loopEndLabel() {
			return loopEndLabel;
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
		protected void propogateRegisterInfo(DFGReadWriteTracking info) {
			startRegisterInfo = DFGGenerator.mergeRegisterInfo(startRegisterInfo, info);
			populateEndRegisterInfoFromStartRegisterInfo();

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

		protected abstract void populateEndRegisterInfoFromStartRegisterInfo();

		@Override
		public void retargetNext(CFGNode oldNode, CFGNode newNode) {
			if(this.body == oldNode) {
				this.body = newNode;
			}
			this.endNode.retargetNext(oldNode, newNode);
			for(CFGNode.LoopBreakNode n : breakNodes) {
				n.retargetNext(oldNode, newNode);
			}
		}

		public abstract void getIndexDFGNodes(Set<DFGNode> nodes);
		public abstract void getSourceDFGNodes(Set<DFGNode> nodes);

		public Code getCausialWYILLangBytecode() { // FIXME: remove once performing bytecode-cfg-bytecode conversions
			return causialLoopBytecode;
		}
		
		public void replaceWith(LoopNode newLoop) {
			newLoop.body = this.body;
			this.body = null;
			newLoop.body.previous.remove(this);
			newLoop.body.previous.add(newLoop);

			for(CFGNode.LoopBreakNode oldNode : this.breakNodes) {
				CFGNode.LoopBreakNode newNode = new CFGNode.LoopBreakNode(newLoop);
				newNode.previous.addAll(oldNode.previous);
				for(CFGNode n : newNode.previous) {
					n.retargetNext(oldNode, newNode);
				}
				oldNode.previous.clear();
				newNode.next = oldNode.next;
				oldNode.next = null;
				newLoop.breakNodes.add(newNode);
			}
			this.breakNodes.clear();

			newLoop.endNode.next = this.endNode.next;
			this.endNode.next = null;
			newLoop.endNode.next.previous.remove(this.endNode);
			newLoop.endNode.next.previous.add(newLoop.endNode);
			newLoop.endNode.previous.addAll(this.endNode.previous);
			for(CFGNode p : newLoop.endNode.previous) {
				p.retargetNext(this.endNode, newLoop.endNode);
			}
			this.endNode.previous.clear();


			newLoop.previous.addAll(this.previous);
			this.previous.clear();
			for(CFGNode n : newLoop.previous) {
				n.retargetNext(this, newLoop);
			}
		}
		
		@Override
		public boolean isEmpty() {
			return body == endNode || body.isEmpty();
		}

		@Override
		public void disconnect() {
			for(CFGNode n : previous) {
				n.retargetNext(this, endNode.next);
			}
			endNode.next.previous.remove(this);
			endNode.next.previous.addAll(previous);
			previous.clear();
			endNode.next = null;
			for(LoopBreakNode n : breakNodes) {
				n.next = null;
			}
		}
	}

	public static class ForAllLoopNode extends CFGNode.LoopNode implements GPUSupportedNode {
		private final Bytecode.ForAll bytecode;

		public ForAllLoopNode(Bytecode.ForAll bytecode, int startIndex, int endIndex) {
			super(bytecode.loopEndLabel(), startIndex, endIndex, bytecode.getWYILLangBytecode());
			this.bytecode = bytecode;
		}

		public Bytecode.ForAll getBytecode() {
			return bytecode;
		}

		@Override
		public boolean isGPUSupported() {
			return true;
		}

		@Override
		protected void populateEndRegisterInfoFromStartRegisterInfo() {
			endRegisterInfo = DFGGenerator.propogateDFGThroughBytecode(bytecode, startRegisterInfo);
		}

		@Override
		public void gatherDFGNodesInto(Set<DFGNode> allDFGNodes) {
			allDFGNodes.addAll(bytecode.readDFGNodes.values());
			allDFGNodes.addAll(bytecode.writtenDFGNodes.values());
		}

		@Override
		protected void clearDFGNodes() {
			startRegisterInfo = null;
			endRegisterInfo = null;
			
			DFGGenerator.clearDFG(bytecode);
		}

		@Override
		public void getIndexDFGNodes(Set<DFGNode> nodes) {
			nodes.add(bytecode.getIndexDFGNode());
		}

		@Override
		public void getSourceDFGNodes(Set<DFGNode> nodes) {
			nodes.add(bytecode.getSourceDFGNode());
		}

		@Override
		public void forBytecode(final BytecodeVisitor bytecodeVisitor) {
			checkIfNeedingLabel(bytecodeVisitor, this);
			
			bytecodeVisitor.visit(bytecode);
			try {
				CFGIterator.iterateSortedCFGScope(new CFGNodeCallback() {
					@Override
					public boolean process(CFGNode node) {
						if(bytecodeVisitor.shouldVisitNode(node)) {
							node.forBytecode(bytecodeVisitor);
						}
						return true;
					}
				}, body, null);
			} catch (NotADAGException e) {
				throw new InternalError("Loop body should be dag");
			}
		}

		public Type getIndexType() {
			return bytecode.getIndexType();
		}
		
		@Override
		public String toString() {
			return super.toString() + " " + getCausialWYILLangBytecode();
		}
	}

	public static class ForLoopNode extends CFGNode.LoopNode implements GPUSupportedNode {
		public static class ForLoopIndex {
			public final int indexRegister;
			public final int startRegister;
			public final int endRegister;
			
			private DFGNode startDFG;
			private DFGNode endDFG;
			private DFGNode indexDFG;
			
			private ForLoopIndex(int indexRegister, int startRegister, int endRegister) {
				this.indexRegister = indexRegister;
				this.startRegister = startRegister;
				this.endRegister = endRegister;
			}
		}
		
		private final Type.Leaf type;
		private final List<ForLoopIndex> indexes = new ArrayList<ForLoopIndex>();

		public ForLoopNode(int indexRegister, Type.Leaf type, int startRegister, int endRegister, String loopEndLabel, int startIndex, int endIndex, Code causialWYILLangBytecode) {
			super(loopEndLabel, startIndex, endIndex, causialWYILLangBytecode);
			
			this.type = type;
			
			indexes.add(new ForLoopIndex(indexRegister, startRegister, endRegister));
		}

		@Override
		public boolean isGPUSupported() {
			return true;
		}

		@Override
		protected void populateEndRegisterInfoFromStartRegisterInfo() {
			endRegisterInfo = new DFGGenerator.DFGReadWriteTracking(startRegisterInfo);
			
			for(ForLoopIndex index : indexes) {
				// Start register
	
				DFGGenerator.DFGInfo writeStartInfo = startRegisterInfo.writeInfo.registerMapping.get(index.startRegister);
				DFGGenerator.DFGInfo readStartInfo = startRegisterInfo.readWriteInfo.registerMapping.get(index.startRegister);
				if(index.startDFG == null) {
					index.startDFG = new DFGNode(this, index.startRegister, type, false);
				}
				index.startDFG.lastModified.addAll(writeStartInfo.lastNodes);
				if(readStartInfo != null) {
					index.startDFG.lastReadOrWrite.addAll(readStartInfo.lastNodes);
				}
	
				endRegisterInfo.readWriteInfo.registerMapping.put(index.startRegister, new DFGGenerator.DFGInfo(index.startDFG));
	
				// End register
	
				DFGGenerator.DFGInfo writeEndInfo = startRegisterInfo.writeInfo.registerMapping.get(index.endRegister);
				DFGGenerator.DFGInfo readEndInfo = startRegisterInfo.readWriteInfo.registerMapping.get(index.endRegister);
				if(index.endDFG == null) {
					index.endDFG = new DFGNode(this, index.endRegister, type, false);
				}
				index.endDFG.lastModified.addAll(writeEndInfo.lastNodes);
				if(readEndInfo != null) {
					index.endDFG.lastReadOrWrite.addAll(readEndInfo.lastNodes);
				}
				
				endRegisterInfo.readWriteInfo.registerMapping.put(index.endRegister, new DFGGenerator.DFGInfo(index.endDFG));
	
				// Index register
	
				DFGGenerator.DFGInfo writeIndexInfo = startRegisterInfo.writeInfo.registerMapping.get(index.indexRegister);
				DFGGenerator.DFGInfo readIndexInfo = startRegisterInfo.readWriteInfo.registerMapping.get(index.indexRegister);
				if(index.indexDFG == null) {
					index.indexDFG = new DFGNode(this, index.indexRegister, type, true);
				}
				if(writeIndexInfo != null) {
					index.indexDFG.lastModified.addAll(writeIndexInfo.lastNodes);
				}
				if(readIndexInfo != null) {
					index.indexDFG.lastReadOrWrite.addAll(readIndexInfo.lastNodes);
				}
	
				endRegisterInfo.readWriteInfo.registerMapping.put(index.indexRegister, new DFGGenerator.DFGInfo(index.indexDFG));
				endRegisterInfo.writeInfo.registerMapping.put(index.indexRegister, new DFGGenerator.DFGInfo(index.indexDFG));
			}
		}

		@Override
		public void gatherDFGNodesInto(Set<DFGNode> allDFGNodes) {
			for(ForLoopIndex index : indexes) {
				allDFGNodes.add(index.startDFG);
				allDFGNodes.add(index.endDFG);
				allDFGNodes.add(index.indexDFG);
			}
		}

		@Override
		protected void clearDFGNodes() {
			startRegisterInfo = null;
			endRegisterInfo = null;
			
			for(ForLoopIndex index : indexes) {
				DFGGenerator.clearDFGNode(index.startDFG);
				DFGGenerator.clearDFGNode(index.endDFG);
				DFGGenerator.clearDFGNode(index.indexDFG);
				
				index.startDFG = null;
				index.endDFG = null;
				index.indexDFG = null;
			}
		}

		@Override
		public void getIndexDFGNodes(Set<DFGNode> nodes) {
			for(ForLoopIndex index : indexes) {
				nodes.add(index.indexDFG);
			}
		}

		@Override
		public void getSourceDFGNodes(Set<DFGNode> nodes) {
			for(ForLoopIndex index : indexes) {
				nodes.add(index.startDFG);
				nodes.add(index.endDFG);
			}
		}

		public Type.Leaf getIndexType() {
			return type;
		}

		@Override
		public void forBytecode(final BytecodeVisitor bytecodeVisitor) {
			throw new InternalError(this.getClass().getSimpleName() + " does not support forBytecode(BytecodeVisitor)");
		}

		public List<ForLoopIndex> getIndexes() {
			return indexes;
		}

		public List<Integer> getIndexRegisters() {
			List<Integer> indexRegisters = new ArrayList<Integer>();
			for(ForLoopIndex index : indexes) {
				indexRegisters.add(index.indexRegister);
			}
			return indexRegisters;
		}

		public List<Integer> getStartRegisters() {
			List<Integer> startRegisters = new ArrayList<Integer>();
			for(ForLoopIndex index : indexes) {
				startRegisters.add(index.startRegister);
			}
			return startRegisters;
		}
		
		public List<Integer> getEndRegisters() {
			List<Integer> endRegisters = new ArrayList<Integer>();
			for(ForLoopIndex index : indexes) {
				endRegisters.add(index.endRegister);
			}
			return endRegisters;
		}
		
		@Override
		public String toString() {
			String str = super.toString() + " for";
			
			for(ForLoopIndex i : indexes) {
				str = str + " " + i.indexRegister + ":" + i.startRegister + "-" + i.endRegister;
			}
			
			return str;
		}
	}

	public static class WhileLoopNode extends CFGNode.LoopNode  implements GPUSupportedNode {
		private final Bytecode.While bytecode;

		public WhileLoopNode(Bytecode.While bytecode, int startIndex, int endIndex) {
			super(bytecode.loopEndLabel(), startIndex, endIndex, bytecode.getWYILLangBytecode());
			this.bytecode = bytecode;
		}

		@Override
		public boolean isGPUSupported() {
			return true;
		}

		public Bytecode.While getBytecode() {
			return bytecode;
		}

		@Override
		protected void populateEndRegisterInfoFromStartRegisterInfo() {
			endRegisterInfo = DFGGenerator.propogateDFGThroughBytecode(bytecode, startRegisterInfo);
		}

		@Override
		public void gatherDFGNodesInto(Set<DFGNode> allDFGNodes) {
			allDFGNodes.addAll(bytecode.readDFGNodes.values());
			allDFGNodes.addAll(bytecode.writtenDFGNodes.values());
		}

		@Override
		protected void clearDFGNodes() {
			startRegisterInfo = null;
			endRegisterInfo = null;
			
			DFGGenerator.clearDFG(bytecode);
		}

		@Override
		public void getIndexDFGNodes(Set<DFGNode> nodes) {
		}

		@Override
		public void getSourceDFGNodes(Set<DFGNode> nodes) {
		}

		@Override
		public void forBytecode(final BytecodeVisitor bytecodeVisitor) {
			checkIfNeedingLabel(bytecodeVisitor, this);
			
			bytecodeVisitor.visit(bytecode);
			try {
				CFGIterator.iterateSortedCFGScope(new CFGNodeCallback() {
					@Override
					public boolean process(CFGNode node) {
						if(bytecodeVisitor.shouldVisitNode(node)) {
							node.forBytecode(bytecodeVisitor);
						}
						return true;
					}
				}, body, null);
			} catch (NotADAGException e) {
				throw new InternalError("Loop body should be dag");
			}
		}
		
		@Override
		public String toString() {
			return super.toString() + " " + getCausialWYILLangBytecode();
		}
	}

	public static class LoopBreakNode extends CFGNode implements GPUSupportedNode, PassThroughNode {
		public final CFGNode.LoopNode loop;

		public CFGNode next;

		public LoopBreakNode(CFGNode.LoopNode loop) {this.loop = loop;}

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
		protected void propogateRegisterInfo(DFGReadWriteTracking info) {
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

		@Override
		protected void clearDFGNodes() {
			startRegisterInfo = null;
			endRegisterInfo = null;
		}

		@Override
		public void retargetNext(CFGNode oldNode, CFGNode newNode) {
			if(next == oldNode) {
				next = newNode;
			}
		}

		@Override
		public void forBytecode(BytecodeVisitor bytecodeVisitor) {
			checkIfNeedingLabel(bytecodeVisitor, this);
			
			gotoLabel(calculateLabel(next), bytecodeVisitor);
		}

		@Override
		public boolean isEmpty() {
			return false;
		}

		@Override
		public void disconnect() {
			// Do nothing as loop parent will deal with it
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
		protected void propogateRegisterInfo(DFGReadWriteTracking info) {
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
		protected void clearDFGNodes() {
			startRegisterInfo = null;
			endRegisterInfo = null;
		}

		@Override
		public void retargetNext(CFGNode oldNode, CFGNode newNode) {
			if(next == oldNode) {
				next = newNode;
			}
		}

		@Override
		public void forBytecode(BytecodeVisitor bytecodeVisitor) {
			checkIfNeedingLabel(bytecodeVisitor, this);
			
			bytecodeVisitor.visit(new Bytecode.LoopEnd(Code.LoopEnd(loop.loopEndLabel)));
			
			gotoLabel(calculateLabel(next), bytecodeVisitor);
		}

		@Override
		public boolean isEmpty() {
			return false;
		}

		@Override
		public void disconnect() {
			// Do nothing as loop parent will deal with it
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
		public CFGNode defaultBranch; // Will never be null

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
		protected void propogateRegisterInfo(DFGReadWriteTracking info) {
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

		@Override
		protected void clearDFGNodes() {
			startRegisterInfo = null;
			endRegisterInfo = null;
			
			DFGGenerator.clearDFG(switchBytecode);
		}

		@Override
		public void retargetNext(CFGNode oldNode, CFGNode newNode) {
			for(int n=0;n<branches.size();n++) {
				Pair<Constant, CFGNode> pair = branches.get(n);
				if(pair.second() == oldNode) {
					branches.set(n, new Pair<Constant, CFGNode>(pair.first(), newNode));
				}
			}
			if(defaultBranch == oldNode) {
				defaultBranch = newNode;
			}
		}

		@Override
		public void forBytecode(BytecodeVisitor bytecodeVisitor) {
			checkIfNeedingLabel(bytecodeVisitor, this);

			String defaultLabel = calculateLabel(defaultBranch);
			
			List<Pair<Constant, String>> cases = new ArrayList<Pair<Constant, String>>();
			for(Pair<Constant, CFGNode> branch : branches) {
				String label = calculateLabel(branch.second());
				
				cases.add(new Pair<Constant, String>(branch.first(), label));
			}
			bytecodeVisitor.visit(new Bytecode.Switch(Code.Switch(switchBytecode.getType(), getCheckedRegister(), defaultLabel, cases)));
		}

		public Type getCheckedType() {
			return switchBytecode.getType();
		}

		@Override
		public boolean isEmpty() {
			if(!branches.isEmpty()) {
				for(Pair<Constant, CFGNode> p : branches) {
					if(!p.second().isEmpty()) {
						return false;
					}
				}
			}
			
			return true;
		}

		@Override
		public void disconnect() {
			for(CFGNode n : previous) {
				n.retargetNext(this, defaultBranch);
			}
			defaultBranch.previous.remove(this);
			defaultBranch.previous.addAll(previous);
			previous.clear();
			defaultBranch = null;
			for(Pair<Constant, CFGNode> p : branches) {
				p.second().previous.remove(this);
			}
			branches.clear();
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
		protected void propogateRegisterInfo(DFGReadWriteTracking info) {
			startRegisterInfo = DFGGenerator.mergeRegisterInfo(startRegisterInfo, info);
			endRegisterInfo = DFGGenerator.propogateDFGThroughBytecode((Bytecode)conditionalJump, startRegisterInfo);
		}

		@Override
		public void gatherDFGNodesInto(Set<DFGNode> allDFGNodes) {
			allDFGNodes.addAll(((Bytecode)conditionalJump).readDFGNodes.values());
			allDFGNodes.addAll(((Bytecode)conditionalJump).writtenDFGNodes.values());
		}

		@Override
		protected void clearDFGNodes() {
			startRegisterInfo = null;
			endRegisterInfo = null;
			
			DFGGenerator.clearDFG((Bytecode)conditionalJump);
		}

		@Override
		public boolean isGPUSupported() {
			return conditionalJump instanceof Bytecode.GPUSupportedBytecode && ((Bytecode.GPUSupportedBytecode)conditionalJump).isGPUCompatable();
		}

		public ConditionalJump getBytecode() {
			return conditionalJump;
		}

		@Override
		public void retargetNext(CFGNode oldNode, CFGNode newNode) {
			if(conditionMet == oldNode) {
				conditionMet = newNode;
			}
			if(conditionUnmet == oldNode) {
				conditionUnmet = newNode;
			}
		}

		@Override
		public void forBytecode(BytecodeVisitor bytecodeVisitor) {
			checkIfNeedingLabel(bytecodeVisitor, this);
			
			String metLabel = calculateLabel(conditionMet);
			String unmetLabel = calculateLabel(conditionUnmet);
			
			if(conditionalJump instanceof Bytecode.ComparisonBasedJump) {
				Bytecode.ComparisonBasedJump ifBytecode = (Bytecode.ComparisonBasedJump)conditionalJump;
				bytecodeVisitor.visit(new Bytecode.ComparisonBasedJump(Code.If(ifBytecode.getType(), ifBytecode.getLeftOperand(), ifBytecode.getRightOperand(), ifBytecode.getComparison(), metLabel)));
			}
			else if(conditionalJump instanceof Bytecode.TypeBasedJump) {
				Bytecode.TypeBasedJump isBytecode = (Bytecode.TypeBasedJump)conditionalJump;
				bytecodeVisitor.visit(new Bytecode.TypeBasedJump(Code.IfIs(isBytecode.getType(), isBytecode.getTestedOperand(), isBytecode.getTypeOperand(), metLabel)));
			}
			else {
				throw new InternalError("Unexpected conditional jump type ecountered: " + conditionalJump);
			}
			
			bytecodeVisitor.visit(new Bytecode.UnconditionalJump(Code.Goto(unmetLabel)));
		}

		@Override
		public boolean isEmpty() {
			return conditionMet == conditionUnmet;
		}

		@Override
		public void disconnect() {
			for(CFGNode n : previous) {
				n.retargetNext(this, conditionUnmet);
			}
			conditionUnmet.previous.remove(this);
			conditionUnmet.previous.addAll(previous);
			previous.clear();
			conditionMet.previous.remove(this);
			conditionMet = null;
			conditionUnmet = null;
		}
		
		@Override
		public String toString() {
			return super.toString() + " " + conditionalJump.toString(); 
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
		protected void propogateRegisterInfo(DFGReadWriteTracking info) {
			startRegisterInfo = DFGGenerator.mergeRegisterInfo(startRegisterInfo, info);
			if(bytecode == null) {
				endRegisterInfo = startRegisterInfo;
			}
			else {
				endRegisterInfo = DFGGenerator.propogateDFGThroughBytecode(bytecode, startRegisterInfo);
			}
		}

		@Override
		public void gatherDFGNodesInto(Set<DFGNode> allDFGNodes) {
			if(bytecode != null) {
				allDFGNodes.addAll(bytecode.readDFGNodes.values());
				allDFGNodes.addAll(bytecode.writtenDFGNodes.values());
			}
		}

		@Override
		protected void clearDFGNodes() {
			startRegisterInfo = null;
			endRegisterInfo = null;
			
			if(bytecode != null) {
				DFGGenerator.clearDFG(bytecode);
			}
		}

		@Override
		public boolean isGPUSupported() {
			return true;
		}

		public Return getBytecode() {
			return bytecode;
		}

		@Override
		public void retargetNext(CFGNode oldNode, CFGNode newNode) {
		}

		@Override
		public void forBytecode(BytecodeVisitor bytecodeVisitor) {
			checkIfNeedingLabel(bytecodeVisitor, this);
			
			if(bytecode == null || bytecode.isVoid()) {
				bytecodeVisitor.visit(new Bytecode.Return(Code.Return()));
			}
			else {
				bytecodeVisitor.visit(new Bytecode.Return(Code.Return(bytecode.getType(), bytecode.getOperand())));
			}
		}

		@Override
		public boolean isEmpty() {
			return false;
		}

		@Override
		public void disconnect() {
			for(CFGNode n : previous) {
				n.retargetNext(this, null);
			}
			previous.clear();
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
		protected void propogateRegisterInfo(DFGReadWriteTracking info) {
			endRegisterInfo = startRegisterInfo = DFGGenerator.mergeRegisterInfo(startRegisterInfo, info);
		}

		@Override
		public void gatherDFGNodesInto(Set<DFGNode> allDFGNodes) {
		}

		@Override
		protected void clearDFGNodes() {
			startRegisterInfo = null;
			endRegisterInfo = null;
		}

		@Override
		public void retargetNext(CFGNode oldNode, CFGNode newNode) {
		}

		@Override
		public void forBytecode(BytecodeVisitor bytecodeVisitor) {
			throw new InternalError(this.getClass().getSimpleName() + "  cannot be visited by BytecodeVisitor");
		}

		@Override
		public boolean isEmpty() {
			return false;
		}

		@Override
		public void disconnect() {
			for(CFGNode n : previous) {
				n.retargetNext(this, null);
			}
			previous.clear();
		}
	}
	
	/**
	 * A simple dummy CFGNode which can only be used as a placeholder, any other operations
	 * will result in an exception. Intended for temporary use.
	 * 
	 * @author melby
	 *
	 */
	public static class DummyNode extends CFGNode {
		public CFGNode next;
		
		public DummyNode(CFGNode next) {
			this.next = next;
		}
		
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
		protected void propogateRegisterInfo(DFGReadWriteTracking info) {
			endRegisterInfo = startRegisterInfo = DFGGenerator.mergeRegisterInfo(startRegisterInfo, info);
		}

		@Override
		public void gatherDFGNodesInto(Set<DFGNode> allDFGNodes) {
		}

		@Override
		protected void clearDFGNodes() {
			startRegisterInfo = null;
			endRegisterInfo = null;
		}

		@Override
		public void retargetNext(CFGNode oldNode, CFGNode newNode) {
			if(next == oldNode) {
				next = newNode;
			}
		}

		@Override
		public void forBytecode(BytecodeVisitor bytecodeVisitor) {
		}

		@Override
		public boolean isEmpty() {
			return false;
		}

		@Override
		public void disconnect() {
			for(CFGNode n : previous) {
				n.retargetNext(this, null);
			}
			previous.clear();
		}
	}
}