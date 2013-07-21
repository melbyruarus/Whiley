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
import wyocl.ar.DFGGenerator;
import wyocl.ar.DFGNode;
import wyocl.ar.utils.CFGIterator;
import wyocl.ar.utils.DFGIterator;
import wyocl.filter.LoopFilterCFGCompatabilityAnalyser.AnalyserResult;
import wyocl.filter.LoopType;

public class ForallToForOptimisationStage {
	public static void process(final CFGNode.DummyNode rootNode, final AnalyserResult analyserResult, final Map<Integer, DFGNode> argumentRegisters) {
		final boolean[] earlyFinish = new boolean[1];
		earlyFinish[0] = true;
		while(earlyFinish[0]) {
			earlyFinish[0] = false;

			CFGIterator.iterateCFGFlow(new CFGIterator.CFGNodeCallback() {
				@Override
				public boolean process(CFGNode node) {
					if(node instanceof CFGNode.ForAllLoopNode && analyserResult.loopCompatabilities.get(node) != LoopType.CPU_EXPLICIT) {
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
								replaceForAllWithFor(loop, listCreationBytecodes, analyserResult);
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
	
	private static void replaceForAllWithFor(ForAllLoopNode loop, Set<Bytecode.Binary> listCreationBytecodes, final AnalyserResult analyserResult) {
		//------------------------
		// Perform actual swap
		//------------------------
		Type.Leaf indexType = (Type.Leaf)loop.getBytecode().getIndexType();

		int lowerRegister = DFGIterator.maxUsedRegister(loop) + 1;
		int upperRegister = lowerRegister+1;

		for(Bytecode.Binary bytecode : listCreationBytecodes) {
			List<Bytecode> bytecodes = ((CFGNode.VanillaCFGNode)bytecode.cfgNode).body.instructions;
			int index = bytecodes.indexOf(bytecode);
			if(index < 0) {
				throw new InternalError("Bytecode not contained in bytecode.cfgNode.body.instructions");
			}

			bytecodes.add(index, new Bytecode.Assign(Code.Assign(indexType, lowerRegister, bytecode.getLeftOperand())));
			bytecodes.add(index, new Bytecode.Assign(Code.Assign(indexType, upperRegister, bytecode.getRightOperand())));
		}

		CFGNode.ForLoopNode forLoop = new CFGNode.ForLoopNode(loop.getBytecode().getIndexRegister(),
											indexType, lowerRegister, upperRegister, loop.loopEndLabel(),
											loop.startIndex, loop.endIndex, loop.getCausialWYILLangBytecode());

		forLoop.body = loop.body;
		loop.body = null;
		forLoop.body.previous.remove(loop);
		forLoop.body.previous.add(forLoop);

		for(CFGNode.LoopBreakNode oldNode : loop.breakNodes) {
			CFGNode.LoopBreakNode newNode = new CFGNode.LoopBreakNode(forLoop);
			newNode.previous.addAll(oldNode.previous);
			for(CFGNode n : newNode.previous) {
				n.retargetNext(oldNode, newNode);
			}
			oldNode.previous.clear();
			newNode.next = oldNode.next;
			oldNode.next = null;
			forLoop.breakNodes.add(newNode);
		}
		loop.breakNodes.clear();

		forLoop.endNode.next = loop.endNode.next;
		loop.endNode.next = null;
		forLoop.endNode.next.previous.remove(loop.endNode);
		forLoop.endNode.next.previous.add(forLoop.endNode);
		forLoop.endNode.previous.addAll(loop.endNode.previous);
		for(CFGNode p : forLoop.endNode.previous) {
			p.retargetNext(loop.endNode, forLoop.endNode);
		}
		loop.endNode.previous.clear();


		forLoop.previous.addAll(loop.previous);
		loop.previous.clear();
		for(CFGNode n : forLoop.previous) {
			n.retargetNext(loop, forLoop);
		}
		
		//------------------------
		// Update analyser results
		//------------------------
		
		analyserResult.loopCompatabilities.put(forLoop, analyserResult.loopCompatabilities.get(loop));
	}
}
