package wyocl.ar;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import wybs.util.Pair;
import wyil.lang.Code;
import wyil.lang.Constant;
import wyil.lang.Type;
import wyil.lang.Type.EffectiveCollection;
import wyil.lang.Type.EffectiveTuple;
import wyocl.ar.ARGenerator.CFGNode;
import wyocl.ar.ARGenerator.DFGNode;

public abstract class Bytecode {
	public CFGNode cfgNode;
	public final Map<Integer, DFGNode> writtenDFGNodes = new HashMap<Integer, DFGNode>();
	public final Map<Integer, DFGNode> readDFGNodes = new HashMap<Integer, DFGNode>();
	
	public static class TypeDescription {
		private final RootType rootType;
		private final Object type;
		
		enum RootType {
			TYPE,
			EFFECTIVE_COLLECTION,
			EFFECTIVE_TUPLE
		}
		
		public TypeDescription(Type type) {this.type = type; rootType = RootType.TYPE;}
		public TypeDescription(EffectiveCollection type) {this.type = type; rootType = RootType.EFFECTIVE_COLLECTION;}
		public TypeDescription(EffectiveTuple type) {this.type = type; rootType = RootType.EFFECTIVE_TUPLE;}
		
		public Type getType() {
			return (Type)type;
		}
		
		public EffectiveCollection getEffectiveCollection() {
			return (EffectiveCollection)type;
		}
		
		public EffectiveTuple getEffectiveTuple() {
			return (EffectiveTuple)type;
		}
		
		public RootType getRootType() {
			return rootType;
		}
	}
	
	public abstract void getRegisterSummary(Set<Pair<Integer, TypeDescription>> writtenRegisters, Set<Integer> readRegisters);
		
	public static interface Data {
	}
	
	public static interface Control {
	}
	
	public static interface Loop extends Bytecode.Control {
		String loopEndLabel();
	}
	
	public static interface Jump extends Bytecode.Control {
	}
	
	public static interface Target {
		public String name();
	}
	
	public static class Unary extends Bytecode implements Bytecode.Data {
		private final Code.UnArithOp code;
		
		public Unary(Code.UnArithOp code) { this.code = code; }

		@Override
		public void getRegisterSummary(Set<Pair<Integer, TypeDescription>> writtenRegisters, Set<Integer> readRegisters) {
			writtenRegisters.add(new Pair<Integer, TypeDescription>(code.target, new TypeDescription(code.type)));
			readRegisters.add(code.operand);
		}
	}
	
	public static class Binary extends Bytecode implements Bytecode.Data {
		private final Code.BinArithOp code;
		
		public Binary(Code.BinArithOp code) { this.code = code; }
		
		@Override
		public void getRegisterSummary(Set<Pair<Integer, TypeDescription>> writtenRegisters, Set<Integer> readRegisters) {
			writtenRegisters.add(new Pair<Integer, TypeDescription>(code.target, new TypeDescription(code.type)));
			readRegisters.add(code.leftOperand);
			readRegisters.add(code.rightOperand);
		}
	}
	
	public static class ConstLoad extends Bytecode implements Bytecode.Data {
		private final Code.Const code;
		
		public ConstLoad(Code.Const code) { this.code = code; }
		
		@Override
		public void getRegisterSummary(Set<Pair<Integer, TypeDescription>> writtenRegisters, Set<Integer> readRegisters) {
			writtenRegisters.add(new Pair<Integer, TypeDescription>(code.target, new TypeDescription(code.constant.type())));
		}
	}
	
	public static class Assign extends Bytecode implements Bytecode.Data {
		private final Code.Assign code;
		
		public Assign(Code.Assign code) { this.code = code; }
		
		@Override
		public void getRegisterSummary(Set<Pair<Integer, TypeDescription>> writtenRegisters, Set<Integer> readRegisters) {
			writtenRegisters.add(new Pair<Integer, TypeDescription>(code.target, new TypeDescription(code.type)));
			readRegisters.add(code.operand);
		}
	}
	
	public static class Move extends Bytecode implements Bytecode.Data {
		private final Code.Move code;
		
		public Move(Code.Move code) { this.code = code; }
		
		@Override
		public void getRegisterSummary(Set<Pair<Integer, TypeDescription>> writtenRegisters, Set<Integer> readRegisters) {
			writtenRegisters.add(new Pair<Integer, TypeDescription>(code.target, new TypeDescription(code.type)));
			readRegisters.add(code.operand);
		}
	}
	
	public static class Convert extends Bytecode implements Bytecode.Data {
		private final Code.Convert code;
		
		public Convert(Code.Convert code) { this.code = code; }
		
		@Override
		public void getRegisterSummary(Set<Pair<Integer, TypeDescription>> writtenRegisters, Set<Integer> readRegisters) {
			writtenRegisters.add(new Pair<Integer, TypeDescription>(code.target, new TypeDescription(code.type)));
			readRegisters.add(code.operand);
		}
	}
	
	public static class Load extends Bytecode implements Bytecode.Data {
		private final Code.IndexOf code;
		
		public Load(Code.IndexOf code) { this.code = code; }
		
		@Override
		public void getRegisterSummary(Set<Pair<Integer, TypeDescription>> writtenRegisters, Set<Integer> readRegisters) {
			writtenRegisters.add(new Pair<Integer, TypeDescription>(code.target, new TypeDescription(code.type)));
			readRegisters.add(code.leftOperand);
			readRegisters.add(code.rightOperand);
		}
	}
	
	public static class LengthOf extends Bytecode implements Bytecode.Data {
		private final Code.LengthOf code;
		
		public LengthOf(Code.LengthOf code) { this.code = code; }
		
		@Override
		public void getRegisterSummary(Set<Pair<Integer, TypeDescription>> writtenRegisters, Set<Integer> readRegisters) {
			writtenRegisters.add(new Pair<Integer, TypeDescription>(code.target, new TypeDescription(code.type)));
			readRegisters.add(code.operand);
		}
	}
	
	public static class TupleLoad extends Bytecode implements Bytecode.Data {
		private final Code.TupleLoad code;
		
		public TupleLoad(Code.TupleLoad code) { this.code = code; }
		
		@Override
		public void getRegisterSummary(Set<Pair<Integer, TypeDescription>> writtenRegisters, Set<Integer> readRegisters) {
			writtenRegisters.add(new Pair<Integer, TypeDescription>(code.target, new TypeDescription(code.type)));
			readRegisters.add(code.operand);
		}
	}
	
	public static class Update extends Bytecode implements Bytecode.Data {
		private final Code.Update code;
		
		public Update(Code.Update code) { this.code = code; }
		
		@Override
		public void getRegisterSummary(Set<Pair<Integer, TypeDescription>> writtenRegisters, Set<Integer> readRegisters) {
			writtenRegisters.add(new Pair<Integer, TypeDescription>(code.target, new TypeDescription(code.type)));
			readRegisters.add(code.operand);
			for(int i : code.operands) {
				readRegisters.add(i);
			}
		}
	}
	
	public static class For extends Bytecode implements Bytecode.Loop {
		private final Code.ForAll code;
		
		public For(Code.ForAll code) { this.code = code; }

		@Override
		public String loopEndLabel() {
			return code.target;
		}
		
		@Override
		public void getRegisterSummary(Set<Pair<Integer, TypeDescription>> writtenRegisters, Set<Integer> readRegisters) {
			writtenRegisters.add(new Pair<Integer, TypeDescription>(code.indexOperand, new TypeDescription(code.type)));
			readRegisters.add(code.sourceOperand);
		}
	}
	
	public static class While extends Bytecode implements Bytecode.Loop {
		private final Code.Loop code;
		
		public While(Code.Loop code) { this.code = code; }
		
		@Override
		public String loopEndLabel() {
			return code.target;
		}
		
		@Override
		public void getRegisterSummary(Set<Pair<Integer, TypeDescription>> writtenRegisters, Set<Integer> readRegisters) {
		}
	}
	
	public static class UnconditionalJump extends Bytecode implements Bytecode.Jump {
		private final Code.Goto code;
		
		public UnconditionalJump(Code.Goto code) { this.code = code; }

		public String target() {
			return code.target;
		}
		
		@Override
		public void getRegisterSummary(Set<Pair<Integer, TypeDescription>> writtenRegisters, Set<Integer> readRegisters) {
		}
	}
	
	public static class ConditionalJump extends Bytecode implements Bytecode.Jump {
		private final Code.If code;
		
		public ConditionalJump(Code.If code) { this.code = code; }

		public String conditionMetTarget() {
			return code.target;
		}
		
		@Override
		public void getRegisterSummary(Set<Pair<Integer, TypeDescription>> writtenRegisters, Set<Integer> readRegisters) {
			readRegisters.add(code.leftOperand);
			readRegisters.add(code.rightOperand);
		}
	}
	
	public static class Switch extends Bytecode implements Bytecode.Jump {
		private final Code.Switch code;
		
		public Switch(Code.Switch code) { this.code = code; }
		
		public List<Pair<Constant, String>> branchTargets() {
			return code.branches;
		}

		public String defaultTarget() {
			return code.defaultTarget;
		}
		
		@Override
		public void getRegisterSummary(Set<Pair<Integer, TypeDescription>> writtenRegisters, Set<Integer> readRegisters) {
			readRegisters.add(code.operand);
		}
	}
	
	public static class Label extends Bytecode implements Bytecode.Control, Bytecode.Target {
		private final Code.Label code;
		
		public Label(Code.Label code) { this.code = code; }

		@Override
		public String name() {
			return code.label;
		}
		
		@Override
		public void getRegisterSummary(Set<Pair<Integer, TypeDescription>> writtenRegisters, Set<Integer> readRegisters) {
		}
	}
	
	public static class LoopEnd extends Bytecode implements Bytecode.Control, Bytecode.Target {
		private final Code.LoopEnd code;
		
		public LoopEnd(Code.LoopEnd code) { this.code = code; }
		
		@Override
		public String name() {
			return code.label;
		}
		
		@Override
		public void getRegisterSummary(Set<Pair<Integer, TypeDescription>> writtenRegisters, Set<Integer> readRegisters) {
		}
	}
	
	public static class Return extends Bytecode implements Bytecode.Jump {
		private final Code.Return code;
		
		public Return(Code.Return code) { this.code = code; }
		
		@Override
		public void getRegisterSummary(Set<Pair<Integer, TypeDescription>> writtenRegisters, Set<Integer> readRegisters) {
			if(code.type != Type.T_VOID) {
				readRegisters.add(code.operand);
			}
		}
	}
	
	public static class Invoke extends Bytecode {
		private final Code.Invoke code;
		
		public Invoke(Code.Invoke code) { this.code = code; }
		
		@Override
		public void getRegisterSummary(Set<Pair<Integer, TypeDescription>> writtenRegisters, Set<Integer> readRegisters) {
			writtenRegisters.add(new Pair<Integer, TypeDescription>(code.target, new TypeDescription(code.type.ret())));
			for(int i : code.operands) {
				readRegisters.add(i);
			}
		}
	}
	
	public static Bytecode bytecodeForCode(Code code) {
		if(code instanceof Code.ForAll) {
			return new Bytecode.For((Code.ForAll)code);
		}
		else if(code instanceof Code.Loop) { // Must be after Code.ForAll
			return new Bytecode.While((Code.Loop)code);
		}
		else if(code instanceof Code.Nop) {
			return null;
		}
		else if(code instanceof Code.TryCatch) {
			throw new NotImplementedException();
		}
		else if(code instanceof Code.Const) {
			return new Bytecode.ConstLoad((Code.Const)code);
		}
		else if(code instanceof Code.Goto) {
			return new Bytecode.UnconditionalJump((Code.Goto)code);
		}
		else if(code instanceof Code.TryEnd) { // Must be before Code.Label
			throw new NotImplementedException();
		}
		else if(code instanceof Code.LoopEnd) {
			return new Bytecode.LoopEnd((Code.LoopEnd)code);
		}
		else if(code instanceof Code.Label) {
			return new Bytecode.Label((Code.Label)code);
		}
		else if(code instanceof Code.Assert) {
			return null; // FIXME: this should probably do something else
		}
		else if(code instanceof Code.Assign) {
			return new Bytecode.Assign((Code.Assign)code);
		}
		else if(code instanceof Code.Convert) {
			return new Bytecode.Convert((Code.Convert)code);
		}
		else if(code instanceof Code.Debug) {
			throw new NotImplementedException();
		}
		else if(code instanceof Code.Dereference) {
			throw new NotImplementedException();
		}
		else if(code instanceof Code.Assume) {
			return null; // FIXME: this should probably do something else
		}
		else if(code instanceof Code.FieldLoad) {
			throw new NotImplementedException();
		}
		else if(code instanceof Code.If) {
			return  new Bytecode.ConditionalJump((Code.If)code);
		}
		else if(code instanceof Code.IfIs) {
			throw new NotImplementedException();
		}
		else if(code instanceof Code.IndexOf) {
			return new Bytecode.Load((Code.IndexOf)code);
		}
		else if(code instanceof Code.IndirectInvoke) {
			throw new NotImplementedException();
		}
		else if(code instanceof Code.Invert) {
			throw new NotImplementedException();
		}
		else if(code instanceof Code.Invoke) {
			return new Bytecode.Invoke((Code.Invoke)code);
		}
		else if(code instanceof Code.Lambda) {
			throw new NotImplementedException();
		}
		else if(code instanceof Code.LengthOf) {
			return new Bytecode.LengthOf((Code.LengthOf)code);
		}
		else if(code instanceof Code.Move) {
			return new Bytecode.Move((Code.Move)code);
		}
		else if(code instanceof Code.NewList) {
			throw new NotImplementedException();
		}
		else if(code instanceof Code.NewMap) {
			throw new NotImplementedException();
		}
		else if(code instanceof Code.NewObject) {
			throw new NotImplementedException();
		}
		else if(code instanceof Code.NewRecord) {
			throw new NotImplementedException();
		}
		else if(code instanceof Code.NewSet) {
			throw new NotImplementedException();
		}
		else if(code instanceof Code.NewTuple) {
			throw new NotImplementedException();
		}
		else if(code instanceof Code.Not) {
			throw new NotImplementedException();
		}
		else if(code instanceof Code.Return) {
			return new Bytecode.Return((Code.Return)code);
		}
		else if(code instanceof Code.SubList) {
			throw new NotImplementedException();
		}
		else if(code instanceof Code.SubString) {
			throw new NotImplementedException();
		}
		else if(code instanceof Code.Switch) {
			return new Bytecode.Switch((Code.Switch)code);
		}
		else if(code instanceof Code.Throw) {
			throw new NotImplementedException();
		}
		else if(code instanceof Code.TupleLoad) {
			return new Bytecode.TupleLoad((Code.TupleLoad)code);
		}
		else if(code instanceof Code.Update) {
			return new Bytecode.Update((Code.Update)code);
		}
		else if(code instanceof Code.Void) {
			throw new NotImplementedException();
		}
		else if(code instanceof Code.BinArithOp) {
			return new Bytecode.Binary((Code.BinArithOp)code);
		}
		else if(code instanceof Code.BinListOp) {
			throw new NotImplementedException();
		}
		else if(code instanceof Code.BinSetOp) {
			throw new NotImplementedException();
		}
		else if(code instanceof Code.BinStringOp) {
			throw new NotImplementedException();
		}
		else if(code instanceof Code.UnArithOp) {
			return new Bytecode.Unary((Code.UnArithOp)code);
		}
		else {
			throw new RuntimeException("Unknown bytecode encountered: "+code+" ("+code.getClass()+")");
		}
	}
}