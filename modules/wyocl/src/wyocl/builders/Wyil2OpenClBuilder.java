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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import wybs.lang.Builder;
import wybs.lang.Logger;
import wybs.lang.NameSpace;
import wybs.lang.Path;
import wybs.util.Pair;
import wyil.lang.Block;
import wyil.lang.Code;
import wyil.lang.Type;
import wyil.lang.WyilFile;
import wyocl.ar.ARGenerator;
import wyocl.filter.Argument;
import wyocl.filter.LoopFilter;
import wyocl.lang.ClFile;
import wyocl.openclwriter.NotSupportedByGPGPUException;
import wyocl.openclwriter.OpenCLOpWriter;
import wyocl.util.SymbolUtilities;

public class Wyil2OpenClBuilder implements Builder {
	private Logger logger = Logger.NULL;
	private HashMap<Path.ID, WyilFile> wyilFiles = new HashMap<Path.ID, WyilFile>();
	
	/**
	 * This Filter is used to find bodies of loops which have been determined to
	 * be parallelisable and output them to a OpenCL kernel
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
		
	@SuppressWarnings("unchecked")
	public void build(List<Pair<Path.Entry<?>,Path.Entry<?>>> delta) throws IOException {

		Runtime runtime = Runtime.getRuntime();
		long start = System.currentTimeMillis();
		long memory = runtime.freeMemory();

		// ========================================================================
		// write files
		// ========================================================================

		for(Pair<Path.Entry<?>,Path.Entry<?>> p : delta) {
			Path.Entry<?> cl = p.second();
			if(cl.contentType() == ClFile.ContentType) {
				Path.Entry<WyilFile> wy = (Path.Entry<WyilFile>) p.first();
				wyilFiles.put(wy.id(), wy.read());
			}
		}
		
		for(Pair<Path.Entry<?>,Path.Entry<?>> p : delta) {
			Path.Entry<?> cl = p.second();
			if(cl.contentType() == ClFile.ContentType) {
				Path.Entry<ClFile> df = (Path.Entry<ClFile>) cl;
				Path.Entry<WyilFile> wy = (Path.Entry<WyilFile>) p.first();
				// build the OpenCL-File
				ClFile contents = build(wy.read());								
				// finally, write the file into its destination
				df.write(contents);
			} else {
				//System.err.println("Skipping .... " + f.contentType());
			}
		}

		// ========================================================================
		// Done
		// ========================================================================

		long endTime = System.currentTimeMillis();
		logger.logTimedMessage("Wyil => OpenCL: compiled " + delta.size() + " file(s)", endTime - start, memory - runtime.freeMemory());
	}	
	
	protected ClFile build(WyilFile module) {
		loopFilter = new LoopFilter(module.id());
		// The forward deceleration writer
		StringWriter forwardDecWriter = new StringWriter();
		PrintWriter forwardDecpWriter = new PrintWriter(forwardDecWriter);
		HashSet<String> declaredMethods = new HashSet<String>();
		
		// The invoked function writer
		HashSet<String> invokedFunctions = new HashSet<String>();
		
		// The kernel writer
		StringWriter kernelWriter = new StringWriter();
		PrintWriter kernelpWriter = new PrintWriter(kernelWriter);
		
		for(WyilFile.MethodDeclaration method : module.methods()) {				
			build(method, declaredMethods, forwardDecpWriter, invokedFunctions, kernelpWriter);			
		}
		loopFilter = null;
		
		StringWriter writer = new StringWriter();
		PrintWriter pWriter = new PrintWriter(writer);
		pWriter.println("// Automatically generated from " + module.filename());
		OpenCLOpWriter.writeRuntime(pWriter);
		pWriter.println(forwardDecWriter.toString());
		for(String s : invokedFunctions) {
			pWriter.println(s);
		}
		pWriter.println(kernelWriter.toString());
		
		return new ClFile(writer.toString());
	}
	
	protected void build(WyilFile.MethodDeclaration method, HashSet<String> declaredMethods, PrintWriter forwardDecpWriter, HashSet<String> invokedFunctions, PrintWriter kernelpWriter) {
		for(WyilFile.Case c : method.cases()) {
			write(c, method, declaredMethods, forwardDecpWriter, invokedFunctions, kernelpWriter);
		}
	}
		
	protected void write(WyilFile.Case c, WyilFile.MethodDeclaration method, HashSet<String> declaredMethods, PrintWriter forwardDecpWriter, HashSet<String> invokedFunctions, PrintWriter kernelpWriter) {
		write(c.body(), c, method, declaredMethods, forwardDecpWriter, invokedFunctions, kernelpWriter);
	}
	
	protected void write(Block b, WyilFile.Case c, WyilFile.MethodDeclaration method, HashSet<String> declaredMethods, PrintWriter forwardDecpWriter, HashSet<String> invokedFunctions, PrintWriter kernelpWriter) {
		loopFilter.beginBlock(b);
		for(Block.Entry e : b) {
			write(e, c, method, declaredMethods, forwardDecpWriter, invokedFunctions, kernelpWriter);
		}
		loopFilter.endBlock();
	}
	
	protected void write(Block.Entry entry, WyilFile.Case c, WyilFile.MethodDeclaration method, HashSet<String> declaredMethods, PrintWriter forwardDecpWriter, HashSet<String> invokedFunctions, PrintWriter kernelpWriter) {		
		switch(loopFilter.filter(entry)) {
			case SKIP:
			case DEFAULT:
				break;
			case FILTER_RESULTS_READY:
				if(loopFilter.wasLoopFiltered()) {
					Set<ARGenerator.ReturnNode> exits = new HashSet<ARGenerator.ReturnNode>();
					ARGenerator.CFGNode root = ARGenerator.processEntries(loopFilter.getFilteredEntries(), exits);
					writeOpenCLKernel(loopFilter.getFilteredEntries(), loopFilter.getKernelArguments(), declaredMethods, forwardDecpWriter, invokedFunctions, kernelpWriter);
				}
				break;
		}
	}

	protected static int kid = 0;
	private void writeOpenCLKernel(List<Block.Entry> filteredEntries, List<Argument> kernelArguments, final HashSet<String> declaredMethods, final PrintWriter forwardDecpWriter, final HashSet<String> invokedFunctions, PrintWriter kernelpWriter) {
		final OpenCLOpWriter invokedFunctionDeclerationOpWriter[] = new OpenCLOpWriter[1];
		final HashSet<String> writingMethods = new HashSet<String>();
		
		final OpenCLOpWriter.FunctionInvokeTranslator functionTranslator[] = new OpenCLOpWriter.FunctionInvokeTranslator[1]; 
		functionTranslator[0] = new OpenCLOpWriter.FunctionInvokeTranslator() {
			@Override
			public String translateFunctionName(Code.Invoke code) {
				if(code.type instanceof Type.Function) {
					String name = SymbolUtilities.nameMangle(code.name.name(), code.type);
					
					if(!declaredMethods.contains(name)) {
						if(writingMethods.contains(name)) {
							throw new NotSupportedByGPGPUException("Recursion not supported");
						}
						writingMethods.add(name);
						
						ArrayList<Argument> functionArguments = new ArrayList<Argument>();
						int reg = 0;
						for(Type t : code.type.params()) {
							functionArguments.add(new Argument(t, reg));
							reg++;
						}
						
						// FIXME: no checks for return type type
						invokedFunctionDeclerationOpWriter[0].writeFunctionDecleration(null, (Type.Leaf)code.type.ret(), name, functionArguments, forwardDecpWriter);
						forwardDecpWriter.println(';');
						
						StringWriter invokedFunctionWriter = new StringWriter();
						PrintWriter invokedFunctionpWriter = new PrintWriter(invokedFunctionWriter);
						OpenCLOpWriter invokedFunctionOpWriter = new OpenCLOpWriter(functionTranslator[0]);
						
						invokedFunctionOpWriter.writeFunctionDecleration(null, (Type.Leaf)code.type.ret(), name, functionArguments, invokedFunctionpWriter);
						invokedFunctionpWriter.print(" {\n");
						
						WyilFile module = wyilFiles.get(code.name.module());
						if(module == null) {
							throw new RuntimeException("Unable to find module: "+code.name.module());
						}
						Block block = null;
						for(WyilFile.MethodDeclaration m : module.method(code.name.name())) {
							if(name.equals(SymbolUtilities.nameMangle(m.name(), m.type()))) {
								// FIXME: what are the multiple cases?
								block = m.cases().get(0).body();
								break;
							}
						}
						
						if(block == null) {
							throw new RuntimeException("Unable to find method: "+code.name.name());
						}
						
						ArrayList<Block.Entry> entries = new ArrayList<Block.Entry>();
						for(Block.Entry e : block) {
							entries.add(e);
						}
						
						invokedFunctionOpWriter.writeBlock(entries, invokedFunctionpWriter);
						invokedFunctionpWriter.println("}");
						
						invokedFunctions.add(invokedFunctionWriter.toString());
						
						declaredMethods.add(name);
						writingMethods.remove(name);
					}
					
					return name;
				}
				else {
					throw new NotSupportedByGPGPUException();
				}
			}
		};
		
		invokedFunctionDeclerationOpWriter[0] = new OpenCLOpWriter(functionTranslator[0]);
		OpenCLOpWriter kernelOpWriter = new OpenCLOpWriter(functionTranslator[0]);
		
		kernelOpWriter.writeFunctionDecleration("__kernel", Type.T_VOID, "whiley_gpgpu_func_"+kid, kernelArguments, kernelpWriter);
		
		kernelpWriter.print(" {\n");
		kernelOpWriter.writeBlock(filteredEntries, kernelpWriter);
		kernelpWriter.println("}");
	}
}
