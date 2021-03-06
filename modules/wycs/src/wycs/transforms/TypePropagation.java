package wycs.transforms;

import java.util.*;

import static wybs.lang.SyntaxError.*;
import wybs.lang.Builder;
import wybs.lang.NameID;
import wybs.lang.SyntacticElement;
import wybs.lang.Transform;
import wybs.util.Pair;
import wybs.util.ResolveError;
import wybs.util.Triple;
import wycs.builders.Wyal2WycsBuilder;
import wycs.core.SemanticType;
import wycs.core.Value;
import wycs.core.WycsFile;
import wycs.syntax.*;

public class TypePropagation implements Transform<WyalFile> {
	
	/**
	 * Determines whether type propagation is enabled or not.
	 */
	private boolean enabled = getEnable();

	private final Wyal2WycsBuilder builder;
	
	private String filename;

	// ======================================================================
	// Constructor(s)
	// ======================================================================

	public TypePropagation(Builder builder) {
		this.builder = (Wyal2WycsBuilder) builder;
	}
	
	// ======================================================================
	// Configuration Methods
	// ======================================================================
		
	public static String describeEnable() {
		return "Enable/disable type propagation";
	}

	public static boolean getEnable() {
		return true; // default value
	}

	public void setEnable(boolean flag) {
		this.enabled = flag;
	}

	// ======================================================================
	// Apply method
	// ======================================================================
		
	public void apply(WyalFile wf) {
		if(enabled) {
			this.filename = wf.filename();

			for (WyalFile.Declaration s : wf.declarations()) {
				propagate(s);
			}
		}
	}

	private void propagate(WyalFile.Declaration s) {		
		if(s instanceof WyalFile.Function) {
			propagate((WyalFile.Function)s);
		} else if(s instanceof WyalFile.Define) {
			propagate((WyalFile.Define)s);
		} else if(s instanceof WyalFile.Assert) {
			propagate((WyalFile.Assert)s);
		} else if(s instanceof WyalFile.Import) {
			
		} else {
			internalFailure("unknown statement encountered (" + s + ")",
					filename, s);
		}
	}
	
	private void propagate(WyalFile.Function s) {
		if(s.constraint != null) {
			HashSet<String> generics = new HashSet<String>(s.generics);
			HashMap<String,SemanticType> environment = new HashMap<String,SemanticType>();
			addNamedVariables(s.from, environment,generics,s);
			addNamedVariables(s.to, environment,generics,s);
			SemanticType r = propagate(s.constraint,environment,generics,s);
			checkIsSubtype(SemanticType.Bool,r,s.constraint);		
		}
	}
	
	private void propagate(WyalFile.Define s) {
		HashSet<String> generics = new HashSet<String>(s.generics);
		HashMap<String,SemanticType> environment = new HashMap<String,SemanticType>();		
		addNamedVariables(s.from, environment,generics,s);
		SemanticType r = propagate(s.condition,environment,generics,s);
		checkIsSubtype(SemanticType.Bool,r,s.condition);		
	}
		
	private void addNamedVariables(TypePattern pattern,
			HashMap<String, SemanticType> environment,
			HashSet<String> generics, WyalFile.Context context) {

		SemanticType type = builder.convert(pattern.toSyntacticType(), generics, context);
		
		if (pattern.var != null) {
			if (environment.containsKey(pattern.var)) {
				internalFailure("duplicate variable name encountered",
						filename, pattern);
			}
			environment.put(pattern.var, type);
		}

		if (pattern instanceof TypePattern.Tuple) {
			TypePattern.Tuple st = (TypePattern.Tuple) pattern;
			for (TypePattern t : st.patterns) {
				addNamedVariables(t, environment, generics, context);
			}
		}
		
		pattern.attributes().add(new TypeAttribute(type));
	}
	
	private void propagate(WyalFile.Assert s) {
		HashMap<String,SemanticType> environment = new HashMap<String,SemanticType>();
		SemanticType t = propagate(s.expr, environment, new HashSet<String>(), s);
		checkIsSubtype(SemanticType.Bool,t, s.expr);
	}
	
	/**
	 * Perform type propagation through a given expression, returning the type
	 * of value that is returned by evaluating this expression.
	 * 
	 * @param e
	 * @param environment
	 * @param generics
	 * @param context
	 * @return
	 */
	private SemanticType propagate(Expr e,
			HashMap<String, SemanticType> environment,
			HashSet<String> generics, WyalFile.Context context) {
		SemanticType t;
		if(e instanceof Expr.Variable) {
			t = propagate((Expr.Variable)e, environment, generics, context);
		} else if(e instanceof Expr.Constant) {
			t = propagate((Expr.Constant)e, environment, generics, context);
		} else if(e instanceof Expr.Unary) {
			t = propagate((Expr.Unary)e, environment, generics, context);
		} else if(e instanceof Expr.Binary) {
			t = propagate((Expr.Binary)e, environment, generics, context);
		} else if(e instanceof Expr.Ternary) {
			t = propagate((Expr.Ternary)e, environment, generics, context);
		} else if(e instanceof Expr.Nary) {
			t = propagate((Expr.Nary)e, environment, generics, context);
		} else if(e instanceof Expr.Quantifier) {
			t = propagate((Expr.Quantifier)e, environment, generics, context);
		} else if(e instanceof Expr.FunCall) {
			t = propagate((Expr.FunCall)e, environment, generics, context);
		} else if(e instanceof Expr.IndexOf) {
			t = propagate((Expr.IndexOf)e, environment, generics, context);
		} else {
			internalFailure("unknown expression encountered (" + e + ")",
					filename, e);
			return null;
		}
		e.attributes().add(new TypeAttribute(t));
		return returnType(e);
	}
	
	private SemanticType propagate(Expr.Variable e,
			HashMap<String, SemanticType> environment,
			HashSet<String> generics, WyalFile.Context context) {
		SemanticType t = environment.get(e.name);
		if(t == null) {
			internalFailure("undeclared variable encountered (" + e + ")",
					filename, e);
		}
		return t;
	}
	
	private SemanticType propagate(Expr.Constant e,
			HashMap<String, SemanticType> environment,
			HashSet<String> generics, WyalFile.Context context) {
		return e.value.type();
	}

	private SemanticType propagate(Expr.Unary e,
			HashMap<String, SemanticType> environment,
			HashSet<String> generics, WyalFile.Context context) {
		SemanticType op_type = propagate(e.operand,environment,generics,context);
		
		switch(e.op) {
		case NOT:
			checkIsSubtype(SemanticType.Bool,op_type,e);
			break;
		case NEG:
			checkIsSubtype(SemanticType.IntOrReal,op_type,e);
			break;
		case LENGTHOF:
			checkIsSubtype(SemanticType.SetAny,op_type,e);			
		}
		return op_type;
	}
	
	private SemanticType propagate(Expr.IndexOf e,
			HashMap<String, SemanticType> environment,
			HashSet<String> generics, WyalFile.Context context) {
		SemanticType src_type = propagate(e.operand, environment, generics,
				context);
		SemanticType index_type = propagate(e.index, environment, generics,
				context);
		if(src_type instanceof SemanticType.EffectiveTuple) {
			SemanticType.EffectiveTuple tt = (SemanticType.EffectiveTuple) src_type;
			checkIsSubtype(SemanticType.Int, index_type, e.operand);
			if (!(e.index instanceof Expr.Constant)) {
				syntaxError("constant index required for tuple load", filename,
						e.index);
			}  			
		} else {
			checkIsSubtype(SemanticType.SetTupleAnyAny, src_type, e.operand);
			// FIXME: handle case for effective set (i.e. union of sets)  
			SemanticType.Set st = (SemanticType.Set) src_type;
			SemanticType.EffectiveTuple tt = (SemanticType.EffectiveTuple) st.element();
			// FIXME: handle case for effective tuple of wrong size
			checkIsSubtype(tt.tupleElement(0), index_type, e.index);
		}
		
		return src_type;
	}
	
	private SemanticType propagate(Expr.Binary e,
			HashMap<String, SemanticType> environment,
			HashSet<String> generics, WyalFile.Context context) {
		SemanticType lhs_type = propagate(e.leftOperand,environment,generics,context);
		SemanticType rhs_type = propagate(e.rightOperand,environment,generics,context);
		
		switch(e.op) {
		case ADD:
		case SUB:
		case MUL:
		case DIV:
		case REM:
			checkIsSubtype(SemanticType.IntOrReal,lhs_type,e.leftOperand);
			checkIsSubtype(SemanticType.IntOrReal,rhs_type,e.rightOperand);
			return SemanticType.Or(lhs_type,rhs_type);
		case EQ:
		case NEQ:
			return SemanticType.Or(lhs_type,rhs_type);
		case IMPLIES:
		case IFF:
			checkIsSubtype(SemanticType.Bool,lhs_type,e.leftOperand);
			checkIsSubtype(SemanticType.Bool,rhs_type,e.rightOperand);
			return SemanticType.Bool;
		case LT:
		case LTEQ:
		case GT:
		case GTEQ:
			checkIsSubtype(SemanticType.IntOrReal,lhs_type,e.leftOperand);
			checkIsSubtype(SemanticType.IntOrReal,rhs_type,e.rightOperand);
			return SemanticType.Or(lhs_type,rhs_type);
		case IN: {
			checkIsSubtype(SemanticType.SetAny,rhs_type,e.rightOperand);
			SemanticType.Set s = (SemanticType.Set) rhs_type;
			return s;
		}
		case SUBSET:
		case SUBSETEQ:
		case SUPSET:
		case SUPSETEQ: {
			checkIsSubtype(SemanticType.SetAny,lhs_type,e.leftOperand);
			checkIsSubtype(SemanticType.SetAny,rhs_type,e.rightOperand);
			// following can cause some problems
			// checkIsSubtype(lhs_type,rhs_type,e);
			return SemanticType.Or(lhs_type,rhs_type);	
		}
		case SETUNION: {
			checkIsSubtype(SemanticType.SetAny,lhs_type,e.leftOperand);
			checkIsSubtype(SemanticType.SetAny,rhs_type,e.rightOperand);
			SemanticType.Set l = (SemanticType.Set) lhs_type;
			SemanticType.Set r = (SemanticType.Set) rhs_type;
			return SemanticType.Set(true,SemanticType.Or(l.element(),r.element()));
		}
		case SETINTERSECTION: {
			checkIsSubtype(SemanticType.SetAny,lhs_type,e.leftOperand);
			checkIsSubtype(SemanticType.SetAny,rhs_type,e.rightOperand);
			// TODO: the following gives a more accurate type, but there are
			// some outstanding issues related to the type system reduction
			// rules.
			//return SemanticType.And(lhs_type,rhs_type);
			SemanticType.Set l = (SemanticType.Set) lhs_type;
			SemanticType.Set r = (SemanticType.Set) rhs_type;
			return SemanticType.Set(true,SemanticType.Or(l.element(),r.element()));
		}
		case LISTAPPEND: {
			checkIsSubtype(SemanticType.SetTupleAnyAny, lhs_type, e.leftOperand);
			checkIsSubtype(SemanticType.SetTupleAnyAny, rhs_type,
					e.rightOperand);
			SemanticType.Set l = (SemanticType.Set) lhs_type;
			SemanticType.Set r = (SemanticType.Set) rhs_type;
			return SemanticType.Set(true,
					SemanticType.Or(l.element(), r.element()));
		}
		case RANGE: {
			checkIsSubtype(SemanticType.Int, lhs_type, e.leftOperand);
			checkIsSubtype(SemanticType.Int, rhs_type, e.rightOperand);
			return SemanticType.Set(true,
					SemanticType.Tuple(SemanticType.Int, SemanticType.Int));
		}
		}
		
		internalFailure("unknown binary expression encountered (" + e + ")",
				filename, e);
		return null; // deadcode
	}
	
	private SemanticType propagate(Expr.Ternary e,
			HashMap<String, SemanticType> environment,
			HashSet<String> generics, WyalFile.Context context) {
		SemanticType firstType = propagate(e.firstOperand,environment,generics,context);
		SemanticType secondType = propagate(e.secondOperand,environment,generics,context);
		SemanticType thirdType = propagate(e.thirdOperand,environment,generics,context);
		switch(e.op) {
		case UPDATE:
			checkIsSubtype(SemanticType.SetTupleAnyAny,firstType,e.firstOperand);
			// FIXME: should this handle map updates?
			checkIsSubtype(SemanticType.Int, secondType, e.secondOperand);
			SemanticType.Set l = (SemanticType.Set) firstType;
			SemanticType.Tuple elementType = SemanticType.Tuple(SemanticType.Int,thirdType);
			checkIsSubtype(l.element(),elementType,e.thirdOperand);
			return firstType;
		case SUBLIST:
			checkIsSubtype(SemanticType.SetTupleAnyAny,firstType,e.firstOperand);
			checkIsSubtype(SemanticType.Int, secondType, e.secondOperand);
			checkIsSubtype(SemanticType.Int, thirdType, e.thirdOperand);
			return firstType;
		}
		internalFailure("unknown ternary expression encountered (" + e + ")",
				filename, e);
		return null; // deadcode
	}
	
	private SemanticType propagate(Expr.Nary e,
			HashMap<String, SemanticType> environment,
			HashSet<String> generics, WyalFile.Context context) {
		Expr[] e_operands = e.operands;
		SemanticType[] op_types = new SemanticType[e_operands.length];
		
		for(int i=0;i!=e_operands.length;++i) {
			op_types[i] = propagate(e_operands[i],environment,generics,context);
		}
		
		switch(e.op) {
		case AND:
		case OR:
			for(int i=0;i!=e_operands.length;++i) {
				checkIsSubtype(SemanticType.Bool,op_types[i],e_operands[i]);
			}
			return SemanticType.Bool;		
		case TUPLE:
			return SemanticType.Tuple(op_types);
		case SET:
			if (op_types.length == 0) {
				return SemanticType.Set(true, SemanticType.Void);
			} else {
				return SemanticType.Set(true, SemanticType.Or(op_types));
			}
		case LIST:
			if (op_types.length == 0) {
				return SemanticType.Set(true, SemanticType.Void);
			} else {
				return SemanticType.Set(
						true,
						SemanticType.Tuple(SemanticType.Int,
								SemanticType.Or(op_types)));
			}
		}
		
		internalFailure("unknown nary expression encountered (" + e + ")",
				filename, e);
		return null; // deadcode
	}
	
	private SemanticType propagate(Expr.Quantifier e,
			HashMap<String, SemanticType> environment,
			HashSet<String> generics, WyalFile.Context context) {
		environment = new HashMap<String,SemanticType>(environment);
		
		propagate(e.pattern,environment,generics,context);		
		SemanticType r = propagate(e.operand,environment,generics,context);
		checkIsSubtype(SemanticType.Bool,r,e.operand);
		
		return SemanticType.Bool;
	}
	
	private void  propagate(TypePattern pattern,
			HashMap<String, SemanticType> environment,
			HashSet<String> generics, WyalFile.Context context) {
		SemanticType type = builder.convert(pattern.toSyntacticType(),generics,context);
		
		if(pattern instanceof TypePattern.Tuple) {
			TypePattern.Tuple tt = (TypePattern.Tuple) pattern;
			for(TypePattern p : tt.patterns) {
				propagate(p,environment,generics,context);
			}
		}
		if(pattern.var != null) {
			environment.put(pattern.var,type);
		}
		if(pattern.source != null) {
			SemanticType ct = propagate(pattern.source,environment,generics,context);
			checkIsSubtype(SemanticType.SetAny,ct,pattern);
			// TODO: need effective set here
			SemanticType.Set set_t = (SemanticType.Set) ct;
			checkIsSubtype(type,set_t.element(),pattern);
		}
		if(pattern.constraint != null) {
			SemanticType ct = propagate(pattern.constraint,environment,generics,context);
			checkIsSubtype(SemanticType.Bool,ct,pattern);
		}		
		
		pattern.attributes().add(new TypeAttribute(type));
	}
	
	private SemanticType propagate(Expr.FunCall e,
			HashMap<String, SemanticType> environment,
			HashSet<String> generics, WyalFile.Context context) {
				
		SemanticType.Function fnType;		
		
		try {			
			Pair<NameID,SemanticType.Function> p = builder.resolveAsFunctionType(e.name,context);			
			fnType = p.second();
		} catch(ResolveError re) {
			syntaxError("cannot resolve as function or definition call", context.file().filename(), e, re);
			return null;
		}
		
		SemanticType[] fn_generics = fnType.generics();
		
		if (fn_generics.length != e.generics.length) {
			// could resolve this with inference in the future.
			syntaxError(
					"incorrect number of generic arguments provided (got "
							+ e.generics.length + ", required "
							+ fn_generics.length + ")", context.file()
							.filename(), e);
		}
			
		SemanticType argument = propagate(e.operand, environment, generics,
				context);
		HashMap<String, SemanticType> binding = new HashMap<String, SemanticType>();

		for (int i = 0; i != e.generics.length; ++i) {
			SemanticType.Var gv = (SemanticType.Var) fn_generics[i];
			binding.put(gv.name(),
					builder.convert(e.generics[i], generics, context));
		}

		fnType = (SemanticType.Function) fnType.substitute(binding);		
		checkIsSubtype(fnType.from(), argument, e.operand);
		return fnType;	
	}
		
	/**
	 * Calculate the most precise type that captures those possible values a
	 * given expression can evaluate to.
	 * 
	 * @param e
	 * @return
	 */
	public static SemanticType returnType(Expr e) {
		SemanticType type = e.attribute(TypeAttribute.class).type;
		if (e instanceof Expr.Variable || e instanceof Expr.Constant
				|| e instanceof Expr.Quantifier) {
			return type; 
		} else if(e instanceof Expr.Unary) {
			Expr.Unary ue = (Expr.Unary) e;
			switch(ue.op) {
			case NOT:
				return SemanticType.Bool;
			case NEG:
				return type;
			case LENGTHOF:				
				return SemanticType.Int;		
			}
		} else if(e instanceof Expr.Binary) {
			Expr.Binary ue = (Expr.Binary) e;
			switch(ue.op) {
			case ADD:
			case SUB:
			case MUL:
			case DIV:
			case REM:
			case SETUNION:
			case SETINTERSECTION:
			case LISTAPPEND:
			case RANGE:
				return type;
			case EQ:
			case NEQ:			
			case IMPLIES:
			case IFF:				
			case LT:
			case LTEQ:
			case GT:
			case GTEQ:				
			case IN: 
			case SUBSET:
			case SUBSETEQ:
			case SUPSET:
			case SUPSETEQ: 
				return SemanticType.Bool;							
			}
		} else if(e instanceof Expr.Ternary) {
			Expr.Ternary ue = (Expr.Ternary) e;
			switch(ue.op) {
			case UPDATE:
			case SUBLIST:
				return type;
			}
		} else if(e instanceof Expr.Nary) {
			Expr.Nary ue = (Expr.Nary) e;
			switch(ue.op) {
			case AND:
			case OR:				
				return SemanticType.Bool;		
			case TUPLE:
			case SET:
			case LIST:
				return type;
			}
		} else if(e instanceof Expr.IndexOf) {
			Expr.IndexOf ue = (Expr.IndexOf) e;
			if(type instanceof SemanticType.EffectiveTuple) {
				SemanticType.EffectiveTuple tt = (SemanticType.EffectiveTuple) type;				
				Value.Integer idx = (Value.Integer) ((Expr.Constant) ue.index).value;
				return tt.tupleElement(idx.value.intValue());
			} else {
				SemanticType.Set st = (SemanticType.Set) type;
				SemanticType.EffectiveTuple tt = (SemanticType.EffectiveTuple) st.element();
				return tt.tupleElement(1);
			}
		} else {
			Expr.FunCall fc = (Expr.FunCall) e;
			return ((SemanticType.Function) type).to();
		}
		// should be deadcode.
		throw new IllegalArgumentException("Invalid opcode for expression");
	}
	
	/**
	 * Check that t1 :> t2 or, equivalently, that t2 is a subtype of t1. A type
	 * <code>t1</code> is said to be a subtype of another type <code>t2</code>
	 * iff the semantic set described by <code>t1</code> contains that described
	 * by <code>t2</code>.
	 * 
	 * @param t1
	 *            --- Semantic type that should contain <code>t2</code>.
	 * @param t2
	 *            --- Semantic type that shold be contained by <code>t1/code>.
	 * @param element
	 *            --- Syntax error is reported against this element if
	 *            <code>t1</code> does not contain <code>t2</code>.
	 */
	private void checkIsSubtype(SemanticType t1, SemanticType t2, SyntacticElement element) {
		if(!SemanticType.isSubtype(t1,t2)) {
			syntaxError("expected type " + t1 + ", got type " + t2,filename,element);
		}
	}
}
