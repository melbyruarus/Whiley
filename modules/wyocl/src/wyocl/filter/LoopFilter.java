package wyocl.filter;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

import wybs.lang.NameID;
import wybs.lang.Path.ID;
import wybs.util.Trie;
import wyil.lang.Block;
import wyil.lang.Code;
import wyil.lang.Constant;
import wyil.lang.Type;
import wyjc.runtime.WyType;

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
	private ArrayList<Argument> kernelArguments;
	private int indexRegister;
	
	private ArrayList<Type> executeGPUCodeFunctionArgumentTypes = new ArrayList<Type>() {
		private static final long serialVersionUID = 1L;

		{
			this.add(Type.List(Type.T_ANY, false));
		}
	};
	private Type.FunctionOrMethod executeGPUCodeFunctionType = Type.FunctionOrMethod.Function(Type.List(Type.T_ANY, false), Type.T_VOID, executeGPUCodeFunctionArgumentTypes);
	private NameID executeGPUCodeFunctionPath = new NameID(Trie.fromString("whiley/gpgpu/Util"), "executeWYGPUKernel");
	
	private Block currentBlock;
	
	public static class Argument implements Comparable<Argument> {
		public final Type type;
		public final int register;
		private boolean readonly = true;
		
		private Argument(Object type, int register) {
			this.type = (Type)type;
			this.register = register;
		}
		
		@Override
		public int compareTo(Argument arg0) {
			return register - arg0.register;
		}

		public boolean isReadonly() {
			return readonly;
		}

		public void setReadonly(boolean readonly) {
			this.readonly = readonly;
		}
	}
	
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
		// TODO: Check source isn't modified?
		
		kernelArguments = new ArrayList<Argument>();
		checkLoopForGPGPUCompatability(kernelArguments);
		// Remove possible duplication of the kernel argument
		Iterator<Argument> it = kernelArguments.iterator();
		while(it.hasNext()) {
			Argument arg = it.next();
			if(arg.register == forAll.sourceOperand) {
				if(!arg.readonly) {
					throw new RuntimeException("GPU cannot loop over an array that is being simultaniously updated");
				}
				it.remove();
			}
		}
		Collections.sort(kernelArguments);
		kernelArguments.add(0, new Argument(forAll.type, forAll.sourceOperand));
				
		ArrayList<Integer> argumentRegisters = new ArrayList<Integer>();
		for(Argument arg : kernelArguments) {
			argumentRegisters.add(arg.register);
		}
		
		// TODO: actually output marshaling and unmarshaling code here? Avoids cost of function call, wrapping/unwrapping multiple times and type tests
		
		setIndexRegister(forAll.indexOperand);
		
		// TODO: now we need to see which of these registers are read/written
		// or reused (written to before being read)
		
		// TODO: figure out the dependencies between loops
		
		// TODO: has register usage been optimized by this point? does it make a difference?
		
		// TODO: we're going to have to adjust all the registers used in the stored code
		// We could do that by just always +1 when calling filter for the first time - is there a limit to the number of registers?
		// Does this introduce problems?
		
		// TODO: check modifiedRegisters.size()
		
		final int temporaryListRegister = 100; // FIXME: don't hard code target
		final int temporaryCounterRegister = 101; // FIXME: don't hard code
		
		replacementEntries.add(new Block.Entry(Code.NewList(Type.List(Type.T_ANY, false), temporaryListRegister, argumentRegisters)));
		
		ArrayList<Integer> argumentsToFunction = new ArrayList<Integer>();
		argumentsToFunction.add(temporaryListRegister);
		replacementEntries.add(new Block.Entry(Code.Invoke(executeGPUCodeFunctionType, temporaryListRegister, argumentsToFunction, executeGPUCodeFunctionPath)));
		
		int count = 0;
		for(Argument arg : kernelArguments) {
			replacementEntries.add(new Block.Entry(Code.Const(temporaryCounterRegister, Constant.V_INTEGER(BigInteger.valueOf(count)))));
			replacementEntries.add(new Block.Entry(Code.IndexOf(Type.List(arg.type, true), arg.register, temporaryListRegister, temporaryCounterRegister)));
			count++;
		}
	}
	
	private boolean checkLoopForGPGPUCompatability(List<Argument> arguments) {
		boolean startedLoop = false;
		boolean finishedLoop = false;
		
		HashSet<Integer> possibleInputs = new HashSet<Integer>();
		HashSet<Integer> possibleOutputs = new HashSet<Integer>();
		HashSet<Integer> readRegistersInLoop = new HashSet<Integer>();
		HashSet<Integer> writtenRegistersInLoop = new HashSet<Integer>();
		HashSet<Integer> writtenRegistersAfterLoop = new HashSet<Integer>();
		
		HashMap<Integer, Type> typesAtStartOfLoop = new HashMap<Integer, Type>();
		HashMap<Integer, Type> typesAtEndOfLoop = new HashMap<Integer, Type>();
				
		for(Block.Entry e : currentBlock) {
			Code code = e.code;
						
			if(!startedLoop) { // Before loop
				if(e.equals(filteredEntries.get(0))) {
					startedLoop = true;
					continue;
				}
				
				HashSet<Integer> written = new HashSet<Integer>();
				ModifiedRegisterAnalysis.getModifiedRegisters(code, null, written);
				ModifiedRegisterAnalysis.getAssignedType(code, typesAtStartOfLoop);
				possibleInputs.addAll(written);
				possibleOutputs.addAll(written);
			}
			else if(!finishedLoop) { // Inside loop
				if(e.equals(filteredEntries.get(filteredEntries.size()-1))) {
					finishedLoop = true;
					continue;
				}
				
				HashSet<Integer> read = new HashSet<Integer>();
				HashSet<Integer> written = new HashSet<Integer>();
				ModifiedRegisterAnalysis.getModifiedRegisters(code, read, written);
				ModifiedRegisterAnalysis.getAssignedType(code, typesAtEndOfLoop);
				// This code must occur before writtenRegistersInLoop.add(...)
				for(int reg : read) {
					if(!writtenRegistersInLoop.contains(reg)) {
						readRegistersInLoop.add(reg);
					}
				}
				writtenRegistersInLoop.addAll(written);
			}
			else { // After loop
				HashSet<Integer> read = new HashSet<Integer>();
				HashSet<Integer> written = new HashSet<Integer>();
				ModifiedRegisterAnalysis.getModifiedRegisters(code, read, written);
				// This code must occur before writtenRegistersInLoop.add(...)
				for(int reg : read) {
					if(!writtenRegistersAfterLoop.contains(reg)) {
						possibleOutputs.add(reg);
					}
				}
				writtenRegistersAfterLoop.addAll(written);
			}
			
			// TODO: check for data dependencies
		}
		
		// Compute inputs and outputs
		HashSet<Integer> outputs = new HashSet<Integer>(writtenRegistersInLoop);
		outputs.retainAll(possibleOutputs);
		HashSet<Integer> inputs = new HashSet<Integer>(readRegistersInLoop);
		inputs.retainAll(possibleInputs);
		
		// Compile argument set
		HashMap<Integer, Argument> dependancies = new HashMap<Integer, Argument>();
		
		for(int reg : inputs) {
			dependancies.put(reg, new Argument(typesAtStartOfLoop.get(reg), reg));
		}
		for(int reg : outputs) {
			Argument arg = dependancies.get(reg);
			
			if(arg == null) {
				arg = new Argument(typesAtEndOfLoop.get(reg), reg);
				dependancies.put(reg, arg);
			}
			
			arg.setReadonly(false);
		}
		
		// Return results
		arguments.addAll(dependancies.values());
		
		return true;
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
	public List<Block.Entry> getReplacementEntries() {
		return replacementEntries;
	}
	
	public List<Block.Entry> getFilteredEntries() {
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
	
	public List<Argument> getKernelArguments() {
		return kernelArguments;
	}
}
