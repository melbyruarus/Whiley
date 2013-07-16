package wyocl.ar;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import wybs.lang.NameID;
import wybs.util.Pair;
import wyil.lang.Code;
import wyil.lang.Code.BinArithKind;
import wyil.lang.Code.UnArithKind;
import wyil.lang.Constant;
import wyil.lang.Type;

public abstract class Bytecode implements DFGNode.DFGNodeCause {
	public CFGNode cfgNode;
	public final Map<Integer, DFGNode> writtenDFGNodes = new HashMap<Integer, DFGNode>();
	public final Map<Integer, DFGNode> readDFGNodes = new HashMap<Integer, DFGNode>();
	private final Code wyilLangCode;
	
	protected abstract void getRegisterSummary(Set<Pair<Integer, Type>> writtenRegisters, Set<Integer> readRegisters);
	
	public String getCodeString() {
		return wyilLangCode.toString();
	}
	
	public String getDFGNodeSummaryString() {
		String ret = "written: ";
		for(DFGNode n : writtenDFGNodes.values()) {
			ret += n.getSummary() + " ";
		}
		ret += "read: ";
		for(DFGNode n : readDFGNodes.values()) {
			ret += n.getSummary() + " ";
		}
		return ret;
	}
	
	public Bytecode(Code code) {
		this.wyilLangCode = code;
	}
	
	public Code getWYILLangBytecode() {
		return wyilLangCode;
	}
	
	public static interface GPUSupportedBytecode {
	}
		
	public static interface Data {
	}
	
	public static interface Control {
	}
	
	public static interface Loop extends Bytecode.Control {
		String loopEndLabel();
	}
	
	public static interface Jump extends Bytecode.Control {
	}
	
	public static interface ConditionalJump extends Bytecode.Jump {
		public String getConditionMetTargetLabel();

		public void getCheckedRegisters(Set<Integer> checkedRegisters);
	}
	
	public static interface Call {
	}
	
	public static interface Exception {
	}
	
	public static interface Target {
		public String name();
	}
	
	public static class Unary extends Bytecode implements Bytecode.Data, Bytecode.GPUSupportedBytecode {
		private final Code.UnArithOp code;
		
		public Unary(Code.UnArithOp code) { super(code); this.code = code; }

		@Override
		protected void getRegisterSummary(Set<Pair<Integer, Type>> writtenRegisters, Set<Integer> readRegisters) {
			writtenRegisters.add(new Pair<Integer, Type>(code.target, (code.type)));
			readRegisters.add(code.operand);
		}

		public Type getType() {
			return code.type;
		}

		public int getTarget() {
			return code.target;
		}

		public UnArithKind getArithKind() {
			return code.kind;
		}

		public int getOperand() {
			return code.operand;
		}
	}
	
	public static class Binary extends Bytecode implements Bytecode.Data, Bytecode.GPUSupportedBytecode {
		private final Code.BinArithOp code;
		
		public Binary(Code.BinArithOp code) { super(code); this.code = code; }
		
		@Override
		protected void getRegisterSummary(Set<Pair<Integer, Type>> writtenRegisters, Set<Integer> readRegisters) {
			writtenRegisters.add(new Pair<Integer, Type>(code.target, (code.type)));
			readRegisters.add(code.leftOperand);
			readRegisters.add(code.rightOperand);
		}

		public Type getType() {
			return code.type;
		}

		public int getTarget() {
			return code.target;
		}

		public BinArithKind getArithKind() {
			return code.kind;
		}

		public int getLeftOperand() {
			return code.leftOperand;
		}

		public int getRightOperand() {
			return code.rightOperand;
		}
	}
	
	public static class ConstLoad extends Bytecode implements Bytecode.Data, Bytecode.GPUSupportedBytecode {
		private final Code.Const code;
		
		public ConstLoad(Code.Const code) { super(code); this.code = code; }
		
		@Override
		protected void getRegisterSummary(Set<Pair<Integer, Type>> writtenRegisters, Set<Integer> readRegisters) {
			writtenRegisters.add(new Pair<Integer, Type>(code.target, (code.constant.type())));
		}

		public Constant getConstant() {
			return code.constant;
		}
		
		public int getTarget() {
			return code.target;
		}
	}
	
	public static class Assign extends Bytecode implements Bytecode.Data, Bytecode.GPUSupportedBytecode {
		private final Code.Assign code;
		
		public Assign(Code.Assign code) { super(code); this.code = code; }
		
		@Override
		protected void getRegisterSummary(Set<Pair<Integer, Type>> writtenRegisters, Set<Integer> readRegisters) {
			writtenRegisters.add(new Pair<Integer, Type>(code.target, (code.type)));
			readRegisters.add(code.operand);
		}

		public int getTarget() {
			return code.target;
		}

		public Type getType() {
			return code.type;
		}

		public int getOperand() {
			return code.operand;
		}
	}
	
	public static class Move extends Bytecode implements Bytecode.Data, Bytecode.GPUSupportedBytecode {
		private final Code.Move code;
		
		public Move(Code.Move code) { super(code); this.code = code; }
		
		@Override
		protected void getRegisterSummary(Set<Pair<Integer, Type>> writtenRegisters, Set<Integer> readRegisters) {
			writtenRegisters.add(new Pair<Integer, Type>(code.target, (code.type)));
			readRegisters.add(code.operand);
		}

		public int getTarget() {
			return code.target;
		}

		public Type getType() {
			return code.type;
		}
		
		public int getOperand() {
			return code.operand;
		}
	}
	
	public static class Convert extends Bytecode implements Bytecode.Data, Bytecode.GPUSupportedBytecode {
		private final Code.Convert code;
		
		public Convert(Code.Convert code) { super(code); this.code = code; }
		
		@Override
		protected void getRegisterSummary(Set<Pair<Integer, Type>> writtenRegisters, Set<Integer> readRegisters) {
			writtenRegisters.add(new Pair<Integer, Type>(code.target, (code.type)));
			readRegisters.add(code.operand);
		}
		
		public int getTarget() {
			return code.target;
		}

		public Type getType() {
			return code.type;
		}
		
		public int getOperand() {
			return code.operand;
		}
	}
	
	public static class Load extends Bytecode implements Bytecode.Data, Bytecode.GPUSupportedBytecode {
		private final Code.IndexOf code;
		
		public Load(Code.IndexOf code) { super(code); this.code = code; }
		
		@Override
		protected void getRegisterSummary(Set<Pair<Integer, Type>> writtenRegisters, Set<Integer> readRegisters) {
			writtenRegisters.add(new Pair<Integer, Type>(code.target, (Type)code.type));
			readRegisters.add(code.leftOperand);
			readRegisters.add(code.rightOperand);
		}
		
		public int getTarget() {
			return code.target;
		}

		public Type.EffectiveIndexible getType() {
			return code.type;
		}

		public int getLeftOperand() {
			return code.leftOperand;
		}
		
		public int getRightOperand() {
			return code.rightOperand;
		}
	}
	
	public static class LengthOf extends Bytecode implements Bytecode.Data, Bytecode.GPUSupportedBytecode {
		private final Code.LengthOf code;
		
		public LengthOf(Code.LengthOf code) { super(code); this.code = code; }
		
		@Override
		protected void getRegisterSummary(Set<Pair<Integer, Type>> writtenRegisters, Set<Integer> readRegisters) {
			writtenRegisters.add(new Pair<Integer, Type>(code.target, (Type)code.type));
			readRegisters.add(code.operand);
		}
		
		public int getTarget() {
			return code.target;
		}
		
		public int getOperand() {
			return code.operand;
		}

		public Type.EffectiveCollection getType() {
			return code.type;
		}
	}
	
	public static class TupleLoad extends Bytecode implements Bytecode.Data, Bytecode.GPUSupportedBytecode {
		private final Code.TupleLoad code;
		
		public TupleLoad(Code.TupleLoad code) { super(code); this.code = code; }
		
		@Override
		protected void getRegisterSummary(Set<Pair<Integer, Type>> writtenRegisters, Set<Integer> readRegisters) {
			writtenRegisters.add(new Pair<Integer, Type>(code.target, (Type)code.type));
			readRegisters.add(code.operand);
		}

		public int getTarget() {
			return code.target;
		}

		public Type.EffectiveTuple getType() {
			return code.type;
		}

		public int getIndex() {
			return code.index;
		}

		public int getOperand() {
			return code.operand;
		}
	}
	
	public static class Update extends Bytecode implements Bytecode.Data, Bytecode.GPUSupportedBytecode {
		private final Code.Update code;
		
		public Update(Code.Update code) { super(code); this.code = code; }
		
		@Override
		protected void getRegisterSummary(Set<Pair<Integer, Type>> writtenRegisters, Set<Integer> readRegisters) {
			writtenRegisters.add(new Pair<Integer, Type>(code.target, (code.type)));
			readRegisters.add(code.operand);
			for(int i : code.operands) {
				readRegisters.add(i);
			}
		}

		public Type getDataStructureType() {
			return code.type;
		}

		@SuppressWarnings("rawtypes")
		public Iterable<Code.Update.LVal> getLValueIterator() {
			return code;
		}

		public int getTarget() {
			return code.target;
		}

		public int getOperand() {
			return code.operand;
		}
	}
	
	public static class BinStringOp extends Bytecode implements Data {
		private final wyil.lang.Code.BinStringOp code;
		
		public BinStringOp(wyil.lang.Code.BinStringOp code) {
			super(code);
			this.code = code;
		}

		@Override
		protected void getRegisterSummary(Set<Pair<Integer, Type>> writtenRegisters, Set<Integer> readRegisters) {
			writtenRegisters.add(new Pair<Integer, Type>(code.target, ((Type)code.type)));
			readRegisters.add(code.leftOperand);
			readRegisters.add(code.rightOperand);
		}
		
		public int getTarget() {
			return code.target;
		}

		public Type getType() {
			return code.type;
		}
		
		public int getLeftOperand() {
			return code.leftOperand;
		}
		
		public int getRightOperand() {
			return code.rightOperand;
		}
	}

	public static class BinSetOp extends Bytecode implements Data {
		private final wyil.lang.Code.BinSetOp code;
		
		public BinSetOp(wyil.lang.Code.BinSetOp code) { super(code); this.code = code; }

		@Override
		protected void getRegisterSummary(Set<Pair<Integer, Type>> writtenRegisters, Set<Integer> readRegisters) {
			writtenRegisters.add(new Pair<Integer, Type>(code.target, (Type)code.type));
			readRegisters.add(code.leftOperand);
			readRegisters.add(code.rightOperand);
		}
		
		public int getTarget() {
			return code.target;
		}

		public Type.EffectiveSet getType() {
			return code.type;
		}
		
		public int getLeftOperand() {
			return code.leftOperand;
		}
		
		public int getRightOperand() {
			return code.rightOperand;
		}
	}

	public static class BinListOp extends Bytecode implements Data {
		private final wyil.lang.Code.BinListOp code;
		
		public BinListOp(wyil.lang.Code.BinListOp code) { super(code); this.code = code; }

		@Override
		protected void getRegisterSummary(Set<Pair<Integer, Type>> writtenRegisters, Set<Integer> readRegisters) {
			writtenRegisters.add(new Pair<Integer, Type>(code.target, (Type)code.type));
			readRegisters.add(code.leftOperand);
			readRegisters.add(code.rightOperand);
		}
		
		public int getTarget() {
			return code.target;
		}

		public Type.EffectiveList getType() {
			return code.type;
		}
		
		public int getLeftOperand() {
			return code.leftOperand;
		}
		
		public int getRightOperand() {
			return code.rightOperand;
		}
	}

	public static class Void extends Bytecode implements Data {
		private final wyil.lang.Code.Void code;
		
		public Void(wyil.lang.Code.Void code) { super(code); this.code = code; }

		@Override
		protected void getRegisterSummary(Set<Pair<Integer, Type>> writtenRegisters, Set<Integer> readRegisters) {
			writtenRegisters.add(new Pair<Integer, Type>(code.target, (code.type)));
			for(int op : code.operands) {
				readRegisters.add(op);
			}
		}
		
		public int getTarget() {
			return code.target;
		}

		public Type getType() {
			return code.type;
		}
	}
	
	public static class SubString extends Bytecode implements Data {
		private final wyil.lang.Code.SubString code;
		
		public SubString(wyil.lang.Code.SubString code) { super(code); this.code = code; }

		@Override
		protected void getRegisterSummary(Set<Pair<Integer, Type>> writtenRegisters, Set<Integer> readRegisters) {
			writtenRegisters.add(new Pair<Integer, Type>(code.target, ((Type)code.type)));
			for(int op : code.operands) {
				readRegisters.add(op);
			}
		}
	}

	public static class SubList extends Bytecode implements Data {
		private final wyil.lang.Code.SubList code;
		
		public SubList(wyil.lang.Code.SubList code) { super(code); this.code = code; }

		@Override
		protected void getRegisterSummary(Set<Pair<Integer, Type>> writtenRegisters, Set<Integer> readRegisters) {
			writtenRegisters.add(new Pair<Integer, Type>(code.target, ((Type)code.type)));
			for(int op : code.operands) {
				readRegisters.add(op);
			}
		}
	}

	public static class Not extends Bytecode implements Data, Bytecode.GPUSupportedBytecode {
		private final wyil.lang.Code.Not code;
		
		public Not(wyil.lang.Code.Not code) { super(code); this.code = code; }

		@Override
		protected void getRegisterSummary(Set<Pair<Integer, Type>> writtenRegisters, Set<Integer> readRegisters) {
			writtenRegisters.add(new Pair<Integer, Type>(code.target, (code.type)));
			readRegisters.add(code.operand);
		}
		
		public int getTarget() {
			return code.target;
		}
		
		public int getOperand() {
			return code.operand;
		}
		
		public Type getType() {
			return code.type;
		}
	}

	public static class NewTuple extends Bytecode implements Data {
		private final wyil.lang.Code.NewTuple code;
		
		public NewTuple(wyil.lang.Code.NewTuple code) { super(code); this.code = code; }

		@Override
		protected void getRegisterSummary(Set<Pair<Integer, Type>> writtenRegisters, Set<Integer> readRegisters) {
			writtenRegisters.add(new Pair<Integer, Type>(code.target, ((Type)code.type)));
			for(int op : code.operands) {
				readRegisters.add(op);
			}
		}
	}

	public static class NewList extends Bytecode implements Data {
		private final wyil.lang.Code.NewList code;
		
		public NewList(wyil.lang.Code.NewList code) { super(code); this.code = code; }

		@Override
		protected void getRegisterSummary(Set<Pair<Integer, Type>> writtenRegisters, Set<Integer> readRegisters) {
			writtenRegisters.add(new Pair<Integer, Type>(code.target, ((Type)code.type)));
			for(int op : code.operands) {
				readRegisters.add(op);
			}
		}
	}

	public static class NewRecord extends Bytecode implements Data {
		private final wyil.lang.Code.NewRecord code;
		
		public NewRecord(wyil.lang.Code.NewRecord code) { super(code); this.code = code; }

		@Override
		protected void getRegisterSummary(Set<Pair<Integer, Type>> writtenRegisters, Set<Integer> readRegisters) {
			writtenRegisters.add(new Pair<Integer, Type>(code.target, ((Type)code.type)));
			for(int op : code.operands) {
				readRegisters.add(op);
			}
		}
	}

	public static class NewMap extends Bytecode implements Data {
		private final wyil.lang.Code.NewMap code;
		
		public NewMap(wyil.lang.Code.NewMap code) { super(code); this.code = code; }

		@Override
		protected void getRegisterSummary(Set<Pair<Integer, Type>> writtenRegisters, Set<Integer> readRegisters) {
			writtenRegisters.add(new Pair<Integer, Type>(code.target, ((Type)code.type)));
			for(int op : code.operands) {
				readRegisters.add(op);
			}
		}
	}
	
	public static class NewObject extends Bytecode implements Data {
		private final wyil.lang.Code.NewObject code;
		
		public NewObject(wyil.lang.Code.NewObject code) { super(code); this.code = code; }

		@Override
		protected void getRegisterSummary(Set<Pair<Integer, Type>> writtenRegisters, Set<Integer> readRegisters) {
			writtenRegisters.add(new Pair<Integer, Type>(code.target, ((Type)code.type)));
			readRegisters.add(code.operand);
		}
	}
	
	public static class NewSet extends Bytecode implements Data {
		private final wyil.lang.Code.NewSet code;
		
		public NewSet(wyil.lang.Code.NewSet code) { super(code); this.code = code; }

		@Override
		protected void getRegisterSummary(Set<Pair<Integer, Type>> writtenRegisters, Set<Integer> readRegisters) {
			writtenRegisters.add(new Pair<Integer, Type>(code.target, ((Type)code.type)));
			for(int op : code.operands) {
				readRegisters.add(op);
			}
		}
	}

	public static class Invert extends Bytecode implements Data {
		private final wyil.lang.Code.Invert code;
		
		public Invert(wyil.lang.Code.Invert code) { super(code); this.code = code; }

		@Override
		protected void getRegisterSummary(Set<Pair<Integer, Type>> writtenRegisters, Set<Integer> readRegisters) {
			writtenRegisters.add(new Pair<Integer, Type>(code.target, ((Type)code.type)));
			readRegisters.add(code.operand);
		}
	}

	public static class FieldLoad extends Bytecode implements Data {
		private final wyil.lang.Code.FieldLoad code;
		
		public FieldLoad(wyil.lang.Code.FieldLoad code) { super(code); this.code = code; }

		@Override
		protected void getRegisterSummary(Set<Pair<Integer, Type>> writtenRegisters, Set<Integer> readRegisters) {
			writtenRegisters.add(new Pair<Integer, Type>(code.target, ((Type)code.type)));
			readRegisters.add(code.operand);
		}
	}

	public static class Dereference extends Bytecode implements Data {
		private final wyil.lang.Code.Dereference code;
		
		public Dereference(wyil.lang.Code.Dereference code) { super(code); this.code = code; }

		@Override
		protected void getRegisterSummary(Set<Pair<Integer, Type>> writtenRegisters, Set<Integer> readRegisters) {
			writtenRegisters.add(new Pair<Integer, Type>(code.target, (code.type)));
			readRegisters.add(code.operand);
		}
	}

	public static class Debug extends Bytecode implements Data {
		private final wyil.lang.Code.Debug code;
		
		public Debug(wyil.lang.Code.Debug code) { super(code); this.code = code; }

		@Override
		protected void getRegisterSummary(Set<Pair<Integer, Type>> writtenRegisters, Set<Integer> readRegisters) {
			readRegisters.add(code.operand);
		}
	}
	
	public static class ForAll extends Bytecode implements Bytecode.Loop, Bytecode.GPUSupportedBytecode {
		private final Code.ForAll code;
		
		public ForAll(Code.ForAll code) { super(code); this.code = code; }

		@Override
		public String loopEndLabel() {
			return code.target;
		}
		
		@Override
		protected void getRegisterSummary(Set<Pair<Integer, Type>> writtenRegisters, Set<Integer> readRegisters) {
			writtenRegisters.add(new Pair<Integer, Type>(code.indexOperand, (Type)code.type.element()));
			readRegisters.add(code.sourceOperand);
		}

		public int getIndexRegister() {
			return code.indexOperand;
		}
		
		public Type getIndexType() {
			return code.type.element();
		}

		public int getSourceRegister() {
			return code.sourceOperand;
		}

		public Type getSourceType() {
			return (Type)code.type;
		}

		public DFGNode getIndexDFGNode() {			
			return writtenDFGNodes.get(getIndexRegister());
		}

		public DFGNode getSourceDFGNode() {
			return readDFGNodes.get(getSourceRegister());
		}
	}
	
	public static class While extends Bytecode implements Bytecode.Loop, Bytecode.GPUSupportedBytecode {
		private final Code.Loop code;
		
		public While(Code.Loop code) { super(code); this.code = code; }
		
		@Override
		public String loopEndLabel() {
			return code.target;
		}
		
		@Override
		protected void getRegisterSummary(Set<Pair<Integer, Type>> writtenRegisters, Set<Integer> readRegisters) {
		}
	}
	
	public static class UnconditionalJump extends Bytecode implements Bytecode.Jump, Bytecode.GPUSupportedBytecode {
		private final Code.Goto code;
		
		public UnconditionalJump(Code.Goto code) { super(code); this.code = code; }

		public String getTargetLabel() {
			return code.target;
		}
		
		@Override
		protected void getRegisterSummary(Set<Pair<Integer, Type>> writtenRegisters, Set<Integer> readRegisters) {
		}
	}
	
	public static class ComparisonBasedJump extends Bytecode implements Bytecode.ConditionalJump, Bytecode.GPUSupportedBytecode {
		private final Code.If code;
		
		public ComparisonBasedJump(Code.If code) { super(code); this.code = code; }

		@Override
		public String getConditionMetTargetLabel() {
			return code.target;
		}
		
		@Override
		protected void getRegisterSummary(Set<Pair<Integer, Type>> writtenRegisters, Set<Integer> readRegisters) {
			readRegisters.add(code.leftOperand);
			readRegisters.add(code.rightOperand);
		}

		@Override
		public void getCheckedRegisters(Set<Integer> checkedRegisters) {
			checkedRegisters.add(code.leftOperand);
			checkedRegisters.add(code.rightOperand);
		}
		
		public int getLeftOperand() {
			return code.leftOperand;
		}
		
		public int getRightOperand() {
			return code.rightOperand;
		}
		
		public Code.Comparator getComparison() {
			return code.op;
		}

		public Type getType() {
			return code.type;
		}
	}
	
	public static class TypeBasedJump extends Bytecode implements Bytecode.ConditionalJump {
		private final wyil.lang.Code.IfIs code;
		
		public TypeBasedJump(wyil.lang.Code.IfIs code) { super(code); this.code = code; }
		
		@Override
		public String getConditionMetTargetLabel() {
			return code.target;
		}

		@Override
		protected void getRegisterSummary(Set<Pair<Integer, Type>> writtenRegisters, Set<Integer> readRegisters) {
			readRegisters.add(code.operand);
		}

		@Override
		public void getCheckedRegisters(Set<Integer> checkedRegisters) {
			checkedRegisters.add(code.operand);
		}

		public Type getType() {
			return code.type;
		}

		public int getTestedOperand() {
			return code.operand;
		}

		public Type getTypeOperand() {
			return code.rightOperand;
		}
	}
	
	public static class Switch extends Bytecode implements Bytecode.Jump, Bytecode.GPUSupportedBytecode {
		private final Code.Switch code;
		
		public Switch(Code.Switch code) { super(code); this.code = code; }
		
		public List<Pair<Constant, String>> getBranchTargets() {
			return code.branches;
		}

		public String getDefaultTargetLabel() {
			return code.defaultTarget;
		}
		
		@Override
		protected void getRegisterSummary(Set<Pair<Integer, Type>> writtenRegisters, Set<Integer> readRegisters) {
			readRegisters.add(code.operand);
		}

		public int getCheckedRegister() {
			return code.operand;
		}

		public Type getType() {
			return code.type;
		}
	}
	
	public static class Label extends Bytecode implements Bytecode.Control, Bytecode.Target, Bytecode.GPUSupportedBytecode {
		private final Code.Label code;
		
		public Label(Code.Label code) { super(code); this.code = code; }

		@Override
		public String name() {
			return code.label;
		}
		
		@Override
		protected void getRegisterSummary(Set<Pair<Integer, Type>> writtenRegisters, Set<Integer> readRegisters) {
		}
	}
	
	public static class LoopEnd extends Bytecode implements Bytecode.Control, Bytecode.Target, Bytecode.GPUSupportedBytecode {
		private final Code.LoopEnd code;
		
		public LoopEnd(Code.LoopEnd code) { super(code); this.code = code; }
		
		@Override
		public String name() {
			return code.label;
		}
		
		@Override
		protected void getRegisterSummary(Set<Pair<Integer, Type>> writtenRegisters, Set<Integer> readRegisters) {
		}
	}
	
	public static class Return extends Bytecode implements Bytecode.Jump, Bytecode.GPUSupportedBytecode {
		private final Code.Return code;
		
		public Return(Code.Return code) { super(code); this.code = code; }
		
		@Override
		protected void getRegisterSummary(Set<Pair<Integer, Type>> writtenRegisters, Set<Integer> readRegisters) {
			if(code.type != Type.T_VOID) {
				readRegisters.add(code.operand);
			}
		}
		
		public Type getType() {
			return code.type;
		}
		
		public int getOperand() {
			return code.operand;
		}

		public boolean isVoid() {
			return code.type == Type.T_VOID;
		}
	}
	
	public static class Invoke extends Bytecode implements Bytecode.Call, Bytecode.GPUSupportedBytecode {
		private final Code.Invoke code;
		
		public Invoke(Code.Invoke code) { super(code); this.code = code; }
		
		@Override
		protected void getRegisterSummary(Set<Pair<Integer, Type>> writtenRegisters, Set<Integer> readRegisters) {
			writtenRegisters.add(new Pair<Integer, Type>(code.target, (code.type.ret())));
			for(int i : code.operands) {
				readRegisters.add(i);
			}
		}

		public Type.FunctionOrMethod getType() {
			return code.type;
		}

		public int getTarget() {
			return code.target;
		}

		public int[] getOperands() {
			return code.operands;
		}

		public NameID getName() {
			return code.name;
		}
	}
	
	public static class Lambda extends Bytecode implements Bytecode.Call {
		private final wyil.lang.Code.Lambda code;
		
		public Lambda(wyil.lang.Code.Lambda code) { super(code); this.code = code; }

		@Override
		protected void getRegisterSummary(Set<Pair<Integer, Type>> writtenRegisters, Set<Integer> readRegisters) {
			writtenRegisters.add(new Pair<Integer, Type>(code.target, (code.type.ret())));
			for(int op : code.operands) {
				readRegisters.add(op);
			}
		}
	}
	
	public static class IndirectInvoke extends Bytecode implements Bytecode.Call {
		private final wyil.lang.Code.IndirectInvoke code;
		
		public IndirectInvoke(wyil.lang.Code.IndirectInvoke code) { super(code); this.code = code; }

		@Override
		protected void getRegisterSummary(Set<Pair<Integer, Type>> writtenRegisters, Set<Integer> readRegisters) {
			writtenRegisters.add(new Pair<Integer, Type>(code.target, (code.type.ret())));
			readRegisters.add(code.operand);
			for(int op : code.operands) {
				readRegisters.add(op);
			}
		}
	}
	
	public static class Assume extends Bytecode implements Exception {
		private final wyil.lang.Code.Assume code;
		
		public Assume(wyil.lang.Code.Assume code) { super(code); this.code = code; }

		@Override
		protected void getRegisterSummary(Set<Pair<Integer, Type>> writtenRegisters, Set<Integer> readRegisters) {
			readRegisters.add(code.leftOperand);
			readRegisters.add(code.rightOperand);
		}
	}

	public static class Throw extends Bytecode implements Exception {
		private final wyil.lang.Code.Throw code;
		
		public Throw(wyil.lang.Code.Throw code) { super(code); this.code = code; }

		@Override
		protected void getRegisterSummary(Set<Pair<Integer, Type>> writtenRegisters, Set<Integer> readRegisters) {
			readRegisters.add(code.operand);
		}
	}

	public static class Assert extends Bytecode implements Exception {
		private final wyil.lang.Code.Assert code;
		
		public Assert(wyil.lang.Code.Assert code) { super(code); this.code = code; }

		@Override
		protected void getRegisterSummary(Set<Pair<Integer, Type>> writtenRegisters, Set<Integer> readRegisters) {
			readRegisters.add(code.leftOperand);
			readRegisters.add(code.rightOperand);
		}
	}

	public static class TryEnd extends Bytecode implements Exception {
		private final wyil.lang.Code.TryEnd code;
		
		public TryEnd(wyil.lang.Code.TryEnd code) { super(code); this.code = code; }

		@Override
		protected void getRegisterSummary(Set<Pair<Integer, Type>> writtenRegisters, Set<Integer> readRegisters) {
		}
	}

	public static class TryCatch extends Bytecode implements Exception {
		private final wyil.lang.Code.TryCatch code;
		
		public TryCatch(wyil.lang.Code.TryCatch code) { super(code); this.code = code; }

		@Override
		protected void getRegisterSummary(Set<Pair<Integer, Type>> writtenRegisters, Set<Integer> readRegisters) {
			readRegisters.add(code.operand);
		}
	}
	
	
	public static Bytecode bytecodeForCode(Code code) {
		if(code instanceof Code.ForAll) {
			return new Bytecode.ForAll((Code.ForAll)code);
		}
		else if(code instanceof Code.Loop) { // Must be after Code.ForAll
			return new Bytecode.While((Code.Loop)code);
		}
		else if(code instanceof Code.Nop) {
			return null;
		}
		else if(code instanceof Code.TryCatch) {
			return new Bytecode.TryCatch((Code.TryCatch)code);
		}
		else if(code instanceof Code.Const) {
			return new Bytecode.ConstLoad((Code.Const)code);
		}
		else if(code instanceof Code.Goto) {
			return new Bytecode.UnconditionalJump((Code.Goto)code);
		}
		else if(code instanceof Code.TryEnd) { // Must be before Code.Label
			return new Bytecode.TryEnd((Code.TryEnd)code);
		}
		else if(code instanceof Code.LoopEnd) {
			return new Bytecode.LoopEnd((Code.LoopEnd)code);
		}
		else if(code instanceof Code.Label) {
			return new Bytecode.Label((Code.Label)code);
		}
		else if(code instanceof Code.Assert) { // FIXME: Add these back, just skip when OpenCL
			return null;//return new Bytecode.Assert((Code.Assert)code);
		}
		else if(code instanceof Code.Assign) {
			return new Bytecode.Assign((Code.Assign)code);
		}
		else if(code instanceof Code.Convert) {
			return new Bytecode.Convert((Code.Convert)code);
		}
		else if(code instanceof Code.Debug) {
			return new Bytecode.Debug((Code.Debug)code);
		}
		else if(code instanceof Code.Dereference) {
			return new Bytecode.Dereference((Code.Dereference)code);
		}
		else if(code instanceof Code.Assume) { // FIXME: Add these back, just skip when OpenCL
			return null;//return new Bytecode.Assume((Code.Assume)code);
		}
		else if(code instanceof Code.FieldLoad) {
			return new Bytecode.FieldLoad((Code.FieldLoad)code);
		}
		else if(code instanceof Code.If) {
			return  new Bytecode.ComparisonBasedJump((Code.If)code);
		}
		else if(code instanceof Code.IfIs) {
			return new Bytecode.TypeBasedJump((Code.IfIs)code);
		}
		else if(code instanceof Code.IndexOf) {
			return new Bytecode.Load((Code.IndexOf)code);
		}
		else if(code instanceof Code.IndirectInvoke) {
			return new Bytecode.IndirectInvoke((Code.IndirectInvoke)code);
		}
		else if(code instanceof Code.Invert) {
			return new Bytecode.Invert((Code.Invert)code);
		}
		else if(code instanceof Code.Invoke) {
			return new Bytecode.Invoke((Code.Invoke)code);
		}
		else if(code instanceof Code.Lambda) {
			return new Bytecode.Lambda((Code.Lambda)code);
		}
		else if(code instanceof Code.LengthOf) {
			return new Bytecode.LengthOf((Code.LengthOf)code);
		}
		else if(code instanceof Code.Move) {
			return new Bytecode.Move((Code.Move)code);
		}
		else if(code instanceof Code.NewList) {
			return new Bytecode.NewList((Code.NewList)code);
		}
		else if(code instanceof Code.NewMap) {
			return new Bytecode.NewMap((Code.NewMap)code);
		}
		else if(code instanceof Code.NewObject) {
			return new Bytecode.NewObject((Code.NewObject)code);
		}
		else if(code instanceof Code.NewRecord) {
			return new Bytecode.NewRecord((Code.NewRecord)code);
		}
		else if(code instanceof Code.NewSet) {
			return new Bytecode.NewSet((Code.NewSet)code);
		}
		else if(code instanceof Code.NewTuple) {
			return new Bytecode.NewTuple((Code.NewTuple)code);
		}
		else if(code instanceof Code.Not) {
			return new Bytecode.Not((Code.Not)code);
		}
		else if(code instanceof Code.Return) {
			return new Bytecode.Return((Code.Return)code);
		}
		else if(code instanceof Code.SubList) {
			return new Bytecode.SubList((Code.SubList)code);
		}
		else if(code instanceof Code.SubString) {
			return new Bytecode.SubString((Code.SubString)code);
		}
		else if(code instanceof Code.Switch) {
			return new Bytecode.Switch((Code.Switch)code);
		}
		else if(code instanceof Code.Throw) {
			return new Bytecode.Throw((Code.Throw)code);
		}
		else if(code instanceof Code.TupleLoad) {
			return new Bytecode.TupleLoad((Code.TupleLoad)code);
		}
		else if(code instanceof Code.Update) {
			return new Bytecode.Update((Code.Update)code);
		}
		else if(code instanceof Code.Void) {
			return new Bytecode.Void((Code.Void)code);
		}
		else if(code instanceof Code.BinArithOp) {
			return new Bytecode.Binary((Code.BinArithOp)code);
		}
		else if(code instanceof Code.BinListOp) {
			return new Bytecode.BinListOp((Code.BinListOp)code);
		}
		else if(code instanceof Code.BinSetOp) {
			return new Bytecode.BinSetOp((Code.BinSetOp)code);
		}
		else if(code instanceof Code.BinStringOp) {
			return new Bytecode.BinStringOp((Code.BinStringOp)code);
		}
		else if(code instanceof Code.UnArithOp) {
			return new Bytecode.Unary((Code.UnArithOp)code);
		}
		else {
			throw new RuntimeException("Unknown bytecode encountered: "+code+" ("+code.getClass()+")");
		}
	}
}