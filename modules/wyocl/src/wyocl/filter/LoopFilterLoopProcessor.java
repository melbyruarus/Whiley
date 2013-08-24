package wyocl.filter;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import wybs.lang.NameID;
import wybs.util.Trie;
import wyil.lang.Block;
import wyil.lang.Code;
import wyil.lang.Constant;
import wyil.lang.Type;
import wyocl.ar.Bytecode;
import wyocl.ar.CFGNode;
import wyocl.ar.DFGGenerator;
import wyocl.ar.DFGNode;
import wyocl.ar.utils.CFGIterator;
import wyocl.ar.utils.DFGIterator;
import wyocl.ar.utils.NotADAGException;

public class LoopFilterLoopProcessor {
	private static ArrayList<Type> executeWYGPUKernelOverArrayArgumentTypes = new ArrayList<Type>() {
		private static final long serialVersionUID = 1L;

		{
			this.add(Type.T_STRING);
			this.add(Type.T_INT);
			this.add(Type.List(Type.T_ANY, false));
			this.add(Type.List(Type.T_ANY, false));
		}
	};
	private static Type.FunctionOrMethod executeWYGPUKernelOverArrayFunctionType = Type.FunctionOrMethod.Function(Type.List(Type.T_ANY, false), Type.T_VOID, executeWYGPUKernelOverArrayArgumentTypes);
	private static NameID executeWYGPUKernelOverArrayFunctionPath = new NameID(Trie.fromString("whiley/gpgpu/Util"), "executeWYGPUKernelOverArray");

	private static ArrayList<Type> executeWYGPUKernelOverRangeArgumentTypes = new ArrayList<Type>() {
		private static final long serialVersionUID = 1L;

		{
			this.add(Type.T_STRING);
			this.add(Type.T_INT);
			this.add(Type.List(Type.T_ANY, false));
			this.add(Type.T_INT);
			this.add(Type.T_INT);
		}
	};
	private static Type.FunctionOrMethod executeWYGPUKernelOverRangeFunctionType = Type.FunctionOrMethod.Function(Type.List(Type.T_ANY, false), Type.T_VOID, executeWYGPUKernelOverRangeArgumentTypes);
	private static NameID executeWYGPUKernelOverRangeFunctionPath = new NameID(Trie.fromString("whiley/gpgpu/Util"), "executeWYGPUKernelOverRange");
	
	static OpenCLKernelInvocationDescription process(CFGNode.LoopNode loopNode, String modulePath, int kernelID) {
		List<Block.Entry> replacementEntries = new ArrayList<Block.Entry>();

		// FIXME: Check source isn't modified?

		List<Argument> kernelArguments = new ArrayList<Argument>();
		determineKernelArguments(loopNode, kernelArguments);
		Collections.sort(kernelArguments);
		
		if(loopNode instanceof CFGNode.ForAllLoopNode) {
			Bytecode.ForAll loopBytecode = ((CFGNode.ForAllLoopNode)loopNode).getBytecode();

			// Remove possible duplication of the kernel argument
			Iterator<Argument> it = kernelArguments.iterator();
			while(it.hasNext()) {
				Argument arg = it.next();
				if(arg.register == loopBytecode.getSourceRegister()) {
					if(!arg.readonly) {
						// FIXME: re-add this check when read-only primitive types are supported by runtime (see ref:2123sdsds)
						//throw new RuntimeException("GPU cannot loop over an array that is being simultaneously updated");
					}
					it.remove();
				}
			}
		}

		ArrayList<Integer> argumentRegisters = new ArrayList<Integer>();
		for(Argument arg : kernelArguments) {
			argumentRegisters.add(arg.register);
		}

		// TODO: actually output marshaling and unmarshaling code here? Avoids cost of function call, wrapping/unwrapping multiple times and type tests

		final int temporaryListRegister = DFGIterator.maxUsedRegister(loopNode) + 1;
		final int temporaryCounterRegister = temporaryListRegister + 1;
		final int temporaryModuleNameRegister = temporaryCounterRegister + 1;

		replacementEntries.add(new Block.Entry(Code.NewList(Type.List(Type.T_ANY, false), temporaryListRegister, argumentRegisters)));
		replacementEntries.add(new Block.Entry(Code.Const(temporaryModuleNameRegister, Constant.V_STRING(modulePath))));
		replacementEntries.add(new Block.Entry(Code.Const(temporaryCounterRegister, Constant.V_INTEGER(BigInteger.valueOf(kernelID)))));
		ArrayList<Integer> argumentsToFunction = new ArrayList<Integer>();
		argumentsToFunction.add(temporaryModuleNameRegister);
		argumentsToFunction.add(temporaryCounterRegister);
		argumentsToFunction.add(temporaryListRegister);

		if(loopNode instanceof CFGNode.ForAllLoopNode) {
			CFGNode.ForAllLoopNode forNode = (CFGNode.ForAllLoopNode)loopNode;

			argumentsToFunction.add(forNode.getBytecode().getSourceRegister());

			replacementEntries.add(new Block.Entry(Code.Invoke(executeWYGPUKernelOverArrayFunctionType, temporaryListRegister, argumentsToFunction, executeWYGPUKernelOverArrayFunctionPath)));
		}
		else if(loopNode instanceof CFGNode.ForLoopNode) {
			CFGNode.ForLoopNode forNode = (CFGNode.ForLoopNode)loopNode;

			argumentsToFunction.add(forNode.getStartRegister());
			argumentsToFunction.add(forNode.getEndRegister());
			
			replacementEntries.add(new Block.Entry(Code.Invoke(executeWYGPUKernelOverRangeFunctionType, temporaryListRegister, argumentsToFunction, executeWYGPUKernelOverRangeFunctionPath)));
		}
		else {
			throw new InternalError("Unknown loop type encountered: "+loopNode);
		}

		int count = 0;
		for(Argument arg : kernelArguments) {
			replacementEntries.add(new Block.Entry(Code.Const(temporaryCounterRegister, Constant.V_INTEGER(BigInteger.valueOf(count)))));
			replacementEntries.add(new Block.Entry(Code.IndexOf(Type.List(arg.type, false), arg.register, temporaryListRegister, temporaryCounterRegister)));
			count++;
		}

		if(loopNode instanceof CFGNode.ForAllLoopNode) {
			CFGNode.ForAllLoopNode forNode = (CFGNode.ForAllLoopNode)loopNode;

			kernelArguments.add(0, new Argument(forNode.getBytecode().getSourceType(), forNode.getBytecode().getSourceRegister()));
		}
		
		try {
			return new OpenCLKernelInvocationDescription(loopNode, CFGIterator.createNestedRepresentation(loopNode.body), kernelArguments, replacementEntries, kernelID);
		} catch (NotADAGException e) {
			throw new InternalError("Shouldn't be getting NotADAGException here, should already have been tested");
		}
	}

	private static void determineKernelArguments(CFGNode.LoopNode loop, List<Argument> arguments) {
		final Set<DFGNode> dfgNodesInLoop = new HashSet<DFGNode>();

		Set<CFGNode> endNodes = new HashSet<CFGNode>();
		endNodes.add(loop.endNode);
		endNodes.addAll(loop.breakNodes);
		CFGIterator.iterateCFGFlow(new CFGIterator.CFGNodeCallback() {
			@Override
			public boolean process(CFGNode node) {
				node.gatherDFGNodesInto(dfgNodesInLoop);
				return true;
			}
		}, loop.body, endNodes);

		Set<DFGNode> dfgNodesProvidedToKernel = new HashSet<DFGNode>();
		dfgNodesProvidedToKernel.addAll(dfgNodesInLoop);
		loop.getIndexDFGNodes(dfgNodesProvidedToKernel);
		Set<DFGNode> source = new HashSet<DFGNode>();
		loop.getSourceDFGNodes(source);
		for(DFGNode n : source) {
			dfgNodesProvidedToKernel.addAll(n.lastModified);
		}

		Map<Integer, Type> endTypes = new HashMap<Integer, Type>();

		for(CFGNode endNode : endNodes) {
			for(Map.Entry<Integer, DFGGenerator.DFGInfo> entry : endNode.getEndRegisterInfo().writeInfo.registerMapping.entrySet()) {
				endTypes.put(entry.getKey(), DFGGenerator.mergeTypes(endTypes.get(entry.getKey()), entry.getValue().type));
			}
		}

		Map<Integer, Type> inputs = new HashMap<Integer, Type>();
		Map<Integer, Type> outputs = new HashMap<Integer, Type>();

		for(DFGNode n : dfgNodesInLoop) {
			if(!n.isAssignment) {
				for(DFGNode lastModified : n.lastModified) {
					if(!dfgNodesProvidedToKernel.contains(lastModified)) {
						inputs.put(lastModified.register, DFGGenerator.mergeTypes(lastModified.type, inputs.get(lastModified.register)));
					}
				}
			}
			for(DFGNode nextRead : n.nextRead) {
				if(!dfgNodesInLoop.contains(nextRead)) {
					outputs.put(nextRead.register, DFGGenerator.mergeTypes(nextRead.type, outputs.get(nextRead.register)));
				}
			}
		}
		
		// Compile argument set
		Map<Integer, Argument> dependancies = new HashMap<Integer, Argument>();

		for(Map.Entry<Integer, Type> entry : inputs.entrySet()) {
			int register = entry.getKey();
			Type type = entry.getValue();
			
			dependancies.put(register, new Argument(type, register));
		}
		for(Map.Entry<Integer, Type> entry : outputs.entrySet()) {
			int register = entry.getKey();
			Type type = entry.getValue();

			Argument arg = dependancies.get(register);

			if(arg == null) {
				arg = new Argument(type, register);
				dependancies.put(register, arg);
			}

			arg.setReadonly(false);
		}

		// Return results
		arguments.addAll(dependancies.values());

		// TODO: don't have to do this. Need to add support to runtime though ref:2123sdsds
		for (Argument arg : arguments) {
			arg.setReadonly(false);
		}
	}
}
