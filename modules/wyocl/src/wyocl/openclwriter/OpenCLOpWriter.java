package wyocl.openclwriter;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import wybs.util.Pair;
import wyil.lang.Block;
import wyil.lang.Code;
import wyil.lang.Code.Comparator;
import wyil.lang.Code.ForAll;
import wyil.lang.Code.UnArithKind;
import wyil.lang.Code.UnArithOp;
import wyil.lang.Constant;
import wyil.lang.Type;
import wyil.lang.Type.Leaf;
import wyocl.filter.Argument;
import wyocl.openclwriter.OpenCLTypeWriter.ExpressionWriter;

public class OpenCLOpWriter {
	private static final String GPGPU_RUNTIME_PREFIX = "whiley_gpgpu_runtime_";
	private static final boolean WRITE_BYTECODES_TO_COMMENTS = true;
	public final OpenCLTypeWriter typeWriter = new OpenCLTypeWriter(this);
	private int indentationLevel = 1;
	protected final PrintWriter writer;
	private ArrayList<String> scopeEndLabels = new ArrayList<String>();
	private int forallIndexCount = 0;
	private final FunctionInvokeTranslator functionTranslator;
	
	public interface FunctionInvokeTranslator {
		/**
		 * The OpWriter will call this method when it encounters a invoke bytecode
		 * and requires a translation of the function name from WYIL to OpenCL.
		 * It is the responsibility of the implementer of this method to ensure
		 * that a function with a matching name and type signature exists in the OpenCL
		 * file. The appropriate place to write these called functions is when this 
		 * method is called.
		 * 
		 * @param code The bytecode of the function invoke
		 * @return The name of the translated function
		 */
		public String translateFunctionName(Code.Invoke code);
	}
	
	public static void writeRuntime(PrintWriter writer) { // TODO: this should be loaded in from a file or something
		writer.println();
		writer.println("// Beginning whiley GPGPU runtime library");
		writer.println("// Ending whiley GPGPU runtime library");
		writer.println();
	}
	
	public OpenCLOpWriter(PrintWriter writer, FunctionInvokeTranslator functionTranslator) {
		this.writer = writer;
		this.functionTranslator = functionTranslator;
	}

	public void writeBlock(List<Block.Entry> filteredEntries) {
		indentationLevel = 1;
		
		if(filteredEntries.size() > 0) {
			Block.Entry first = filteredEntries.get(0);
			if(first.code instanceof Code.Loop) {
				filteredEntries = filteredEntries.subList(1, filteredEntries.size());
				
				if(first.code instanceof Code.ForAll) {
					Code.ForAll forAll = (ForAll)first.code;
					writeIndents();
					typeWriter.writeLHS(forAll.indexOperand, forAll.type.element());
					writer.print(" = ");
					typeWriter.writeListAccessor(forAll.sourceOperand, new ExpressionWriter() {
						@Override
						public void writeExpression(PrintWriter writer) {
							writeKernelGlobalIndex(0); // TODO: Support multiple dimensions
						}
					});
					writer.println("; // Get work item");
				}
				else {
					throw new NotImplementedException();
					
					// TODO: implement
				}
			}
		}
		
		for(Block.Entry e : filteredEntries) {
			System.out.println(e.code);
			writeBlockEntry(e);
		}
	}
	
	private void writeKernelGlobalIndex(int dimension) {
		writer.print("get_global_id(");
		writer.print(dimension);
		writer.print(')');
	}
	
	public void writeBlockEntry(Block.Entry entry) {
		Code code = entry.code;
		if(code instanceof Code.ForAll) {
			writeCode((Code.ForAll)code);
		}
		else if(code instanceof Code.Loop) { // Must be after Code.ForAll
			writeCode((Code.Loop)code);
		}
		else if(code instanceof Code.Nop) {
			writeCode((Code.Nop)code);
		}
		else if(code instanceof Code.TryCatch) {
			writeCode((Code.TryCatch)code);
		}
		else if(code instanceof Code.Const) {
			writeCode((Code.Const)code);
		}
		else if(code instanceof Code.Goto) {
			writeCode((Code.Goto)code);
		}
		else if(code instanceof Code.TryEnd) { // Must be before Code.Label
			writeCode((Code.TryEnd)code);
		}
		else if(code instanceof Code.Label) {
			writeCode((Code.Label)code);
		}
		else if(code instanceof Code.Assert) {
			writeCode((Code.Assert)code);
		}
		else if(code instanceof Code.Assign) {
			writeCode((Code.Assign)code);
		}
		else if(code instanceof Code.Convert) {
			writeCode((Code.Convert)code);
		}
		else if(code instanceof Code.Debug) {
			writeCode((Code.Debug)code);
		}
		else if(code instanceof Code.Dereference) {
			writeCode((Code.Dereference)code);
		}
		else if(code instanceof Code.Assume) {
			writeCode((Code.Assume)code);
		}
		else if(code instanceof Code.FieldLoad) {
			writeCode((Code.FieldLoad)code);
		}
		else if(code instanceof Code.If) {
			writeCode((Code.If)code);
		}
		else if(code instanceof Code.IfIs) {
			writeCode((Code.IfIs)code);
		}
		else if(code instanceof Code.IndexOf) {
			writeCode((Code.IndexOf)code);
		}
		else if(code instanceof Code.IndirectInvoke) {
			writeCode((Code.IndirectInvoke)code);
		}
		else if(code instanceof Code.Invert) {
			writeCode((Code.Invert)code);
		}
		else if(code instanceof Code.Invoke) {
			writeCode((Code.Invoke)code);
		}
		else if(code instanceof Code.Lambda) {
			writeCode((Code.Lambda)code);
		}
		else if(code instanceof Code.LengthOf) {
			writeCode((Code.LengthOf)code);
		}
		else if(code instanceof Code.Move) {
			writeCode((Code.Move)code);
		}
		else if(code instanceof Code.NewList) {
			writeCode((Code.NewList)code);
		}
		else if(code instanceof Code.NewMap) {
			writeCode((Code.NewMap)code);
		}
		else if(code instanceof Code.NewObject) {
			writeCode((Code.NewObject)code);
		}
		else if(code instanceof Code.NewRecord) {
			writeCode((Code.NewRecord)code);
		}
		else if(code instanceof Code.NewSet) {
			writeCode((Code.NewSet)code);
		}
		else if(code instanceof Code.NewTuple) {
			writeCode((Code.NewTuple)code);
		}
		else if(code instanceof Code.Not) {
			writeCode((Code.Not)code);
		}
		else if(code instanceof Code.Return) {
			writeCode((Code.Return)code);
		}
		else if(code instanceof Code.SubList) {
			writeCode((Code.SubList)code);
		}
		else if(code instanceof Code.SubString) {
			writeCode((Code.SubString)code);
		}
		else if(code instanceof Code.Switch) {
			writeCode((Code.Switch)code);
		}
		else if(code instanceof Code.Throw) {
			writeCode((Code.Throw)code);
		}
		else if(code instanceof Code.TupleLoad) {
			writeCode((Code.TupleLoad)code);
		}
		else if(code instanceof Code.Update) {
			writeCode((Code.Update)code);
		}
		else if(code instanceof Code.Void) {
			writeCode((Code.Void)code);
		}
		else if(code instanceof Code.BinArithOp) {
			writeCode((Code.BinArithOp)code);
		}
		else if(code instanceof Code.BinListOp) {
			writeCode((Code.BinListOp)code);
		}
		else if(code instanceof Code.BinSetOp) {
			writeCode((Code.BinSetOp)code);
		}
		else if(code instanceof Code.BinStringOp) {
			writeCode((Code.BinStringOp)code);
		}
		else if(code instanceof Code.UnArithOp) {
			writeCode((Code.UnArithOp)code);
		}
		else {
			throw new RuntimeException("Unknown bytecode encountered: "+code+" ("+code.getClass()+")");
		}
	}

	protected void writeIndents() {
		for(int i=0;i<indentationLevel;i++) {
			writer.print('\t');
		}
	}
	
	private void writePrimitiveBinArithOp(Code.BinArithKind kind, Type.Leaf type, ExpressionWriter lhs, ExpressionWriter rhs) {
		writer.print('(');
		switch(kind) {
			case ADD:
				lhs.writeExpression(writer);
				writer.print(' ');
				writer.print('+');
				writer.print(' ');
				rhs.writeExpression(writer);
				break;
			case SUB:
				lhs.writeExpression(writer);
				writer.print(' ');
				writer.print('-');
				writer.print(' ');
				rhs.writeExpression(writer);
				break;
			case MUL:
				lhs.writeExpression(writer);
				writer.print(' ');
				writer.print('*');
				writer.print(' ');
				rhs.writeExpression(writer);
				break;
			case DIV:
				lhs.writeExpression(writer);
				writer.print(' ');
				writer.print('/');
				writer.print(' ');
				rhs.writeExpression(writer);
				break;
			case BITWISEAND:
				lhs.writeExpression(writer);
				writer.print(' ');
				writer.print('&');
				writer.print(' ');
				rhs.writeExpression(writer);
				break;
			case BITWISEOR:
				lhs.writeExpression(writer);
				writer.print(' ');
				writer.print('|');
				writer.print(' ');
				rhs.writeExpression(writer);
				break;
			case BITWISEXOR:
				lhs.writeExpression(writer);
				writer.print(' ');
				writer.print('^');
				writer.print(' ');
				rhs.writeExpression(writer);
				break;
			case LEFTSHIFT:
				lhs.writeExpression(writer);
				writer.print(' ');
				writer.print("<<");
				writer.print(' ');
				rhs.writeExpression(writer);
				break;
			case RIGHTSHIFT:
				lhs.writeExpression(writer);
				writer.print(' ');
				writer.print(">>");
				writer.print(' ');
				rhs.writeExpression(writer);
				break;
			case RANGE:
				throw new NotImplementedException();
			case REM:
				lhs.writeExpression(writer);
				writer.print(' ');
				writer.print("&");
				writer.print(' ');
				rhs.writeExpression(writer);
		}
		writer.print(')');
	}
	
	private void writePrimitiveUnArithOp(UnArithKind kind, Leaf type, ExpressionWriter expressionWriter) {
		writer.print('(');
		switch(kind) {
			case NEG:
				writer.print('-');
				expressionWriter.writeExpression(writer);
				break;
			case DENOMINATOR:
				throw new NotSupportedByGPGPUException();
			case NUMERATOR:
				throw new NotSupportedByGPGPUException();
		}
		writer.print(')');
	}
	
	private void writeComparitor(Comparator op, int leftOperand, int rightOperand) {
		writer.print('(');
		switch(op) {
			case SUBSET:
				throw new NotImplementedException();
			case SUBSETEQ:
				throw new NotImplementedException();
			case ELEMOF:
				throw new NotImplementedException();
			case EQ:
				typeWriter.writeRHS(leftOperand);
				writer.print(" == ");
				typeWriter.writeRHS(rightOperand);
				break;
			case GT:
				typeWriter.writeRHS(leftOperand);
				writer.print(" > ");
				typeWriter.writeRHS(rightOperand);
				break;
			case GTEQ:
				typeWriter.writeRHS(leftOperand);
				writer.print(" >= ");
				typeWriter.writeRHS(rightOperand);
				break;
			case LT:
				typeWriter.writeRHS(leftOperand);
				writer.print(" < ");
				typeWriter.writeRHS(rightOperand);
				break;
			case LTEQ:
				typeWriter.writeRHS(leftOperand);
				writer.print(" <= ");
				typeWriter.writeRHS(rightOperand);
				break;
			case NEQ:
				typeWriter.writeRHS(leftOperand);
				writer.print(" != ");
				typeWriter.writeRHS(rightOperand);
				break;
		}
		writer.print(')');
	}
	
	private void beginScope(String scopeEnd) {
		indentationLevel++;
		scopeEndLabels.add(scopeEnd);
	}
	
	private void checkEndScope(Code.Label code) {
		while(scopeEndLabels.contains(code.label)) {
			indentationLevel--;
			writeIndents();
			writer.print('}');
			writeLineEnd(code, false);
			scopeEndLabels.remove(code.label);
		}
	}
	
	private void writeLineEnd(Code code) {
		writeLineEnd(code, true);
	}
	
	private void writeLineEnd(Code code, boolean semicolon) {
		if(semicolon) {
			writer.print(';');
		}
		
		if(WRITE_BYTECODES_TO_COMMENTS) {
			writer.print(" // ");
			writer.println(code);
		}
		else {
			writer.print('\n');
		}
	}
	
	private void writeCode(final UnArithOp code) {
		if(!(code.type instanceof Type.Leaf)){
			throw new RuntimeException("Dont know how to handle nonleaf types for UnArithOp: "+code.type);
		}
		
		writeIndents();
		typeWriter.writeLHS(code.target, code.type);
		writer.print(" = ");
		writePrimitiveUnArithOp(code.kind, (Type.Leaf)code.type, new ExpressionWriter() {
			@Override
			public void writeExpression(PrintWriter writer) {
				typeWriter.writeRHS(code.operand);
			}
		});
		writeLineEnd(code);
	}

	private void writeCode(Code.BinStringOp code) {
		throw new NotImplementedException();
	}

	private void writeCode(Code.BinSetOp code) {
		throw new NotImplementedException();
	}

	private void writeCode(Code.BinListOp code) {
		throw new NotImplementedException();
	}

	private void writeCode(final Code.BinArithOp code) {
		if(!(code.type instanceof Type.Leaf)){
			throw new RuntimeException("Dont know how to handle nonleaf types for BinArithOp: "+code.type);
		}
		
		writeIndents();
		typeWriter.writeLHS(code.target, code.type);
		writer.print(" = ");
		writePrimitiveBinArithOp(code.kind, (Type.Leaf)code.type, new ExpressionWriter() {
			
			@Override
			public void writeExpression(PrintWriter writer) {
				typeWriter.writeRHS(code.leftOperand);
			}
		}, new ExpressionWriter() {
			
			@Override
			public void writeExpression(PrintWriter writer) {
				typeWriter.writeRHS(code.rightOperand);
			}
		});
		writeLineEnd(code);
	}

	private void writeCode(Code.Void code) {
		writeIndents();
		writeLineEnd(code);
	}

	private void writeCode(Code.Update code) {
		// TODO: implement this properly
		if(code.type instanceof Type.List) {
			for(Code.LVal<Type.EffectiveList> _lv : code) {
				final Code.ListLVal lv = (Code.ListLVal)_lv;
				
				writeIndents();
				typeWriter.writeListAccessor(code.target, new ExpressionWriter() {
					@Override
					public void writeExpression(PrintWriter writer) {
						typeWriter.writeRHS(lv.indexOperand);
					}
				});
				writer.print(" = ");
				typeWriter.writeRHS(code.operand); // TODO: support more than one
				writeLineEnd(code);
			}
		}
		else {
			throw new NotImplementedException();
		}
	}

	private void writeCode(final Code.TupleLoad code) {
		writeIndents();
		typeWriter.writeLHS(code.target, code.type.element(code.index));
		writer.print(" = ");
		typeWriter.writeTupleAccessor(code.operand, new ExpressionWriter() {
			@Override
			public void writeExpression(PrintWriter writer) {
				writer.print(code.index);
			}
		});
		writeLineEnd(code);
	}

	private void writeCode(Code.Throw code) {
		throw new NotImplementedException();
	}

	private void writeCode(Code.Switch code) {
		writeIndents();
		for(Pair<Constant, String> caseStatement : code.branches) {
			writer.print("if(");
			writer.print(caseStatement.first());
			writer.print(" == ");
			typeWriter.writeRHS(code.operand);
			writer.print(") { goto ");
			writer.print(caseStatement.second());
			writer.print("; }");
			writeLineEnd(code, false);
			writeIndents();
			writer.print("else ");
		}
		writer.print("{ goto ");
		writer.print(code.defaultTarget);
		writer.print("; }");
		writeLineEnd(code, false);
	}

	private void writeCode(Code.SubString code) {
		throw new NotImplementedException();
	}

	private void writeCode(Code.SubList code) {
		throw new NotImplementedException();
	}

	private void writeCode(Code.Return code) {
		writeIndents();
		writer.print("return ");
		typeWriter.writeRHS(code.operand);
		writeLineEnd(code);
	}

	private void writeCode(Code.Not code) {
		writeIndents();
		typeWriter.writeLHS(code.target, code.type);
		writer.print(" = !");
		typeWriter.writeRHS(code.operand);
		writeLineEnd(code);
	}

	private void writeCode(Code.NewTuple code) {
		throw new NotSupportedByGPGPUException("Dynamic memory allocation not supported");
	}

	private void writeCode(Code.NewSet code) {
		throw new NotSupportedByGPGPUException("Dynamic memory allocation not supported");
	}

	private void writeCode(Code.NewRecord code) {
		throw new NotSupportedByGPGPUException("Dynamic memory allocation not supported");
	}

	private void writeCode(Code.NewMap code) {
		throw new NotSupportedByGPGPUException("Dynamic memory allocation not supported");
	}

	private void writeCode(Code.NewObject code) {
		throw new NotSupportedByGPGPUException("Dynamic memory allocation not supported");
	}

	private void writeCode(Code.NewList code) {
		throw new NotSupportedByGPGPUException("Dynamic memory allocation not supported");
	}

	private void writeCode(Code.Move code) {
		writeIndents();
		typeWriter.writeLHS(code.target, code.type);
		writer.print(" = ");
		typeWriter.writeRHS(code.operand);
		writeLineEnd(code);
	}

	private void writeCode(Code.LengthOf code) {
		writeIndents();
		typeWriter.writeLHS(code.target, code.type.element());
		writer.print(" = ");
		typeWriter.writeListLength(code.operand);
		writeLineEnd(code);
	}

	private void writeCode(Code.Lambda code) {
		throw new NotImplementedException();
	}

	private void writeCode(Code.Invoke code) {
		writeIndents();
		if(code.type.ret() != Type.T_VOID) {
			typeWriter.writeLHS(code.target, code.type.ret());
			writer.print(" = ");
		}
		writer.print(functionTranslator.translateFunctionName(code));
		writer.print('(');
		String sep = "";
		for(int arg : code.operands) {
			writer.print(sep);
			sep = ", ";
			typeWriter.writeRHS(arg);
		}
		writer.print(')');
		writeLineEnd(code);
	}

	private void writeCode(Code.Invert code) {
		writeIndents();
		typeWriter.writeLHS(code.target, code.type);
		writer.print(" = ~");
		typeWriter.writeRHS(code.operand);
		writeLineEnd(code);
	}

	private void writeCode(Code.IndirectInvoke code) {
		throw new NotSupportedByGPGPUException("Function pointers not supported");
	}

	private void writeCode(final Code.IndexOf code) {
		writeIndents();
		typeWriter.writeLHS(code.target, code.type.element());
		writer.print(" = ");
		typeWriter.writeListAccessor(code.leftOperand, new ExpressionWriter() {
			@Override
			public void writeExpression(PrintWriter writer) {
				typeWriter.writeRHS(code.rightOperand);
			}
		});
		writeLineEnd(code);
	}

	private void writeCode(Code.IfIs code) {
		throw new NotImplementedException();
	}

	private void writeCode(Code.If code) {
		writeIndents();
		writer.print("if(");
		writeComparitor(code.op, code.leftOperand, code.rightOperand);
		writer.print(") { goto ");
		writer.print(code.target);
		writer.print("; }");
		writeLineEnd(code, false);
	}

	private void writeCode(Code.FieldLoad code) {
		throw new NotImplementedException();
	}

	private void writeCode(Code.Assume code) {
		// TODO Auto-generated method stub
		
		writeIndents();
		writeLineEnd(code, false);
	}

	private void writeCode(Code.Dereference code) {
		throw new NotImplementedException();
	}

	private void writeCode(Code.Debug code) {
		throw new NotImplementedException();
	}

	private void writeCode(Code.Convert code) {
		throw new NotImplementedException();
	}

	private void writeCode(Code.Assign code) {
		writeIndents();
		typeWriter.writeLHS(code.target, code.type);
		writer.print(" = ");
		typeWriter.writeRHS(code.operand);
		writeLineEnd(code);
	}

	private void writeCode(Code.Assert code) {
		// TODO Auto-generated method stub
		
		writeIndents();
		writeLineEnd(code, false);
	}

	private void writeCode(Code.Label code) {
		checkEndScope(code);
		writer.print(code.label);
		writer.print(':');
		writeLineEnd(code); // Good to write a semicolon, means we always have a statement to jump to
	}

	private void writeCode(Code.Goto code) {
		writeIndents();
		writer.print("goto ");
		writer.print(code.target);
		writeLineEnd(code);
	}

	private void writeCode(Code.Const code) {
		writeIndents();
		typeWriter.writeLHS(code.target, code.constant.type());
		writer.print(" = ");
		writer.print(code.constant); // FIXME: should be looking at types
		writeLineEnd(code);
	}

	private void writeCode(Code.TryCatch code) {
		throw new NotImplementedException();
	}

	private void writeCode(Code.Nop code) {
		writeIndents();
		writeLineEnd(code);
	}

	private void writeCode(Code.Loop code) {
		writeIndents();
		writer.print("while(1) {");
		writeLineEnd(code, false);
		beginScope(code.target);
	}

	private void writeCode(Code.ForAll code) {
		// Write loop
		writeIndents();
		forallIndexCount++;
		final String indexVar = "forallIndex"+forallIndexCount;
		writer.print("for(int ");
		writer.print(indexVar);
		writer.print(" = 0; ");
		writer.print(indexVar);
		writer.print(" < ");
		typeWriter.writeListLength(code.sourceOperand);
		writer.print("; ++");
		writer.print(indexVar);
		writer.print(") {");
		beginScope(code.target);
		writeLineEnd(code, false);
		
		// Write whiley index variable - i.e. use indexVar to access from source
		writeIndents();
		typeWriter.writeLHS(code.indexOperand, code.type.element());
		writer.print(" = ");
		typeWriter.writeListAccessor(code.sourceOperand, new ExpressionWriter() {
			@Override
			public void writeExpression(PrintWriter writer) {
				writer.print(indexVar);
			}
		});
		writeLineEnd(code);
	}
	
	public void writePreamble() {
		typeWriter.writeOutstandingBoilerplate();
	}
	
	public void writeFunctionDecleration(String attributes, Type.Leaf type, String name, List<Argument> arguments) {
		if(attributes != null) {
			writer.print(attributes);
			writer.print(' ');
		}
		typeWriter.writeReturnType(type);
		writer.print(' ');
		writer.print(name);
		writer.print('(');
		
		String seperator = "";
		for(Argument arg : arguments) {
			writer.print(seperator);
			seperator = ", ";
			
			typeWriter.writeArgDecl(arg);
		}
		
		writer.print(')');
	}
}
