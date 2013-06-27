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

package wyrl.io;

import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Writer;
import java.math.BigInteger;
import java.util.*;

import wyautl.core.Automaton;
import wyautl.io.BinaryAutomataReader;
import wyautl.util.BigRational;
import wybs.io.BinaryInputStream;
import wyrl.core.Attribute;
import wyrl.core.Expr;
import wyrl.core.Pattern;
import wyrl.core.SpecFile;
import wyrl.core.Type;
import wyrl.core.Types;
import wyrl.util.*;
import static wyrl.core.Attribute.*;
import static wyrl.core.SpecFile.*;

public class JavaFileWriter {
	private PrintWriter out;
	private HashSet<Integer> typeTests = new HashSet<Integer>();
	
	public JavaFileWriter(Writer os) {
		this.out = new PrintWriter(os);
	}

	public JavaFileWriter(OutputStream os) {
		this.out = new PrintWriter(os);
	}

	public void write(SpecFile spec) throws IOException {			
		reset();
		translate(spec,spec);		
	}
	
	private void translate(SpecFile spec, SpecFile root) throws IOException {						
		PrintWriter saved = out;		
		
		if(root == spec) {			

			if (!spec.pkg.equals("")) {
				myOut("package " + spec.pkg + ";");
				myOut("");
			}
			
			writeImports();
			myOut("public final class " + spec.name + " {");
			
		}
		
		for (Decl d : spec.declarations) {
			if(d instanceof IncludeDecl) {
				IncludeDecl id = (IncludeDecl) d;
				SpecFile file = id.file;
				translate(file,root);				
			} else  if (d instanceof TermDecl) {
				translate((TermDecl) d);
			} else if (d instanceof RewriteDecl) {
				translate((RewriteDecl) d,root);
			}
		}
		
		if(root == spec) {
			writeReduceDispatch(spec);
			writeInferenceDispatch(spec);
			writeTypeTests();
			writeSchema(spec);
			writeStatsInfo();
			writeMainMethod();
		}
		
		if(root == spec) {			
			myOut("}");
			out.close();
		}
		
		out = saved;
	}

	/**
	 * Reset all global information before proceeding to write out another file.
	 */
	protected void reset() {
		termCounter = 0;
		typeTests.clear();
		typeRegister.clear();
		registeredTypes.clear();
	}
	
	protected void writeImports() {
		myOut("import java.io.*;");
		myOut("import java.util.*;");
		myOut("import java.math.BigInteger;");
		myOut("import wyautl.util.BigRational;");
		myOut("import wyautl.io.*;");
		myOut("import wyautl.core.*;");		
		myOut("import wyrl.io.*;");
		myOut("import wyrl.core.*;");
		myOut("import wyrl.util.Runtime;");
		myOut("import static wyrl.util.Runtime.*;");
		myOut();
	}
	
	public void writeReduceDispatch(SpecFile sf) {
		myOut(1, "public static boolean reduce(Automaton automaton, int start) {");
		myOut(2, "boolean result = false;");
		myOut(2, "boolean changed = true;");
		myOut(2, "int[] tmp = new int[automaton.nStates()*2];");
		myOut(2, "while(changed) {");		
		myOut(3, "changed = false;");		
		myOut(3, "for(int i=start;i<automaton.nStates();++i) {");
		myOut(4, "if(numSteps++ > MAX_STEPS) { return result; } // bail out");
		myOut(4, "if(automaton.get(i) == null) { continue; }");
		int i=0;
		for(ReduceDecl rw : extractDecls(ReduceDecl.class,sf)) {
			Type type = rw.pattern.attribute(Attribute.Type.class).type;
			String mangle = toTypeMangle(type);
			myOut(4,"");
			myOut(4, "if(typeof_" + mangle + "(i,automaton)) {");
			myOut(5, "changed |= reduce_" + mangle + "(i,automaton);");			
			typeTests.add(register(type));
			myOut(5, "if(changed) { break; } // reset");
			myOut(4, "}");
		}
		myOut(3,"}");
		
		myOut(3, "if(changed) {");
		myOut(4, "tmp = Automata.eliminateUnreachableStates(automaton,start,automaton.nStates(),tmp);");
		myOut(4, "result = true;");
		myOut(3, "}");
		myOut(2,"}");
		myOut(2, "automaton.compact(); // restore invariant");
		myOut(2, "return result;");
		myOut(1, "}");
	}

	public void writeInferenceDispatch(SpecFile sf) {
		myOut(1, "public static boolean infer(Automaton automaton) {");
		myOut(2, "reset();");
		myOut(2, "boolean result = false;");
		myOut(2, "boolean changed = true;");
		myOut(2, "automaton.minimise(); // base case for invariant");
		myOut(2, "automaton.compact();");
		myOut(2, "reduce(automaton,0);");
		myOut(2, "while(changed) {");
		myOut(3, "changed = false;");
		myOut(3, "for(int i=0;i<automaton.nStates();++i) {");
		myOut(4, "if(numSteps > MAX_STEPS) { return result; } // bail out");
		myOut(4, "if(automaton.get(i) == null) { continue; }");
		int i = 0;
		for(InferDecl rw : extractDecls(InferDecl.class,sf)) {
			Type type = rw.pattern.attribute(Attribute.Type.class).type;
			String mangle = toTypeMangle(type);
			myOut(4,"");
			myOut(4, "if(typeof_" + mangle + "(i,automaton) &&");
			myOut(5, "infer_" + mangle + "(i,automaton)) {");
			typeTests.add(register(type));
			myOut(5, "changed = true; break; // reset");			
			myOut(4, "}");
		}
		myOut(3,"}");
		myOut(3, "result |= changed;");
		myOut(2,"}");
		myOut(2, "return result;");
		myOut(1, "}");		
	}
	
	public void translate(TermDecl decl) {
		myOut(1, "// term " + decl.type);
		String name = decl.type.name();
		myOut(1, "public final static int K_" + name + " = "
				+ termCounter++ + ";");
		if (decl.type.element() == null) {
			myOut(1, "public final static Automaton.Term " + name
					+ " = new Automaton.Term(K_" + name + ");");
		} else {
			Type.Ref data = decl.type.element();
			Type element = data.element();
			if(element instanceof Type.Collection) {
				// add two helpers
				myOut(1, "public final static int " + name
						+ "(Automaton automaton, int... r0) {" );
				if(element instanceof Type.Set) { 
					myOut(2,"int r1 = automaton.add(new Automaton.Set(r0));");
				} else if(element instanceof Type.Bag) {
					myOut(2,"int r1 = automaton.add(new Automaton.Bag(r0));");
				} else {
					myOut(2,"int r1 = automaton.add(new Automaton.List(r0));");
				}
				myOut(2,"return automaton.add(new Automaton.Term(K_" + name + ", r1));");
				myOut(1,"}");
				
				myOut(1, "public final static int " + name
						+ "(Automaton automaton, List<Integer> r0) {" );
				if(element instanceof Type.Set) { 
					myOut(2,"int r1 = automaton.add(new Automaton.Set(r0));");
				} else if(element instanceof Type.Bag) {
					myOut(2,"int r1 = automaton.add(new Automaton.Bag(r0));");
				} else {
					myOut(2,"int r1 = automaton.add(new Automaton.List(r0));");
				}
				myOut(2,"return automaton.add(new Automaton.Term(K_" + name + ", r1));");
				myOut(1,"}");
			} else if(element instanceof Type.Int) {
				// add two helpers
				myOut(1, "public final static int " + name 
						+ "(Automaton automaton, long r0) {" );			
				myOut(2,"int r1 = automaton.add(new Automaton.Int(r0));");
				myOut(2,"return automaton.add(new Automaton.Term(K_" + name + ", r1));");
				myOut(1,"}");
				
				myOut(1, "public final static int " + name
						+ "(Automaton automaton, BigInteger r0) {" );	
				myOut(2,"int r1 = automaton.add(new Automaton.Int(r0));");
				myOut(2,"return automaton.add(new Automaton.Term(K_" + name + ", r1));");
				myOut(1,"}");
			} else if(element instanceof Type.Real) {
				// add two helpers
				myOut(1, "public final static int " + name 
						+ "(Automaton automaton, long r0) {" );			
				myOut(2,"int r1 = automaton.add(new Automaton.Real(r0));");
				myOut(2,"return automaton.add(new Automaton.Term(K_" + name + ", r1));");
				myOut(1,"}");
				
				myOut(1, "public final static int " + name
						+ "(Automaton automaton, BigRational r0) {" );	
				myOut(2,"int r1 = automaton.add(new Automaton.Real(r0));");
				myOut(2,"return automaton.add(new Automaton.Term(K_" + name + ", r1));");
				myOut(1,"}");
			} else if(element instanceof Type.Strung) {
				// add two helpers
				myOut(1, "public final static int " + name
						+ "(Automaton automaton, String r0) {" );	
				myOut(2,"int r1 = automaton.add(new Automaton.Strung(r0));");
				myOut(2,"return automaton.add(new Automaton.Term(K_" + name + ", r1));");
				myOut(1,"}");
			} else {
				myOut(1, "public final static int " + name
						+ "(Automaton automaton, " + type2JavaType(data) + " r0) {" );			
				myOut(2,"return automaton.add(new Automaton.Term(K_" + name + ", r0));");
				myOut(1,"}");
			}
			
		}
		myOut();
	}

	private int termCounter = 0;

	public void translate(RewriteDecl decl, SpecFile file) {
		boolean isReduction = decl instanceof ReduceDecl;
		Pattern.Term pattern = decl.pattern;
		Type param = pattern.attribute(Attribute.Type.class).type; 
		myOut(1, "// " + decl.pattern);
		
		
		if(decl instanceof ReduceDecl) {
			
			// NOTE: there is a possible very slight bug here, because junk
			// states can be added during a rewrite rule --- even if that
			// rewrite rule does not apply. Such junk states could then cause
			// rewrites themselves, leading to some kind of loop (or
			// inefficiency). This could be addressed by observing what the
			// number of states in the automaton is at this point, and then
			// simply "slicing" off any temporary states which were added. This
			// might equally apply to the inference rules as well.
			
			String sig = toTypeMangle(param) + "(" + type2JavaType(param) + " r0, Automaton automaton) {";
			myOut(1, "public static boolean reduce_" + sig);					
		} else {
			String sig = toTypeMangle(param) + "(" + type2JavaType(param) + " r0, Automaton original) {";
			myOut(1, "public static boolean infer_" + sig);					
			myOut(2, "int start = original.nStates();");
			myOut(2, "Automaton automaton = new Automaton(original);");
		}		
		
		// setup the environment
		Environment environment = new Environment();
		int thus = environment.allocate(param,"this");
		
		// translate pattern
		int level = translate(2,pattern,thus,environment);
		
		// translate expressions
		myOut(1);		

		for(RuleDecl rd : decl.rules) {
			translate(level,rd,isReduction,environment,file);
		}
		
		// close the pattern match
		while(level > 2) {
			myOut(--level,"}");
		}
				
		myOut(level,"return false;");
		myOut(--level,"}");
		myOut();
	}
	
	public int translate(int level, Pattern p, int source, Environment environment) {
		if(p instanceof Pattern.Leaf) {
			return translate(level,(Pattern.Leaf) p,source,environment);
		} else if(p instanceof Pattern.Term) {
			return translate(level,(Pattern.Term) p,source,environment);
		} else if(p instanceof Pattern.Set) {
			return translate(level,(Pattern.Set) p,source,environment);
		} else if(p instanceof Pattern.Bag) {
			return translate(level,(Pattern.Bag) p,source,environment);
		} else  {
			return translate(level,(Pattern.List) p,source,environment);
		} 
	}
	
	public int translate(int level, Pattern.Leaf p, int source, Environment environment) {
		// do nothing?
		return level;
	}
	
	public int translate(int level, Pattern.Term pattern, int source,
			Environment environment) {
		Type.Ref<Type.Term> type = (Type.Ref) pattern
				.attribute(Attribute.Type.class).type;
		source = coerceFromRef(level, pattern, source, environment);
		if (pattern.data != null) {
			Type data = type.element().element();
			int target = environment.allocate(data, pattern.variable);
			myOut(level, type2JavaType(data) + " r" + target + " = r" + source
					+ ".contents;");
			return translate(level, pattern.data, target, environment);
		} else {
			return level;
		}
	}

	public int translate(int level, Pattern.BagOrSet pattern, int source, Environment environment) {
		Type.Ref<Type.Collection> type = (Type.Ref<Type.Collection>) pattern
				.attribute(Attribute.Type.class).type;
		source = coerceFromRef(level, pattern, source, environment);
		
		Pair<Pattern, String>[] elements = pattern.elements;
		
		// construct a for-loop for each fixed element to match
		int[] indices = new int[elements.length];
		for (int i = 0; i != elements.length; ++i) {
			boolean isUnbounded = pattern.unbounded && (i+1) == elements.length;
			Pair<Pattern, String> p = elements[i];
			Pattern pat = p.first();
			String var = p.second();
			Type.Ref pt = (Type.Ref) pat.attribute(Attribute.Type.class).type;			
			int index = environment.allocate(pt,var);
			String name = "i" + index;
			indices[i] = index;
			if(isUnbounded) {
				Type.Collection rt = pattern instanceof Pattern.Bag ? Type.T_BAG(true,pt) : Type.T_SET(true,pt);
				myOut(level, "int j" + index + " = 0;");
				myOut(level, "int[] t" + index + " = new int[r" + source + ".size()-" + i + "];");				
			}
			myOut(level++,"for(int " + name + "=0;" + name + "!=r" + source + ".size();++" + name + ") {");
			myOut(level, type2JavaType(pt) + " r" + index + " = r"
					+ source + ".get(" + name + ");");

			indent(level);out.print("if(");
			// check against earlier indices
			for(int j=0;j<i;++j) {
				out.print(name + " == i" + indices[j] + " || ");
			}
			// check matching type
			myOut("!typeof_" + toTypeMangle(pt) + "(r" + index + ",automaton)) { continue; }");
			typeTests.add(register(pt));
			myOut(level);
			
			if(isUnbounded) {
				myOut(level,"t" + index + "[j" + index + "++] = r" + index + ";");
				myOut(--level,"}");
				if(pattern instanceof Pattern.Set) { 
					Type.Collection rt = Type.T_SET(true,pt);
					int rest = environment.allocate(rt,var);
					myOut(level, type2JavaType(rt) + " r" + rest + " = new Automaton.Set(t" + index + ");");
				} else {
					Type.Collection rt = Type.T_BAG(true,pt);
					int rest = environment.allocate(rt,var);
					myOut(level, type2JavaType(rt) + " r" + rest + " = new Automaton.Bag(t" + index + ");");
				}
			} else {
				level = translate(level++,pat,index,environment);
			}
		}	
		
		return level;
	}

	public int translate(int level, Pattern.List pattern, int source, Environment environment) {
		Type.Ref<Type.List> type = (Type.Ref<Type.List>) pattern
				.attribute(Attribute.Type.class).type;
		source = coerceFromRef(level, pattern, source, environment);
		
		Pair<Pattern, String>[] elements = pattern.elements;
		for (int i = 0; i != elements.length; ++i) {
			Pair<Pattern, String> p = elements[i];
			Pattern pat = p.first();
			String var = p.second();
			Type.Ref pt = (Type.Ref) pat.attribute(Attribute.Type.class).type;
			int element;
			if(pattern.unbounded && (i+1) == elements.length) {
				Type.List tc = Type.T_LIST(true, pt);
				element = environment.allocate(tc);
				myOut(level, type2JavaType(tc) + " r" + element + " = r"
						+ source + ".sublist(" + i + ");");
			} else {
				element = environment.allocate(pt);				
				myOut(level, type2JavaType(pt) + " r" + element + " = r"
						+ source + ".get(" + i + ");");
				level = translate(level,pat, element, environment);
			}			
			if (var != null) {
				environment.put(element, var);
			}
		}
		return level;
	}
	
	public void translate(int level, RuleDecl decl, boolean isReduce, Environment environment, SpecFile file) {
		int thus = environment.get("this");
		
		// TODO: can optimise this by translating lets within the conditionals
		// in the case that the conditionals don't refer to those lets. This
		// will then prevent unnecessary object creation.
		
		for(Pair<String,Expr> let : decl.lets) {
			String letVar = let.first();
			Expr letExpr = let.second();
			int result = translate(2, letExpr, environment, file);
			environment.put(result, letVar);
		}
		if(decl.condition != null) {
			int condition = translate(level, decl.condition, environment, file);
			myOut(level++, "if(r" + condition + ") {");
		}
		int result = translate(level, decl.result, environment, file);
		result = coerceFromValue(level,decl.result,result,environment);
		
		myOut(level, "if(r" + thus + " != r" + result + ") {");
		myOut(level+1,"automaton.rewrite(r" + thus + ", r" + result + ");");
		
		// FIXME: we can potentially get rid of the swap by requiring automaton
		// to "reset" themselves to their original size before the rule began
		// (and any junk states were added).
		
		if(isReduce) {						
			myOut(level+1, "numReductions++;");			
			//myOut(level+2, "original.swap(automaton);");			
			myOut(level+1, "return true;");
		} else {			
			myOut(level+1, "reduce(automaton,start);");
			myOut(level+1, "if(!automaton.equals(original)) {");			
			myOut(level+2, "original.swap(automaton);");
			myOut(level+2, "reduce(original,0);");
			myOut(level+2, "numInferences++;");			
			myOut(level+2, "return true;");
			myOut(level+1, "} else { numMisinferences++; }");
		}
		myOut(level,"}");
		if(decl.condition != null) {
			myOut(--level,"}");
		}
	}
	
	public void writeSchema(SpecFile spec) {
		myOut(1,
				"// =========================================================================");
		myOut(1, "// Schema");
		myOut(1,
				"// =========================================================================");
		myOut();
		myOut(1, "public static final Schema SCHEMA = new Schema(new Schema.Term[]{");
		
		boolean firstTime=true;		
		for(TermDecl td : extractDecls(TermDecl.class,spec)) {
			if (!firstTime) {
				myOut(",");
			}
			firstTime=false;						
			myOut(2,"// " + td.type.toString());
			indent(2);writeSchema(td.type);
		}
		myOut();
		myOut(1, "});");		
	}
	
	private void writeSchema(Type.Term tt) {
		Automaton automaton = tt.automaton();
		BitSet visited = new BitSet(automaton.nStates());
		writeSchema(automaton.getRoot(0),automaton,visited);
	}
	
	private void writeSchema(int node, Automaton automaton, BitSet visited) {
		if(node < 0) {
			// bypass virtual node
		} else if (visited.get(node)) {
			out.print("Schema.Any");
			return;
		} else {
			visited.set(node);
		}
		// you can scratch your head over why this is guaranteed ;)
		Automaton.Term state = (Automaton.Term) automaton.get(node);
		switch (state.kind) {
		case wyrl.core.Types.K_Void:
			out.print("Schema.Void");
			break;
		case wyrl.core.Types.K_Any:
			out.print("Schema.Any");
			break;
			case wyrl.core.Types.K_Bool:
				out.print("Schema.Bool");
				break;
			case wyrl.core.Types.K_Int:
				out.print("Schema.Int");
				break;
			case wyrl.core.Types.K_Real:
				out.print("Schema.Real");
				break;
			case wyrl.core.Types.K_String:
				out.print("Schema.String");
				break;
			case wyrl.core.Types.K_Not:
				out.print("Schema.Not(");
				writeSchema(state.contents, automaton, visited);
				out.print(")");
				break;
			case wyrl.core.Types.K_Ref:
				writeSchema(state.contents, automaton, visited);
				break;
			case wyrl.core.Types.K_Meta:
				out.print("Schema.Meta(");
				writeSchema(state.contents, automaton, visited);
				out.print(")");
				break;
			case wyrl.core.Types.K_Nominal: {				
				// bypass the nominal marker
				Automaton.List list = (Automaton.List) automaton.get(state.contents);
				writeSchema(list.get(1), automaton, visited);
				break;
			}
			case wyrl.core.Types.K_Or: {
				out.print("Schema.Or(");
				Automaton.Set set = (Automaton.Set) automaton.get(state.contents);
				for(int i=0;i!=set.size();++i) {
					if(i != 0) { out.print(", "); }
					writeSchema(set.get(i), automaton, visited);
				}
				out.print(")");
				break;
			}
			case wyrl.core.Types.K_Set: {
				out.print("Schema.Set(");
				Automaton.List list = (Automaton.List) automaton.get(state.contents);
				// FIXME: need to deref unbounded bool here as well
				out.print("true");
				Automaton.Bag set = (Automaton.Bag) automaton.get(list.get(1));
				for(int i=0;i!=set.size();++i) {
					out.print(",");
					writeSchema(set.get(i), automaton, visited);
				}
				out.print(")");
				break;
			}
			case wyrl.core.Types.K_Bag: {
				out.print("Schema.Bag(");
				Automaton.List list = (Automaton.List) automaton.get(state.contents);
				// FIXME: need to deref unbounded bool here as well
				out.print("true");
				Automaton.Bag bag = (Automaton.Bag) automaton.get(list.get(1));
				for(int i=0;i!=bag.size();++i) {
					out.print(",");
					writeSchema(bag.get(i), automaton, visited);
				}
				out.print(")");
				break;
			}
			case wyrl.core.Types.K_List: {
				out.print("Schema.List(");
				Automaton.List list = (Automaton.List) automaton.get(state.contents);
				// FIXME: need to deref unbounded bool here as well
				out.print("true");
				Automaton.List list2 = (Automaton.List) automaton.get(list.get(1));
				for(int i=0;i!=list2.size();++i) {
					out.print(",");
					writeSchema(list2.get(i), automaton, visited);
				}
				out.print(")");
				break;
			}
			case wyrl.core.Types.K_Term: {
				out.print("Schema.Term(");
				Automaton.List list = (Automaton.List) automaton.get(state.contents);
				Automaton.Strung str = (Automaton.Strung) automaton.get(list.get(0));
				out.print("\"" + str.value + "\"");
				if(list.size() > 1) {
					out.print(",");
					writeSchema(list.get(1),automaton,visited);
				} 				
				out.print(")");
				break;
			}
			default:
				throw new RuntimeException("Unknown kind encountered: " + state.kind);
		}	
	}
	
	private <T extends Decl> ArrayList<T> extractDecls(Class<T> kind, SpecFile spec) {
		ArrayList r = new ArrayList();
		extractDecls(kind,spec,r);
		return r;
	}
	
	private <T extends Decl> void extractDecls(Class<T> kind, SpecFile spec, ArrayList<T> decls) {
		for(Decl d : spec.declarations) {
			if(kind.isInstance(d)) {
				decls.add((T)d);
			} else if(d instanceof IncludeDecl) {
				IncludeDecl id = (IncludeDecl) d;
				extractDecls(kind,id.file,decls);
			}
		}
	}
	
	public int translate(int level, Expr code, Environment environment, SpecFile file) {
		if (code instanceof Expr.Constant) {
			return translate(level,(Expr.Constant) code, environment, file);
		} else if (code instanceof Expr.UnOp) {
			return translate(level,(Expr.UnOp) code, environment, file);
		} else if (code instanceof Expr.BinOp) {
			return translate(level,(Expr.BinOp) code, environment, file);
		} else if (code instanceof Expr.NaryOp) {
			return translate(level,(Expr.NaryOp) code, environment, file);
		} else if (code instanceof Expr.Constructor) {
			return translate(level,(Expr.Constructor) code, environment, file);
		} else if (code instanceof Expr.ListAccess) {
			return translate(level,(Expr.ListAccess) code, environment, file);
		} else if (code instanceof Expr.ListUpdate) {
			return translate(level,(Expr.ListUpdate) code, environment, file);
		} else if (code instanceof Expr.Variable) {
			return translate(level,(Expr.Variable) code, environment, file);
		} else if (code instanceof Expr.Substitute) {
			return translate(level,(Expr.Substitute) code, environment, file);
		} else if(code instanceof Expr.Comprehension) {
			return translate(level,(Expr.Comprehension) code, environment, file);
		} else if(code instanceof Expr.TermAccess) {
			return translate(level,(Expr.TermAccess) code, environment, file);
		} else if(code instanceof Expr.Cast) {
			return translate(level,(Expr.Cast) code, environment, file);
		} else {
			throw new RuntimeException("unknown expression encountered - " + code);
		}
	}
	
	public int translate(int level, Expr.Cast code, Environment environment, SpecFile file) {
		Type type = code.attribute(Attribute.Type.class).type;

		// first translate src expression, and coerce to a value
		int src = translate(level, code.src, environment, file);
		src = coerceFromRef(level, code.src, src, environment);

		// TODO: currently we only support casting from integer to real!!
		String body = "new Automaton.Real(r" + src + ".value)";

		int target = environment.allocate(type);
		myOut(level, type2JavaType(type) + " r" + target + " = " + body + ";");
		return target;
		
	}
	
	public int translate(int level, Expr.Constant code, Environment environment, SpecFile file) {
		Type type = code.attribute(Attribute.Type.class).type;
		Object v = code.value;
		String rhs;
				
		if (v instanceof Boolean) {
			rhs = v.toString();
		} else if (v instanceof BigInteger) {
			BigInteger bi = (BigInteger) v;
			if(bi.bitLength() <= 64) {
				rhs = "new Automaton.Int(" + bi.longValue() + ")";
			} else {
				rhs = "new Automaton.Int(\"" + bi.toString() + "\")";	
			}
			
		} else if (v instanceof BigRational) {
			BigRational br = (BigRational) v;
			rhs = "new Automaton.Real(\"" + br.toString() + "\")";
			if(br.isInteger()) {
				long lv = br.longValue();
				if(BigRational.valueOf(lv).equals(br)) {
					// Yes, this will fit in a long value. Therefore, inline a
					// long constant as this is faster.
					rhs = "new Automaton.Real(" + lv + ")";
				}
			}
			
		} else if (v instanceof String) {
			rhs = "new Automaton.Strung(\"" + v + "\")";
		} else {		
			throw new RuntimeException("unknown constant encountered (" + v
					+ ")");
		}
		
		int target = environment.allocate(type);
		myOut(level,comment(type2JavaType(type) + " r" + target + " = " + rhs + ";",code.toString()));
		return target;
	}

	public int translate(int level, Expr.UnOp code, Environment environment, SpecFile file) {
		Type type = code.attribute(Attribute.Type.class).type;
		int rhs = translate(level,code.mhs,environment,file);
		rhs = coerceFromRef(level,code.mhs, rhs, environment);
		String body;
		
		switch (code.op) {
		case LENGTHOF:
			body = "r" + rhs + ".lengthOf()";
			break;
		case NUMERATOR:
			body = "r" + rhs + ".numerator()";
			break;
		case DENOMINATOR:
			body = "r" + rhs + ".denominator()";
			break;		
		case NEG:
			body = "r" + rhs + ".negate()";
			break;
		case NOT:
			body = "!r" + rhs;
			break;
		default:
			throw new RuntimeException("unknown unary expression encountered");
		}
		
		int target = environment.allocate(type);
		myOut(level,comment(type2JavaType(type) + " r" + target + " = " + body + ";",code.toString()));
		return target;
	}

	public int translate(int level, Expr.BinOp code, Environment environment, SpecFile file) {
		Type type = code.attribute(Attribute.Type.class).type;
		Type lhs_t = code.lhs.attribute(Attribute.Type.class).type;
		Type rhs_t = code.rhs.attribute(Attribute.Type.class).type;
		int lhs = translate(level,code.lhs,environment,file);
		
		String body;
		
		if(code.op == Expr.BOp.IS && code.rhs instanceof Expr.Constant) {
			// special case for runtime type tests
			Expr.Constant c = (Expr.Constant) code.rhs;			
			Type test = (Type)c.value;
			body = "typeof_" + toTypeMangle(test) + "(r" + lhs +",automaton)";
			typeTests.add(register(test));			
		} else if(code.op == Expr.BOp.AND) {
			// special case to ensure short-circuiting of AND.
			lhs = coerceFromRef(level,code.lhs, lhs, environment);
			int target = environment.allocate(type);	
			myOut(level,comment( type2JavaType(type) + " r" + target + " = " + false + ";",code.toString()));			
			myOut(level++,"if(r" + lhs + ") {");
			int rhs = translate(level,code.rhs,environment,file);
			rhs = coerceFromRef(level,code.rhs, rhs, environment);
			myOut(level,"r" + target + " = r" + rhs + ";");
			myOut(--level,"}");			
			return target;
		} else {
			int rhs = translate(level,code.rhs,environment,file);
			// First, convert operands into values (where appropriate)
			switch(code.op) {
				case EQ:
				case NEQ:
					if(lhs_t instanceof Type.Ref && rhs_t instanceof Type.Ref) {
						// OK to do nothing here...
					} else {
						lhs = coerceFromRef(level,code.lhs, lhs, environment);
						rhs = coerceFromRef(level,code.rhs, rhs, environment);
					}
					break;
				case APPEND:
					// append is a tricky case as we have support the non-symmetic cases
					// for adding a single element to the end or the beginning of a
					// list.
					lhs_t = Type.unbox(lhs_t);
					rhs_t = Type.unbox(rhs_t);

					if(lhs_t instanceof Type.Collection) {
						lhs = coerceFromRef(level,code.lhs, lhs, environment);				
					} else {
						lhs = coerceFromValue(level, code.lhs, lhs, environment);				
					}
					if(rhs_t instanceof Type.Collection) {
						rhs = coerceFromRef(level,code.rhs, rhs, environment);	
					} else {
						rhs = coerceFromValue(level,code.rhs, rhs, environment);
					}
					break;
				case IN:
					lhs = coerceFromValue(level,code.lhs,lhs,environment);
					rhs = coerceFromRef(level,code.rhs,rhs,environment);
					break;
				default:
					lhs = coerceFromRef(level,code.lhs,lhs,environment);
					rhs = coerceFromRef(level,code.rhs,rhs,environment);
			}

			// Second, construct the body of the computation			
			switch (code.op) {
				case ADD:
					body = "r" + lhs + ".add(r" + rhs + ")";
					break;
				case SUB:
					body = "r" + lhs + ".subtract(r" + rhs + ")";
					break;
				case MUL:
					body = "r" + lhs + ".multiply(r" + rhs + ")";
					break;
				case DIV:
					body = "r" + lhs + ".divide(r" + rhs + ")";
					break;
				case OR:
					body = "r" + lhs + " || r" + rhs ;
					break;
				case EQ:
					if(lhs_t instanceof Type.Ref && rhs_t instanceof Type.Ref) { 
						body = "r" + lhs + " == r" + rhs ;
					} else {
						body = "r" + lhs + ".equals(r" + rhs +")" ;
					}
					break;
				case NEQ:
					if(lhs_t instanceof Type.Ref && rhs_t instanceof Type.Ref) {
						body = "r" + lhs + " != r" + rhs ;
					} else {
						body = "!r" + lhs + ".equals(r" + rhs +")" ;
					}
					break;
				case LT:
					body = "r" + lhs + ".compareTo(r" + rhs + ")<0";
					break;
				case LTEQ:
					body = "r" + lhs + ".compareTo(r" + rhs + ")<=0";
					break;
				case GT:
					body = "r" + lhs + ".compareTo(r" + rhs + ")>0";
					break;
				case GTEQ:
					body = "r" + lhs + ".compareTo(r" + rhs + ")>=0";
					break;
				case APPEND: 
					if (lhs_t instanceof Type.Collection) {
						body = "r" + lhs + ".append(r" + rhs + ")";
					} else {
						body = "r" + rhs + ".appendFront(r" + lhs + ")";
					}
					break;
				case DIFFERENCE:
					body = "r" + lhs + ".removeAll(r" + rhs + ")";
					break;
				case IN:
					body = "r" + rhs + ".contains(r" + lhs + ")";
					break;
				case RANGE:
					body = "Runtime.rangeOf(automaton,r" + lhs + ",r" + rhs + ")";
					break;
				default:
					throw new RuntimeException("unknown binary operator encountered: "
							+ code);
			}
		}
		int target = environment.allocate(type);	
		myOut(level,comment( type2JavaType(type) + " r" + target + " = " + body + ";",code.toString()));
		return target;
	}
	
	public int translate(int level, Expr.NaryOp code, Environment environment, SpecFile file) {
		Type type = code.attribute(Attribute.Type.class).type;
		String body = "new Automaton.";				
		
		if(code.op == Expr.NOp.LISTGEN) { 
			body += "List(";
		} else if(code.op == Expr.NOp.BAGGEN) { 
			body += "Bag(";
		} else {
			body += "Set(";
		}
		
		List<Expr> arguments = code.arguments;
		for(int i=0;i!=arguments.size();++i) {
			if(i != 0) {
				body += ", ";
			}
			Expr argument = arguments.get(i);
			int reg = translate(level, argument, environment, file);
			reg = coerceFromValue(level, argument, reg, environment);
			body += "r" + reg;
		}
		
		int target = environment.allocate(type);
		myOut(level,comment(type2JavaType(type) + " r" + target + " = " + body + ");",code.toString()));
		return target;
	}
	
	public int translate(int level, Expr.ListAccess code, Environment environment, SpecFile file) {
		Type type = code.attribute(Attribute.Type.class).type;
		int src = translate(level,code.src, environment,file);		
		int idx = translate(level,code.index, environment,file);
		src = coerceFromRef(level,code.src, src, environment);
		idx = coerceFromRef(level,code.index, idx, environment);
		
		String body = "r" + src + ".indexOf(r" + idx + ")";
				
		int target = environment.allocate(type);
		myOut(level,comment(type2JavaType(type) + " r" + target + " = " + body + ";",code.toString()));
		return target;
	}
	
	public int translate(int level, Expr.ListUpdate code, Environment environment, SpecFile file) {
		Type type = code.attribute(Attribute.Type.class).type;
		int src = translate(level,code.src, environment, file);		
		int idx = translate(level,code.index, environment, file);
		int value = translate(level,code.value, environment, file);
		
		src = coerceFromRef(level,code.src, src, environment);
		idx = coerceFromRef(level,code.index, idx, environment);
		value = coerceFromValue(level,code.value, value, environment);
		
		String body = "r" + src + ".update(r" + idx + ", r" + value + ")";
				
		int target = environment.allocate(type);
		myOut(level,comment(type2JavaType(type) + " r" + target + " = " + body + ";",code.toString()));
		return target;
	}
	
	public int translate(int level, Expr.Constructor code,
			Environment environment, SpecFile file) {
		Type type = code.attribute(Attribute.Type.class).type;
		String body;

		if (code.argument == null) {
			body = code.name;
		} else {
			int arg = translate(level, code.argument, environment, file);
			if(code.external) {
				body = file.name + "$native." + code.name + "(automaton, r" + arg + ")";
			} else { 
				arg = coerceFromValue(level,code.argument,arg,environment);
				body = "new Automaton.Term(K_" + code.name + ",r"
					+  arg + ")";
			}
		}

		int target = environment.allocate(type);
		myOut(level,  type2JavaType(type) + " r" + target + " = " + body + ";");
		return target;
	}
	
	public int translate(int level, Expr.Variable code, Environment environment, SpecFile file) {
		Integer operand = environment.get(code.var);
		if(operand != null) {
			return environment.get(code.var);
		} else {
			Type type = code
					.attribute(Attribute.Type.class).type;
			int target = environment.allocate(type);
			myOut(level, type2JavaType(type) + " r" + target + " = " + code.var + ";");
			return target;
		}
	}
	
	public int translate(int level, Expr.Substitute code, Environment environment, SpecFile file) {
		Type type = code.attribute(Attribute.Type.class).type;
		
		// first, translate all subexpressions and make sure they are
		// references.
		int src = translate(level, code.src, environment, file);
		src = coerceFromValue(level,code.src,src,environment);
		
		int original = translate(level, code.original, environment, file);
		original = coerceFromValue(level,code.original,original,environment);
		
		int replacement = translate(level, code.replacement, environment, file);
		replacement = coerceFromValue(level,code.replacement,replacement,environment);
		
		// second, put in place the substitution
		String body = "automaton.substitute(r" + src + ", r" + original + ", r" + replacement + ")";
		int target = environment.allocate(type);
		myOut(level,  type2JavaType(type) + " r" + target + " = " + body + ";");
		return target;
	}
	
	public int translate(int level, Expr.TermAccess code, Environment environment, SpecFile file) {
		Type type = code.attribute(Attribute.Type.class).type;

		// first translate src expression, and coerce to a value
		int src = translate(level, code.src, environment, file);
		src = coerceFromRef(level, code.src, src, environment);

		String body = "r" + src + ".contents";

		int target = environment.allocate(type);
		myOut(level, type2JavaType(type) + " r" + target + " = " + body + ";");
		return target;
	}
	
	public int translate(int level, Expr.Comprehension expr, Environment environment, SpecFile file) {		
		Type type = expr.attribute(Attribute.Type.class).type;
		int target = environment
				.allocate(type);
		
		// first, translate all source expressions
		int[] sources = new int[expr.sources.size()];
		for(int i=0;i!=sources.length;++i) {
			Pair<Expr.Variable,Expr> p = expr.sources.get(i);
			int operand = translate(level,p.second(),environment,file);
			operand = coerceFromRef(level,p.second(),operand,environment);
			sources[i] = operand;									
		}
		// TODO: initialise result set
		myOut(level, "Automaton.List t" + target + " = new Automaton.List();");
		int startLevel = level;
		
		// initialise result register if needed
		switch(expr.cop) {		
		case NONE:
			myOut(level,type2JavaType(type) + " r" + target + " = true;");
			myOut(level,"outer:");
			break;
		case SOME:
			myOut(level,type2JavaType(type) + " r" + target + " = false;");
			myOut(level,"outer:");
			break;
		}
		
		// second, generate all the for loops
		for (int i = 0; i != sources.length; ++i) {
			Pair<Expr.Variable, Expr> p = expr.sources.get(i);
			Expr.Variable variable = p.first();
			Expr source = p.second();
			Type.Collection sourceType = (Type.Collection) source
					.attribute(Attribute.Type.class).type;
			Type elementType = variable.attribute(Attribute.Type.class).type;
			int index = environment.allocate(elementType, variable.var);
			myOut(level++, "for(int i" + index + "=0;i" + index + "<r"
					+ sources[i] + ".size();i" + index + "++) {");
			String rhs = "r"+ sources[i] + ".get(i" + index + ")";
			// FIXME: need a more general test for a reference type
			if(!(elementType instanceof Type.Ref)) {
				rhs = "automaton.get(" + rhs + ");";
			}
			myOut(level, type2JavaType(elementType) + " r" + index + " = (" + type2JavaType(elementType) + ") " + rhs + ";");			
		}
		
		if(expr.condition != null) {
			int condition = translate(level,expr.condition,environment,file);
			myOut(level++,"if(r" + condition + ") {");			
		}
		
		switch(expr.cop) {
		case SETCOMP:
		case BAGCOMP:
		case LISTCOMP:
			int result = translate(level,expr.value,environment,file);
			result = coerceFromValue(level,expr.value,result,environment);
			myOut(level,"t" + target + ".add(r" + result + ");");
			break;
		case NONE:
			myOut(level,"r" + target + " = false;");
			myOut(level,"break outer;");
			break;
		case SOME:
			myOut(level,"r" + target + " = true;");
			myOut(level,"break outer;");
			break;
		}
		// finally, terminate all the for loops
		while(level > startLevel) {
			myOut(--level,"}");
		}

		switch(expr.cop) {
		case SETCOMP:
			myOut(level, type2JavaType(type) + " r" + target
				+ " = new Automaton.Set(t" + target + ".toArray());");
			break;
		case BAGCOMP:
			myOut(level, type2JavaType(type) + " r" + target
				+ " = new Automaton.Bag(t" + target + ".toArray());");
			break;		
		case LISTCOMP:
			myOut(level, type2JavaType(type) + " r" + target
				+ " = t" + target + ";");
			break;		
		}

		return target;
	}
	
	protected void writeTypeTests() {
		myOut(1,
				"// =========================================================================");
		myOut(1, "// Type Tests");
		myOut(1,
				"// =========================================================================");
		myOut();
		
		myOut(1, "private final static BitSet visited = new BitSet();");
		myOut();
		
		HashSet<Integer> worklist = new HashSet<Integer>(typeTests);
		while (!worklist.isEmpty()) {			
			Integer i = worklist.iterator().next();
			worklist.remove(i);
			writeTypeTest(typeRegister.get(i), worklist);
		}
	}

	protected void writeTypeTest(Type type, HashSet<Integer> worklist) {
		
		if (type instanceof Type.Any) {
			writeTypeTest((Type.Any)type,worklist);
		} else if (type instanceof Type.Bool) {
			writeTypeTest((Type.Bool)type,worklist);
		} else if (type instanceof Type.Int) {
			writeTypeTest((Type.Int)type,worklist);
		} else if (type instanceof Type.Real) {
			writeTypeTest((Type.Real)type,worklist);
		} else if (type instanceof Type.Strung) {
			writeTypeTest((Type.Strung)type,worklist);
		} else if (type instanceof Type.Ref) {
			writeTypeTest((Type.Ref)type,worklist);							
		} else if (type instanceof Type.Nominal) {
			writeTypeTest((Type.Nominal)type,worklist);							
		} else if (type instanceof Type.Not) {
			writeTypeTest((Type.Not)type,worklist);							
		} else if (type instanceof Type.Term) {
			writeTypeTest((Type.Term)type,worklist);
		} else if (type instanceof Type.Collection) {
			writeTypeTest((Type.Collection)type,worklist);							
		} else if (type instanceof Type.Or) {			
			writeTypeTest((Type.Or)type,worklist);							
		} else {
			throw new RuntimeException(
					"internal failure --- type test not implemented (" + type
							+ ")");
		}		
	}
	
	protected void writeTypeTest(Type.Any type, HashSet<Integer> worklist) {
		String mangle = toTypeMangle(type);
		myOut(1, "// " + type);
		myOut(1, "private static boolean typeof_" + mangle
				+ "(Automaton.State state, Automaton automaton) {");		
		myOut(2, "return true;");
		myOut(1, "}");
		myOut();
	}
	
	protected void writeTypeTest(Type.Bool type, HashSet<Integer> worklist) {
		String mangle = toTypeMangle(type);
		myOut(1, "// " + type);
		myOut(1, "private static boolean typeof_" + mangle
				+ "(Automaton.State state, Automaton automaton) {");		
		myOut(2, "return state.kind == Automaton.K_BOOL;");
		myOut(1, "}");
		myOut();
	}
	
	protected void writeTypeTest(Type.Int type, HashSet<Integer> worklist) {
		String mangle = toTypeMangle(type);
		myOut(1, "// " + type);
		myOut(1, "private static boolean typeof_" + mangle
				+ "(Automaton.State state, Automaton automaton) {");		
		myOut(2, "return state.kind == Automaton.K_INT;");
		myOut(1, "}");
		myOut();
	}
	
	protected void writeTypeTest(Type.Real type, HashSet<Integer> worklist) {
		String mangle = toTypeMangle(type);
		myOut(1, "// " + type);
		myOut(1, "private static boolean typeof_" + mangle
				+ "(Automaton.State state, Automaton automaton) {");		
		myOut(2, "return state.kind == Automaton.K_REAL;");
		myOut(1, "}");
		myOut();
	}
	
	protected void writeTypeTest(Type.Strung type, HashSet<Integer> worklist) {
		String mangle = toTypeMangle(type);
		myOut(1, "// " + type);
		myOut(1, "private static boolean typeof_" + mangle
				+ "(Automaton.State state, Automaton automaton) {");		
		myOut(2, "return state.kind == Automaton.K_STRING;");
		myOut(1, "}");
		myOut();
	}
	
	protected void writeTypeTest(Type.Ref type, HashSet<Integer> worklist) {
				Type element = type.element();		
		String mangle = toTypeMangle(type);		
		String elementMangle = toTypeMangle(element);		
		myOut(1, "// " + type);
		myOut(1, "private static boolean typeof_" + mangle
				+ "(int index, Automaton automaton) {");
		myOut(2, "if(index < 0) {");
		myOut(3, " return typeof_" + elementMangle + "(automaton.get(index),automaton);");
		myOut(2, "} else {");
		myOut(3, "int tmp = index + (automaton.nStates() * " + registeredTypes.get(type) + ");");
		myOut(3, "if(visited.get(tmp)) {");
		myOut(4, "return true;");
		myOut(3, "} else {");
		myOut(4, "visited.set(tmp);");
		myOut(4, "boolean r = typeof_" + elementMangle + "(automaton.get(index),automaton);");
		myOut(4, "visited.clear(tmp);");
		myOut(4, "return r;");
		myOut(3, "}");
		myOut(2, "}");
		myOut(1, "}");
		myOut();			
		
		int handle = register(element);
		if (typeTests.add(handle)) {
			worklist.add(handle);
		}
	}
	
	protected void writeTypeTest(Type.Not type, HashSet<Integer> worklist) {
		Type element = type.element();
		String mangle = toTypeMangle(type);
		String elementMangle = toTypeMangle(element);
		myOut(1, "// " + type);
		myOut(1, "private static boolean typeof_" + mangle
				+ "(Automaton.State state, Automaton automaton) {");
		myOut(2, "return !typeof_" + elementMangle + "(state,automaton);");
		myOut(1, "}");
		myOut();

		int handle = register(element);
		if (typeTests.add(handle)) {
			worklist.add(handle);
		}
	}
	
	protected void writeTypeTest(Type.Nominal type, HashSet<Integer> worklist) {
		Type element = type.element();		
		String mangle = toTypeMangle(type);		
		String elementMangle = toTypeMangle(element);		
		myOut(1, "// " + type);
		if(element instanceof Type.Ref) {
			myOut(1, "private static boolean typeof_" + mangle
					+ "(int index, Automaton automaton) {");		
			myOut(2, "return typeof_" + elementMangle + "(index,automaton);");
			myOut(1, "}");
			myOut();		
		} else {
			myOut(1, "private static boolean typeof_" + mangle
					+ "(Automaton.State state, Automaton automaton) {");		
			myOut(2, "return typeof_" + elementMangle + "(state,automaton);");
			myOut(1, "}");
			myOut();	
		}
				
		int handle = register(element);
		if (typeTests.add(handle)) {
			worklist.add(handle);
		}
	}
	
	protected void writeTypeTest(Type.Term type, HashSet<Integer> worklist) {
		String mangle = toTypeMangle(type);
		myOut(1, "// " + type);
		myOut(1, "private static boolean typeof_" + mangle
				+ "(Automaton.State state, Automaton automaton) {");
		
		myOut(2, "if(state instanceof Automaton.Term && state.kind == K_"
				+ type.name() + ") {");
		// FIXME: there is definitely a bug here since we need the offset within
		// the automaton state
		Type data = type.element();
		if (data != null) {
			myOut(3, "int data = ((Automaton.Term)state).contents;");
			myOut(3, "if(typeof_" + toTypeMangle(data)
					+ "(data,automaton)) { return true; }");
			int handle = register(data);
			if (typeTests.add(handle)) {
				worklist.add(handle);
			}
		} else {
			myOut(3, "return true;");
		}
		myOut(2, "}");
		myOut(2, "return false;");		
		myOut(1, "}");
		myOut();
	}
	
	protected void writeTypeTest(Type.Or type, HashSet<Integer> worklist) {
		String mangle = toTypeMangle(type);
		myOut(1, "// " + type);
		myOut(1, "private static boolean typeof_" + mangle
				+ "(Automaton.State state, Automaton automaton) {");
		indent(2); out.print("return ");
		boolean firstTime=true;
		for(Type element : type.elements()) {
			if(!firstTime) {
				myOut();indent(3);out.print("|| ");
			}
			firstTime=false;
			String elementMangle = toTypeMangle(element);
			out.print("typeof_" + elementMangle + "(state,automaton)");
			int handle = register(element);
			if (typeTests.add(handle)) {
				worklist.add(handle);
			}			
		}
		myOut(";");
		myOut(1, "}");
		myOut();
		
	}
	
	protected void writeTypeTest(Type.Collection type, HashSet<Integer> worklist) {
		String mangle = toTypeMangle(type);
		myOut(1, "// " + type);
		myOut(1, "private static boolean typeof_" + mangle
				+ "(Automaton.State _state, Automaton automaton) {");		
		myOut(2, "if(_state instanceof Automaton.Collection) {");
		myOut(3, "Automaton.Collection state = (Automaton.Collection) _state;");
		
		Type[] tt_elements = type.elements();
		int min = tt_elements.length;
		if (type.unbounded()) {
			myOut(3, "if(state.size() < " + (min - 1)
					+ ") { return false; }");
		} else {
			myOut(3, "if(state.size() != " + min + ") { return false; }");
		}
		
		int level = 3;
		if(type instanceof Type.List) {
			// easy, sequential match case
			for (int i = 0; i != tt_elements.length; ++i) {
				myOut(3, "int s" + i + " = " + i + ";");				
			}
		} else {
			// hard, non-sequential match
			for (int i = 0; i != tt_elements.length; ++i) {
				if(!type.unbounded() || i+1 < tt_elements.length) {
					String idx = "s" + i;
					myOut(3+i, "for(int " + idx + "=0;" + idx + " < state.size();++" + idx + ") {");
					if(i > 0) {
						indent(3+i);out.print("if(");
						for(int j=0;j<i;++j) {
							if(j != 0) {
								out.print(" || ");
							}
							out.print(idx  + "==s" + j);
						}
						out.println(") { continue; }");
					}
					level++;
				}
			}			
		}
		
		myOut(level, "boolean result=true;");
		myOut(level, "for(int i=0;i!=state.size();++i) {");
		myOut(level+1, "int child = state.get(i);");
		for (int i = 0; i != tt_elements.length; ++i) {
			Type pt = tt_elements[i];
			String pt_mangle = toTypeMangle(pt);
			if (type.unbounded() && (i + 1) == tt_elements.length) {
				if(i == 0) {
					myOut(level+1, "{");
				} else {
					myOut(level+1, "else {");
				}
			} else if(i == 0){
				myOut(level+1, "if(i == s" + i + ") {");
			} else {
				myOut(level+1, "else if(i == s" + i + ") {");
			}
			myOut(level+2, "if(!typeof_" + pt_mangle
					+ "(child,automaton)) { result=false; break; }");
			myOut(level+1, "}");
			
			int handle = register(pt);
			if (typeTests.add(handle)) {
				worklist.add(handle);
			}
		}
		
		myOut(level,"}");
		myOut(level,"if(result) { return true; } // found match");
		if(type instanceof Type.Bag || type instanceof Type.Set) {
			for (int i = 0; i != tt_elements.length; ++i) {
				if(!type.unbounded() || i+1 < tt_elements.length) {
					myOut(level - (i+1),"}");
				}
			}
		}

		myOut(2, "}");
		myOut(2,"return false;");
		myOut(1, "}");		
		myOut();
	}

	protected void writeStatsInfo() {
		myOut(1,"public static long MAX_STEPS = 50000;");
		myOut(1,"public static long numSteps = 0;");
		myOut(1,"public static long numReductions = 0;");
		myOut(1,"public static long numInferences = 0;");
		myOut(1,"public static long numMisinferences = 0;");		
		
		myOut(1,"public static void reset() {");
		myOut(2,"numSteps = 0;");
		myOut(2,"numReductions = 0;");
		myOut(2,"numInferences = 0;");
		myOut(2,"numMisinferences = 0;");				
		myOut(1,"}");
	}
	
	protected void writeMainMethod() {
		myOut(1,
				"// =========================================================================");
		myOut(1, "// Main Method");
		myOut(1,
				"// =========================================================================");
		myOut();
		myOut(1, "public static void main(String[] args) throws IOException {");
		myOut(2, "try {");
		myOut(3,
				"PrettyAutomataReader reader = new PrettyAutomataReader(System.in,SCHEMA);");
		myOut(3,
				"PrettyAutomataWriter writer = new PrettyAutomataWriter(System.out,SCHEMA);");
		myOut(3, "Automaton automaton = reader.read();");
		myOut(3, "System.out.print(\"PARSED: \");");
		myOut(3, "print(automaton);");
		myOut(3, "infer(automaton);");
		myOut(3, "System.out.print(\"REWROTE: \");");
		myOut(3, "print(automaton);");						
		myOut(3, "System.out.println(\"(Reductions=\" + numReductions + \", Inferences=\" + numInferences + \", Misinferences=\" + numMisinferences + \", steps = \" + numSteps + \")\");");
		myOut(2, "} catch(PrettyAutomataReader.SyntaxError ex) {");
		myOut(3, "System.err.println(ex.getMessage());");
		myOut(2, "}");
		myOut(1, "}");
		
		myOut(1,"");
		myOut(1,"static void print(Automaton automaton) {");
		myOut(2,"try {");
		myOut(3,
				"PrettyAutomataWriter writer = new PrettyAutomataWriter(System.out,SCHEMA);");
		myOut(3, "writer.write(automaton);");
		myOut(3, "writer.flush();");
		myOut(3, "System.out.println();");
		myOut(2,"} catch(IOException e) { System.err.println(\"I/O error printing automaton\"); }");
		myOut(1,"}");
	}

	public String comment(String code, String comment) {
		int nspaces = 30 - code.length();
		String r = "";
		for(int i=0;i<nspaces;++i) {
			r += " ";
		}
		return code + r + " // " + comment;
	}
	
	public String toTypeMangle(Type t) {
		return Integer.toString(register(t));	
	}
	
	/**
	 * Convert a Wyrl type into its equivalent Java type.
	 * 
	 * @param type
	 * @return
	 */
	public String type2JavaType(Type type) {
		if (type instanceof Type.Any) {
			return "Object";
		} else if (type instanceof Type.Int) {
			return "Automaton.Int";
		} else if (type instanceof Type.Real) {
			return "Automaton.Real";
		} else if (type instanceof Type.Bool) {
			return "boolean";
		} else if (type instanceof Type.Strung) {
			return "Automaton.Strung";
		} else if (type instanceof Type.Term) {
			return "Automaton.Term";
		} else if (type instanceof Type.Ref) {
			return "int";
		} else if (type instanceof Type.Nominal) {
			Type.Nominal nom = (Type.Nominal) type;
			return type2JavaType(nom.element());
		} else if (type instanceof Type.Or) {
			return "Object";
		} else if (type instanceof Type.List) {
			return "Automaton.List";			
		} else if (type instanceof Type.Bag) {
			return "Automaton.Bag";
		} else if (type instanceof Type.Set) {
			return "Automaton.Set";
		}
		throw new RuntimeException("unknown type encountered: " + type);
	}
	
	public int coerceFromValue(int level, Expr expr, int register, Environment environment) {
		Type type = expr.attribute(Attribute.Type.class).type;
		if(type instanceof Type.Ref) {
			return register;
		} else {
			Type.Ref refType = Type.T_REF(type);
			int result = environment.allocate(refType);
			String src = "r" + register;
			if(refType.element() instanceof Type.Bool) {
				// special thing needed for bools
				src = src + " ? Automaton.TRUE : Automaton.FALSE";
			}
			myOut(level, type2JavaType(refType) + " r" + result + " = automaton.add(" + src + ");");
			return result;
		}
	}

	public int coerceFromRef(int level, SyntacticElement elem, int register,
			Environment environment) {
		Type type = elem.attribute(Attribute.Type.class).type;
		
		if (type instanceof Type.Ref) {
			Type.Ref refType = (Type.Ref) type;
			Type element = refType.element();
			int result = environment.allocate(element);
			String cast = type2JavaType(element);			
			String body = "automaton.get(r" + register + ")";
			// special case needed for booleans
			if(element instanceof Type.Bool) {
				body = "((Automaton.Bool)" + body + ").value";
			} else {
				body = "(" + cast + ") " + body;
			}
			myOut(level, cast + " r" + result + " = " + body + ";");
			return result;
		} else {
			return register;
		}
	}
	
	protected void myOut() {
		myOut(0, "");
	}

	protected void myOut(int level) {
		myOut(level, "");
	}

	protected void myOut(String line) {
		myOut(0, line);
	}

	protected void myOut(int level, String line) {
		for (int i = 0; i < level; ++i) {
			out.print("\t");
		}
		out.println(line);
	}

	protected void indent(int level) {
		for (int i = 0; i < level; ++i) {
			out.print("\t");
		}
	}
	
	private HashMap<Type,Integer> registeredTypes = new HashMap<Type,Integer>();
	private ArrayList<Type> typeRegister = new ArrayList<Type>();	
	
	private int register(Type t) {
		//Types.reduce(t.automaton());
		Integer i = registeredTypes.get(t);
		if(i == null) {
			int r = typeRegister.size();
			registeredTypes.put(t, r);
			typeRegister.add(t);
			return r;
		} else {
			return i;
		}
	}
	
	private static class Environment {
		private final HashMap<String, Integer> var2idx = new HashMap<String, Integer>();
		private final ArrayList<Type> idx2type = new ArrayList<Type>();

		public int allocate(Type t) {
			int idx = idx2type.size();
			idx2type.add(t);
			return idx;
		}

		public int allocate(Type t, String v) {
			int r = allocate(t);
			var2idx.put(v, r);
			return r;
		}

		public Integer get(String v) {
			return var2idx.get(v);
		}

		public void put(int idx, String v) {
			var2idx.put(v, idx);
		}

		public ArrayList<Type> asList() {
			return idx2type;
		}

		public String toString() {
			return idx2type.toString() + "," + var2idx.toString();
		}
	}
}
