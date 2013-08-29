package wyocl.filter.optimizer;

import java.util.Map;

import wyocl.ar.CFGNode;
import wyocl.ar.DFGGenerator;
import wyocl.ar.DFGNode;
import wyocl.filter.CFGCompatabilityAnalyser.LoopAnalyserResult;
import wyocl.filter.optimizer.stages.DeadCodeEliminationStage;
import wyocl.filter.optimizer.stages.ForallToForOptimisationStage;
import wyocl.filter.optimizer.stages.MultidimensionalArrayFlatteningStage;
import wyocl.filter.optimizer.stages.MultidimensionalLoopFlatteningStage;

public class CFGOptimizer {
	private static final int OPTIMISATION_LEVEL = getOpLevel();
	
	private static int getOpLevel() {
		String s = System.getenv("WHILEY_OPTIMISATION_LEVEL");
		if(s != null && s.length() > 0) {
			try {
				return Integer.parseInt(s);
			} catch (NumberFormatException e) {
				return 100;
			}
		}
		else {
			return 100;
		}
	}
	
	public static CFGNode processAfterAnalysis(CFGNode rootNode, LoopAnalyserResult analyserResult, final Map<Integer, DFGNode> argumentRegisters) {
		CFGNode.DummyNode dummyNode = new CFGNode.DummyNode(rootNode);
		rootNode.previous.add(dummyNode);
				
		if(OPTIMISATION_LEVEL >= 1) {
			ForallToForOptimisationStage.processAfterAnalysis(dummyNode, analyserResult, argumentRegisters);
		}
		
		MultidimensionalArrayFlatteningStage.process(dummyNode, analyserResult, argumentRegisters);
		
		DFGGenerator.clearDFG(dummyNode);
		DFGGenerator.populateDFG(dummyNode, argumentRegisters);
		
		if(OPTIMISATION_LEVEL >= 2) {
			MultidimensionalLoopFlatteningStage.process(dummyNode, analyserResult, argumentRegisters);
		}
		
		// TODO: performConstantPropogation(dummyNode);
		
		DFGGenerator.clearDFG(dummyNode);
		DFGGenerator.populateDFG(dummyNode, argumentRegisters);
		
		DeadCodeEliminationStage.process(dummyNode, argumentRegisters);
		
		rootNode.previous.remove(dummyNode);
		return dummyNode.next;
	}
	
	public static CFGNode processBeforeAnalysis(CFGNode rootNode, final Map<Integer, DFGNode> argumentRegisters) {
		CFGNode.DummyNode dummyNode = new CFGNode.DummyNode(rootNode);
		rootNode.previous.add(dummyNode);
		
		if(OPTIMISATION_LEVEL >= 1) {
			ForallToForOptimisationStage.processBeforeAnalysis(dummyNode, argumentRegisters);
		}
		
		// TODO: performConstantPropogation(dummyNode);
		
		DFGGenerator.clearDFG(dummyNode);
		DFGGenerator.populateDFG(dummyNode, argumentRegisters);
		
		DeadCodeEliminationStage.process(dummyNode, argumentRegisters);
		
		rootNode.previous.remove(dummyNode);
		return dummyNode.next;
	}
}
