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

package wyil.io;

import java.io.*;
import java.util.*;

import wybs.lang.Attribute;
import wybs.lang.Builder;
import wybs.lang.Path;
import wybs.lang.Transform;
import wyil.lang.*;
import wyil.lang.WyilFile.*;

/**
 * Writes WYIL bytecodes in a textual from to a given file.
 * 
 * <b>NOTE:</b> currently, this class is somewhat broken since it does not
 * provide any way to specify the output directory. Rather, it simply puts the
 * WYIL file in the same place as the Whiley file.
 * 
 * @author David J. Pearce
 * 
 */
public final class WyilFilePrinter implements Transform<WyilFile> {
	private PrintWriter out;
	private boolean writeLabels = getLabels();
	private boolean writeAttributes = getAttributes();
	private boolean writeSlots = getSlots();
		
	public WyilFilePrinter(wybs.lang.Builder builder) {

	}
	
	public WyilFilePrinter(PrintWriter writer) {
		this.out = writer;
	}
	
	public WyilFilePrinter(OutputStream stream) {
		this.out = new PrintWriter(new OutputStreamWriter(stream));
	}
	
	public void setLabels(boolean flag) {
		writeLabels = flag;
	}
	
	public static boolean getLabels() {
		return false;
	}
	
	public static String describeLabels() {
		return "Include all labels in output";
	}
	
	public void setAttributes(boolean flag) {
		writeAttributes = flag;
	}
	
	public static boolean getAttributes() {
		return false;
	}
	
	public static String describeAttributes() {
		return "Include bytecode attributes in output";
	}
	
	public void setSlots(boolean flag) {
		writeSlots = flag;
	}
	
	public static boolean getSlots() {
		return false;
	}
	
	public static String describeSlots() {
		return "Include raw slow numbers in output";
	}
	
	public void apply(WyilFile module) throws IOException {
		
		if(out == null) {
			
			// TODO: where does this go?
			
			String filename = module.filename().replace(".whiley", ".wyasm");
			out = new PrintWriter(new FileOutputStream(filename));
		}
	
		//out.println("module: " + module.id());
		out.println("source-file: " + module.filename());
		out.println();
		for(ConstantDeclaration cd : module.constants()) {
			writeModifiers(cd.modifiers(),out);
			out.println("constant " + cd.name() + " = " + cd.constant());
		}
		if(!module.constants().isEmpty()) {
			out.println();
		}
		for(TypeDeclaration td : module.types()) {
			Type t = td.type();			
			String t_str;			
			t_str = t.toString();
			writeModifiers(td.modifiers(),out);
			out.println("type " + td.name() + " : " + t_str);
			Block constraint = td.constraint();
			if(constraint != null) {
				out.println("where:");				
				write(0,td.constraint(),out);
			}
			out.println();
		}

		for(MethodDeclaration md : module.methods()) {
			write(md,out);
			out.println();
		}
		out.flush();		
	}
	
	private void write(MethodDeclaration method, PrintWriter out) {
		for (Case c : method.cases()) {
			write(c, method, out);
		}
	}
	
	private void write(Case mcase, MethodDeclaration method, PrintWriter out) {
		writeModifiers(method.modifiers(),out);
		Type.FunctionOrMethod ft = method.type(); 
		out.print(ft.ret() + " ");
		List<Type> pts = ft.params();
		
		int li = 0;
		
		out.print(method.name() + "(");
		for(int i=0;i!=ft.params().size();++i) {						
			if(i!=0) {
				out.print(", ");
			}			
			out.print(pts.get(i));			
		}
		out.println("):");							

		Block precondition = mcase.precondition();
		if(precondition != null) {			
			out.println("requires: ");			
			write(0,precondition,out);
		}
		
		Block postcondition = mcase.postcondition();
		if(postcondition != null) {
			out.println("ensures: ");							
			write(0,postcondition,out);
		}
		out.println("body: ");
		boolean firstTime=true;		
			
		write(0,mcase.body(),out);	
	}
	
	private void write(int indent, Block blk, PrintWriter out) {
		for(Block.Entry s : blk) {
			if(s.code instanceof Code.LoopEnd) {				
				--indent;
			} else if(s.code instanceof Code.Label) { 
				write(indent-1,s.code,s.attributes(),out);
			} else {
				write(indent,s.code,s.attributes(),out);
			}
			if(s.code instanceof Code.Loop) {
				Code.Loop loop = (Code.Loop) s.code; 
				indent++;								
			} else if(s.code instanceof Code.Loop) {
				indent++;
			}
		}
	}
	
	private void write(int indent, Code c, List<Attribute> attributes, PrintWriter out) {		
		String line = "null";		
		tabIndent(indent+1,out);
	
		// First, write out code	
		if(c instanceof Code.LoopEnd) {
			Code.LoopEnd cend = (Code.LoopEnd)c;
			if(writeLabels) {
				line = "end " + cend.label;
			} else {
				line = "end";
			}
		} else {
			line = c.toString();		
		}
		
		// Second, write attributes				
		while(line.length() < 40) {
			line += " ";
		}
		out.print(line);
		if (writeAttributes && attributes.size() > 0) {
			out.print(" # ");
			boolean firstTime = true;
			for (Attribute a : attributes) {
				if (!firstTime) {
					out.print(", ");
				}
				firstTime = false;
				out.print(a);
			}
		}
		out.println();
	}
	
	private static void writeModifiers(List<Modifier> modifiers, PrintWriter out) {		
		for(Modifier m : modifiers) {						
			out.print(m.toString());
			out.print(" ");
		}
	}
	
	private static String getLocal(int index, List<String> locals) {
		if(index < locals.size()) {
			// is a named local
			return locals.get(index);
		} else {
			return "%" + (index - locals.size());
		}
	}
	
	private static void tabIndent(int indent, PrintWriter out) {
		indent = indent * 4;
		for(int i=0;i<indent;++i) {
			out.print(" ");
		}
	}
}
