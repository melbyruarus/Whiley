package wyocl.filter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;

import wybs.lang.NameID;
import wybs.lang.Path.ID;
import wybs.util.Trie;
import wyil.lang.Block;
import wyil.lang.Code;
import wyil.lang.Code.AbstractAssignable;
import wyil.lang.Type;

public class LoopFilter {
	/**
	 * The endLabel is used to determine when we're within a for loop being
	 * translated in OpenCL. If this is <code>null</code> then we're *not*
	 * within a for loop. Otherwise, we are.
	 */
	private String endLabel;
	/**
	 * A unique path which can be used to identify the module currently
	 * being filtered.
	 */
	private final String modulePath;
	/**
	 * A list of entries which have been filtered out and are awaiting processing
	 */
	private ArrayList<Block.Entry> filteredEntries;
	private ArrayList<Block.Entry> replacementEntries;
	private int indexRegister;
	
	private ArrayList<Type> executeGPUCodeFunctionArgumentTypes = new ArrayList<Type>() {
		private static final long serialVersionUID = 1L;

		{
			this.add(Type.List(Type.T_ANY, false));
			this.add(Type.List(Type.T_ANY, false));
		}
	};
	private Type.FunctionOrMethod executeGPUCodeFunctionType = Type.FunctionOrMethod.Function(Type.T_VOID, Type.T_VOID, executeGPUCodeFunctionArgumentTypes);
	private NameID executeGPUCodeFunctionPath = new NameID(Trie.fromString("whiley/gpgpu/Util"), "executeWYGPUKernel");
	
	private Block currentBlock;
	
	public LoopFilter(ID id) {
		StringBuilder sb = new StringBuilder();
		for(String s : id) {
			sb.append('_');
			sb.append(s.replaceAll("[^a-z,A-Z]", "_"));
		}
		modulePath = sb.toString();
	}

	public FilterAction filter(Block.Entry entry) {
		if(currentBlock == null) {
			throw new InternalError("beginBlock() be be called before filter()");
		}
		
		// TODO: we're going to have to do something like store all byte codes for the scope
		// so that we can determine what dependancies the loop has and what arguments it is
		// going to need
		
		// TODO: we're going to need the last free register
		
		Code code = entry.code;
		
		if(endLabel == null) {
			if(code instanceof Code.ForAll) {
				Code.ForAll forall = (Code.ForAll)code;
				endLabel = forall.target;
				filteredEntries = new ArrayList<Block.Entry>();
				filteredEntries.add(entry);
				
				return FilterAction.SKIP;
			}
			else {
				return FilterAction.DEFAULT;
			}
		}
		else {
			if(code instanceof Code.Label) {
				Code.Label label = (Code.Label)code;
				if(label.label.equals(endLabel)) {
					filteredEntries.add(entry);
					endLabel = null;
					
					processFilteredBytecodes();
					
					return FilterAction.FILTER_RESULTS_READY;
				}
			}
			
			filteredEntries.add(entry);
			
			return FilterAction.SKIP;
		}
	}

	/**
	 * This method goes through the contents of filteredEntries once the loop
	 * has been finished, and determines what entries should be filtered out
	 * for OpenCL, which entries should be left intact, and what entries should
	 * be added if any.
	 * 
	 * This method outputs the entries which should stay in the source or be added
	 * to replacementEntries, and the entries which have been filtered out will be
	 * stored in filteredEntries (this includes the loop start and end label).
	 */
	private void processFilteredBytecodes() {
		// TODO: determine if the loop can be parallalized, and if there
		// are nested loops figure out which level of the loop should be
		// parallalized
		
		replacementEntries = new ArrayList<Block.Entry>();
		
		Code.ForAll forAll = (Code.ForAll)filteredEntries.get(0).code;
		HashSet<Integer> modifiedRegisters = new HashSet<Integer>();
		forAll.registers(modifiedRegisters);
		// TODO: this only works with foralls, and maybe not all foralls (what happens if source get modified?)
		modifiedRegisters.remove(forAll.sourceOperand);
		modifiedRegisters.remove(forAll.indexOperand);
		
		HashSet<Integer> readWriteRegisterSet = new HashSet<Integer>();
		determineReadWriteRegisters(readWriteRegisterSet, modifiedRegisters);
		ArrayList<Integer> readWriteRegisters = new ArrayList<Integer>();
		readWriteRegisters.addAll(readWriteRegisterSet);
		Collections.sort(readWriteRegisters); // Ensure that arguments order is deterministic
		
		setIndexRegister(forAll.indexOperand);
		
		// TODO: now we need to see which of these registers are read/written
		// or reused (written to before being read)
		
		// TODO: figure out the dependencies between loops
		
		// TODO: has register usage been optimized by this point? does it make a difference?
		
		// TODO: we're going to have to adjust all the registers used in the stored code
		// We could do that by just always +1 when calling filter for the first time - is there a limit to the number of registers?
		// Does this introduce problems?
		
		// TODO: check modifiedRegisters.size()
		
		replacementEntries.add(new Block.Entry(Code.NewList(Type.List(Type.T_ANY, false), 100, readWriteRegisters))); // FIXME: don't hard code target
		
		ArrayList<Integer> argumentsToFunction = new ArrayList<Integer>();
		argumentsToFunction.add(forAll.sourceOperand);
		argumentsToFunction.add(100);
		replacementEntries.add(new Block.Entry(Code.Invoke(executeGPUCodeFunctionType, Code.NULL_REG, argumentsToFunction, executeGPUCodeFunctionPath)));
	}
	
	private void determineReadWriteRegisters(HashSet<Integer> readWriteRegisters, HashSet<Integer> modifiedRegisters) {
		for(Block.Entry e : currentBlock) {
			if(e.equals(filteredEntries.get(0))) {
				break;
			}
			
			// TODO: will all registers which are written by the loop be assigned to beforehand?
			// i.e. determine scope
			// we could read after end label and see if read before written
			
			// TODO: Check which are read before written inside loop.
			// TODO: Check which are read outside loop before being overwritten
			
			Code code = e.code;
			if(code instanceof AbstractAssignable) {
				int target = ((AbstractAssignable)code).target;
				
				if(modifiedRegisters.contains(target)) {
					readWriteRegisters.add(target);
				}
			}
		}
	}

	/**
	 * Get the list of entries which should be put into the place of the
	 * entries which have been filtered out.
	 * 
	 * NOTE: make sure to avoid the possibility of the filter being re-run
	 * on the output of this function. So if you loop over the contents of
	 * this array make sure the function that processes the entries does not call
	 * any filters.
	 * 
	 * @return
	 */
	public ArrayList<Block.Entry> getReplacementEntries() {
		return replacementEntries;
	}
	
	public ArrayList<Block.Entry> getFilteredEntries() {
		return filteredEntries;
	}
	
	public boolean wasLoopFiltered() {
		return filteredEntries.size() > 0;
	}

	public void beginBlock(Block blk) {
		currentBlock = blk;
	}

	public void endBlock() {
		currentBlock = null;
	}

	public int getIndexRegister() {
		return indexRegister;
	}

	public void setIndexRegister(int indexRegister) {
		this.indexRegister = indexRegister;
	}
}
