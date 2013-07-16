package wyocl.ar.utils;

import wyocl.ar.Bytecode;
import wyocl.ar.Bytecode.Assign;
import wyocl.ar.Bytecode.Binary;
import wyocl.ar.Bytecode.ComparisonBasedJump;
import wyocl.ar.Bytecode.ConstLoad;
import wyocl.ar.Bytecode.Convert;
import wyocl.ar.Bytecode.ForAll;
import wyocl.ar.Bytecode.Invoke;
import wyocl.ar.Bytecode.Label;
import wyocl.ar.Bytecode.LengthOf;
import wyocl.ar.Bytecode.Load;
import wyocl.ar.Bytecode.LoopEnd;
import wyocl.ar.Bytecode.Move;
import wyocl.ar.Bytecode.Not;
import wyocl.ar.Bytecode.Return;
import wyocl.ar.Bytecode.Switch;
import wyocl.ar.Bytecode.TupleLoad;
import wyocl.ar.Bytecode.Unary;
import wyocl.ar.Bytecode.UnconditionalJump;
import wyocl.ar.Bytecode.Update;
import wyocl.ar.Bytecode.While;

public class BytecodeInstanceSwitch {
	public static interface BytecodeInstanceSwitchVisitor {
		void visitUnary(Unary b);
		void visitBinary(Binary b);
		void visitConstLoad(ConstLoad b);
		void visitAssign(Assign b);
		void visitMove(Move b);
		void visitConvert(Convert b);
		void visitLoad(Load b);
		void visitLengthOf(LengthOf b);
		void visitTupleLoad(TupleLoad b);
		void visitUpdate(Update b);
		void visitFor(ForAll b);
		void visitWhile(While b);
		void visitUnconditionalJump(UnconditionalJump b);
		void visitComparisonBasedJump(ComparisonBasedJump b);
		void visitSwitch(Switch b);
		void visitLabel(Label b);
		void visitLoopEnd(LoopEnd b);
		void visitReturn(Return b);
		void visitInvoke(Invoke b);
		void visitNot(Not b);
	}
	
	public static void on(Bytecode.GPUSupportedBytecode b, BytecodeInstanceSwitchVisitor visitor) {
		if(b instanceof Bytecode.Unary) {
			visitor.visitUnary((Bytecode.Unary)b);
		}
		else if(b instanceof Bytecode.Binary) {
			visitor.visitBinary((Bytecode.Binary)b);
		}
		else if(b instanceof Bytecode.ConstLoad) {
			visitor.visitConstLoad((Bytecode.ConstLoad)b);
		}
		else if(b instanceof Bytecode.Assign) {
			visitor.visitAssign((Bytecode.Assign)b);
		}
		else if(b instanceof Bytecode.Move) {
			visitor.visitMove((Bytecode.Move)b);
		}
		else if(b instanceof Bytecode.Convert) {
			visitor.visitConvert((Bytecode.Convert)b);
		}
		else if(b instanceof Bytecode.Load) {
			visitor.visitLoad((Bytecode.Load)b);
		}
		else if(b instanceof Bytecode.LengthOf) {
			visitor.visitLengthOf((Bytecode.LengthOf)b);
		}
		else if(b instanceof Bytecode.TupleLoad) {
			visitor.visitTupleLoad((Bytecode.TupleLoad)b);
		}
		else if(b instanceof Bytecode.Update) {
			visitor.visitUpdate((Bytecode.Update)b);
		}
		else if(b instanceof Bytecode.ForAll) {
			visitor.visitFor((Bytecode.ForAll)b);
		}
		else if(b instanceof Bytecode.While) {
			visitor.visitWhile((Bytecode.While)b);
		}
		else if(b instanceof Bytecode.UnconditionalJump) {
			visitor.visitUnconditionalJump((Bytecode.UnconditionalJump)b);
		}
		else if(b instanceof Bytecode.ComparisonBasedJump) {
			visitor.visitComparisonBasedJump((Bytecode.ComparisonBasedJump)b);
		}
		else if(b instanceof Bytecode.Switch) {
			visitor.visitSwitch((Bytecode.Switch)b);
		}
		else if(b instanceof Bytecode.Label) {
			visitor.visitLabel((Bytecode.Label)b);
		}
		else if(b instanceof Bytecode.LoopEnd) {
			visitor.visitLoopEnd((Bytecode.LoopEnd)b);
		}
		else if(b instanceof Bytecode.Return) {
			visitor.visitReturn((Bytecode.Return)b);
		}
		else if(b instanceof Bytecode.Invoke) {
			visitor.visitInvoke((Bytecode.Invoke)b);
		}
		else if(b instanceof Bytecode.Not) {
			visitor.visitNot((Bytecode.Not)b);
		}
		else {
			throw new RuntimeException("Unknown bytecode encountered: "+b+" ("+b.getClass()+")");
		}
	}
}
