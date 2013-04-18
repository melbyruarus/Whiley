package wyocl.filter;

import java.util.HashMap;
import java.util.Set;

import wyil.lang.Code;
import wyil.lang.Type;

public class ModifiedRegisterAnalysis {
	@SuppressWarnings("unchecked")
	public static void getModifiedRegisters(Code code, Set<Integer> readRegisters, Set<Integer> modifiedRegisters) {
		if(code instanceof Code.AbstractAssignable) {
			checkedAdd(modifiedRegisters, ((Code.AbstractAssignable) code).target);
			if(code instanceof Code.AbstractBinaryAssignable) {
				checkedAdd(readRegisters, ((Code.AbstractBinaryAssignable<Type>) code).leftOperand);
				checkedAdd(readRegisters, ((Code.AbstractBinaryAssignable<Type>) code).rightOperand);
			}
			else if(code instanceof Code.AbstractNaryAssignable) {
				for(int i : ((Code.AbstractNaryAssignable<Type>) code).operands) {
					checkedAdd(modifiedRegisters, i);
				}
			}
			else if(code instanceof Code.AbstractSplitNaryAssignable) {
				checkedAdd(readRegisters, ((Code.AbstractSplitNaryAssignable<Type>) code).operand);
				for(int i : ((Code.AbstractSplitNaryAssignable<Type>) code).operands) {
					checkedAdd(modifiedRegisters, i);
				}
			}
			else if(code instanceof Code.AbstractUnaryAssignable) {
				checkedAdd(readRegisters, ((Code.AbstractUnaryAssignable<Type>) code).operand);
			}
			else if(code instanceof Code.Const) {
				// Nothing to do
			}
		}
		else if(code instanceof Code.AbstractUnaryOp) {
			checkedAdd(readRegisters, ((Code.AbstractUnaryOp<Type>) code).operand);
			
			// Never modifies anything so dont need to look at subtypes
		}
		else if(code instanceof Code.AbstractBinaryOp) {
			checkedAdd(readRegisters, ((Code.AbstractBinaryOp<Type>) code).leftOperand);
			checkedAdd(readRegisters, ((Code.AbstractBinaryOp<Type>) code).rightOperand);
			
			// Never modifies anything so dont need to look at subtypes
		}
		else if(code instanceof Code.Goto) {
			// Unconditional, so no reads or writes
		}
		else if(code instanceof Code.Label) {
			// No reads or writes
		}
		else if(code instanceof Code.Loop) {
			// Standard loop depends on nothing
			
			if(code instanceof Code.ForAll) {
				checkedAdd(readRegisters, ((Code.ForAll) code).sourceOperand);
			}
		}
		else if(code instanceof Code.Nop) {
			// No reads or writes
		}
		else if(code instanceof Code.TryCatch) {
			// TODO: not entirely sure about this
			
			checkedAdd(modifiedRegisters, ((Code.TryCatch) code).operand);
		}
	}

	private static <T> void checkedAdd(Set<T> set, T target) {
		if(set != null) {
			set.add(target);
		}
	}

	public static void getAssignedType(Code code, HashMap<Integer, Type> types) {
		if(code instanceof Code.AbstractAssignable) {
			Code.AbstractAssignable assignable = (Code.AbstractAssignable)code;
			
			types.put(assignable.target, typeof(assignable));
		}
	}

	@SuppressWarnings("unchecked")
	public static Type typeof(Code.AbstractAssignable assignable) {
		if(assignable instanceof Code.AbstractUnaryAssignable) {
			return ((Code.AbstractUnaryAssignable<Type>) assignable).type;
		}
		else if(assignable instanceof Code.AbstractBinaryAssignable) {
			return ((Code.AbstractBinaryAssignable<Type>) assignable).type;
		}
		else if(assignable instanceof Code.AbstractNaryAssignable) {
			return ((Code.AbstractNaryAssignable<Type>) assignable).type;
		}
		else if(assignable instanceof Code.AbstractSplitNaryAssignable) {
			return ((Code.AbstractSplitNaryAssignable<Type>) assignable).type;
		}
		else if(assignable instanceof Code.AbstractSplitNaryAssignable) {
			return ((Code.AbstractSplitNaryAssignable<Type>) assignable).type;
		}
		else if(assignable instanceof Code.Const) {
			return ((Code.Const) assignable).constant.type();
		}
		else {
			throw new RuntimeException("Unsupported AbstractAssignable type encountered: "+assignable);
		}
	}
}
