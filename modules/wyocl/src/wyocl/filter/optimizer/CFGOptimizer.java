package wyocl.filter.optimizer;

import java.util.Map;

import wyocl.ar.CFGNode;
import wyocl.ar.DFGNode;
import wyocl.filter.LoopFilterCFGCompatabilityAnalyser.AnalyserResult;
import wyocl.filter.optimizer.stages.ForallToForOptimisationStage;

public class CFGOptimizer {

	public static CFGNode process(CFGNode rootNode, AnalyserResult analyserResult, final Map<Integer, DFGNode> argumentRegisters) {
		CFGNode.DummyNode dummyNode = new CFGNode.DummyNode(rootNode);
		
		ForallToForOptimisationStage.process(dummyNode, analyserResult, argumentRegisters);
		
		// FIXME: performConstantPropogation(dummyNode);
		analyseDFGForDeadCode(dummyNode);
		
		return dummyNode.next;
	}

	private static void analyseDFGForDeadCode(CFGNode.DummyNode dummyNode) {
		// TODO: implement
	}
}
