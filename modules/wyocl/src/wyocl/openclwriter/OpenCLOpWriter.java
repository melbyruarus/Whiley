package wyocl.openclwriter;

import java.io.PrintWriter;
import java.io.StringWriter;
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
	public final OpenCLTypeWriter typeWriter = new OpenCLTypeWriter();;
	private int indentationLevel = 1;
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
	
	public OpenCLOpWriter(FunctionInvokeTranslator functionTranslator) {
		this.functionTranslator = functionTranslator;
	}

	public void writeBlock(List<Block.Entry> filteredEntries, PrintWriter bodyWriter) {
		indentationLevel = 1;
		StringWriter sw = new StringWriter();
		PrintWriter writer = new PrintWriter(sw);
		
		if(filteredEntries.size() > 0) {
			Block.Entry first = filteredEntries.get(0);
			if(first.code instanceof Code.Loop) {
				filteredEntries = filteredEntries.subList(1, filteredEntries.size());
				
				if(first.code instanceof Code.ForAll) {
					Code.ForAll forAll = (ForAll)first.code;
					writeIndents(writer);
					typeWriter.writeLHS(forAll.indexOperand, forAll.type.element(), writer);
					writer.print(" = ");
					typeWriter.writeListAccessor(forAll.sourceOperand, new ExpressionWriter() {
						@Override
						public void writeExpression(PrintWriter writer) {
							writeKernelGlobalIndex(0, writer); // TODO: Support multiple dimensions
						}
					}, writer);
					writer.println("; // Get work item");
				}
				else {
					throw new NotImplementedException();
					
					// TODO: implement
				}
			}
		}
		
		for(Block.Entry e : filteredEntries) {
			writeBlockEntry(e, writer);
		}
		
		bodyWriter.print(typeWriter.boilerPlate());
		bodyWriter.print(sw.toString());
	}
	
	private void writeKernelGlobalIndex(int dimension, PrintWriter writer) {
		writer.print("get_global_id(");
		writer.print(dimension);
		writer.print(')');
	}
	
	private void writeBlockEntry(Block.Entry entry, PrintWriter writer) {
		Code code = entry.code;
		if(code instanceof Code.ForAll) {
			writeCode((Code.ForAll)code, writer);
		}
		else if(code instanceof Code.Loop) { // Must be after Code.ForAll
			writeCode((Code.Loop)code, writer);
		}
		else if(code instanceof Code.Nop) {
			writeCode((Code.Nop)code, writer);
		}
		else if(code instanceof Code.TryCatch) {
			writeCode((Code.TryCatch)code, writer);
		}
		else if(code instanceof Code.Const) {
			writeCode((Code.Const)code, writer);
		}
		else if(code instanceof Code.Goto) {
			writeCode((Code.Goto)code, writer);
		}
		else if(code instanceof Code.TryEnd) { // Must be before Code.Label
			writeCode((Code.TryEnd)code, writer);
		}
		else if(code instanceof Code.Label) {
			writeCode((Code.Label)code, writer);
		}
		else if(code instanceof Code.Assert) {
			writeCode((Code.Assert)code, writer);
		}
		else if(code instanceof Code.Assign) {
			writeCode((Code.Assign)code, writer);
		}
		else if(code instanceof Code.Convert) {
			writeCode((Code.Convert)code, writer);
		}
		else if(code instanceof Code.Debug) {
			writeCode((Code.Debug)code, writer);
		}
		else if(code instanceof Code.Dereference) {
			writeCode((Code.Dereference)code, writer);
		}
		else if(code instanceof Code.Assume) {
			writeCode((Code.Assume)code, writer);
		}
		else if(code instanceof Code.FieldLoad) {
			writeCode((Code.FieldLoad)code, writer);
		}
		else if(code instanceof Code.If) {
			writeCode((Code.If)code, writer);
		}
		else if(code instanceof Code.IfIs) {
			writeCode((Code.IfIs)code, writer);
		}
		else if(code instanceof Code.IndexOf) {
			writeCode((Code.IndexOf)code, writer);
		}
		else if(code instanceof Code.IndirectInvoke) {
			writeCode((Code.IndirectInvoke)code, writer);
		}
		else if(code instanceof Code.Invert) {
			writeCode((Code.Invert)code, writer);
		}
		else if(code instanceof Code.Invoke) {
			writeCode((Code.Invoke)code, writer);
		}
		else if(code instanceof Code.Lambda) {
			writeCode((Code.Lambda)code, writer);
		}
		else if(code instanceof Code.LengthOf) {
			writeCode((Code.LengthOf)code, writer);
		}
		else if(code instanceof Code.Move) {
			writeCode((Code.Move)code, writer);
		}
		else if(code instanceof Code.NewList) {
			writeCode((Code.NewList)code, writer);
		}
		else if(code instanceof Code.NewMap) {
			writeCode((Code.NewMap)code, writer);
		}
		else if(code instanceof Code.NewObject) {
			writeCode((Code.NewObject)code, writer);
		}
		else if(code instanceof Code.NewRecord) {
			writeCode((Code.NewRecord)code, writer);
		}
		else if(code instanceof Code.NewSet) {
			writeCode((Code.NewSet)code, writer);
		}
		else if(code instanceof Code.NewTuple) {
			writeCode((Code.NewTuple)code, writer);
		}
		else if(code instanceof Code.Not) {
			writeCode((Code.Not)code, writer);
		}
		else if(code instanceof Code.Return) {
			writeCode((Code.Return)code, writer);
		}
		else if(code instanceof Code.SubList) {
			writeCode((Code.SubList)code, writer);
		}
		else if(code instanceof Code.SubString) {
			writeCode((Code.SubString)code, writer);
		}
		else if(code instanceof Code.Switch) {
			writeCode((Code.Switch)code, writer);
		}
		else if(code instanceof Code.Throw) {
			writeCode((Code.Throw)code, writer);
		}
		else if(code instanceof Code.TupleLoad) {
			writeCode((Code.TupleLoad)code, writer);
		}
		else if(code instanceof Code.Update) {
			writeCode((Code.Update)code, writer);
		}
		else if(code instanceof Code.Void) {
			writeCode((Code.Void)code, writer);
		}
		else if(code instanceof Code.BinArithOp) {
			writeCode((Code.BinArithOp)code, writer);
		}
		else if(code instanceof Code.BinListOp) {
			writeCode((Code.BinListOp)code, writer);
		}
		else if(code instanceof Code.BinSetOp) {
			writeCode((Code.BinSetOp)code, writer);
		}
		else if(code instanceof Code.BinStringOp) {
			writeCode((Code.BinStringOp)code, writer);
		}
		else if(code instanceof Code.UnArithOp) {
			writeCode((Code.UnArithOp)code, writer);
		}
		else {
			throw new RuntimeException("Unknown bytecode encountered: "+code+" ("+code.getClass()+")");
		}
	}

	private void writeIndents(PrintWriter writer) {
		Utils.writeIndents(writer, indentationLevel);
	}
	
	private void writePrimitiveBinArithOp(Code.BinArithKind kind, Type.Leaf type, ExpressionWriter lhs, ExpressionWriter rhs, PrintWriter writer) {
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
				writer.print("%");
				writer.print(' ');
				rhs.writeExpression(writer);
		}
		writer.print(')');
	}
	
	private void writePrimitiveUnArithOp(UnArithKind kind, Leaf type, ExpressionWriter expressionWriter, PrintWriter writer) {
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
	
	private void writeComparitor(Comparator op, int leftOperand, int rightOperand, PrintWriter writer) {
		writer.print('(');
		switch(op) {
			case SUBSET:
				throw new NotImplementedException();
			case SUBSETEQ:
				throw new NotImplementedException();
			case ELEMOF:
				throw new NotImplementedException();
			case EQ:
				typeWriter.writeRHS(leftOperand, writer);
				writer.print(" == ");
				typeWriter.writeRHS(rightOperand, writer);
				break;
			case GT:
				typeWriter.writeRHS(leftOperand, writer);
				writer.print(" > ");
				typeWriter.writeRHS(rightOperand, writer);
				break;
			case GTEQ:
				typeWriter.writeRHS(leftOperand, writer);
				writer.print(" >= ");
				typeWriter.writeRHS(rightOperand, writer);
				break;
			case LT:
				typeWriter.writeRHS(leftOperand, writer);
				writer.print(" < ");
				typeWriter.writeRHS(rightOperand, writer);
				break;
			case LTEQ:
				typeWriter.writeRHS(leftOperand, writer);
				writer.print(" <= ");
				typeWriter.writeRHS(rightOperand, writer);
				break;
			case NEQ:
				typeWriter.writeRHS(leftOperand, writer);
				writer.print(" != ");
				typeWriter.writeRHS(rightOperand, writer);
				break;
		}
		writer.print(')');
	}
	
	private void beginScope(String scopeEnd) {
		indentationLevel++;
		scopeEndLabels.add(scopeEnd);
	}
	
	private void checkEndScope(Code.Label code, PrintWriter writer) {
		while(scopeEndLabels.contains(code.label)) {
			indentationLevel--;
			writeIndents(writer);
			writer.print('}');
			writeLineEnd(code, false, writer);
			scopeEndLabels.remove(code.label);
		}
	}
	
	private void writeLineEnd(Code code, PrintWriter writer) {
		writeLineEnd(code, true, writer);
	}
	
	private void writeLineEnd(Code code, boolean semicolon, PrintWriter writer) {
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
	
	private void writeCode(final UnArithOp code, PrintWriter writer) {
		if(!(code.type instanceof Type.Leaf)){
			throw new RuntimeException("Dont know how to handle nonleaf types for UnArithOp: "+code.type);
		}
		
		writeIndents(writer);
		typeWriter.writeLHS(code.target, code.type, writer);
		writer.print(" = ");
		writePrimitiveUnArithOp(code.kind, (Type.Leaf)code.type, new ExpressionWriter() {
			@Override
			public void writeExpression(PrintWriter writer) {
				typeWriter.writeRHS(code.operand, writer);
			}
		}, writer);
		writeLineEnd(code, writer);
	}

	private void writeCode(Code.BinStringOp code, PrintWriter writer) {
		throw new NotImplementedException();
	}

	private void writeCode(Code.BinSetOp code, PrintWriter writer) {
		throw new NotImplementedException();
	}

	private void writeCode(Code.BinListOp code, PrintWriter writer) {
		throw new NotImplementedException();
	}

	private void writeCode(final Code.BinArithOp code, PrintWriter writer) {
		if(!(code.type instanceof Type.Leaf)){
			throw new RuntimeException("Dont know how to handle nonleaf types for BinArithOp: "+code.type);
		}
		
		writeIndents(writer);
		typeWriter.writeLHS(code.target, code.type, writer);
		writer.print(" = ");
		writePrimitiveBinArithOp(code.kind, (Type.Leaf)code.type, new ExpressionWriter() {
			
			@Override
			public void writeExpression(PrintWriter writer) {
				typeWriter.writeRHS(code.leftOperand, writer);
			}
		}, new ExpressionWriter() {
			
			@Override
			public void writeExpression(PrintWriter writer) {
				typeWriter.writeRHS(code.rightOperand, writer);
			}
		}, writer);
		writeLineEnd(code, writer);
	}

	private void writeCode(Code.Void code, PrintWriter writer) {
		writeIndents(writer);
		writeLineEnd(code, writer);
	}

	private void writeCode(Code.Update code, PrintWriter writer) {
		// TODO: implement this properly
		if(code.type instanceof Type.List) {
			for(Code.LVal<Type.EffectiveList> _lv : code) {
				final Code.ListLVal lv = (Code.ListLVal)_lv;
				
				writeIndents(writer);
				typeWriter.writeListAccessor(code.target, new ExpressionWriter() {
					@Override
					public void writeExpression(PrintWriter writer) {
						typeWriter.writeRHS(lv.indexOperand, writer);
					}
				}, writer);
				writer.print(" = ");
				typeWriter.writeRHS(code.operand, writer); // TODO: support more than one
				writeLineEnd(code, writer);
			}
		}
		else {
			throw new NotImplementedException();
		}
	}

	private void writeCode(final Code.TupleLoad code, PrintWriter writer) {
		writeIndents(writer);
		typeWriter.writeLHS(code.target, code.type.element(code.index), writer);
		writer.print(" = ");
		typeWriter.writeTupleAccessor(code.operand, new ExpressionWriter() {
			@Override
			public void writeExpression(PrintWriter writer) {
				writer.print(code.index);
			}
		}, writer);
		writeLineEnd(code, writer);
	}

	private void writeCode(Code.Throw code, PrintWriter writer) {
		throw new NotImplementedException();
	}

	private void writeCode(Code.Switch code, PrintWriter writer) {
		writeIndents(writer);
		for(Pair<Constant, String> caseStatement : code.branches) {
			writer.print("if(");
			writer.print(caseStatement.first());
			writer.print(" == ");
			typeWriter.writeRHS(code.operand, writer);
			writer.print(") { goto ");
			writer.print(caseStatement.second());
			writer.print("; }");
			writeLineEnd(code, false, writer);
			writeIndents(writer);
			writer.print("else ");
		}
		writer.print("{ goto ");
		writer.print(code.defaultTarget);
		writer.print("; }");
		writeLineEnd(code, false, writer);
	}

	private void writeCode(Code.SubString code, PrintWriter writer) {
		throw new NotImplementedException();
	}

	private void writeCode(Code.SubList code, PrintWriter writer) {
		throw new NotImplementedException();
	}

	private void writeCode(Code.Return code, PrintWriter writer) {
		writeIndents(writer);
		writer.print("return ");
		typeWriter.writeRHS(code.operand, writer);
		writeLineEnd(code, writer);
	}

	private void writeCode(Code.Not code, PrintWriter writer) {
		writeIndents(writer);
		typeWriter.writeLHS(code.target, code.type, writer);
		writer.print(" = !");
		typeWriter.writeRHS(code.operand, writer);
		writeLineEnd(code, writer);
	}

	private void writeCode(Code.NewTuple code, PrintWriter writer) {
		throw new NotSupportedByGPGPUException("Dynamic memory allocation not supported");
	}

	private void writeCode(Code.NewSet code, PrintWriter writer) {
		throw new NotSupportedByGPGPUException("Dynamic memory allocation not supported");
	}

	private void writeCode(Code.NewRecord code, PrintWriter writer) {
		throw new NotSupportedByGPGPUException("Dynamic memory allocation not supported");
	}

	private void writeCode(Code.NewMap code, PrintWriter writer) {
		throw new NotSupportedByGPGPUException("Dynamic memory allocation not supported");
	}

	private void writeCode(Code.NewObject code, PrintWriter writer) {
		throw new NotSupportedByGPGPUException("Dynamic memory allocation not supported");
	}

	private void writeCode(Code.NewList code, PrintWriter writer) {
		throw new NotSupportedByGPGPUException("Dynamic memory allocation not supported");
	}

	private void writeCode(Code.Move code, PrintWriter writer) {
		writeIndents(writer);
		typeWriter.writeLHS(code.target, code.type, writer);
		writer.print(" = ");
		typeWriter.writeRHS(code.operand, writer);
		writeLineEnd(code, writer);
	}

	private void writeCode(Code.LengthOf code, PrintWriter writer) {
		writeIndents(writer);
		typeWriter.writeLHS(code.target, code.type.element(), writer);
		writer.print(" = ");
		typeWriter.writeListLength(code.operand, writer);
		writeLineEnd(code, writer);
	}

	private void writeCode(Code.Lambda code, PrintWriter writer) {
		throw new NotImplementedException();
	}

	private void writeCode(Code.Invoke code, PrintWriter writer) {
		writeIndents(writer);
		if(code.type.ret() != Type.T_VOID) {
			typeWriter.writeLHS(code.target, code.type.ret(), writer);
			writer.print(" = ");
		}
		writer.print(functionTranslator.translateFunctionName(code));
		writer.print('(');
		String sep = "";
		for(int arg : code.operands) {
			writer.print(sep);
			sep = ", ";
			typeWriter.writeRHS(arg, writer);
		}
		writer.print(')');
		writeLineEnd(code, writer);
	}

	private void writeCode(Code.Invert code, PrintWriter writer) {
		writeIndents(writer);
		typeWriter.writeLHS(code.target, code.type, writer);
		writer.print(" = ~");
		typeWriter.writeRHS(code.operand, writer);
		writeLineEnd(code, writer);
	}

	private void writeCode(Code.IndirectInvoke code, PrintWriter writer) {
		throw new NotSupportedByGPGPUException("Function pointers not supported");
	}

	private void writeCode(final Code.IndexOf code, PrintWriter writer) {
		writeIndents(writer);
		typeWriter.writeLHS(code.target, code.type.element(), writer);
		writer.print(" = ");
		typeWriter.writeListAccessor(code.leftOperand, new ExpressionWriter() {
			@Override
			public void writeExpression(PrintWriter writer) {
				typeWriter.writeRHS(code.rightOperand, writer);
			}
		}, writer);
		writeLineEnd(code, writer);
	}

	private void writeCode(Code.IfIs code, PrintWriter writer) {
		throw new NotImplementedException();
	}

	private void writeCode(Code.If code, PrintWriter writer) {
		writeIndents(writer);
		writer.print("if(");
		writeComparitor(code.op, code.leftOperand, code.rightOperand, writer);
		writer.print(") { goto ");
		writer.print(code.target);
		writer.print("; }");
		writeLineEnd(code, false, writer);
	}

	private void writeCode(Code.FieldLoad code, PrintWriter writer) {
		throw new NotImplementedException();
	}

	private void writeCode(Code.Assume code, PrintWriter writer) {
		// TODO Auto-generated method stub
		
		writeIndents(writer);
		writeLineEnd(code, false, writer);
	}

	private void writeCode(Code.Dereference code, PrintWriter writer) {
		throw new NotImplementedException();
	}

	private void writeCode(Code.Debug code, PrintWriter writer) {
		throw new NotImplementedException();
	}

	private void writeCode(Code.Convert code, PrintWriter writer) {
		throw new NotImplementedException();
	}

	private void writeCode(Code.Assign code, PrintWriter writer) {
		writeIndents(writer);
		typeWriter.writeLHS(code.target, code.type, writer);
		writer.print(" = ");
		typeWriter.writeRHS(code.operand, writer);
		writeLineEnd(code, writer);
	}

	private void writeCode(Code.Assert code, PrintWriter writer) {
		// TODO Auto-generated method stub
		
		writeIndents(writer);
		writeLineEnd(code, false, writer);
	}

	private void writeCode(Code.Label code, PrintWriter writer) {
		checkEndScope(code, writer);
		writer.print(code.label);
		writer.print(':');
		writeLineEnd(code, writer); // Good to write a semicolon, means we always have a statement to jump to
	}

	private void writeCode(Code.Goto code, PrintWriter writer) {
		writeIndents(writer);
		writer.print("goto ");
		writer.print(code.target);
		writeLineEnd(code, writer);
	}

	private void writeCode(Code.Const code, PrintWriter writer) {
		writeIndents(writer);
		typeWriter.writeLHS(code.target, code.constant.type(), writer);
		writer.print(" = ");
		writer.print(code.constant); // FIXME: should be looking at types
		writeLineEnd(code, writer);
	}

	private void writeCode(Code.TryCatch code, PrintWriter writer) {
		throw new NotImplementedException();
	}

	private void writeCode(Code.Nop code, PrintWriter writer) {
		writeIndents(writer);
		writeLineEnd(code, writer);
	}

	private void writeCode(Code.Loop code, PrintWriter writer) {
		writeIndents(writer);
		writer.print("while(1) {");
		writeLineEnd(code, false, writer);
		beginScope(code.target);
	}

	private void writeCode(Code.ForAll code, PrintWriter writer) {
		// Write loop
		writeIndents(writer);
		forallIndexCount++;
		final String indexVar = "forallIndex"+forallIndexCount;
		writer.print("for(int ");
		writer.print(indexVar);
		writer.print(" = 0; ");
		writer.print(indexVar);
		writer.print(" < ");
		typeWriter.writeListLength(code.sourceOperand, writer);
		writer.print("; ++");
		writer.print(indexVar);
		writer.print(") {");
		beginScope(code.target);
		writeLineEnd(code, false, writer);
		
		// Write whiley index variable - i.e. use indexVar to access from source
		writeIndents(writer);
		typeWriter.writeLHS(code.indexOperand, code.type.element(), writer);
		writer.print(" = ");
		typeWriter.writeListAccessor(code.sourceOperand, new ExpressionWriter() {
			@Override
			public void writeExpression(PrintWriter writer) {
				writer.print(indexVar);
			}
		}, writer);
		writeLineEnd(code, writer);
	}
	
	public void writeFunctionDecleration(String attributes, Type.Leaf type, String name, List<Argument> arguments, PrintWriter writer) {
		if(attributes != null) {
			writer.print(attributes);
			writer.print(' ');
		}
		typeWriter.writeReturnType(type, writer);
		writer.print(' ');
		writer.print(name);
		writer.print('(');
		
		String seperator = "";
		for(Argument arg : arguments) {
			writer.print(seperator);
			seperator = ", ";
			
			typeWriter.writeArgDecl(arg, writer);
		}
		
		writer.print(')');
	}
}
