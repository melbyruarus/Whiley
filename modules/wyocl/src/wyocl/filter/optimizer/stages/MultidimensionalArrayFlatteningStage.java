package wyocl.filter.optimizer.stages;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import wybs.lang.NameID;
import wybs.util.Pair;
import wybs.util.Trie;
import wyil.lang.Code;
import wyil.lang.Code.BinArithKind;
import wyil.lang.Code.ListLVal;
import wyil.lang.Constant;
import wyil.lang.Type;
import wyil.lang.Type.EffectiveList;
import wyil.lang.Type.Leaf;
import wyocl.ar.ARPrinter;
import wyocl.ar.Bytecode;
import wyocl.ar.CFGGenerator;
import wyocl.ar.CFGNode;
import wyocl.ar.CFGNode.DummyNode;
import wyocl.ar.CFGNode.VanillaCFGNode;
import wyocl.ar.DFGGenerator;
import wyocl.ar.DFGNode;
import wyocl.ar.utils.CFGIterator;
import wyocl.ar.utils.CFGIterator.CFGNestedRepresentationVisitor;
import wyocl.ar.utils.CFGIterator.Entry;
import wyocl.ar.utils.DFGIterator;
import wyocl.ar.utils.NotADAGException;
import wyocl.filter.CFGCompatabilityAnalyser.LoopAnalyserResult;
import wyocl.filter.LoopType;

public class MultidimensionalArrayFlatteningStage {
	private static final boolean DEBUG = false;

	public static void process(final DummyNode dummyNode, final LoopAnalyserResult analyserResult, Map<Integer, DFGNode> argumentRegisters) {
		try {
			if(DEBUG) {
				System.err.println("------before-------");
				try {
					ARPrinter.print(dummyNode, false);
				} catch (NotADAGException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			
			final CFGNode.LoopNode foundImplicit[] = new CFGNode.LoopNode[1];
			final Map<Integer, Integer> flattenedLoopRegisters = new HashMap<Integer, Integer>();
			final Map<Integer, Pair<Integer, Type.Leaf>> flattenedLoopType = new HashMap<Integer, Pair<Integer, Type.Leaf>>();
			final Map<Integer, int[]> arrayDimensions = new HashMap<Integer, int[]>();
			final int freeRegister[] = new int[1];
			freeRegister[0] = DFGIterator.maxUsedRegister(dummyNode)+1;
						
			CFGIterator.traverseNestedRepresentation(CFGIterator.createNestedRepresentation(dummyNode), new CFGNestedRepresentationVisitor() {
				@SuppressWarnings({ "unchecked", "rawtypes" })
				@Override
				public void visitNonNestedNode(CFGNode node) {						
					if(foundImplicit[0] != null) {
						if(node instanceof CFGNode.VanillaCFGNode) {
							CFGNode.VanillaCFGNode vanilla = (CFGNode.VanillaCFGNode)node;														
							for(Bytecode b : new ArrayList<Bytecode>(vanilla.body.instructions)) {
								// Check to see if it is update or indexof
								if(b instanceof Bytecode.Load) {
									Bytecode.Load l = (Bytecode.Load)b;
									
									if(flattenedLoopRegisters.containsKey(l.getLeftOperand())) {
										Type.List flatType = Type.List(flattenedLoopType.get(l.getLeftOperand()).second(), false);
										int dimensions = flattenedLoopType.get(l.getLeftOperand()).first();
										int flatRegister = flattenedLoopRegisters.get(l.getLeftOperand());
										
										if(dimensions > 1) {
											List<Integer> indexRegisters = new ArrayList<Integer>();
											for(int i=0;i<dimensions;i++) {
												indexRegisters.add(freeRegister[0]++);
											}
											List<Pair<CFGNode.VanillaCFGNode, Bytecode.Load>> finalIndexes = new ArrayList<Pair<CFGNode.VanillaCFGNode, Bytecode.Load>>();
											stripOutIndexes(l, indexRegisters, finalIndexes, 0);
																					
											for(Pair<CFGNode.VanillaCFGNode, Bytecode.Load> p : finalIndexes) {
												int index = p.first().body.instructions.indexOf(p.second());
												p.first().body.instructions.remove(index);
												
												DFGGenerator.clearDFG(p.second());
												
												int indexRegister = freeRegister[0]++;
												
												List<Bytecode> newBytecodes = new ArrayList<Bytecode>();
												newBytecodes.add(new Bytecode.ConstLoad(Code.Const(indexRegister, Constant.V_INTEGER(BigInteger.ZERO))));
												for(int i=0;i<dimensions;i++) {
													newBytecodes.add(new Bytecode.Binary(Code.BinArithOp(Type.T_INT, indexRegister, indexRegister, arrayDimensions.get(l.getLeftOperand())[i], BinArithKind.MUL)));
													newBytecodes.add(new Bytecode.Binary(Code.BinArithOp(Type.T_INT, indexRegister, indexRegister, indexRegisters.get(i), BinArithKind.ADD)));
												}
												newBytecodes.add(new Bytecode.Load(Code.IndexOf(flatType, p.second().getTarget(), flatRegister, indexRegister)));
												for(Bytecode bytec : newBytecodes) {
													bytec.cfgNode = vanilla;
												}
												
												vanilla.body.instructions.addAll(index, newBytecodes);
											}
										}
									}
								}
								else if(b instanceof Bytecode.Update) {
									Bytecode.Update u = (Bytecode.Update)b;
									if(flattenedLoopRegisters.containsKey(u.getTarget())) {
										Type.List flatType = Type.List(flattenedLoopType.get(u.getTarget()).second(), false);
										int flatRegister = flattenedLoopRegisters.get(u.getTarget());
										int dimensions = flattenedLoopType.get(u.getTarget()).first();
										
										if(dimensions > 1) {
											int index = vanilla.body.instructions.indexOf(u);
											vanilla.body.instructions.remove(index);
											
											DFGGenerator.clearDFG(u);
											
											int indexRegister = freeRegister[0]++;
											
											List<Bytecode> newBytecodes = new ArrayList<Bytecode>();
											newBytecodes.add(new Bytecode.ConstLoad(Code.Const(indexRegister, Constant.V_INTEGER(BigInteger.ZERO))));
											int count = 0;
											for(ListLVal lval : (Iterable<ListLVal>)(Iterable)u.getLValueIterator()) {
												newBytecodes.add(new Bytecode.Binary(Code.BinArithOp(Type.T_INT, indexRegister, indexRegister, arrayDimensions.get(u.getTarget())[count], BinArithKind.MUL)));
												newBytecodes.add(new Bytecode.Binary(Code.BinArithOp(Type.T_INT, indexRegister, indexRegister, lval.indexOperand, BinArithKind.ADD)));
												count++;
											}
											Set<Integer> indexSet = new HashSet<Integer>();
											indexSet.add(indexRegister);
											newBytecodes.add(new Bytecode.Update(Code.Update(flatType, flatRegister, u.getOperand(), indexSet, flatType, Collections.EMPTY_SET)));
											for(Bytecode bytec : newBytecodes) {
												bytec.cfgNode = vanilla;
											}
											
											vanilla.body.instructions.addAll(index, newBytecodes);
										}
									}
								}
							}
						}
					}
				}
				
				@Override
				public void exitedNestedNode(CFGNode node) {
					if(foundImplicit[0] == node) {
						foundImplicit[0] = null;
					}
				}
				
				@Override
				public void enteredNestedNode(CFGNode node, List<Entry> nestedEntries) {
					if(foundImplicit[0] == null && node instanceof CFGNode.LoopNode) {
						CFGNode.LoopNode loop = (CFGNode.LoopNode)node;
						if(analyserResult.loopCompatabilities.get(loop) == LoopType.GPU_IMPLICIT) {
							foundImplicit[0] = loop;

							// Flatten all used arrays
							for(Pair<Integer, Pair<Integer, Type.Leaf>> p : analyserResult.gpuLoopArrayAccesses.get(node)) {
								if(p.second().first() > 1) {
									int newRegister = freeRegister[0]++;
									int oldRegister = p.first();
									
									Pair<Integer, int[]> result = flattenMultidimensionalArray(oldRegister, p.second().first(), newRegister, loop, freeRegister[0], p.second().second(), constructMultidimensionalListType(p.second().second(), p.second().first()));
									freeRegister[0] = result.first();
									
									flattenedLoopRegisters.put(oldRegister, newRegister);
									flattenedLoopType.put(oldRegister, p.second());
									arrayDimensions.put(oldRegister, result.second());
								}
							}
						}
					}
				}
			});
			
			CFGGenerator.populateIdentifiers(dummyNode, 0);

			if(DEBUG) {
				System.err.println("------after-------");
				System.err.println("Replaced : " + flattenedLoopRegisters);
				try {
					ARPrinter.print(dummyNode, false);
				} catch (NotADAGException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		} catch (NotADAGException e) {
			throw new RuntimeException(e); // Should never happen
		}
	}
	
	private static void stripOutIndexes(Bytecode.Load load, List<Integer> readIndexes, List<Pair<VanillaCFGNode, Bytecode.Load>> finalIndexes, int depth) {
		CFGNode.VanillaCFGNode vanillaCFGNode = (VanillaCFGNode)load.cfgNode;
		int index = vanillaCFGNode.body.instructions.indexOf(load);
		Bytecode b = new Bytecode.Assign(Code.Assign(Type.T_INT, readIndexes.get(depth), load.getRightOperand()));
		b.cfgNode = vanillaCFGNode;
		vanillaCFGNode.body.instructions.add(index, b);
				
		if(depth + 1 < readIndexes.size()) {
			vanillaCFGNode.body.instructions.remove(load);
			
			for(DFGNode node : load.writtenDFGNodes.values()) {	
				for(DFGNode nextRead : node.nextRead) {
					if(nextRead.cause instanceof Bytecode.Load) {
						stripOutIndexes((Bytecode.Load)nextRead.cause, readIndexes, finalIndexes, depth+1);
					}
				}
			}
		}
		else {
			finalIndexes.add(new Pair<VanillaCFGNode, Bytecode.Load>(vanillaCFGNode, load));
		}
		
		DFGGenerator.clearDFG(load);
	}

	protected static EffectiveList constructMultidimensionalListType(Leaf leaf, int dimension) {
		if(dimension == 1) {
			return Type.List(leaf, false);
		}
		else {
			return Type.List((Type)constructMultidimensionalListType(leaf, dimension-1), false);
		}
	}

	protected static Pair<Integer, int[]> flattenMultidimensionalArray(int oldRegister, int dimensions, int newRegister, CFGNode.LoopNode loop, int freeRegister, Type.Leaf elementType, Type.EffectiveList listType) {
		Type.List flatType = Type.List(elementType, false);
		int returnStore = freeRegister++;
		int dimensionsListRegister = freeRegister++;
		int counterRegister = freeRegister++;
		int numberOfDimensionsRegister = freeRegister++;
		
		int flattenOperands[] = new int[2];
		flattenOperands[0] = oldRegister;
		flattenOperands[1] = numberOfDimensionsRegister;
		
		Type.List dimensionsListType = Type.List(Type.T_INT, false);
		Type.Tuple returnType = Type.Tuple(Type.T_LIST_ANY, dimensionsListType);
		Type.Tuple pretendReturnType = Type.Tuple(flatType, dimensionsListType);
		
		Type.Function flattenFunctionType = Type.Function(returnType, Type.T_VOID, Type.T_LIST_ANY, Type.T_INT);
		NameID flattenName = new NameID(Trie.fromString("whiley/gpgpu/Util"), "flattenMultidimensionalArray");
		CFGNode.VanillaCFGNode headerNode = new CFGNode.VanillaCFGNode();
		headerNode.body.instructions.add(new Bytecode.ConstLoad(Code.Const(numberOfDimensionsRegister, Constant.V_INTEGER(BigInteger.valueOf(dimensions)))));
		headerNode.body.instructions.add(new Bytecode.Invoke(Code.Invoke(flattenFunctionType, returnStore, flattenOperands, flattenName)));
		headerNode.body.instructions.add(new Bytecode.TupleLoad(Code.TupleLoad(pretendReturnType, newRegister, returnStore, 0)));
		headerNode.body.instructions.add(new Bytecode.TupleLoad(Code.TupleLoad(pretendReturnType, dimensionsListRegister, returnStore, 1)));
		int registersForDimensions[] = new int[dimensions];
		for(int dim=0;dim<dimensions;dim++) {
			registersForDimensions[dim] = freeRegister++;
			headerNode.body.instructions.add(new Bytecode.ConstLoad(Code.Const(counterRegister, Constant.V_INTEGER(BigInteger.valueOf(dim)))));
			headerNode.body.instructions.add(new Bytecode.Load(Code.IndexOf(dimensionsListType, registersForDimensions[dim], dimensionsListRegister, counterRegister)));
		}
		
		for(Bytecode b : headerNode.body.instructions) {
			b.cfgNode = headerNode;
		}
		
		for(CFGNode n : loop.previous) {
			n.retargetNext(loop, headerNode);
			headerNode.previous.add(n);
		}
		headerNode.next = loop;
		loop.previous.clear();
		loop.previous.add(headerNode);
		
		int unflattenOperands[] = new int[4];
		unflattenOperands[0] = newRegister;
		unflattenOperands[1] = oldRegister;
		unflattenOperands[2] = numberOfDimensionsRegister;
		unflattenOperands[3] = dimensionsListRegister;
		
		Type.Function unflattenFunctionType = Type.Function(Type.T_LIST_ANY, Type.T_VOID, Type.T_LIST_ANY, Type.T_LIST_ANY, Type.T_INT, dimensionsListType);
		NameID unflattenName = new NameID(Trie.fromString("whiley/gpgpu/Util"), "unflattenMultidimensionalArray");
		CFGNode.VanillaCFGNode footerNode = new CFGNode.VanillaCFGNode();
		Bytecode invk = new Bytecode.Invoke(Code.Invoke(unflattenFunctionType, freeRegister++, unflattenOperands, unflattenName));
		invk.cfgNode = footerNode;
		footerNode.body.instructions.add(invk);
		
		footerNode.next = loop.endNode.next;
		loop.endNode.next = footerNode;
		footerNode.previous.add(loop.endNode);
		footerNode.next.previous.remove(loop.endNode);
		
		return new Pair<Integer, int[]>(freeRegister, registersForDimensions);
	}
}
