package wyocl.ar;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import wybs.util.Pair;
import wyil.lang.Type;

public class DFGGenerator {
	public static void populateDFG(CFGNode root, Map<Integer, Pair<Type, Set<DFGNode>>> registerTypes, Set<CFGNode> endNodes) {
		if(endNodes == null) {
			endNodes = new HashSet<CFGNode>();
		}
		
		Set<Pair<CFGNode, Map<Integer, Pair<Type, Set<DFGNode>>>>> fringe = new HashSet<Pair<CFGNode, Map<Integer, Pair<Type, Set<DFGNode>>>>>();
		fringe.add(new Pair<CFGNode, Map<Integer, Pair<Type, Set<DFGNode>>>>(root, registerTypes));
		
		while(fringe.size() > 0) {
			Pair<CFGNode, Map<Integer, Pair<Type, Set<DFGNode>>>> anEntry = fringe.iterator().next();
			CFGNode node = anEntry.first();
			Map<Integer, Pair<Type, Set<DFGNode>>> info = anEntry.second();
			
			node.propogateRegisterInfo(info);
			
			if(!endNodes.contains(node)) {
				Set<CFGNode> nodes = new HashSet<CFGNode>();
				node.getFlowNextNodes(nodes);
				for(CFGNode next : nodes) {
					fringe.add(new Pair<CFGNode, Map<Integer, Pair<Type, Set<DFGNode>>>>(next, node.getEndRegisterInfo()));
				}
			}
		}
	}
	
	public static Map<Integer, Pair<Type, Set<DFGNode>>> mergeRegisterInfo(Map<Integer, Pair<Type, Set<DFGNode>>> one, Map<Integer, Pair<Type, Set<DFGNode>>> two) {
		if(one == null && two == null) {
			return new HashMap<Integer, Pair<Type, Set<DFGNode>>>();
		}
		else if(one == null) {
			return two;
		}
		else if(two == null) {
			return one;
		}
		else {
			Map<Integer, Pair<Type, Set<DFGNode>>> merged = new HashMap<Integer, Pair<Type, Set<DFGNode>>>();
			
			merged.putAll(one);
			
			for(Map.Entry<Integer, Pair<Type, Set<DFGNode>>> entry : two.entrySet()) {
				Pair<Type, Set<DFGNode>> info = merged.get(entry.getKey());
				if(info != null) {
					merged.put(entry.getKey(), mergeInfo(info, entry.getValue()));
				}
				else {
					merged.put(entry.getKey(), entry.getValue());
				}
			}
			
			return merged;
		}
	}

	private static Pair<Type, Set<DFGNode>> mergeInfo(Pair<Type, Set<DFGNode>> one, Pair<Type, Set<DFGNode>> two) {
		Set<DFGNode> nodes = new HashSet<DFGNode>();
		nodes.addAll(one.second());
		nodes.addAll(two.second());
		return new Pair<Type, Set<DFGNode>>(mergeTypes(one.first(), two.first()), nodes);
	}

	public static Type mergeTypes(Type one, Type two) {
		if(one == null && two == null) {
			return null;
		}
		else if(one == null) {
			return two;
		}
		else if(two == null) {
			return one;
		}
		
		if(one.equals(two)) {
			return one;
		}
		else {
			ArrayList<Type> bounds = new ArrayList<Type>();
			
			if(one instanceof Type.Union && two instanceof Type.Union) {
				bounds.addAll(((Type.Union)one).bounds());
				bounds.addAll(((Type.Union)two).bounds());
				return Type.Union(bounds);
			}
			else if(one instanceof Type.Union) {
				bounds.addAll(((Type.Union)one).bounds());
				bounds.add(two);
				return Type.Union(bounds);
			}
			else if(two instanceof Type.Union) {
				bounds.add(two);
				bounds.addAll(((Type.Union)two).bounds());
				return Type.Union(bounds);
			}
			else {
				bounds.add(one);
				bounds.add(two);
				return Type.Union(bounds);
			}
		}
	}

	public static Map<Integer, Pair<Type, Set<DFGNode>>> propogateTypesThroughBytecodes(List<Bytecode> instructions, Map<Integer, Pair<Type, Set<DFGNode>>> registerInfo) {
		Map<Integer, Pair<Type, Set<DFGNode>>> runningState = new HashMap<Integer, Pair<Type, Set<DFGNode>>>(registerInfo);
		
		for(Bytecode b : instructions) {
			Set<Integer> readRegisters = new HashSet<Integer>();
			Set<Pair<Integer, Type>> writtenRegisters = new HashSet<Pair<Integer, Type>>();
			
			b.getRegisterSummary(writtenRegisters, readRegisters);
			
			for(int register : readRegisters) {
				DFGNode node = b.readDFGNodes.get(register);
				
				if(node == null) {
					node = new DFGNode(b, register);
					b.readDFGNodes.put(register, node);
				}
				
				Pair<Type, Set<DFGNode>> state = runningState.get(register);
				if(state == null) {
					throw new RuntimeException("Internal Inconsistancy");
				}
				node.lastModified.addAll(state.second());
				node.type = mergeTypes(node.type, state.first());
			}
			
			for(Pair<Integer, Type> pair : writtenRegisters) {
				int register = pair.first();
				Type type = pair.second();
				
				DFGNode node = b.readDFGNodes.get(register);
				
				if(node == null) {
					node = new DFGNode(b, register);
					b.readDFGNodes.put(register, node);
				}
				
				Pair<Type, Set<DFGNode>> state = runningState.get(register);
				if(state == null) {
					throw new RuntimeException("Internal Inconsistancy");
				}
				
				node.lastModified.addAll(state.second());
				Set<DFGNode> set = new HashSet<DFGNode>();
				set.add(node);
				runningState.put(register, new Pair<Type, Set<DFGNode>>(type, set));
			}
		}
		
		return runningState;
	}
}
