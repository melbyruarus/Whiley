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
import java.util.List;

import wybs.lang.Builder;
import wybs.lang.Logger;
import wybs.lang.NameSpace;
import wybs.lang.Path;
import wybs.util.Pair;
import wyil.lang.Block;
import wyil.lang.Code;
import wyil.lang.Type;
import wyil.lang.Type.Int;
import wyil.lang.Type.Real;
import wyil.lang.WyilFile;
import wyil.lang.Block.Entry;
import wyocl.filter.LoopFilter;
import wyocl.lang.ClFile;

public class Wyil2OpenClBuilder implements Builder {
	
	private Logger logger = Logger.NULL;
	
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
		
	public void build(List<Pair<Path.Entry<?>,Path.Entry<?>>> delta) throws IOException {

		Runtime runtime = Runtime.getRuntime();
		long start = System.currentTimeMillis();
		long memory = runtime.freeMemory();

		// ========================================================================
		// write files
		// ========================================================================

		for(Pair<Path.Entry<?>,Path.Entry<?>> p : delta) {
			Path.Entry<?> f = p.second();
			if(f.contentType() == ClFile.ContentType) {
				//System.err.println("Processing .... ");
				Path.Entry<WyilFile> sf = (Path.Entry<WyilFile>) p.first();
				Path.Entry<ClFile> df = (Path.Entry<ClFile>) f;
				// build the C-File
				ClFile contents = build(sf.read());								
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
		StringWriter writer = new StringWriter();
		PrintWriter pWriter = new PrintWriter(writer);
		pWriter.println("// Automatically generated from " + module.filename());
		for(WyilFile.MethodDeclaration method : module.methods()) {				
			build(method,pWriter);			
		}
		loopFilter = null;
		return new ClFile(writer.toString());
	}
	
	protected void build(WyilFile.MethodDeclaration method, PrintWriter writer) {
		for(WyilFile.Case c : method.cases()) {
			write(c,method,writer);
		}
	}
		
	protected void write(WyilFile.Case c, WyilFile.MethodDeclaration method, PrintWriter writer) {
		write(c.body(),c,method,writer);
	}
	
	protected void write(Block b, WyilFile.Case c, WyilFile.MethodDeclaration method, PrintWriter writer) {
		loopFilter.beginBlock(b);
		for(Block.Entry e : b) {
			write(e,c,method,writer);
		}
		loopFilter.endBlock();
	}
	
	protected static int kid = 0;
	
	protected void write(Block.Entry entry, WyilFile.Case c, WyilFile.MethodDeclaration method, PrintWriter writer) {
		Code code = entry.code;
		
		switch(loopFilter.filter(entry)) {
			case SKIP:
			case DEFAULT:
				break;
			case FILTER_RESULTS_READY:
				if(loopFilter.wasLoopFiltered()) {
					writeOpenCLKernel(loopFilter.getFilteredEntries(), loopFilter.getKernelArguments(), writer);
				}
				break;
		}
	}

	private void writeOpenCLKernel(List<Entry> filteredEntries, List<LoopFilter.Argument> kernelArguments, PrintWriter writer) {
		// Eventually this wont be string, it'll be something else
		HashMap<Integer, String> arguments = new HashMap<Integer, String>();
		
		writer.print("__kernel void whiley_gpgpu_func_"+kid+"(");
		kid = kid + 1;
		
		String seperator = "";
		int nextArgumentNumber = 0;
		for(LoopFilter.Argument arg : kernelArguments) {
			writer.print(seperator);
			seperator = ", ";
			
			writeKernelArgument(arg, arguments, nextArgumentNumber, writer);
			nextArgumentNumber++;
		}
		
		writer.print(") {\n");
		
		writer.println("	int whiley_gpgpu_job_id = get_global_id(0);\n" +
"	int whiley_gpgpu_arg_0_size = whiley_gpgpu_arg_0?((int *)whiley_gpgpu_arg_0)[0]:0;\n" +
"	whiley_gpgpu_arg_0 = (__global int *)(((int *)whiley_gpgpu_arg_0) + 1);\n" +
"\n" +
"	*whiley_gpgpu_arg_1 += whiley_gpgpu_arg_0[whiley_gpgpu_job_id];\n"+
"	whiley_gpgpu_arg_0[whiley_gpgpu_job_id] = *whiley_gpgpu_arg_1;\n");
		
		writer.println("}");
	}

	private void writeKernelArgument(LoopFilter.Argument arg, HashMap<Integer, String> arguments, int nextArgumentNumber, PrintWriter writer) {
		if(arg.type instanceof Type.List) {
			writeKernelArgument((Type.List)arg.type, arg.isReadonly(), arguments, nextArgumentNumber, writer);
		}
		else if(arg.type instanceof Type.Leaf) {
			writeKernelArgument((Type.Leaf)arg.type, arg.isReadonly(), arguments, nextArgumentNumber, writer);
		}
		else {
			throw new RuntimeException("Unknown type encountered when constructing kernel arguments: "+arg.type);
		}
	}
	
	private String convertPrimitive(Type.Leaf type) {
		if(type instanceof Type.Int) {
			return "int";
		}
		else if(type instanceof Type.Real) {
			return "float";
		}
		else {
			throw new RuntimeException("Unknown primitive type encountered: "+type);
		}
	}

	private void writeKernelArgument(Type.Leaf type, boolean readonly, HashMap<Integer, String> arguments, int nextArgumentNumber, PrintWriter writer) {
		if(!readonly) {
			writer.print("__global ");
		}
		writer.print(convertPrimitive(type));
		writer.print(' ');
		if(!readonly) {
			writer.print('*');
		}
		writer.print("whiley_gpgpu_arg_");
		writer.print(nextArgumentNumber);
	}

	private void writeKernelArgument(Type.List type, boolean readonly, HashMap<Integer, String> arguments, int nextArgumentNumber, PrintWriter writer) {
		writer.print("__global ");
		if(!(type.element() instanceof Type.Leaf)) {
			throw new RuntimeException("Currently only support lists of primitive types, don't support: "+type);
		}
		writer.print(convertPrimitive((Type.Leaf)type.element()));
		writer.print(" *whiley_gpgpu_arg_");
		writer.print(nextArgumentNumber);
	}
}
