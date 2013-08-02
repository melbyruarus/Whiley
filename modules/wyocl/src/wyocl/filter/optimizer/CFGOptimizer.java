package wyocl.filter.optimizer;

import java.util.Map;

import wyocl.ar.CFGNode;
import wyocl.ar.DFGNode;
import wyocl.filter.CFGCompatabilityAnalyser.LoopAnalyserResult;
import wyocl.filter.optimizer.stages.DeadCodeEliminationStage;
import wyocl.filter.optimizer.stages.ForallToForOptimisationStage;

public class CFGOptimizer {

	public static CFGNode processAfterAnalysis(CFGNode rootNode, LoopAnalyserResult analyserResult, final Map<Integer, DFGNode> argumentRegisters) {
		CFGNode.DummyNode dummyNode = new CFGNode.DummyNode(rootNode);
		rootNode.previous.add(dummyNode);
		
		ForallToForOptimisationStage.processAfterAnalysis(dummyNode, analyserResult, argumentRegisters);
		
		// FIXME: performConstantPropogation(dummyNode);
		
		DeadCodeEliminationStage.process(dummyNode, argumentRegisters);
		
		rootNode.previous.remove(dummyNode);
		return dummyNode.next;
	}
	
	public static CFGNode processBeforeAnalysis(CFGNode rootNode, final Map<Integer, DFGNode> argumentRegisters) {
		CFGNode.DummyNode dummyNode = new CFGNode.DummyNode(rootNode);
		rootNode.previous.add(dummyNode);
		
		ForallToForOptimisationStage.processBeforeAnalysis(dummyNode, argumentRegisters);
				
		// FIXME: performConstantPropogation(dummyNode);
		
		DeadCodeEliminationStage.process(dummyNode, argumentRegisters);
		
		rootNode.previous.remove(dummyNode);
		return dummyNode.next;
	}
}
