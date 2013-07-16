package wyocl.filter;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import wybs.lang.NameID;
import wybs.lang.Path.ID;
import wybs.util.Trie;
import wyil.lang.Block;
import wyil.lang.Code;
import wyil.lang.Constant;
import wyil.lang.Type;
import wyil.lang.WyilFile;
import wyocl.ar.Bytecode;
import wyocl.ar.CFGGenerator;
import wyocl.ar.CFGNode;
import wyocl.ar.CFGNode.LoopNode;
import wyocl.ar.CFGNode.UnresolvedTargetNode;
import wyocl.ar.CFGNode.VanillaCFGNode;
import wyocl.ar.DFGGenerator;
import wyocl.ar.DFGNode;
import wyocl.ar.utils.CFGIterator;
import wyocl.ar.utils.CFGIterator.CFGNodeCallback;
import wyocl.ar.utils.CFGIterator.Entry;
import wyocl.ar.utils.NotADAGException;

public class LoopFilter {
	private static final boolean DEBUG = false;

	/**
	 * The endLabel is used to determine when we're within a for loop being
	 * translated in OpenCL. If this is <code>null</code> then we're *not*
	 * within a for loop. Otherwise, we are.
	 */
	private String endLabel;
	/**
	 * A unique path which can be used to identify the module currently
	 * being filtered.
	 */
	private final String modulePath;

	private ArrayList<Type> executeWYGPUKernelOverArrayArgumentTypes = new ArrayList<Type>() {
		private static final long serialVersionUID = 1L;

		{
			this.add(Type.T_STRING);
			this.add(Type.List(Type.T_ANY, false));
			this.add(Type.List(Type.T_ANY, false));
		}
	};
	private Type.FunctionOrMethod executeWYGPUKernelOverArrayFunctionType = Type.FunctionOrMethod.Function(Type.List(Type.T_ANY, false), Type.T_VOID, executeWYGPUKernelOverArrayArgumentTypes);
	private NameID executeWYGPUKernelOverArrayFunctionPath = new NameID(Trie.fromString("whiley/gpgpu/Util"), "executeWYGPUKernelOverArray");

	private ArrayList<Type> executeWYGPUKernelOverRangeArgumentTypes = new ArrayList<Type>() {
		private static final long serialVersionUID = 1L;

		{
			this.add(Type.T_STRING);
			this.add(Type.List(Type.T_ANY, false));
			this.add(Type.T_INT);
			this.add(Type.T_INT);
		}
	};
	private Type.FunctionOrMethod executeWYGPUKernelOverRangeFunctionType = Type.FunctionOrMethod.Function(Type.List(Type.T_ANY, false), Type.T_VOID, executeWYGPUKernelOverRangeArgumentTypes);
	private NameID executeWYGPUKernelOverRangeFunctionPath = new NameID(Trie.fromString("whiley/gpgpu/Util"), "executeWYGPUKernelOverRange");

	private Block currentBlock;
	private boolean skipCurrentBlock;
	private CFGNode currentBlocksCFG;
	private Map<Code, LoopDescription> currentBlocksLoops;
	private List<Entry> currentBlocksSortedCFG;

	private LoopDescription currentLoop;

	private List<Block.Entry> replacementEntries;
	private List<Argument> kernelArguments;
	private Map<Integer, DFGNode> methodArgumentsDFGNodes;

	private static class LoopDescription {
		private LoopType type;
		private final LoopNode loopNode;
		private final List<CFGIterator.Entry> nestedEntries;
		private boolean bytecodesGPUCombatable = false;
		private boolean breaksGPUCombatable = false;
		private boolean dataDependanciesGPUCombatable = false;
		private boolean earlyReturnCompatable = false;
		private boolean typesCompatable = false;

		public LoopDescription(LoopNode loopNode, List<Entry> nestedEntries) {
			this.loopNode = loopNode;
			this.nestedEntries = nestedEntries;
		}

		public LoopType getType() {
			return type;
		}

		public LoopNode getCFGNode() {
			return loopNode;
		}

		public void setBytecodesGPUCombatable(boolean b) {
			bytecodesGPUCombatable = b;
		}

		public void setBreaksGPUCombatable(boolean b) {
			breaksGPUCombatable = b;
		}

		public void setDataDependanciesCombatable(boolean b) {
			dataDependanciesGPUCombatable = b;
		}

		public boolean getLoopCombatability() {
			return bytecodesGPUCombatable && breaksGPUCombatable && dataDependanciesGPUCombatable && earlyReturnCompatable && typesCompatable;
		}

		public List<CFGIterator.Entry> getNestedEntries() {
			return nestedEntries;
		}

		public void setType(LoopType type) {
			this.type = type;
		}

		public void setEarlyReturnCombatable(boolean b) {
			earlyReturnCompatable = b;
		}

		public void setTypesCombatable(boolean b) {
			typesCompatable  = b;
		}
	}

	public LoopFilter(ID id) {
		modulePath = id.toString();
	}

	public FilterAction filter(Block.Entry entry) {
		if(currentBlock == null) {
			throw new InternalError("beginBlock() must be called before filter()");
		}

		if(skipCurrentBlock) {
			return FilterAction.DEFAULT;
		}

		// TODO: we're going to need the last free register

		Code code = entry.code;

		if(endLabel == null) {
			if(code instanceof Code.ForAll) {
				Code.ForAll forall = (Code.ForAll)code;

				LoopDescription description = currentBlocksLoops.get(forall);
				if(description == null) {
					throw new InternalError("Unable to find loop description for: " + forall);
				}

				if(description.getType() == LoopType.GPU_IMPLICIT) {
					endLabel = forall.target;
					currentLoop = description;

					return FilterAction.SKIP;
				}
				else {
					return FilterAction.DEFAULT;
				}
			}
			else {
				return FilterAction.DEFAULT;
			}
		}
		else {
			if(code instanceof Code.Label) {
				Code.Label label = (Code.Label)code;
				if(label.label.equals(endLabel)) {
					endLabel = null;

					produceReplacementEntries();

					return FilterAction.FILTER_RESULTS_READY;
				}
			}

			return FilterAction.SKIP;
		}
	}

	private void produceReplacementEntries() {
		replacementEntries = new ArrayList<Block.Entry>();

		CFGNode.LoopNode loopNode = currentLoop.getCFGNode();
		// FIXME: Check source isn't modified?

		kernelArguments = new ArrayList<Argument>();
		determineKernelArguments(kernelArguments);
		Collections.sort(kernelArguments);

		if(loopNode instanceof CFGNode.ForAllLoopNode) {
			Bytecode.ForAll loopBytecode = ((CFGNode.ForAllLoopNode)loopNode).getBytecode();

			// Remove possible duplication of the kernel argument
			Iterator<Argument> it = kernelArguments.iterator();
			while(it.hasNext()) {
				Argument arg = it.next();
				if(arg.register == loopBytecode.getSourceRegister()) {
					if(!arg.readonly) {
						// FIXME: re-add this check when read-only primitive types are supported by runtime (see ref:2123sdsds)
						//throw new RuntimeException("GPU cannot loop over an array that is being simultaneously updated");
					}
					it.remove();
				}
			}
		}

		ArrayList<Integer> argumentRegisters = new ArrayList<Integer>();
		for(Argument arg : kernelArguments) {
			argumentRegisters.add(arg.register);
		}

		// TODO: actually output marshaling and unmarshaling code here? Avoids cost of function call, wrapping/unwrapping multiple times and type tests

		final int temporaryListRegister = 230200; // FIXME: don't hard code target
		final int temporaryCounterRegister = 230201; // FIXME: don't hard code
		final int temporaryModuleNameRegister = 230202; // FIXME: don't hard code

		replacementEntries.add(new Block.Entry(Code.NewList(Type.List(Type.T_ANY, false), temporaryListRegister, argumentRegisters)));
		replacementEntries.add(new Block.Entry(Code.Const(temporaryModuleNameRegister, Constant.V_STRING(modulePath))));
		ArrayList<Integer> argumentsToFunction = new ArrayList<Integer>();
		argumentsToFunction.add(temporaryModuleNameRegister);
		argumentsToFunction.add(temporaryListRegister);

		if(loopNode instanceof CFGNode.ForAllLoopNode) {
			CFGNode.ForAllLoopNode forNode = (CFGNode.ForAllLoopNode)loopNode;

			argumentsToFunction.add(forNode.getBytecode().getSourceRegister());

			replacementEntries.add(new Block.Entry(Code.Invoke(executeWYGPUKernelOverArrayFunctionType, temporaryListRegister, argumentsToFunction, executeWYGPUKernelOverArrayFunctionPath)));
		}
		else if(loopNode instanceof CFGNode.ForLoopNode) {
			CFGNode.ForLoopNode forNode = (CFGNode.ForLoopNode)loopNode;

			argumentsToFunction.add(forNode.getStartRegister());
			argumentsToFunction.add(forNode.getEndRegister());
			
			replacementEntries.add(new Block.Entry(Code.Invoke(executeWYGPUKernelOverRangeFunctionType, temporaryListRegister, argumentsToFunction, executeWYGPUKernelOverRangeFunctionPath)));
		}
		else {
			throw new InternalError("Unknown loop type encountered: "+loopNode);
		}

		int count = 0;
		for(Argument arg : kernelArguments) {
			replacementEntries.add(new Block.Entry(Code.Const(temporaryCounterRegister, Constant.V_INTEGER(BigInteger.valueOf(count)))));
			replacementEntries.add(new Block.Entry(Code.IndexOf(Type.List(arg.type, true), arg.register, temporaryListRegister, temporaryCounterRegister)));
			count++;
		}

		if(loopNode instanceof CFGNode.ForAllLoopNode) {
			CFGNode.ForAllLoopNode forNode = (CFGNode.ForAllLoopNode)loopNode;

			kernelArguments.add(0, new Argument(forNode.getBytecode().getSourceType(), forNode.getBytecode().getSourceRegister()));
		}
	}

	private void determineKernelArguments(List<Argument> arguments) {
		CFGNode.LoopNode loop = currentLoop.getCFGNode();

		final Set<DFGNode> dfgNodesInLoop = new HashSet<DFGNode>();

		Set<CFGNode> endNodes = new HashSet<CFGNode>();
		endNodes.add(loop.endNode);
		endNodes.addAll(currentLoop.getCFGNode().breakNodes);
		CFGIterator.iterateCFGForwards(new CFGIterator.CFGNodeCallback() {
			@Override
			public boolean process(CFGNode node) {
				node.gatherDFGNodesInto(dfgNodesInLoop);
				return true;
			}
		}, loop.body, endNodes);

		Set<DFGNode> dfgNodesProvidedToKernel = new HashSet<DFGNode>();
		dfgNodesProvidedToKernel.addAll(dfgNodesInLoop);
		loop.getIndexDFGNodes(dfgNodesProvidedToKernel);
		Set<DFGNode> source = new HashSet<DFGNode>();
		loop.getSourceDFGNodes(source);
		for(DFGNode n : source) {
			dfgNodesProvidedToKernel.addAll(n.lastModified);
		}

		Map<Integer, Type> endTypes = new HashMap<Integer, Type>();

		for(CFGNode endNode : endNodes) {
			for(Map.Entry<Integer, DFGGenerator.DFGInfo> entry : endNode.getEndRegisterInfo().writeInfo.registerMapping.entrySet()) {
				endTypes.put(entry.getKey(), DFGGenerator.mergeTypes(endTypes.get(entry.getKey()), entry.getValue().type));
			}
		}

		Map<Integer, Type> inputs = new HashMap<Integer, Type>();
		Map<Integer, Type> outputs = new HashMap<Integer, Type>();

		for(DFGNode n : dfgNodesInLoop) {
			if(!n.isAssignment) {
				for(DFGNode lastModified : n.lastModified) {
					if(!dfgNodesProvidedToKernel.contains(lastModified)) {
						inputs.put(lastModified.register, DFGGenerator.mergeTypes(lastModified.type, inputs.get(lastModified.register)));
					}
				}
			}
			for(DFGNode nextRead : n.nextRead) {
				if(!dfgNodesInLoop.contains(nextRead)) {
					outputs.put(nextRead.register, DFGGenerator.mergeTypes(nextRead.type, outputs.get(nextRead.register)));
				}
			}
		}

		// Compile argument set
		Map<Integer, Argument> dependancies = new HashMap<Integer, Argument>();

		for(Map.Entry<Integer, Type> entry : inputs.entrySet()) {
			int register = entry.getKey();
			Type type = entry.getValue();
			
			dependancies.put(register, new Argument(type, register));
		}
		for(Map.Entry<Integer, Type> entry : outputs.entrySet()) {
			int register = entry.getKey();
			Type type = entry.getValue();

			Argument arg = dependancies.get(register);

			if(arg == null) {
				arg = new Argument(type, register);
				dependancies.put(register, arg);
			}

			arg.setReadonly(false);
		}

		// Return results
		arguments.addAll(dependancies.values());

		// TODO: don't have to do this. Need to add support to runtime though ref:2123sdsds
		for (Argument arg : arguments) {
			arg.setReadonly(false);
		}
	}

	public void beginBlock(Block blk) {
		if(methodArgumentsDFGNodes == null) {
			throw new InternalError("beginMethod() must be called before beginBlock()");
		}

		currentBlock = blk;
		currentBlocksLoops = new HashMap<Code, LoopDescription>();

		List<Block.Entry> entries = new ArrayList<Block.Entry>(); // TODO: method of actually getting this?
		for(Block.Entry be : blk) {
			entries.add(be);
		}

		Set<CFGNode.ReturnNode> exitPoints = new HashSet<CFGNode.ReturnNode>();
		Set<UnresolvedTargetNode> unresolvedTargets = new HashSet<CFGNode.UnresolvedTargetNode>();
		currentBlocksCFG = CFGGenerator.processEntries(entries, exitPoints, unresolvedTargets, methodArgumentsDFGNodes);

		try {
			currentBlocksSortedCFG = CFGIterator.createNestedRepresentation(currentBlocksCFG);
			skipCurrentBlock = !preprocessLoops();
		} catch (NotADAGException e) {
			System.err.println("Somehow the current block has a CFG which is not a DAG -> ???? leaving this block entirely on the CPU for the time being");
			skipCurrentBlock = true;
		}
	}

	private boolean preprocessLoops() {
		if(DEBUG) { System.err.println("Preprocess loops"); }

		CFGIterator.traverseNestedRepresentation(currentBlocksSortedCFG, new CFGIterator.CFGNestedRepresentationVisitor() {
			@Override
			public void visitNonNestedNode(CFGNode node) {
			}

			@Override
			public void exitedNestedNode(CFGNode node) {
			}

			@Override
			public void enteredNestedNode(CFGNode node, List<Entry> nestedEntries) {
				if(node instanceof CFGNode.LoopNode) {
					CFGNode.LoopNode loopNode = (CFGNode.LoopNode)node;
					currentBlocksLoops.put(loopNode.getCausialWYILLangBytecode(), new LoopDescription(loopNode, nestedEntries));
				}
			}
		});

		if(!currentBlocksLoops.isEmpty()) {
			determineLoopsBytecodeCompatability();
			determineLoopsBreakCompatability();
			determineLoopsEarlyReturnCompatability();
			determineLoopsDataDependancyCompatability();
			determineLoopsRegisterTypeCompatability();
			determineLoopsFunctionCallCompatability();
			determineLoopTypes();

			return true;
		}
		else {
			return false;
		}
	}

	private void determineLoopsFunctionCallCompatability() {
		// FIXME: implement
	}

	private void determineLoopsRegisterTypeCompatability() {
		for(final LoopDescription loop : currentBlocksLoops.values()) {
			loop.setTypesCombatable(true);

			final Set<DFGNode> nodes = new HashSet<DFGNode>();
			Set<CFGNode> endNodes = new HashSet<CFGNode>();
			loop.getCFGNode().getScopeNextNodes(endNodes);
			CFGIterator.iterateCFGForwards(new CFGIterator.CFGNodeCallback() {
				@Override
				public boolean process(CFGNode node) {
					node.gatherDFGNodesInto(nodes);
					return true;
				}
			}, loop.getCFGNode(), endNodes);

			for(DFGNode n : nodes) {
				if(!SupportedTypes.includes(n.type)) {
					if(DEBUG) { System.err.println("Loop " + loop + " not compatable because non-supported type contained: "+n.type); }
					loop.setTypesCombatable(false);
					break;
				}
			}
		}
	}

	private void determineLoopsEarlyReturnCompatability() {
		for(final LoopDescription loop : currentBlocksLoops.values()) {
			loop.setEarlyReturnCombatable(true);

			boolean canHaveReturns = loop.getCFGNode().endNode.next instanceof CFGNode.ReturnNode;

			if(!canHaveReturns) {
				Set<CFGNode> endNodes = new HashSet<CFGNode>();
				loop.getCFGNode().getScopeNextNodes(endNodes);

				CFGIterator.iterateCFGForwards(new CFGNodeCallback() {
					@Override
					public boolean process(CFGNode node) {
						if(node instanceof CFGNode.ReturnNode) {
							loop.setEarlyReturnCombatable(false);
							return false;
						}

						return true;
					}
				}, loop.getCFGNode(), endNodes);
			}
		}
	}

	private void determineLoopsBytecodeCompatability() {
		if(DEBUG) { System.err.println("Determine bytecode compatability"); }

		for(final LoopDescription loop : currentBlocksLoops.values()) {
			loop.setBytecodesGPUCombatable(true);

			Set<CFGNode> endNodes = new HashSet<CFGNode>();
			loop.getCFGNode().getScopeNextNodes(endNodes);

			CFGIterator.iterateCFGForwards(new CFGNodeCallback() {
				@Override
				public boolean process(CFGNode node) {
					if(node instanceof VanillaCFGNode) {
						VanillaCFGNode vanilaNode = (VanillaCFGNode)node;
						for(Bytecode b : vanilaNode.body.instructions) {
							if(!(b instanceof Bytecode.GPUSupportedBytecode)) {
								loop.setBytecodesGPUCombatable(false);
								if(DEBUG) { System.err.println("Loop " + loop + " not compatable because non-supported bytecode contained: "+b); }
								return false;
							}
						}
					}
					else if(!(node instanceof CFGNode.GPUSupportedNode)) {
						loop.setBytecodesGPUCombatable(false);
						if(DEBUG) { System.err.println("Loop " + loop + " not compatable because non-supported node contained: "+node); }
						return false;
					}
					else {
						CFGNode.GPUSupportedNode gpuNode = (CFGNode.GPUSupportedNode)node;
						if(!gpuNode.isGPUSupported()) {
							loop.setBytecodesGPUCombatable(false);
							if(DEBUG) { System.err.println("Loop " + loop + " not compatable because non-supported node contained: "+node); }
							return false;
						}
					}

					return true;
				}
			}, loop.getCFGNode(), endNodes);
		}
	}

	private void determineLoopsBreakCompatability() {
		if(DEBUG) { System.err.println("Determine loop break compatability"); }

		for(LoopDescription loop : currentBlocksLoops.values()) {
			loop.setBreaksGPUCombatable(true);

			for(CFGNode.LoopBreakNode node : loop.getCFGNode().breakNodes) {
				if(node.next != loop.getCFGNode().endNode.next) {
					if(DEBUG) { System.err.println("Loop " + loop + " not compatable because non-supported loop break contained: "+node); }
					loop.setBreaksGPUCombatable(false);
				}
			}
		}
	}

	private void determineLoopsDataDependancyCompatability() {
		if(DEBUG) { System.err.println("Determine loop data dependancies"); }

		for(LoopDescription loop : currentBlocksLoops.values()) {
			loop.setDataDependanciesCombatable(true);

			// FIXME: check for read before write, not occurrence - always going to be there.

//			boolean earlyBreak = false;
//
//			ForLoopNode cfgNode = loop.getCFGNode();
//			for(Pair<Type, Set<DFGNode>> value : cfgNode.body.getStartTypes().values()) {
//				for(DFGNode dfgNode : value.second()) {
//					CFGNode node = null;
//					if(dfgNode.cause instanceof Bytecode) {
//						Bytecode bytecode = (Bytecode)dfgNode.cause;
//						node = bytecode.cfgNode;
//					}
//					else if(dfgNode.cause instanceof CFGNode) {
//						node = (CFGNode)dfgNode.cause;
//					}
//					else if(dfgNode.cause == null) { // This can only occur for registers defined outside this function, i.e. argument
//						break;
//					}
//					else {
//						throw new InternalError("Unknown DFGNode cause: " + dfgNode.cause);
//					}
//
//					if(CFGIterator.doesNodeDependUpon(node, cfgNode.body, cfgNode.endNode)) {
//						loop.setDataDependanciesCombatable(false);
//						if(DEBUG) { System.err.println("Loop " + loop + " not compatable because non-supported data denendancy contained on register: "+dfgNode.register); }
//						earlyBreak = true;
//					}
//
//					if(earlyBreak) { break; }
//				}
//
//				if(earlyBreak) { break; }
//			}
		}
	}

	private void determineLoopTypes() {
		if(DEBUG) { System.err.println("Deterine loop types"); }

		final Stack<LoopDescription> outerLoops = new Stack<LoopDescription>();

		CFGIterator.traverseNestedRepresentation(currentBlocksSortedCFG, new CFGIterator.CFGNestedRepresentationVisitor() {
			@Override
			public void visitNonNestedNode(CFGNode node) {
				if(node instanceof CFGNode.LoopNode) {
					LoopDescription loop = currentBlocksLoops.get(((CFGNode.LoopNode) node).getCausialWYILLangBytecode());
					if(outerLoops.isEmpty()) {
						if(loop.getLoopCombatability()) {
							loop.setType(LoopType.GPU_IMPLICIT);
						}
						else {
							loop.setType(LoopType.CPU_EXPLICIT);
						}
					}
					else {
						switch(outerLoops.peek().getType()) {
							case GPU_EXPLICIT:
								loop.setType(LoopType.GPU_EXPLICIT);
								break;
							case GPU_IMPLICIT:
								// TODO: implicit inside implicit
								loop.setType(LoopType.GPU_EXPLICIT);
								break;
							case CPU_EXPLICIT:
								if(loop.getLoopCombatability()) {
									loop.setType(LoopType.GPU_IMPLICIT);
								}
								else {
									loop.setType(LoopType.CPU_EXPLICIT);
								}
								break;
						}
					}
				}
			}

			@Override
			public void exitedNestedNode(CFGNode node) {
				if(node instanceof CFGNode.LoopNode) {
					outerLoops.pop();
				}
			}

			@Override
			public void enteredNestedNode(CFGNode node, List<Entry> nestedEntries) {
				if(node instanceof CFGNode.LoopNode) {
					outerLoops.push(currentBlocksLoops.get(((CFGNode.LoopNode) node).getCausialWYILLangBytecode()));
				}
			}
		});
	}

	public void endBlock() {
		currentBlock = null;
		currentBlocksCFG = null;
		currentBlocksLoops = null;
	}

	public void beginMethod(WyilFile.MethodDeclaration method) {
		methodArgumentsDFGNodes = new HashMap<Integer, DFGNode>();

		int register = 0;
		for(Type t : method.type().params()) {
			DFGNode node = new DFGNode(null, register, t, true);
			methodArgumentsDFGNodes.put(register, node);
			register++;
		}
	}

	public void endMethod() {
		methodArgumentsDFGNodes = null;
	}

	public List<Block.Entry> getReplacementEntries() {
		return replacementEntries;
	}

	public List<CFGIterator.Entry> getLoopBody() {
		return currentLoop.getNestedEntries();
	}

	public LoopNode getLoopNode() {
		return currentLoop.getCFGNode();
	}

	public List<Argument> getKernelArguments() {
		return kernelArguments;
	}
}
