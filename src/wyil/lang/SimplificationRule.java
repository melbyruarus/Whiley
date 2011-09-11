package wyil.lang;

import java.util.ArrayList;

import wyautl.lang.*;

/**
 * <p>
 * The simplification rule implements a rewrite rule for the automata
 * representation of types in Whiley. The rewrite rule applies a number of
 * relatively straightforward simplifications, including:
 * </p>
 * <ul>
 * <li><code>T | any</code> => <code>any</code>.</li>
 * <li><code>T & any</code> => <code>T</code>.
 * <li><code>T | void</code> => <code>T</code>.</li>
 * <li><code>T & void</code> => <code>void</code>.
 * <li><code>(T_1 | T_2) | T_3</code> => <code>(T_1 | T_2) | T_3</code>.
 * <li><code>(T_1 & T_2) & T_3</code> => <code>(T_1 & T_2) & T_3</code>.</li>
 * </ul>
 * <p>
 * <b>NOTE:</b> applications of this rewrite rule may leave states which are
 * unreachable from the root. Therefore, the resulting automata should be
 * extracted after rewriting to eliminate any such states.
 * </p>
 * 
 * @author djp
 * 
 */
public final class SimplificationRule implements RewriteRule {
	
	public final boolean apply(int index, Automata automata) {
		Automata.State state = automata.states[index];
		switch(state.kind) {
		case Type.K_UNION:
			return applyUnion(index,state,automata);
		case Type.K_INTERSECTION:
			return applyIntersection(index,state,automata);
		case Type.K_NEGATION:
			return applyNot(index,state,automata);
		}
		return false;
	}
	
	public boolean applyNot(int index, Automata.State state,
			Automata automata) {				
		int childIndex = state.children[0];
		Automata.State child = automata.states[childIndex];
		switch(child.kind) {
			case Type.K_ANY:
				automata.states[index] = new Automata.State(Type.K_VOID);
				return true;
			case Type.K_VOID:
				automata.states[index] = new Automata.State(Type.K_ANY);
				return true;
			case Type.K_NEGATION:
				// bypass this node altogether
				int childChildIndex = child.children[0];
				Automata.State childChild = automata.states[childChildIndex];
				automata.states[index] = new Automata.State(childChild);
				return true;
			case Type.K_UNION:
			case Type.K_INTERSECTION: {						
				int[] child_children = child.children;
				int[] nchildren = new int[child_children.length];
				Automata.State[] nstates = new Automata.State[child_children.length];				
				for(int i=0;i!=child_children.length;++i) {					
					int[] children = new int[1];
					children[0] = child_children[i];
					nchildren[i] = i + automata.size();
					nstates[i] = new Automata.State(Type.K_NEGATION,children);
				}
				Automatas.inplaceAppendAll(automata, nstates);
				state = automata.states[index];
				int nkind = child.kind == Type.K_UNION ? Type.K_INTERSECTION : Type.K_UNION;
				state.kind = nkind;				
				state.children = nchildren;
				state.deterministic = true;
				
				return true;
			}
			default:
				return false;
		}
	}
	
	public boolean applyIntersection(int index, Automata.State state,
			Automata automata) {				
		int[] children = state.children;		
		for(int i=0;i!=children.length;++i) {			
			int iChild = children[i];
			Automata.State child = automata.states[iChild];
			switch(child.kind) {
			case Type.K_VOID:								
				automata.states[index] = new Automata.State(Type.K_VOID);
				return true;
			case Type.K_INTERSECTION:
				return flattenChildren(index,i,state,automata);
			case Type.K_UNION:
				return applyIntersection_2(index,state,automata);			
			}
		}
		return applyIntersection_4(index, state, automata);		
	}
	
	/**
	 * This rule distributes over children of type UNION
	 * 
	 * @param index
	 * @param state
	 * @param automata
	 * @return
	 */
	public boolean applyIntersection_2(int index, Automata.State state,
			Automata automata) {
		return false;
	}
	
	/**
	 * This rule removes subsumed children from an intersection.
	 * 
	 * @param index
	 * @param state
	 * @param automata
	 * @return
	 */
	public boolean applyIntersection_4(int index, Automata.State state,
			Automata automata) {
		boolean changed = false;
		int[] children = state.children;		
		int kind = Type.K_ANY;
		ArrayList<Integer> nchildren = new ArrayList<Integer>();
		
		for(int i=0;i!=children.length;++i) {			
			int iChild = children[i];
			// check for contractive case
			if(iChild != index) {					
				// check whether this child is subsumed				
				Automata.State child = automata.states[iChild];
				if(kind == Type.K_ANY) {				
					kind = child.kind;
				} else if (kind != child.kind && child.kind != Type.K_ANY) {
					// FIXME: surely there's a bug here if e.g. child is !void
					// this indicates the intersection is equivalent to void
					automata.states[index] = new Automata.State(Type.K_VOID);
					return true;
				}								
				nchildren.add(iChild);				
			} else {				
				changed = true;
			}					
		}	
		
		if(kind >= 0 && kind < Type.K_STRING) {
			automata.states[index] = new Automata.State(kind);
		} else if(nchildren.size() == 0) {
			// this can happen in the case of an intersection which has only itself as a
			// child.
			automata.states[index] = new Automata.State(Type.K_VOID);
		} else if(nchildren.size() == 1) {
			// bypass this node altogether
			int child = nchildren.get(0);
			automata.states[index] = new Automata.State(automata.states[child]);			
		} else if(changed) {			
			children = new int[nchildren.size()];
			for(int i=0;i!=children.length;++i) {
				children[i] = nchildren.get(i);
			}
			automata.states[index] = new Automata.State(Type.K_INTERSECTION, children,
					false);
		}
		return changed;
	}	
	
	public boolean applyUnion(int index, Automata.State state,
			Automata automata) {		
		int[] children = state.children;				
		for(int i=0;i!=children.length;++i) {			
			int iChild = children[i];
			Automata.State child = automata.states[iChild];
			switch(child.kind) {
			case Type.K_ANY:								
				automata.states[index] = new Automata.State(Type.K_ANY);
				return true;			
			case Type.K_UNION:
				return flattenChildren(index,i,state,automata);
			}
		}
		return applyUnion_2(index,state,automata);
	}
	
	
	public boolean applyUnion_2(int index, Automata.State state,
			Automata automata) {
		boolean changed = false;
		int[] children = state.children;		
		ArrayList<Integer> nchildren = new ArrayList<Integer>();
		
		for(int i=0;i!=children.length;++i) {			
			int iChild = children[i];
			Automata.State child = automata.states[iChild];
			switch(child.kind) {
			case Type.K_ANY:								
				automata.states[index] = new Automata.State(Type.K_ANY);
				return true;						
			default:
				// check for contractive case
				if(iChild != index) {					
					// check whether this child is subsumed
					boolean subsumed = false;
					if(!subsumed) {
						nchildren.add(iChild);
					} else {
						changed = true;
					}
				} else {
					changed = true;
				}
			}						
		}
		
		if(nchildren.size() == 0) {
			// this can happen in the case of a union which has only itself as a
			// child.
			automata.states[index] = new Automata.State(Type.K_VOID);
		} else if(nchildren.size() == 1) {
			// bypass this node altogether
			int child = nchildren.get(0);
			automata.states[index] = new Automata.State(automata.states[child]);			
		} else if(changed) {			
			children = new int[nchildren.size()];
			for(int i=0;i!=children.length;++i) {
				children[i] = nchildren.get(i);
			}
			automata.states[index] = new Automata.State(Type.K_UNION, children,
					false);
		}
		return changed;
	}

	/**
	 * This rule flattens children which have the same kind as the given state.
	 * 
	 * @param index
	 * @param state
	 * @param automata
	 * @return
	 */
	public boolean flattenChildren(int index, int start,
			Automata.State state, Automata automata) {
		ArrayList<Integer> nchildren = new ArrayList<Integer>();
		int[] children = state.children;
		final int kind = state.kind;		
		
		for (int i = start; i != children.length; ++i) {
			int iChild = children[i];
			Automata.State child = automata.states[iChild];
			if(child.kind == kind) {
				for (int c : child.children) {
					nchildren.add(c);
				}
			} else {				
				nchildren.add(iChild);
			}
		}

		children = new int[nchildren.size()];
		for (int i = 0; i != children.length; ++i) {
			children[i] = nchildren.get(i);
		}
		
		automata.states[index] = new Automata.State(kind,
				children, false);

		return true;
	}	
}
