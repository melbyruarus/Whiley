package wyocl.ar.utils;

import wyocl.ar.CFGNode;
import wyocl.ar.CFGNode.ConditionalJumpNode;
import wyocl.ar.CFGNode.ForLoopNode;
import wyocl.ar.CFGNode.LoopBreakNode;
import wyocl.ar.CFGNode.LoopEndNode;
import wyocl.ar.CFGNode.MultiConditionalJumpNode;
import wyocl.ar.CFGNode.ReturnNode;
import wyocl.ar.CFGNode.UnresolvedTargetNode;
import wyocl.ar.CFGNode.VanillaCFGNode;
import wyocl.ar.CFGNode.WhileLoopNode;

public class CFGInstanceSwitch {
	public static interface CFGInstanceSwitchVisitor {
		void visitVanillaNode(VanillaCFGNode node);
		void visitForNode(ForLoopNode node);
		void visitWhileNode(WhileLoopNode node);
		void visitLoopBreakNode(LoopBreakNode node);
		void visitLoopEndNode(LoopEndNode node);
		void visitMultiConditionalJumpNode(MultiConditionalJumpNode node);
		void visitConditionalJumpNode(ConditionalJumpNode node);
		void visitReturnNode(ReturnNode node);
		void visitUnresolvedTargetNode(UnresolvedTargetNode node);
	}
	
	public static void on(CFGNode node, CFGInstanceSwitchVisitor visitor) {
		if(node instanceof CFGNode.VanillaCFGNode) {
			visitor.visitVanillaNode((CFGNode.VanillaCFGNode)node);
		}
		else if(node instanceof CFGNode.ForLoopNode) {
			visitor.visitForNode((CFGNode.ForLoopNode)node);
		}
		else if(node instanceof CFGNode.WhileLoopNode) {
			visitor.visitWhileNode((CFGNode.WhileLoopNode)node);
		}
		else if(node instanceof CFGNode.LoopBreakNode) {
			visitor.visitLoopBreakNode((CFGNode.LoopBreakNode)node);
		}
		else if(node instanceof CFGNode.LoopEndNode) {
			visitor.visitLoopEndNode((CFGNode.LoopEndNode)node);
		}
		else if(node instanceof CFGNode.MultiConditionalJumpNode) {
			visitor.visitMultiConditionalJumpNode((CFGNode.MultiConditionalJumpNode)node);
		}
		else if(node instanceof CFGNode.ConditionalJumpNode) {
			visitor.visitConditionalJumpNode((CFGNode.ConditionalJumpNode)node);
		}
		else if(node instanceof CFGNode.ReturnNode) {
			visitor.visitReturnNode((CFGNode.ReturnNode)node);
		}
		else if(node instanceof CFGNode.UnresolvedTargetNode) {
			visitor.visitUnresolvedTargetNode((CFGNode.UnresolvedTargetNode)node);
		}
		else {
			throw new RuntimeException("Internal state inconsistancy");
		}
	}
}
