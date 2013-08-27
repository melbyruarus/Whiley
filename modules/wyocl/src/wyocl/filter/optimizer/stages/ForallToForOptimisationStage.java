package wyocl.filter.optimizer.stages;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import wyil.lang.Code;
import wyil.lang.Code.BinArithKind;
import wyil.lang.Type;
import wyocl.ar.Bytecode;
import wyocl.ar.CFGGenerator;
import wyocl.ar.CFGNode;
import wyocl.ar.CFGNode.ForAllLoopNode;
import wyocl.ar.CFGNode.ForLoopNode;
import wyocl.ar.DFGGenerator;
import wyocl.ar.DFGNode;
import wyocl.ar.utils.CFGIterator;
import wyocl.ar.utils.DFGIterator;
import wyocl.filter.CFGCompatabilityAnalyser.LoopAnalyserResult;
import wyocl.filter.LoopType;

public class ForallToForOptimisationStage {
	public static void processBeforeAnalysis(final CFGNode.DummyNode rootNode, final Map<Integer, DFGNode> argumentRegisters) {
		final boolean[] earlyFinish = new boolean[1];
		earlyFinish[0] = true;
		while(earlyFinish[0]) {
			earlyFinish[0] = false;

			CFGIterator.iterateCFGFlow(new CFGIterator.CFGNodeCallback() {
				@Override
				public boolean process(CFGNode node) {
					if(node instanceof CFGNode.ForAllLoopNode) {
						CFGNode.ForAllLoopNode loop = (CFGNode.ForAllLoopNode)node;
						DFGNode sourceDFGNode = loop.getBytecode().getSourceDFGNode();
						if(sourceDFGNode.type instanceof Type.EffectiveList) {
							boolean allAreRanges = true;
							Set<Bytecode.Binary> listCreationBytecodes = new HashSet<Bytecode.Binary>();

							for(DFGNode lastModification : sourceDFGNode.lastModified) {
								if(lastModification.cause instanceof Bytecode.Binary) {
									Bytecode.Binary bin = (Bytecode.Binary)lastModification.cause;
									if(bin.getArithKind().equals(BinArithKind.RANGE) &&
											bin.readDFGNodes.get(bin.getLeftOperand()).type instanceof Type.Int &&
											bin.readDFGNodes.get(bin.getRightOperand()).type instanceof Type.Int) {
										listCreationBytecodes.add(bin);
									}
									else {
										allAreRanges = false;
									}
								}
								else {
									allAreRanges = false;
								}
							}

							if(allAreRanges) {
								replaceForAllWithFor(loop, listCreationBytecodes);
								earlyFinish[0] = true;


								CFGGenerator.populateIdentifiers(rootNode, 0);
								DFGGenerator.clearDFG(rootNode);
								DFGGenerator.populateDFG(rootNode, argumentRegisters);

								return false; // Have to stop iterating as we have just modified the CFG, outer loop will restart iteration
							}
						}
					}
					return true;
				}
			}, rootNode, null);
		}
	}
	
	public static void processAfterAnalysis(final CFGNode.DummyNode rootNode, final LoopAnalyserResult analyserResult, final Map<Integer, DFGNode> argumentRegisters) {
		final boolean[] earlyFinish = new boolean[1];
		earlyFinish[0] = true;
		while(earlyFinish[0]) {
			earlyFinish[0] = false;

			CFGIterator.iterateCFGFlow(new CFGIterator.CFGNodeCallback() {
				@Override
				public boolean process(CFGNode node) {
					if(node instanceof CFGNode.ForLoopNode) {
						CFGNode.ForLoopNode loop = (CFGNode.ForLoopNode)node;
						
						if(analyserResult.loopCompatabilities.get(loop) == LoopType.CPU_EXPLICIT) {
							replaceForWithForAll(loop, analyserResult);
							
							earlyFinish[0] = true;
	
							CFGGenerator.populateIdentifiers(rootNode, 0);
							DFGGenerator.clearDFG(rootNode);
							DFGGenerator.populateDFG(rootNode, argumentRegisters);
	
							return false; // Have to stop iterating as we have just modified the CFG, outer loop will restart iteration
						}
					}
					return true;
				}
			}, rootNode, null);
		}
	}
	
	private static void replaceForAllWithFor(ForAllLoopNode loop, Set<Bytecode.Binary> listCreationBytecodes) {
		//------------------------
		// Perform actual swap
		//------------------------
		Type.Leaf indexType = (Type.Leaf)loop.getIndexType();

		int lowerRegister = DFGIterator.maxUsedRegister(loop) + 1;
		int upperRegister = lowerRegister+1;

		for(Bytecode.Binary bytecode : listCreationBytecodes) {
			List<Bytecode> bytecodes = ((CFGNode.VanillaCFGNode)bytecode.cfgNode).body.instructions;
			int index = bytecodes.indexOf(bytecode);
			if(index < 0) {
				throw new InternalError("Bytecode not contained in bytecode.cfgNode.body.instructions");
			}

			Bytecode lower = new Bytecode.Assign(Code.Assign(indexType, lowerRegister, bytecode.getLeftOperand()));
			lower.cfgNode = bytecode.cfgNode;
			Bytecode upper = new Bytecode.Assign(Code.Assign(indexType, upperRegister, bytecode.getRightOperand()));
			upper.cfgNode = bytecode.cfgNode;
			bytecodes.add(index, lower);
			bytecodes.add(index, upper);
		}

		CFGNode.ForLoopNode forLoop = new CFGNode.ForLoopNode(loop.getBytecode().getIndexRegister(),
											indexType, lowerRegister, upperRegister, loop.loopEndLabel(),
											loop.startIndex, loop.endIndex, loop.getCausialWYILLangBytecode());

		loop.replaceWith(forLoop);
	}
	
	private static void replaceForWithForAll(ForLoopNode loop, final LoopAnalyserResult analyserResult) {
		//------------------------
		// Perform actual swap
		//------------------------
		Type.Leaf indexType = loop.getIndexType();

		int listRegister = DFGIterator.maxUsedRegister(loop) + 1;

		CFGNode.VanillaCFGNode newVanillaNode = new CFGNode.VanillaCFGNode();
		
		if(loop.getIndexes().size() != 1) {
			throw new InternalError("Unexpected number of indexes (" + loop.getIndexes().size() + ") on a for loop, has the order of optimisations been incorrectly specified?");
		}
		
		Bytecode createRange = new Bytecode.Binary(Code.BinArithOp(Type.List(loop.getIndexType(), false), listRegister, loop.getIndexes().get(0).startRegister, loop.getIndexes().get(0).endRegister, BinArithKind.RANGE));
		createRange.cfgNode = newVanillaNode;
		newVanillaNode.body.instructions.add(createRange);

		Bytecode.ForAll loopBytecode = new Bytecode.ForAll(Code.ForAll(Type.List(indexType, false),
															listRegister,
															loop.getIndexes().get(0).indexRegister,
															((Code.Loop)loop.getCausialWYILLangBytecode()).modifiedOperands,
															loop.loopEndLabel()));
		
		CFGNode.ForAllLoopNode forLoop = new CFGNode.ForAllLoopNode(loopBytecode,
																	loop.startIndex,
																	loop.endIndex);
		loopBytecode.cfgNode = forLoop;
		
		loop.replaceWith(forLoop);
		
		for(CFGNode p : forLoop.previous) {
			p.retargetNext(forLoop, newVanillaNode);
		}
		
		newVanillaNode.previous.addAll(forLoop.previous);
		forLoop.previous.clear();
		forLoop.previous.add(newVanillaNode);
		newVanillaNode.next = forLoop;
		
		//------------------------
		// Update analyser results
		//------------------------
		
		analyserResult.loopCompatabilities.put(forLoop, analyserResult.loopCompatabilities.get(loop));
	}
}
