package wyocl.openclwriter;

import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import wyil.lang.Type;
import wyocl.filter.Argument;

public class OpenCLTypeWriter {
	private static final String VAR_PREFIX = "register_";
	private HashMap<Integer, VariableDefn> definedVariables = new HashMap<Integer, VariableDefn>();
	private LinkedList<String> outstandingBoilerplate = new LinkedList<String>();
	private final OpenCLOpWriter opWriter;
	
	public interface ExpressionWriter {
		public void writeExpression(PrintWriter writer);
	}
	
	private static abstract class VariableDefn {
		public final int register;
		
		public VariableDefn(int register) {
			this.register = register;
		}
		
		abstract public void writeDecleration(PrintWriter writer, List<String> boilerPlate);
		abstract public void writeAccessor(PrintWriter writer);
	}
	
	private static class PrimitiveDefn extends VariableDefn {
		public final Type.Leaf type;
		public final boolean asPointer;
		
		public PrimitiveDefn(int register, Type.Leaf type, boolean asPointer) {
			super(register);
			
			this.type = type;
			this.asPointer = asPointer;
		}

		@Override
		public void writeDecleration(PrintWriter writer, List<String> boilerPlate) {
			if(asPointer) {
				writer.print("__global ");
			}
			writer.print(primitiveType(type));
			writer.print(' ');
			if(asPointer) {
				writer.print('*');
			}
			writer.print(VAR_PREFIX);
			writer.print(register);
		}
		
		@Override
		public void writeAccessor(PrintWriter writer) {
			writer.print('(');
			if(asPointer) {
				writer.print('*');
			}
			writer.print(VAR_PREFIX);
			writer.print(register);
			writer.print(')');
		}
	}
	
	private static class ListDefn extends VariableDefn {
		private static final String SIZE_SUFFIX = "_size";
		public final Type.List type;
		
		public ListDefn(int register, Type.List type) {
			super(register);
			
			this.type = type;
		}

		@Override
		public void writeDecleration(PrintWriter writer, List<String> boilerPlate) {
			writer.print("__global ");
			// TODO: check for composite types
			if(!(type.element() instanceof Type.Leaf)) {
				throw new RuntimeException("Currently only support lists of primitive types, don't support: "+type);
			}
			writer.print(primitiveType((Type.Leaf)type.element()));
			writer.print(" *"+VAR_PREFIX);
			writer.print(register);
			
			boilerPlate.add("// Begin register "+register+" list unpacking");
			boilerPlate.add("int "+VAR_PREFIX+register+SIZE_SUFFIX+" = "+VAR_PREFIX+register+"?((__global int *)"+VAR_PREFIX+register+")[0]:0;");
			boilerPlate.add(VAR_PREFIX+register+" = (__global "+primitiveType((Type.Leaf)type.element())+" *)(((int *)"+VAR_PREFIX+register+") + 1);");
			boilerPlate.add("// End register "+register+" list unpacking");
		}
		
		@Override
		public void writeAccessor(PrintWriter writer) {
			writer.print('(');
			writer.print(VAR_PREFIX);
			writer.print(register);
			writer.print(')');
		}
		
		public void writeAccessor(ExpressionWriter indexExpression, PrintWriter writer) {
			writer.print('(');
			writer.print(VAR_PREFIX);
			writer.print(register);
			writer.print('[');
			indexExpression.writeExpression(writer);
			writer.print(']');
			writer.print(')');
		}
		
		public void writeLengthAccessor(PrintWriter writer) {
			writer.print('(');
			writer.print(VAR_PREFIX);
			writer.print(register);
			writer.print(SIZE_SUFFIX);
			writer.print(')');
		}
	}
	
	private static class TupleDefn extends VariableDefn {
		private static final String SIZE_SUFFIX = "_size";
		public final Type.Tuple type;
		
		public TupleDefn(int register, Type.Tuple type) {
			super(register);
			
			this.type = type;
		}

		@Override
		public void writeDecleration(PrintWriter writer, List<String> boilerPlate) {
			writer.print("__global ");
			// TODO: check for composite types
			if(!(type.element(0) instanceof Type.Leaf)) {
				throw new RuntimeException("Currently only support tuples of primitive types, don't support: "+type);
			}
			writer.print(primitiveType((Type.Leaf)type.element(0)));
			writer.print(" *"+VAR_PREFIX);
			writer.print(register);
			
			boilerPlate.add("// Begin register "+register+" tuple unpacking");
			boilerPlate.add("int "+VAR_PREFIX+register+SIZE_SUFFIX+" = "+VAR_PREFIX+register+"?((__global int *)"+VAR_PREFIX+register+")[0]:0;");
			boilerPlate.add(VAR_PREFIX+register+" = (__global "+primitiveType((Type.Leaf)type.element(0))+" *)(((int *)"+VAR_PREFIX+register+") + 1);");
			boilerPlate.add("// End register "+register+" tuple unpacking");
		}
		
		@Override
		public void writeAccessor(PrintWriter writer) {
			writer.print('(');
			writer.print(VAR_PREFIX);
			writer.print(register);
			writer.print(')');
		}
		
		public void writeAccessor(ExpressionWriter indexExpression, PrintWriter writer) {
			writer.print('(');
			writer.print(VAR_PREFIX);
			writer.print(register);
			writer.print('[');
			indexExpression.writeExpression(writer);
			writer.print(']');
			writer.print(')');
		}
	}
	
	public OpenCLTypeWriter(OpenCLOpWriter opWriter) {
		this.opWriter = opWriter;
	}

	protected static String primitiveType(Type.Leaf type) {
		if(type instanceof Type.Int) {
			return "int";
		}
		else if(type instanceof Type.Real) {
			return "float";
		}
		else if(type instanceof Type.Void) {
			return "void";
		}
		else {
			throw new RuntimeException("Unknown primitive type encountered: "+type);
		}
	}
	
	public void writeArgDecl(Argument arg) {
		writeVariableDecl(arg.type, !arg.isReadonly(), arg.register);
	}
	
	private void writeVariableDecl(Type type, boolean asPointer, int register) {
		if(type instanceof Type.List) {
			writeVariableDecl((Type.List)type, asPointer, register);
		}
		else if(type instanceof Type.Tuple) {
			writeVariableDecl((Type.Tuple)type, asPointer, register);
		}
		else if(type instanceof Type.Leaf) {
			writeVariableDecl((Type.Leaf)type, asPointer, register);
		}
		else {
			throw new RuntimeException("Unknown type encountered when writing type decleration: "+type);
		}
	}
	
	private void writeVariableDecl(Type.Leaf type, boolean asPointer, int register) {
		PrimitiveDefn prim = new PrimitiveDefn(register, type, asPointer);
		prim.writeDecleration(opWriter.writer, outstandingBoilerplate);
		definedVariables.put(register, prim);
	}

	private void writeVariableDecl(Type.List type, boolean asPointer, int register) {
		ListDefn list = new ListDefn(register, type);
		list.writeDecleration(opWriter.writer, outstandingBoilerplate);
		definedVariables.put(register, list);
	}
	
	private void writeVariableDecl(Type.Tuple type, boolean asPointer, int register) {
		TupleDefn tuple = new TupleDefn(register, type);
		tuple.writeDecleration(opWriter.writer, outstandingBoilerplate);
		definedVariables.put(register, tuple);
	}
	
	protected void writeLHS(int target, Type type) {
		if(definedVariables.containsKey(target)) {
			definedVariables.get(target).writeAccessor(opWriter.writer);
		}
		else {
			writeVariableDecl(type, false, target);
			// TODO: figure this out - need a callback?
			//writeOutstandingBoilerplate(writer);
		}
	}

	protected void writeListAccessor(int operand, ExpressionWriter indexWriter) {
		if(definedVariables.containsKey(operand)) {
			VariableDefn var = definedVariables.get(operand);
			if(var instanceof ListDefn) {
				((ListDefn)var).writeAccessor(indexWriter, opWriter.writer);
			}
			else {
				throw new RuntimeException("Indexing into non-list type: "+operand);
			}
		}
		else {
			throw new RuntimeException("Use of undefined list: "+operand);
		}
	}
	
	public void writeListLength(int operand) {
		if(definedVariables.containsKey(operand)) {
			VariableDefn var = definedVariables.get(operand);
			if(var instanceof ListDefn) {
				((ListDefn)var).writeLengthAccessor(opWriter.writer);
			}
			else {
				throw new RuntimeException("Lengthof non-list type: "+operand);
			}
		}
		else {
			throw new RuntimeException("Use of undefined list: "+operand);
		}
	}
	
	protected void writeTupleAccessor(int operand, ExpressionWriter indexWriter) {
		if(definedVariables.containsKey(operand)) {
			VariableDefn var = definedVariables.get(operand);
			if(var instanceof TupleDefn) {
				((TupleDefn)var).writeAccessor(indexWriter, opWriter.writer);
			}
			else {
				throw new RuntimeException("Indexing into non-list type: "+operand);
			}
		}
		else {
			throw new RuntimeException("Use of undefined list: "+operand);
		}
	}

	protected void writeRHS(int operand) {
		if(definedVariables.containsKey(operand)) {
			definedVariables.get(operand).writeAccessor(opWriter.writer);
		}
		else {
			throw new RuntimeException("Use of undefined variable: "+operand);
		}
	}
	
	protected void writeOutstandingBoilerplate() {
		Iterator<String> it = outstandingBoilerplate.iterator();
		opWriter.writer.println();
		opWriter.writeIndents();
		opWriter.writer.println("// ------- Beginning of boilerplate -------");
		while(it.hasNext()) {
			opWriter.writeIndents();
			opWriter.writer.println(it.next());
		}
		opWriter.writeIndents();
		opWriter.writer.println("// ---------- End of boilerplate ----------");
		opWriter.writer.println();
		outstandingBoilerplate.clear();
	}

	public void writeReturnType(Type.Leaf type) {
		opWriter.writer.print(primitiveType(type));
	}
}
