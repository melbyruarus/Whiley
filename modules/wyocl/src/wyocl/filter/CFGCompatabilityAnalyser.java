package wyocl.filter;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import wybs.lang.NameID;
import wyil.lang.Type;
import wyocl.ar.Bytecode;
import wyocl.ar.CFGNode;
import wyocl.ar.CFGNode.LoopNode;
import wyocl.ar.CFGNode.VanillaCFGNode;
import wyocl.ar.DFGNode;
import wyocl.ar.utils.CFGIterator;
import wyocl.ar.utils.CFGIterator.CFGNodeCallback;
import wyocl.ar.utils.CFGIterator.Entry;
import wyocl.util.SymbolUtilities;

public class CFGCompatabilityAnalyser {
	private final static boolean DEBUG = false;

	public static class LoopAnalyserResult {
		public final boolean anyLoopsCompatable;
		public final Map<CFGNode, LoopType> loopCompatabilities;
		public final Set<String> loopCalledFunctions;
		
		public LoopAnalyserResult(boolean anyLoopsCompatable, Map<CFGNode, LoopType> loopCompatabilities, Set<String> loopCalledFunctions) {
			this.anyLoopsCompatable = anyLoopsCompatable;
			this.loopCompatabilities = loopCompatabilities;
			this.loopCalledFunctions = loopCalledFunctions;
		}
	}

	public static LoopAnalyserResult analyse(CFGNode rootNode, List<Entry> sortedCFG, Map<String, Boolean> compatableFunctions) {
		return new LoopAnalysisTask().process(rootNode, sortedCFG, compatableFunctions);
	}
	
	public static boolean analyseFunction(CFGNode rootNode, Map<Integer, DFGNode> arguments, Type returnType) {
		return new FunctionAnalysisTask().process(rootNode, arguments, returnType);
	}
	
	private static class LoopDescription {
		public LoopType type;
		public final LoopNode loopNode;
		public boolean bytecodesCompatable = false;
		public boolean breaksCompatable = false;
		public boolean dataDependanciesCompatable = false;
		public boolean earlyReturnCompatable = false;
		public boolean typesCompatable = false;
		public boolean functionCallsCompatable;
		public HashSet<String> functionCalls;

		public LoopDescription(LoopNode loopNode) {
			this.loopNode = loopNode;
		}
		
		public boolean getLoopCombatability() {
			return bytecodesCompatable && breaksCompatable && dataDependanciesCompatable && earlyReturnCompatable && typesCompatable && functionCallsCompatable;
		}
	}
	
	private static abstract class AbstractAnalysisTask {
		protected boolean determineRegisterTypeCompatability(CFGNode rootNode) {
			boolean typesCompatable = true;

			final Set<DFGNode> nodes = new HashSet<DFGNode>();
			Set<CFGNode> endNodes = new HashSet<CFGNode>();
			rootNode.getScopeNextNodes(endNodes);
			CFGIterator.iterateCFGFlow(new CFGIterator.CFGNodeCallback() {
				@Override
				public boolean process(CFGNode node) {
					node.gatherDFGNodesInto(nodes);
					return true;
				}
			}, rootNode, endNodes);

			for(DFGNode n : nodes) {
				if(!SupportedTypes.includes(n.type)) {
					if(DEBUG) { System.err.println("Code not compatable because non-supported type contained: "+n.type); }
					typesCompatable = false;
					break;
				}
			}
			
			return typesCompatable;
		}
		
		protected boolean determineBytecodeCompatability(CFGNode rootNode) {
			final boolean[] bytecodesCompatable = new boolean[1];
			bytecodesCompatable[0] = true;
			
			Set<CFGNode> endNodes = new HashSet<CFGNode>();
			rootNode.getScopeNextNodes(endNodes);

			CFGIterator.iterateCFGFlow(new CFGNodeCallback() {
				@Override
				public boolean process(CFGNode node) {
					if(node instanceof VanillaCFGNode) {
						VanillaCFGNode vanilaNode = (VanillaCFGNode)node;
						for(Bytecode b : vanilaNode.body.instructions) {
							if(!(b instanceof Bytecode.GPUSupportedBytecode)) {
								bytecodesCompatable[0] = false;
								if(DEBUG) { System.err.println("Code not compatable because non-supported bytecode contained: "+b); }
								return false;
							}
						}
					}
					else if(!(node instanceof CFGNode.GPUSupportedNode)) {
						bytecodesCompatable[0] = false;
						if(DEBUG) { System.err.println("Code not compatable because non-supported node contained: "+node); }
						return false;
					}
					else {
						CFGNode.GPUSupportedNode gpuNode = (CFGNode.GPUSupportedNode)node;
						if(!gpuNode.isGPUSupported()) {
							bytecodesCompatable[0] = false;
							if(DEBUG) { System.err.println("Code not compatable because non-supported node contained: "+node); }
							return false;
						}
					}

					return true;
				}
			}, rootNode, endNodes);
			
			return bytecodesCompatable[0];
		}
	}
	
	private static class FunctionAnalysisTask extends AbstractAnalysisTask {
		public boolean process(CFGNode rootNode, Map<Integer, DFGNode> arguments, Type returnType) {
			return determineBytecodeCompatability(rootNode) && determineRegisterTypeCompatability(rootNode) && determinePassByValueComplience(rootNode, arguments, returnType);
		}
		
		private boolean determinePassByValueComplience(CFGNode rootNode, Map<Integer, DFGNode> arguments, Type returnType) {
			// FIXME: implement
			return true;
		}
	}

	private static class LoopAnalysisTask extends AbstractAnalysisTask {
		private Map<CFGNode, LoopDescription> allLoops = new HashMap<CFGNode, LoopDescription>();
		private List<Entry> sortedCFG;
		private Map<String, Boolean> compatableFunctions;
		
		private LoopAnalyserResult process(CFGNode rootNode, List<Entry> sortedCFG, Map<String, Boolean> compatableFunctions) {
			if(DEBUG) { System.err.println("Preprocess loops"); }
			
			this.sortedCFG = sortedCFG;
			this.compatableFunctions = compatableFunctions;

			CFGIterator.iterateCFGFlow(new CFGNodeCallback() {
				@Override
				public boolean process(CFGNode node) {
					if(node instanceof CFGNode.LoopNode) {
						CFGNode.LoopNode loopNode = (CFGNode.LoopNode)node;
						allLoops.put(loopNode, new LoopDescription(loopNode));
					}
					return true;
				}
			}, rootNode, null);

			if(!allLoops.isEmpty()) {
				determineLoopsBytecodeCompatability();
				determineLoopsBreakCompatability();
				determineLoopsEarlyReturnCompatability();
				determineLoopsDataDependancyCompatability();
				determineLoopsRegisterTypeCompatability();
				determineLoopsFunctionCallCompatability();
				determineLoopTypes();

				Map<CFGNode, LoopType> types = new HashMap<CFGNode, LoopType>();
				Set<String> calledFunctions = new HashSet<String>();
				for(LoopDescription l : allLoops.values()) {
					types.put(l.loopNode, l.type);
					calledFunctions.addAll(l.functionCalls);
				}
				return new LoopAnalyserResult(true, types, calledFunctions);
			}
			else {
				return new LoopAnalyserResult(false, null, null);
			}
		}

		private void determineLoopsFunctionCallCompatability() {
			for(final LoopDescription loop : allLoops.values()) {
				loop.functionCallsCompatable = true;
				loop.functionCalls = new HashSet<String>();

				Set<CFGNode> endNodes = new HashSet<CFGNode>();
				loop.loopNode.getScopeNextNodes(endNodes);
				CFGIterator.iterateCFGFlow(new CFGIterator.CFGNodeCallback() {
					@Override
					public boolean process(CFGNode node) {
						if(node instanceof CFGNode.VanillaCFGNode) {
							for(Bytecode b : ((CFGNode.VanillaCFGNode) node).body.instructions) {
								if(b instanceof Bytecode.Invoke) {
									NameID name = ((Bytecode.Invoke) b).getName();
									Type.FunctionOrMethod type = ((Bytecode.Invoke) b).getType();
									String mangledName = SymbolUtilities.nameMangle(name.name(), type);
									loop.functionCalls.add(mangledName);
									
									Boolean ok = compatableFunctions.get(mangledName);
									if(ok == null) {
										if(DEBUG) { System.err.println("Loop " + loop + " not compatable because unresolved function called: "+name); }
										loop.functionCallsCompatable = false;
									}
									else if(ok == false) {
										if(DEBUG) { System.err.println("Loop " + loop + " not compatable because unsupported function called: "+name); }
										loop.functionCallsCompatable = false;
									}
								}
							}
						}
						return true;
					}
				}, loop.loopNode, endNodes);
			}
		}

		private void determineLoopsRegisterTypeCompatability() {
			for(final LoopDescription loop : allLoops.values()) {
				loop.typesCompatable = determineRegisterTypeCompatability(loop.loopNode);
			}
		}

		private void determineLoopsEarlyReturnCompatability() {
			for(final LoopDescription loop : allLoops.values()) {
				loop.earlyReturnCompatable = true;

				boolean canHaveReturns = loop.loopNode.endNode.next instanceof CFGNode.ReturnNode;

				if(!canHaveReturns) {
					Set<CFGNode> endNodes = new HashSet<CFGNode>();
					loop.loopNode.getScopeNextNodes(endNodes);

					CFGIterator.iterateCFGFlow(new CFGNodeCallback() {
						@Override
						public boolean process(CFGNode node) {
							if(node instanceof CFGNode.ReturnNode) {
								loop.earlyReturnCompatable = false;
								return false;
							}

							return true;
						}
					}, loop.loopNode, endNodes);
				}
			}
		}

		private void determineLoopsBytecodeCompatability() {
			if(DEBUG) { System.err.println("Determine bytecode compatability"); }

			for(final LoopDescription loop : allLoops.values()) {
				if(loop.loopNode instanceof CFGNode.ForAllLoopNode || loop.loopNode instanceof CFGNode.ForLoopNode) {
					loop.bytecodesCompatable = determineBytecodeCompatability(loop.loopNode);
				}
				else {
					loop.bytecodesCompatable = false;
				}
			}
		}

		private void determineLoopsBreakCompatability() {
			if(DEBUG) { System.err.println("Determine loop break compatability"); }

			for(LoopDescription loop : allLoops.values()) {
				loop.breaksCompatable = true;

				for(CFGNode.LoopBreakNode node : loop.loopNode.breakNodes) {
					if(node.next != loop.loopNode.endNode.next) {
						if(DEBUG) { System.err.println("Loop " + loop + " not compatable because non-supported loop break contained: "+node); }
						loop.breaksCompatable = false;
					}
				}
			}
		}

		private void determineLoopsDataDependancyCompatability() {
			if(DEBUG) { System.err.println("Determine loop data dependancies"); }

			for(LoopDescription loop : allLoops.values()) {
				loop.dataDependanciesCompatable = true;

				// FIXME: check for read before write, not occurrence - always going to be there.

//				boolean earlyBreak = false;
//	
//				ForLoopNode cfgNode = loop.getCFGNode();
//				for(Pair<Type, Set<DFGNode>> value : cfgNode.body.getStartTypes().values()) {
//					for(DFGNode dfgNode : value.second()) {
//						CFGNode node = null;
//						if(dfgNode.cause instanceof Bytecode) {
//							Bytecode bytecode = (Bytecode)dfgNode.cause;
//							node = bytecode.cfgNode;
//						}
//						else if(dfgNode.cause instanceof CFGNode) {
//							node = (CFGNode)dfgNode.cause;
//						}
//						else if(dfgNode.cause == null) { // This can only occur for registers defined outside this function, i.e. argument
//							break;
//						}
//						else {
//							throw new InternalError("Unknown DFGNode cause: " + dfgNode.cause);
//						}
//	
//						if(CFGIterator.doesNodeDependUpon(node, cfgNode.body, cfgNode.endNode)) {
//							loop.setDataDependanciesCombatable(false);
//							if(DEBUG) { System.err.println("Loop " + loop + " not compatable because non-supported data denendancy contained on register: "+dfgNode.register); }
//							earlyBreak = true;
//						}
//	
//						if(earlyBreak) { break; }
//					}
//	
//					if(earlyBreak) { break; }
//				}
			}
		}

		private void determineLoopTypes() {
			if(DEBUG) { System.err.println("Deterine loop types"); }

			final Stack<LoopDescription> outerLoops = new Stack<LoopDescription>();

			CFGIterator.traverseNestedRepresentation(sortedCFG, new CFGIterator.CFGNestedRepresentationVisitor() {
				@Override
				public void visitNonNestedNode(CFGNode node) {
					if(node instanceof CFGNode.LoopNode) {
						LoopDescription loop = allLoops.get(node);
						if(outerLoops.isEmpty()) {
							if(loop.getLoopCombatability()) {
								loop.type = LoopType.GPU_IMPLICIT;
							}
							else {
								loop.type = LoopType.CPU_EXPLICIT;
							}
						}
						else {
							switch(outerLoops.peek().type) {
								case GPU_EXPLICIT:
									loop.type = LoopType.GPU_EXPLICIT;
									break;
								case GPU_IMPLICIT:
									// TODO: implicit inside implicit
									loop.type = LoopType.GPU_EXPLICIT;
									break;
								case CPU_EXPLICIT:
									if(loop.getLoopCombatability()) {
										loop.type = LoopType.GPU_IMPLICIT;
									}
									else {
										loop.type = LoopType.CPU_EXPLICIT;
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
						outerLoops.push(allLoops.get(node));
					}
				}
			});
		}
	}
}
