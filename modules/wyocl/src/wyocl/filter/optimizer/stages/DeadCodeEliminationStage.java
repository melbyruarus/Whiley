package wyocl.filter.optimizer.stages;

import java.util.List;
import java.util.Map;

import wyocl.ar.ARPrinter;
import wyocl.ar.Bytecode;
import wyocl.ar.CFGGenerator;
import wyocl.ar.CFGNode;
import wyocl.ar.CFGNode.DummyNode;
import wyocl.ar.DFGGenerator;
import wyocl.ar.DFGNode;
import wyocl.ar.utils.CFGIterator;
import wyocl.ar.utils.CFGIterator.CFGNodeCallback;
import wyocl.ar.utils.NotADAGException;

public class DeadCodeEliminationStage {
	private static final boolean DEBUG = false;
	
	public static void process(final DummyNode dummyNode, final Map<Integer, DFGNode> argumentRegisters) {
		// TODO: eliminate iteratively & nodes
		
		if(DEBUG) {
			System.err.println("------before-------");
			try {
				ARPrinter.print(dummyNode, false);
			} catch (NotADAGException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		final boolean earlyTermination[] = new boolean[1];
		earlyTermination[0] = true;
		
		while(earlyTermination[0]) {
			earlyTermination[0] = false;
			
			CFGIterator.iterateCFGFlow(new CFGNodeCallback() {
				@Override
				public boolean process(CFGNode node) {
					if(node != null) {
						// TODO: should probably be polymorphic and on CFGNode, this is currently
						// only to satisfy Forall->For loop optimizations
						if(node instanceof CFGNode.VanillaCFGNode) {
							List<Bytecode> bytecodes = ((CFGNode.VanillaCFGNode) node).body.instructions;
							
							for(int n=0;n<bytecodes.size();n++) {
								Bytecode b = bytecodes.get(n);
								
								if(b instanceof Bytecode.SideeffectFree) {
									if(b.writtenDFGNodes.isEmpty()) {
										bytecodes.remove(n);
										n--;
									}
									else {
										boolean allNotRead = true;
										for(DFGNode d : b.writtenDFGNodes.values()) {
											if(!d.nextRead.isEmpty()) { // TODO: this isn't enough, need to check for cycles and things
												if(!(d.nextRead.size() == 1 && d.nextRead.contains(d))) {
													allNotRead = false;
													break;
												}
											}
											if(!d.nextModified.isEmpty()) { // TODO: this isn't enough, need to check for cycles and things
												if(!(d.nextModified.size() == 1 && d.nextModified.contains(d))) {
													allNotRead = false;
													break;
												}
											}
										}
										
										if(allNotRead) {
											bytecodes.remove(n);
											n--;
										}
									}
								}
							}
						}
						
						if(node.isEmpty()) {
							node.disconnect();
							CFGGenerator.populateIdentifiers(dummyNode, 0);
							DFGGenerator.clearDFG(dummyNode);
							DFGGenerator.populateDFG(dummyNode, argumentRegisters);
							earlyTermination[0] = true;
							return false;
						}
					}
					
					return true;
				}
			}, dummyNode, null);
		}
		
		if(DEBUG) {
			System.err.println("------after-------");
			try {
				ARPrinter.print(dummyNode, false);
			} catch (NotADAGException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
}
