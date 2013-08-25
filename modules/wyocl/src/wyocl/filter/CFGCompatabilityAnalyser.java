package wyocl.filter;

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
import wybs.util.Pair;
import wyil.lang.Code;
import wyil.lang.Code.ListLVal;
import wyil.lang.Type;
import wyil.lang.Type.Leaf;
import wyocl.ar.Bytecode;
import wyocl.ar.Bytecode.Load;
import wyocl.ar.CFGNode;
import wyocl.ar.CFGNode.LoopEndNode;
import wyocl.ar.CFGNode.LoopNode;
import wyocl.ar.CFGNode.VanillaCFGNode;
import wyocl.ar.DFGGenerator.DFGInfo;
import wyocl.ar.DFGGenerator.DFGReadWriteTracking;
import wyocl.ar.DFGNode;
import wyocl.ar.utils.CFGIterator;
import wyocl.ar.utils.CFGIterator.CFGNodeCallback;
import wyocl.ar.utils.CFGIterator.Entry;
import wyocl.ar.utils.DFGIterator;
import wyocl.ar.utils.DFGIterator.DFGNodeCallback;
import wyocl.util.SymbolUtilities;

public class CFGCompatabilityAnalyser {
	private final static boolean DEBUG = false;

	public static class LoopAnalyserResult {
		public final boolean anyLoopsCompatable;
		public final Map<CFGNode, LoopType> loopCompatabilities;
		public final Map<CFGNode, Set<Pair<Integer, Pair<Integer, Type.Leaf>>>> gpuLoopArrayAccesses;
		public final Set<String> loopCalledFunctions;
		
		public LoopAnalyserResult(boolean anyLoopsCompatable, Map<CFGNode, LoopType> loopCompatabilities, Map<CFGNode, Set<Pair<Integer, Pair<Integer, Type.Leaf>>>> gpuLoopMultidimensionalArrayAccesses, Set<String> loopCalledFunctions) {
			this.anyLoopsCompatable = anyLoopsCompatable;
			this.loopCompatabilities = loopCompatabilities;
			this.gpuLoopArrayAccesses = gpuLoopMultidimensionalArrayAccesses;
			this.loopCalledFunctions = loopCalledFunctions;
		}
	}

	public static LoopAnalyserResult analyse(CFGNode rootNode, List<Entry> sortedCFG, Map<String, Boolean> compatableFunctions, boolean ignoreDataDependances, LoopAnalyserResult oldResult) {
		return new LoopAnalysisTask().process(rootNode, sortedCFG, compatableFunctions, ignoreDataDependances, oldResult);
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
		public final Set<Pair<Integer, Pair<Integer, Type.Leaf>>> usedMultidimensionalArrays = new HashSet<Pair<Integer, Pair<Integer, Type.Leaf>>>();

		public LoopDescription(LoopNode loopNode) {
			this.loopNode = loopNode;
		}
		
		public boolean getLoopCombatability() {
			if(DEBUG) {
				System.err.println(loopNode + " " + loopNode.getCausialWYILLangBytecode()
						 + " " + bytecodesCompatable
						 + " " + breaksCompatable
						 + " " + dataDependanciesCompatable
						 + " " + earlyReturnCompatable
						 + " " + typesCompatable
						 + " " + functionCallsCompatable);
			}
			return bytecodesCompatable && breaksCompatable && dataDependanciesCompatable && earlyReturnCompatable && typesCompatable && functionCallsCompatable;
		}
		
		@Override
		public String toString() {
			return super.toString() + " " + loopNode.toString();
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
					if(DEBUG) { System.err.println("Code not compatable because non-supported type contained: "+n.type+" " +n.register); }
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
							if(!(b instanceof Bytecode.GPUSupportedBytecode && ((Bytecode.GPUSupportedBytecode)b).isGPUCompatable())) {
								bytecodesCompatable[0] = false;
								if(DEBUG) { System.err.println("Code not compatable because non-supported bytecode contained: "+b.getCodeString()); }
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
		
		private LoopAnalyserResult process(CFGNode rootNode, List<Entry> sortedCFG, Map<String, Boolean> compatableFunctions, boolean ignoreDataDependances, LoopAnalyserResult oldResult) {
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
				determineLoopsDataDependancyCompatability(ignoreDataDependances, oldResult);
				determineLoopsRegisterTypeCompatability();
				determineLoopsFunctionCallCompatability();
				determineLoopTypes();

				Map<CFGNode, LoopType> types = new HashMap<CFGNode, LoopType>();
				Map<CFGNode, Set<Pair<Integer, Pair<Integer, Type.Leaf>>>> gpuLoopMultidimensioanlArrayUsage = new HashMap<CFGNode, Set<Pair<Integer, Pair<Integer, Type.Leaf>>>>();
				Set<String> calledFunctions = new HashSet<String>();
				boolean anyOk = false;
				for(LoopDescription l : allLoops.values()) {
					if(l.type == LoopType.GPU_IMPLICIT) {
						anyOk = true;
					}
					types.put(l.loopNode, l.type);
					calledFunctions.addAll(l.functionCalls);
					gpuLoopMultidimensioanlArrayUsage.put(l.loopNode, l.usedMultidimensionalArrays);
				}
								
				return new LoopAnalyserResult(anyOk, types, gpuLoopMultidimensioanlArrayUsage, calledFunctions);
			}
			else {
				return new LoopAnalyserResult(false, null, null, null);
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
										return false;
									}
									else if(ok == false) {
										if(DEBUG) { System.err.println("Loop " + loop + " not compatable because unsupported function called: "+name); }
										loop.functionCallsCompatable = false;
										return false;
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
								if(DEBUG) { System.err.println("Loop not compatable because early return not supported"); }
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
					if(DEBUG) { System.err.println("Loop not compatable as wrong type: " + loop.loopNode.getClass().getCanonicalName()); }
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

		private void determineLoopsDataDependancyCompatability(boolean ignoreDataDependances, LoopAnalyserResult oldResult) {
			if(DEBUG) { System.err.println("Determine loop data dependancies"); }

			for(LoopDescription loop : allLoops.values()) {
				// FIXME: Nested loop indexes used should be constant range
				// FIXME: no more than one nested loop
				// FIXME: reads not verified if not using indexes
				
				loop.dataDependanciesCompatable = true;
				final LoopNode cfgNode = loop.loopNode;
				
				if(ignoreDataDependances) {
					LoopType compat = oldResult.loopCompatabilities.get(cfgNode);
					loop.dataDependanciesCompatable = compat == LoopType.GPU_IMPLICIT || compat == LoopType.GPU_EXPLICIT;
					continue;
				}
				
				DFGReadWriteTracking startInfo = cfgNode.getStartRegisterInfo();
				DFGReadWriteTracking endInfo = cfgNode.getEndRegisterInfo();
												
				Set<DFGNode> atEnd = new HashSet<DFGNode>();
				for(DFGInfo i : endInfo.readWriteInfo.registerMapping.values()) {
					atEnd.addAll(i.lastNodes);
				}
				for(DFGInfo i : endInfo.writeInfo.registerMapping.values()) {
					atEnd.addAll(i.lastNodes);
				}
				
				Set<DFGNode> atStart = new HashSet<DFGNode>();
				for(DFGInfo i : startInfo.readWriteInfo.registerMapping.values()) {
					atStart.addAll(i.lastNodes);
				}
				for(DFGInfo i : startInfo.writeInfo.registerMapping.values()) {
					atStart.addAll(i.lastNodes);
				}
				
				final Set<DFGNode> read = new HashSet<DFGNode>();
				final Set<DFGNode> written = new HashSet<DFGNode>();
				final Set<Integer> definedBefore = new HashSet<Integer>();
				
				DFGIterator.iterateDFGAlongLastModified(new DFGNodeCallback() {
					@Override
					public boolean process(DFGNode node) {
						if(doesDFGNodeOccurBetween(cfgNode.body, cfgNode.endNode, node)) {
							if(node.isAssignment) {
								written.add(node);
							}
						}
						else {
							definedBefore.add(node.register);
						}
						
						return true;
					}
				}, atEnd, Collections.<DFGNode>emptySet());
				
				DFGIterator.iterateDFGAlongLastRead(new DFGNodeCallback() {
					@Override
					public boolean process(DFGNode node) {
						if(doesDFGNodeOccurBetween(cfgNode.body, cfgNode.endNode, node)) {
							if(!node.isAssignment) {
								read.add(node);
							}
						}
						else {
							definedBefore.add(node.register);
						}
						
						return true;
					}
				}, atEnd, Collections.<DFGNode>emptySet());
				
				// Strip out loop defined variables
				
				Set<DFGNode> loopDefined = new HashSet<DFGNode>();
				cfgNode.getIndexDFGNodes(loopDefined);
				for(DFGNode n : loopDefined) {
					definedBefore.remove(n.register);
				}
				
				// Do some stuff
				
				Map<Integer, List<Integer>> requiredArrayIndexes = new HashMap<Integer, List<Integer>>();
				Map<Integer, Pair<Integer, Type.Leaf>> elementTypesAndSize = new HashMap<Integer, Pair<Integer, Type.Leaf>>();
				
				// We only care about accesses to common variables
				
				Iterator<DFGNode> it;
				
				it = written.iterator();
				while(it.hasNext()) {
					DFGNode n = it.next();
					
					if(!definedBefore.contains(n.register) || (n.cause instanceof Bytecode && isALoopIndex(n.register, (Bytecode)n.cause))) {
						it.remove();
					}
				}
				
				boolean passesMultidimensionalListToFunction = false; // TODO: Support this
				
				it = read.iterator();
				while(it.hasNext()) {
					DFGNode n = it.next();
					
					boolean found = false;
					
					for(DFGNode other : written) {
						if(other.register == n.register) {
							found = true;
							break;
						}
					}
					
					if(!definedBefore.contains(n.register)) {
						it.remove();
					}
					else if(!found || (n.cause instanceof Bytecode && isALoopIndex(n.register, (Bytecode)n.cause))) {
						if(n.cause instanceof Bytecode.Load) {
							Bytecode.Load l = (Bytecode.Load)n.cause;
							
							if(l.getType() instanceof Type.List && SupportedTypes.includes((Type)l.getType())) {
								elementTypesAndSize.put(l.getLeftOperand(), getMultidimensionalListInfo((Type)l.getType(), 0));
							}
						}
						else if(n.cause instanceof Bytecode.Invoke &&
								n.type instanceof Type.EffectiveList &&
								!(((Type.EffectiveList)n.type).element() instanceof Type.Leaf)) {
							passesMultidimensionalListToFunction = true;
							break;
						}
						
						it.remove();
					}
				}
				
				if(passesMultidimensionalListToFunction) {
					loop.dataDependanciesCompatable = false;
					continue;
				}
								
				if(written.size() > 0) { // We may have a problem
					if(cfgNode instanceof CFGNode.ForLoopNode) {
						CFGNode.ForLoopNode forLoop = (CFGNode.ForLoopNode)cfgNode;
						
						// Assume its ok and then check if it isn't
						loop.dataDependanciesCompatable = true;
												
						for(DFGNode dfgNode : written) {
							if(dfgNode.cause instanceof Bytecode) {								
								Bytecode b = (Bytecode)dfgNode.cause;
								
								if(b instanceof Bytecode.Update) {
									Bytecode.Update u = (Bytecode.Update)b;
									
									if(u.getDataStructureBeforeType() instanceof Type.List && SupportedTypes.includes(u.getDataStructureBeforeType())) {
										boolean failed = false;
										
										List<Integer> requiredIndexes = requiredArrayIndexes.get(u.getTarget());
										List<Integer> tempIndexes = new ArrayList<Integer>();
										boolean oneIsVarying = false;
										
										if(requiredIndexes == null) {
											elementTypesAndSize.put(u.getTarget(), getMultidimensionalListInfo(u.getDataStructureBeforeType(), 0));
										}
										
										// Ok so we now know that we are indexing into a list, just need to find out what the index is
										int count = 0;
										for(@SuppressWarnings("rawtypes") Code.Update.LVal lv : u.getLValueIterator()) {
											if(lv instanceof ListLVal) {
												ListLVal llval = (ListLVal)lv;
												
												if(requiredIndexes == null) {
													tempIndexes.add(llval.indexOperand);
																										
													if(llval.indexOperand == forLoop.getIndexRegister()) {
														oneIsVarying = true;
													}
													else if(!definedBefore.contains(llval.indexOperand) && !isALoopIndex(llval.indexOperand, u)) {
														if(DEBUG) { System.err.println("Loop " + loop + " not compatable because unsupported update to critical reference occurs with unsupported index: "+u.getCodeString() + " " + dfgNode.register); }
														failed = true;
														break;
													}
												}
												else {
													if(!requiredIndexes.get(count).equals(llval.indexOperand)) {
														if(DEBUG) { System.err.println("Loop " + loop + " not compatable because unsupported update to critical reference occurs with inconsistant index: "+u.getCodeString() + " expecting: " + requiredIndexes); }
														failed = true;
														break;
													}
												}
											}
											else {
												if(DEBUG) { System.err.println("Loop " + loop + " not compatable because unsupported update to critical reference performed: "+u.getCodeString()); }
												failed = true;
												break;
											}
											
											count++;
										}
										
										if(requiredIndexes == null) {
											requiredArrayIndexes.put(u.getTarget(), tempIndexes);
											
											if(!oneIsVarying) {
												if(DEBUG) { System.err.println("Loop " + loop + " not compatable because update to critical reference occurs with non-unique index: "+u.getCodeString()); }
												loop.dataDependanciesCompatable = false;
												failed = true;
												break;
											}
										}
										
										if(failed) {
											loop.dataDependanciesCompatable = false;
											break;
										}
									}
									else {
										if(DEBUG) { System.err.println("Loop " + loop + " not compatable because unsupported update to critical reference performed: "+u.getCodeString()); }
										loop.dataDependanciesCompatable = false;
										break;
									}
								}
								else {
									if(DEBUG) { System.err.println("Loop " + loop + " not compatable because unsupported DFGNode bytecode encountered: "+b.getCodeString()); }
									loop.dataDependanciesCompatable = false;
									break;
								}
							}
							else {
								if(DEBUG) { System.err.println("Loop " + loop + " not compatable because unsupported DFGNode cause encountered: "+dfgNode.cause); }
								loop.dataDependanciesCompatable = false;
								break;
							}
						}
						
						for(DFGNode dfgNode : read) {
							if(dfgNode.cause instanceof Bytecode) {
								Bytecode b = (Bytecode)dfgNode.cause;
								
								if(b instanceof Bytecode.Load) {
									Bytecode.Load l = (Bytecode.Load)b;
									
									if(l.getType() instanceof Type.List && SupportedTypes.includes((Type)l.getType())) {										
										// Ok so we now know that we are indexing into a list, just need to find out what the index is
										
										List<Integer> requiredIndexes = requiredArrayIndexes.get(l.getLeftOperand());
										
										if(requiredIndexes != null) {
											if(!indexOfIsMultidimensionalArray(l, requiredIndexes, 0)) {
												if(DEBUG) { System.err.println("Loop " + loop + " not compatable because unsupported indexof: "+l.getCodeString() + " " + dfgNode.register); }
												loop.dataDependanciesCompatable = false;
												break;
											}
										}
										else {
											elementTypesAndSize.put(l.getLeftOperand(), getMultidimensionalListInfo((Type)l.getType(), 0));
										}
									}
									else {
										if(DEBUG) { System.err.println("Loop " + loop + " not compatable because unsupported read of critical reference performed: "+l.getCodeString()); }
										loop.dataDependanciesCompatable = false;
										break;
									}
								}
								else if(b instanceof Bytecode.LengthOf) {
									// FIXME: do this?
								}
								else {
									if(DEBUG) { System.err.println("Loop " + loop + " not compatable because unsupported read DFGNode bytecode encountered: "+b.getCodeString() + " " + dfgNode.register); }
									loop.dataDependanciesCompatable = false;
									break;
								}
							}
							else {
								if(DEBUG) { System.err.println("Loop " + loop + " not compatable because unsupported DFGNode cause encountered: "+dfgNode.cause); }
								loop.dataDependanciesCompatable = false;
								break;
							}
						}
						
						for(Map.Entry<Integer, Pair<Integer, Type.Leaf>> entry : elementTypesAndSize.entrySet()) {
							loop.usedMultidimensionalArrays.add(new Pair<Integer, Pair<Integer, Type.Leaf>>(entry.getKey(), entry.getValue()));
						}
					}
					else {
						if(DEBUG) { System.err.println("Loop " + loop + " not compatable because accesses to shared variables not allowed for: " + cfgNode.getClass()); }
						loop.dataDependanciesCompatable = false;
					}
				}
				else {
					// Don't write to any shared variables, we're all good
					
					loop.dataDependanciesCompatable = true;
				}
			}
		}

		private boolean indexOfIsMultidimensionalArray(Load load, List<Integer> requiredIndexes, int index) {
			if(index >= requiredIndexes.size()) {
				return true;
			}
			
			if(load.getRightOperand() != requiredIndexes.get(index)) {
				return false;
			}
			
			for(DFGNode node : load.writtenDFGNodes.values()) {
				if(node.nextModified.size() > 1 || !node.nextModified.contains(node)) {
					return false;
				}
				
				if(index + 1 >= requiredIndexes.size()) {
					return true;
				}
				
				for(DFGNode nextRead : node.nextRead) {
					if(nextRead.cause instanceof Bytecode.Load) {
						if(!indexOfIsMultidimensionalArray((Bytecode.Load)nextRead.cause, requiredIndexes, index+1)) {
							return false;
						}
					}
				}
			}
			
			return true;
		}

		private boolean isALoopIndex(int register, Bytecode readBytecode) {
			DFGNode readNode = readBytecode.readDFGNodes.get(register);
			
			if(readNode == null) {
				return false;
			}
			else {
				for(DFGNode n : readNode.lastModified) {
					if(!(n.cause instanceof CFGNode.ForLoopNode)) {
						return false;
					}
				}
				
				return true;
			}
		}

		private boolean doesDFGNodeOccurBetween(CFGNode startNode, LoopEndNode endNode, DFGNode dfgNode) {
			CFGNode node = null;
			if(dfgNode.cause instanceof Bytecode) {
				Bytecode bytecode = (Bytecode)dfgNode.cause;
				node = bytecode.cfgNode;
			}
			else if(dfgNode.cause instanceof CFGNode) {
				node = (CFGNode)dfgNode.cause;
			}
			else if(dfgNode.cause == null) { // This can only occur for registers defined outside this function, i.e. argument
				return false;
			}
			else {
				throw new InternalError("Unknown DFGNode cause: " + dfgNode.cause);
			}
												
			return CFGIterator.doesNodeDependUpon(node, startNode, endNode);
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

	public static Pair<Integer, Leaf> getMultidimensionalListInfo(Type type, int dim) {
		if(type instanceof Type.EffectiveList) {
			return getMultidimensionalListInfo(((Type.EffectiveList)type).element(), dim+1);
		}
		else {
			return new Pair<Integer, Leaf>(dim, (Type.Leaf)type);
		}
	}
}
