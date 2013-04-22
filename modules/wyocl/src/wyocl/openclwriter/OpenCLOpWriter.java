package wyocl.openclwriter;

import java.io.PrintWriter;
import java.util.List;

import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import wyil.lang.Block;
import wyil.lang.Code;
import wyil.lang.Code.ForAll;
import wyil.lang.Type;
import wyocl.openclwriter.OpenCLTypeWriter.ExpressionWriter;

public class OpenCLOpWriter {
	private static final String GPGPU_RUNTIME_PREFIX = "whiley_gpgpu_runtime_";
	private static final boolean WRITE_BYTECODES_TO_COMMENTS = true;
	public final OpenCLTypeWriter typeWriter = new OpenCLTypeWriter(this);
	private int indentationLevel = 1;
	protected final PrintWriter writer;
	
	public static void writeRuntime(PrintWriter writer) { // TODO: this should be loaded in from a file or something
		writer.println();
		writer.println("// Beginning whiley GPGPU runtime library");
		writer.println("int whiley_gpgpu_runtime_rem_int(int lhs, int rhs) { return (lhs - rhs * (lhs / rhs)); }");
		writer.println("// Ending whiley GPGPU runtime library");
		writer.println();
	}
	
	public OpenCLOpWriter(PrintWriter writer) {
		this.writer = writer;
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
		else if(code instanceof Code.LoopEnd) { // Must be before Code.Label
			writeCode((Code.LoopEnd)code);
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
				writer.print(GPGPU_RUNTIME_PREFIX);
				writer.print("rem");
				writer.print(OpenCLTypeWriter.primitiveType(type));
				writer.print('(');
				lhs.writeExpression(writer);
				writer.print(", ");
				rhs.writeExpression(writer);
				writer.print(')');
		}
		writer.print(')');
	}
	
	private void writeLineEnd(Code code) {
		if(WRITE_BYTECODES_TO_COMMENTS) {
			writer.print("; // ");
			writer.println(code);
		}
		else {
			writer.println(';');
		}
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

	private void writeCode(Code.TupleLoad code) {
		throw new NotImplementedException();
	}

	private void writeCode(Code.Throw code) {
		throw new NotImplementedException();
	}

	private void writeCode(Code.Switch code) {
		throw new NotImplementedException();
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
		throw new NotImplementedException();
	}

	private void writeCode(Code.NewSet code) {
		throw new NotImplementedException();
	}

	private void writeCode(Code.NewRecord code) {
		throw new NotImplementedException();
	}

	private void writeCode(Code.NewMap code) {
		throw new NotImplementedException();
	}

	private void writeCode(Code.NewObject code) {
		throw new NotImplementedException();
	}

	private void writeCode(Code.NewList code) {
		throw new NotImplementedException();
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
		throw new NotImplementedException();
	}

	private void writeCode(Code.Invert code) {
		writeIndents();
		typeWriter.writeLHS(code.target, code.type);
		writer.print(" = ~");
		typeWriter.writeRHS(code.operand);
		writeLineEnd(code);
	}

	private void writeCode(Code.IndirectInvoke code) {
		throw new NotImplementedException();
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
		// TODO Auto-generated method stub
		
		writeIndents();
		writeLineEnd(code);
	}

	private void writeCode(Code.FieldLoad code) {
		throw new NotImplementedException();
	}

	private void writeCode(Code.Assume code) {
		// TODO Auto-generated method stub
		
		writeIndents();
		writeLineEnd(code);
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
				writeLineEnd(code);
	}

	private void writeCode(Code.Label code) {
		writer.print(code.label);
		writer.print(':');
		writeLineEnd(code);
	}

	private void writeCode(Code.Goto code) {
		// TODO Auto-generated method stub
		
		writeIndents();
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
		// TODO Auto-generated method stub
		
		writeIndents();
		writeLineEnd(code);
	}
	
	private void writeCode(Code.LoopEnd code) {
		writer.print(code.label);
		writer.print(':');
		writeLineEnd(code);
	}

	private void writeCode(Code.ForAll code) {
		// TODO Auto-generated method stub
		
		writeIndents();
		writeLineEnd(code);
	}
	
	public void writePreamble() {
		typeWriter.writeOutstandingBoilerplate();
	}
}
