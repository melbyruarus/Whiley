package wyocl.builders;

import java.util.ArrayList;
import java.util.HashMap;

import wyil.lang.Code;
import wyil.lang.WyilFile;
import wyil.lang.Block.Entry;
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
	
	protected int translate(Entry entry, int freeSlot,
			HashMap<JvmConstant, Integer> constants,
			ArrayList<UnresolvedHandler> handlers, ArrayList<Bytecode> bytecodes) {
		Code code = entry.code;
		
		switch(loopFilter.filter(entry)) {
			case SKIP:
				return freeSlot;
			case DEFAULT:
				return super.translate(entry, freeSlot, constants, handlers, bytecodes);
			case FILTER_RESULTS_READY:
				// Replay all the replacement entries through our superclasses implementation.
				int replacementFreeSlot = freeSlot;
				for(Entry replacementEntry : loopFilter.getReplacementEntries()) {
					replacementFreeSlot = super.translate(replacementEntry, replacementFreeSlot, constants, handlers, bytecodes);
				}
				return replacementFreeSlot;
		}
		
		// I don't see how we can possibly get here? Compiler wants it however.
		return freeSlot;
	}
}
