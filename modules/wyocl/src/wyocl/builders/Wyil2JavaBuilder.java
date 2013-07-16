package wyocl.builders;

import java.util.ArrayList;
import java.util.HashMap;

import wyil.lang.Block;
import wyil.lang.WyilFile;
import wyjvm.attributes.Code.Handler;
import wyjvm.attributes.LineNumberTable;
import wyjvm.lang.Bytecode;
import wyjvm.lang.ClassFile;
import wyocl.filter.LoopFilter;

/**
 * A builder for compiling Wyil files into a combination of Java and OpenCL.
 * Essentially, certain loops marked for execution on the GPU are compiled into
 * OpenCL, whilst the remainder is left in Java with appropriate hooks being
 * installed to interact with the GPU.
 * 
 * 
 */
public class Wyil2JavaBuilder extends wyjc.Wyil2JavaBuilder {
	/**
	 * This Filter is used to strip out loops which have been determined to
	 * be parallelisable and replace them with code which will invoke the
	 * appropriate OpenCL kernel
	 */
	private LoopFilter loopFilter;
	
	@Override
	protected ClassFile build(WyilFile module) {
		loopFilter = new LoopFilter(module.id());
		ClassFile result = super.build(module);
		loopFilter = null;
		return result;
	}
	
	@Override
	protected ClassFile.Method build(int caseNum, WyilFile.Case mcase,
			WyilFile.MethodDeclaration method, HashMap<JvmConstant,Integer> constants) {
		loopFilter.beginMethod(method);
		ClassFile.Method ret = super.build(caseNum, mcase, method, constants);
		loopFilter.endMethod();
		return ret;
	}
	
	@Override
	public void translate(Block blk, int freeSlot,
			HashMap<JvmConstant, Integer> constants,
			ArrayList<Handler> handlers,
			ArrayList<LineNumberTable.Entry> lineNumbers,
			ArrayList<Bytecode> bytecodes) {
		Block newBlock = loopFilter.processBlock(blk, null, null);
		if(newBlock != null) {
			blk = newBlock;
		}
		super.translate(blk, freeSlot, constants, handlers, lineNumbers, bytecodes);
	}
}
