package wyocl.filter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import wybs.lang.NameID;
import wybs.lang.Path.ID;
import wyil.lang.Block;
import wyil.lang.Block.Entry;
import wyil.lang.Code;
import wyil.lang.Type;
import wyil.lang.Type.FunctionOrMethod;
import wyil.lang.WyilFile;
import wyocl.ar.Bytecode;
import wyocl.ar.CFGGenerator;
import wyocl.ar.CFGNode;
import wyocl.ar.CFGNode.BytecodeVisitor;
import wyocl.ar.CFGNode.LoopNode;
import wyocl.ar.CFGNode.UnresolvedTargetNode;
import wyocl.ar.DFGNode;
import wyocl.ar.utils.CFGIterator;
import wyocl.ar.utils.CFGIterator.CFGNodeCallback;
import wyocl.ar.utils.NotADAGException;
import wyocl.filter.CFGCompatabilityAnalyser.LoopAnalyserResult;
import wyocl.filter.optimizer.CFGOptimizer;
import wyocl.util.SymbolUtilities;

public class LoopFilter {
	/**
	 * A unique path which can be used to identify the module currently
	 * being filtered.
	 */
	private final String modulePath;
	private int kernelID = 0;
	private final FunctionResolver functionResolver;
		
	private Map<Integer, DFGNode> methodArgumentsDFGNodes;
	private Map<String, OpenCLFunctionDescription> functionDescriptions = new HashMap<String, OpenCLFunctionDescription>();
	private Set<String> functionsBeingProcessed = new HashSet<String>();
	private Map<String, Set<String>> functionsCalledByFunctions = new HashMap<String, Set<String>>();
	private Map<String, Boolean> functionCompatabilities = new HashMap<String, Boolean>();

	public static interface FunctionResolver {
		public Block blockForFunctionWithName(NameID name, FunctionOrMethod type);
	}
	
	public LoopFilter(ID id, FunctionResolver functionResolver) {
		this.functionResolver = functionResolver;
		modulePath = id.toString();
	}

	public Block processBlock(Block blk, Map<CFGNode.LoopNode, OpenCLKernelInvocationDescription> kernels, Set<String> calledFunctions) {
		if(methodArgumentsDFGNodes == null) {
			throw new InternalError("beginMethod() must be called before processBlock()");
		}
		
		if(kernels == null) {
			kernels = new HashMap<CFGNode.LoopNode, OpenCLKernelInvocationDescription>();
		}
		final Map<CFGNode.LoopNode, OpenCLKernelInvocationDescription> finalkernels = kernels;

		List<Block.Entry> entries = new ArrayList<Block.Entry>();
		for(Block.Entry be : blk) {
			entries.add(be);
			if(be.code instanceof Code.Invoke) {
				NameID name = ((Code.Invoke)be.code).name;
				Type.FunctionOrMethod type = ((Code.Invoke)be.code).type;
				if(!functionCompatabilities.containsKey(name)) {
					processFunction(name, functionResolver.blockForFunctionWithName(name, type), type);
				}
			}
		}

		Set<CFGNode.ReturnNode> exitPoints = new HashSet<CFGNode.ReturnNode>();
		Set<UnresolvedTargetNode> unresolvedTargets = new HashSet<CFGNode.UnresolvedTargetNode>();
		CFGNode rootNode = CFGGenerator.processEntries(entries, exitPoints, unresolvedTargets, methodArgumentsDFGNodes);
		
		try {
			rootNode = CFGOptimizer.processBeforeAnalysis(rootNode, methodArgumentsDFGNodes);
			
			List<CFGIterator.Entry> sortedCFG = CFGIterator.createNestedRepresentation(rootNode);
			final LoopAnalyserResult prelimAnalyserResult = CFGCompatabilityAnalyser.analyse(rootNode, sortedCFG, functionCompatabilities);
			
			rootNode = CFGOptimizer.processAfterAnalysis(rootNode, prelimAnalyserResult, methodArgumentsDFGNodes);
			
			sortedCFG = CFGIterator.createNestedRepresentation(rootNode);
			final LoopAnalyserResult analyserResult = CFGCompatabilityAnalyser.analyse(rootNode, sortedCFG, functionCompatabilities);
			if(!analyserResult.anyLoopsCompatable) {
				return createBlock(analyserResult, finalkernels, rootNode, blk);
			}
			
			if(calledFunctions != null) {
				calledFunctions.addAll(analyserResult.loopCalledFunctions);
				Set<String> fringe = new HashSet<String>(calledFunctions);
				while(!fringe.isEmpty()) {
					String next = fringe.iterator().next();
					fringe.remove(next);
					Set<String> func = functionsCalledByFunctions.get(next);
					if(func != null) {
						fringe.addAll(func);
						calledFunctions.addAll(functionsCalledByFunctions.get(next));
					}
				}
			}
			
			CFGIterator.iterateCFGFlow(new CFGNodeCallback() {
				@Override
				public boolean process(CFGNode node) {
					if(node instanceof CFGNode.LoopNode) {
						if(analyserResult.loopCompatabilities.get(node) == LoopType.GPU_IMPLICIT) {
							finalkernels.put((LoopNode) node, LoopFilterLoopProcessor.process((LoopNode) node, modulePath, kernelID++));
						}
					}
					return true;
				}
			}, rootNode, null);
						
			return createBlock(analyserResult, finalkernels, rootNode, blk);
		} catch (NotADAGException e) {
			System.err.println("Somehow the current block has a CFG which is not a DAG -> ???? leaving this block entirely on the CPU for the time being");
			return null;
		}
	}

	private Block createBlock(final LoopAnalyserResult analyserResult, final Map<CFGNode.LoopNode, OpenCLKernelInvocationDescription> finalkernels, CFGNode rootNode, Block blk) {
		final List<Entry> blockEntries = new ArrayList<Entry>();
		
		CFGIterator.iterateCFGScope(new CFGNodeCallback() {
			@Override
			public boolean process(CFGNode node) {
				if(node instanceof CFGNode.LoopNode) {
					CFGNode.LoopNode loop = (CFGNode.LoopNode)node;
					
					if(analyserResult.loopCompatabilities.get(loop) == null) {
						throw new InternalError("Loop combatabilities have not been determined for: " + loop);
					}
					
					if(analyserResult.loopCompatabilities.get(loop) == LoopType.CPU_EXPLICIT) {
						processCompatableNode(node);
					}
					else {
						if(finalkernels.get(loop) == null) {
							throw new InternalError("Loop replacements should have been computed for: " + loop + " with compatability: " + analyserResult.loopCompatabilities.get(loop));
						}
						
						if(analyserResult.loopCompatabilities.get(loop) == LoopType.GPU_IMPLICIT) {
							blockEntries.add(new Entry(Code.Label(CFGNode.calculateLabel(loop))));
							blockEntries.addAll(finalkernels.get(loop).replacementEntries);
							blockEntries.add(new Entry(Code.Goto(CFGNode.calculateLabel(loop.endNode.next))));
						}
					}
				}
				else {
					processCompatableNode(node);
				}
				return true;
			}

			private void processCompatableNode(CFGNode node) {
				node.forBytecode(new BytecodeVisitor() {
					@Override
					public void visit(Bytecode b) {
						blockEntries.add(new Entry(b.getWYILLangBytecode()));
					}

					@Override
					public boolean shouldVisitNode(CFGNode node) {
						if(node instanceof CFGNode.LoopNode) {
							CFGNode.LoopNode loop = (CFGNode.LoopNode)node;
							if(analyserResult.loopCompatabilities.get(loop) == LoopType.CPU_EXPLICIT) {
								return true;
							}
							else {
								blockEntries.add(new Entry(Code.Label(CFGNode.calculateLabel(loop))));
								blockEntries.addAll(finalkernels.get(loop).replacementEntries);
								blockEntries.add(new Entry(Code.Goto(CFGNode.calculateLabel(loop.endNode.next))));
								return false;
							}
						}
						else {
							return true;
						}
					}
				});
			}
		}, rootNode, null);
		
		return new Block(blk.numInputs(), blockEntries);
	}

	private OpenCLFunctionDescription processFunction(NameID name, Block blk, Type.FunctionOrMethod type) {
		if(blk == null) {
			return null;
		}
		
		String mangledName = SymbolUtilities.nameMangle(name.name(), type);
		
		if(functionsBeingProcessed.contains(mangledName)) {
			return null;
		}
		functionsBeingProcessed.add(mangledName);
		
		Set<String> called = functionsCalledByFunctions.get(mangledName);
		if(called == null) {
			called = new HashSet<String>();
			functionsCalledByFunctions.put(mangledName, called);
		}
		
		for(Block.Entry be : blk) {
			if(be.code instanceof Code.Invoke) {
				NameID calledName = ((Code.Invoke)be.code).name;
				Type.FunctionOrMethod calledType = ((Code.Invoke)be.code).type;
				String mangledCalledName = SymbolUtilities.nameMangle(calledName.name(), calledType);
				
				called.add(mangledCalledName);
				
				if(!functionCompatabilities.containsKey(mangledCalledName)) {
					OpenCLFunctionDescription description = processFunction(calledName, functionResolver.blockForFunctionWithName(calledName, calledType), calledType);
					if(description == null) {
						functionsBeingProcessed.remove(mangledName);
						functionCompatabilities.put(mangledName, false);
						return null;
					}
				}
			}
		}
		
		List<Block.Entry> entries = new ArrayList<Block.Entry>();
		for(Block.Entry be : blk) {
			entries.add(be);
		}
		
		OpenCLFunctionDescription returnValue = null;
		
		Map<Integer, DFGNode> arguments = new HashMap<Integer, DFGNode>();
		List<Argument> params = new ArrayList<Argument>();
		int count = 0;
		for(Type t : type.params()) {
			arguments.put(count, new DFGNode(null, count, t, true));
			params.add(new Argument(t, count));
			count++;
		}
		
		CFGNode rootNode = CFGGenerator.processEntries(entries, null, null, arguments);
		if(CFGCompatabilityAnalyser.analyseFunction(rootNode, arguments, type)) {
			try {
				returnValue = new OpenCLFunctionDescription(mangledName, params, CFGIterator.createNestedRepresentation(rootNode), type.ret());
				
				functionCompatabilities.put(mangledName, true);
				functionDescriptions.put(mangledName, returnValue);
			} catch (NotADAGException e) {
				throw new InternalError("Functions cannot be non-DAGs");
			}
		}
		else {
			functionCompatabilities.put(mangledName, false);
		}
		
		functionsBeingProcessed.remove(mangledName);
		
		return returnValue;
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
	
	public Map<String, OpenCLFunctionDescription> getFunctionDescriptions() {
		return functionDescriptions;
	}
}
