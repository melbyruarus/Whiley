package wyocl.filter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import wybs.lang.Path.ID;
import wyil.lang.Block;
import wyil.lang.Block.Entry;
import wyil.lang.Type;
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
import wyocl.filter.LoopFilterCFGCompatabilityAnalyser.AnalyserResult;
import wyocl.filter.optimizer.CFGOptimizer;

public class LoopFilter {
	/**
	 * A unique path which can be used to identify the module currently
	 * being filtered.
	 */
	private final String modulePath;
	
	private Map<Integer, DFGNode> methodArgumentsDFGNodes;

	public LoopFilter(ID id) {
		modulePath = id.toString();
	}


	public Block processBlock(Block blk, Map<CFGNode.LoopNode, OpenCLKernelInvocationDescription> kernels, Set<OpenCLFunctionDescription> functions) {
		if(methodArgumentsDFGNodes == null) {
			throw new InternalError("beginMethod() must be called before processBlock()");
		}
		
		if(kernels == null) {
			kernels = new HashMap<CFGNode.LoopNode, OpenCLKernelInvocationDescription>();
		}
		final Map<CFGNode.LoopNode, OpenCLKernelInvocationDescription> finalkernels = kernels;

		List<Block.Entry> entries = new ArrayList<Block.Entry>(); // TODO: method of actually getting this?
		for(Block.Entry be : blk) {
			entries.add(be);
		}

		Set<CFGNode.ReturnNode> exitPoints = new HashSet<CFGNode.ReturnNode>();
		Set<UnresolvedTargetNode> unresolvedTargets = new HashSet<CFGNode.UnresolvedTargetNode>();
		CFGNode rootNode = CFGGenerator.processEntries(entries, exitPoints, unresolvedTargets, methodArgumentsDFGNodes);

		try {
			List<CFGIterator.Entry> sortedCFG = CFGIterator.createNestedRepresentation(rootNode);
			final AnalyserResult analyserResult = LoopFilterCFGCompatabilityAnalyser.analyse(rootNode, sortedCFG);
			if(!analyserResult.anyLoopsCompatable) {
				return null;
			}
			rootNode = CFGOptimizer.process(rootNode, analyserResult, methodArgumentsDFGNodes);
			
			CFGIterator.iterateCFGFlow(new CFGNodeCallback() {
				@Override
				public boolean process(CFGNode node) {
					if(node instanceof CFGNode.LoopNode) {
						if(analyserResult.loopCompatabilities.get(node) == LoopType.GPU_IMPLICIT) {
							finalkernels.put((LoopNode) node, LoopFilterLoopProcessor.process((LoopNode) node, modulePath));
						}
					}
					return true;
				}
			}, rootNode, null);
			
			// FIXME: implement functions
			
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
								blockEntries.addAll(finalkernels.get(loop).replacementEntries);
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
									blockEntries.addAll(finalkernels.get(loop).replacementEntries);
									return false;
								}
							}
							else {
								return true;
							}
						}
					}, new HashMap<CFGNode, Integer>(), new int[1]);
				}
			}, rootNode, null);
			
			return new Block(blk.numInputs(), blockEntries);
		} catch (NotADAGException e) {
			System.err.println("Somehow the current block has a CFG which is not a DAG -> ???? leaving this block entirely on the CPU for the time being");
			return null;
		}
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
}
