package wyocl.filter;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import wyocl.ar.Bytecode;
import wyocl.ar.CFGNode;
import wyocl.ar.DFGNode;
import wyocl.ar.CFGNode.LoopNode;
import wyocl.ar.CFGNode.VanillaCFGNode;
import wyocl.ar.utils.CFGIterator;
import wyocl.ar.utils.CFGIterator.CFGNodeCallback;
import wyocl.ar.utils.CFGIterator.Entry;

public class LoopFilterCFGCompatabilityAnalyser {
	private final static boolean DEBUG = false;

	public static class AnalyserResult {
		public final boolean anyLoopsCompatable;
		public final Map<CFGNode, LoopType> loopCompatabilities;
		
		public AnalyserResult(boolean anyLoopsCompatable, Map<CFGNode, LoopType> loopCompatabilities) {
			this.anyLoopsCompatable = anyLoopsCompatable;
			this.loopCompatabilities = loopCompatabilities;
		}
	}

	public static AnalyserResult analyse(CFGNode rootNode, List<Entry> sortedCFG) {
		return new Task().preprocessLoops(rootNode, sortedCFG);
	}
	
	private static class LoopDescription {
		public LoopType type;
		public final LoopNode loopNode;
		public boolean bytecodesCompatable = false;
		public boolean breaksCompatable = false;
		public boolean dataDependanciesCompatable = false;
		public boolean earlyReturnCompatable = false;
		public boolean typesCompatable = false;

		public LoopDescription(LoopNode loopNode) {
			this.loopNode = loopNode;
		}
		
		public boolean getLoopCombatability() {
			return bytecodesCompatable && breaksCompatable && dataDependanciesCompatable && earlyReturnCompatable && typesCompatable;
		}
	}

	private static class Task {
		private Map<CFGNode, LoopDescription> allLoops = new HashMap<CFGNode, LoopDescription>();
		private List<Entry> sortedCFG;
		
		private AnalyserResult preprocessLoops(CFGNode rootNode, List<Entry> sortedCFG) {
			if(DEBUG) { System.err.println("Preprocess loops"); }
			
			this.sortedCFG = sortedCFG;

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
				for(LoopDescription l : allLoops.values()) {
					types.put(l.loopNode, l.type);
				}
				return new AnalyserResult(true, types);
			}
			else {
				return new AnalyserResult(false, null);
			}
		}

		private void determineLoopsFunctionCallCompatability() {
			// FIXME: implement
		}

		private void determineLoopsRegisterTypeCompatability() {
			for(final LoopDescription loop : allLoops.values()) {
				loop.typesCompatable = true;

				final Set<DFGNode> nodes = new HashSet<DFGNode>();
				Set<CFGNode> endNodes = new HashSet<CFGNode>();
				loop.loopNode.getScopeNextNodes(endNodes);
				CFGIterator.iterateCFGFlow(new CFGIterator.CFGNodeCallback() {
					@Override
					public boolean process(CFGNode node) {
						node.gatherDFGNodesInto(nodes);
						return true;
					}
				}, loop.loopNode, endNodes);

				for(DFGNode n : nodes) {
					if(!SupportedTypes.includes(n.type)) {
						if(DEBUG) { System.err.println("Loop " + loop + " not compatable because non-supported type contained: "+n.type); }
						loop.typesCompatable = false;
						break;
					}
				}
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
				loop.bytecodesCompatable = true;

				Set<CFGNode> endNodes = new HashSet<CFGNode>();
				loop.loopNode.getScopeNextNodes(endNodes);

				CFGIterator.iterateCFGFlow(new CFGNodeCallback() {
					@Override
					public boolean process(CFGNode node) {
						if(node instanceof VanillaCFGNode) {
							VanillaCFGNode vanilaNode = (VanillaCFGNode)node;
							for(Bytecode b : vanilaNode.body.instructions) {
								if(!(b instanceof Bytecode.GPUSupportedBytecode)) {
									loop.bytecodesCompatable = false;
									if(DEBUG) { System.err.println("Loop " + loop + " not compatable because non-supported bytecode contained: "+b); }
									return false;
								}
							}
						}
						else if(!(node instanceof CFGNode.GPUSupportedNode)) {
							loop.bytecodesCompatable = false;
							if(DEBUG) { System.err.println("Loop " + loop + " not compatable because non-supported node contained: "+node); }
							return false;
						}
						else {
							CFGNode.GPUSupportedNode gpuNode = (CFGNode.GPUSupportedNode)node;
							if(!gpuNode.isGPUSupported()) {
								loop.bytecodesCompatable = false;
								if(DEBUG) { System.err.println("Loop " + loop + " not compatable because non-supported node contained: "+node); }
								return false;
							}
						}

						return true;
					}
				}, loop.loopNode, endNodes);
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
