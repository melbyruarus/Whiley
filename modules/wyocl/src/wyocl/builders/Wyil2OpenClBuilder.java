// Copyright (c) 2011, David J. Pearce (djp@ecs.vuw.ac.nz)
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are met:
//    * Redistributions of source code must retain the above copyright
//      notice, this list of conditions and the following disclaimer.
//    * Redistributions in binary form must reproduce the above copyright
//      notice, this list of conditions and the following disclaimer in the
//      documentation and/or other materials provided with the distribution.
//    * Neither the name of the <organization> nor the
//      names of its contributors may be used to endorse or promote products
//      derived from this software without specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
// ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
// WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
// DISCLAIMED. IN NO EVENT SHALL DAVID J. PEARCE BE LIABLE FOR ANY
// DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
// (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
// LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
// ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
// (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
// SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

package wyocl.builders;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import wybs.lang.Builder;
import wybs.lang.Logger;
import wybs.lang.NameSpace;
import wybs.lang.Path;
import wybs.util.Pair;
import wyil.lang.Block;
import wyil.lang.Type;
import wyil.lang.WyilFile;
import wyocl.ar.Bytecode;
import wyocl.ar.CFGNode;
import wyocl.filter.LoopFilter;
import wyocl.filter.OpenCLFunctionDescription;
import wyocl.filter.OpenCLKernelInvocationDescription;
import wyocl.lang.ClFile;
import wyocl.openclwriter.OpenCLOpWriter;
import wyocl.openclwriter.OpenCLOpWriter.FunctionInvokeTranslator;
import wyocl.util.GlobalResolver;
import wyocl.util.SymbolUtilities;

public class Wyil2OpenClBuilder implements Builder {
	private static final FunctionInvokeTranslator functionInvokeTranslator = new FunctionInvokeTranslator() {
		@Override
		public String translateFunctionName(Bytecode b) {
			if(b instanceof Bytecode.Invoke) {
				return SymbolUtilities.nameMangle(((Bytecode.Invoke)b).getName().name(), ((Bytecode.Invoke)b).getType());
			} else {
				throw new InternalError("Unexpected function call bytecode: " + b);
			}
		}
	};
	
	private Logger logger = Logger.NULL;
	private GlobalResolver functionResolver;
	
	/**
	 * This Filter is used to find bodies of loops which have been determined to be parallelisable and output them to a OpenCL kernel
	 */
	private LoopFilter loopFilter;
	
	public Wyil2OpenClBuilder() {
		
	}
	
	@Override
	public NameSpace namespace() {
		return null; // does this make sense to be in builder??
	}
	
	public void setLogger(Logger logger) {
		this.logger = logger;
	}
	
	@Override
	@SuppressWarnings("unchecked")
	public void build(List<Pair<Path.Entry<?>, Path.Entry<?>>> delta) throws IOException {
		
		Runtime runtime = Runtime.getRuntime();
		long start = System.currentTimeMillis();
		long memory = runtime.freeMemory();
		
		// ========================================================================
		// write files
		// ========================================================================
		
		functionResolver = new GlobalResolver(delta);
		
		for(Pair<Path.Entry<?>, Path.Entry<?>> p : delta) {
			Path.Entry<?> cl = p.second();
			if(cl.contentType() == ClFile.ContentType) {
				Path.Entry<ClFile> df = (Path.Entry<ClFile>)cl;
				Path.Entry<WyilFile> wy = (Path.Entry<WyilFile>)p.first();
				// build the OpenCL-File
				ClFile contents = build(wy.read());
				// finally, write the file into its destination
				df.write(contents);
			} else {
				// System.err.println("Skipping .... " + f.contentType());
			}
		}
		
		// ========================================================================
		// Done
		// ========================================================================
		
		long endTime = System.currentTimeMillis();
		logger.logTimedMessage("Wyil => OpenCL: compiled " + delta.size() + " file(s)", endTime - start, memory - runtime.freeMemory());
	}
	
	protected ClFile build(WyilFile module) {
		// The forward deceleration writer
		StringWriter forwardDecWriter = new StringWriter();
		PrintWriter forwardDecPWriter = new PrintWriter(forwardDecWriter);
		
		// The function writer
		StringWriter functionWriter = new StringWriter();
		PrintWriter functionPWriter = new PrintWriter(functionWriter);
		
		// The kernel writer
		StringWriter kernelWriter = new StringWriter();
		PrintWriter kernelpWriter = new PrintWriter(kernelWriter);
		
		loopFilter = new LoopFilter(module.id(), functionResolver);
		Set<String> calledFunctions = new HashSet<String>();
		for(WyilFile.MethodDeclaration method : module.methods()) {
			build(method, calledFunctions, kernelpWriter);
		}
		for(String s : calledFunctions) {
			OpenCLFunctionDescription function = loopFilter.getFunctionDescriptions().get(s);
			if(function != null) {
				writeOpenCLFunction(function, forwardDecPWriter, functionPWriter);
			}
		}
		loopFilter = null;
		
		StringWriter writer = new StringWriter();
		PrintWriter pWriter = new PrintWriter(writer);
		pWriter.println("// Automatically generated from " + module.filename());
		OpenCLOpWriter.writeRuntime(pWriter);
		pWriter.println(forwardDecWriter.toString());
		pWriter.print(functionWriter.toString());
		pWriter.println(kernelWriter.toString());
		
		return new ClFile(writer.toString());
	}
	
	protected void build(WyilFile.MethodDeclaration method, Set<String> calledFunctions, PrintWriter kernelpWriter) {
		loopFilter.beginMethod(method);
		for(WyilFile.Case c : method.cases()) {
			write(c, method, calledFunctions, kernelpWriter);
		}
		loopFilter.endMethod();
	}
	
	protected void write(WyilFile.Case c, WyilFile.MethodDeclaration method, Set<String> calledFunctions, PrintWriter kernelpWriter) {
		write(c.body(), c, method, calledFunctions, kernelpWriter);
	}
	
	protected void write(Block b, WyilFile.Case c, WyilFile.MethodDeclaration method, Set<String> calledFunctions, PrintWriter kernelpWriter) {
		Map<CFGNode.LoopNode, OpenCLKernelInvocationDescription> kernels = new HashMap<CFGNode.LoopNode, OpenCLKernelInvocationDescription>();
		loopFilter.processBlock(b, kernels, calledFunctions);
		for(OpenCLKernelInvocationDescription o : kernels.values()) {
			writeOpenCLKernel(o, kernelpWriter);
		}
	}
	
	private void writeOpenCLKernel(OpenCLKernelInvocationDescription kernelDescription, PrintWriter kernelpWriter) {
		OpenCLOpWriter kernelOpWriter = new OpenCLOpWriter(functionInvokeTranslator);
		
		kernelOpWriter.writeFunctionDecleration("__kernel", Type.T_VOID, "whiley_gpgpu_func_" + kernelDescription.id, kernelDescription.kernelArguments, kernelpWriter);
		
		kernelpWriter.print(" {\n");
		kernelOpWriter.writeLoopBodyAsKernel(kernelDescription.loopNode, kernelDescription.loopBody, kernelpWriter);
		kernelpWriter.println("}\n");
	}
	
	private void writeOpenCLFunction(OpenCLFunctionDescription function, PrintWriter forwardDecPWriter, PrintWriter functionPWriter) {
		OpenCLOpWriter functionOpWriter = new OpenCLOpWriter(functionInvokeTranslator);
		OpenCLOpWriter functionDeclWriter = new OpenCLOpWriter(functionInvokeTranslator);
		
		functionDeclWriter.writeFunctionDecleration(null, (Type.Leaf)function.getReturnType(), function.getName(), function.getParams(), forwardDecPWriter);
		forwardDecPWriter.println(';');
		
		functionOpWriter.writeFunctionDecleration(null, (Type.Leaf)function.getReturnType(), function.getName(), function.getParams(), functionPWriter);
		
		functionPWriter.print(" {\n");
		functionOpWriter.writeFunctionBody(function.getEntries(), functionPWriter);
		functionPWriter.println("}\n");
	}
}
