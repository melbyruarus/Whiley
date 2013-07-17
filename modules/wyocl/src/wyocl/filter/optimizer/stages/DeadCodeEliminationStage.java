package wyocl.filter.optimizer.stages;

import java.util.List;
import java.util.Map;

import wyocl.ar.Bytecode;
import wyocl.ar.CFGNode;
import wyocl.ar.CFGNode.DummyNode;
import wyocl.ar.DFGNode;
import wyocl.ar.utils.CFGIterator;
import wyocl.ar.utils.CFGIterator.CFGNodeCallback;
import wyocl.filter.LoopFilterCFGCompatabilityAnalyser.AnalyserResult;

public class DeadCodeEliminationStage {

	public static void process(DummyNode dummyNode, AnalyserResult analyserResult, Map<Integer, DFGNode> argumentRegisters) {
		// TODO: eliminate iteratively & nodes
		
		CFGIterator.iterateCFGFlow(new CFGNodeCallback() {
			@Override
			public boolean process(CFGNode node) {
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
				return true;
			}
		}, dummyNode, null);
	}

}
