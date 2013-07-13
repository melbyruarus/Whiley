package wyocl.ar;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import wybs.util.Pair;
import wyil.lang.Type;
import wyocl.ar.utils.CFGIterator;

public class DFGGenerator {
	private static final boolean DEBUG = false;
	
	public static class DFGInfo {
		public final Type type;
		public final Set<DFGNode> lastNodes = new HashSet<DFGNode>();
		
		public DFGInfo(Type type) {
			this.type = type;
		}
		
		public DFGInfo(Type type, Set<DFGNode> lastAssigments) {
			this.type = type;
			this.lastNodes.addAll(lastAssigments);
		}
		
		public DFGInfo(DFGInfo other) {
			this.type = other.type;
			this.lastNodes.addAll(other.lastNodes);
		}

		public DFGInfo(DFGNode node) {
			this.type = node.type;
			lastNodes.add(node);
		}

		public int hashCode() {
			return 1238978 + type.hashCode() ^ lastNodes.hashCode(); 
		}
			
		public boolean equals(Object o) {
			if(o instanceof DFGInfo) {
				DFGInfo ot = (DFGInfo)o;
				return this.type.equals(ot.type) && this.lastNodes.equals(ot.lastNodes);
			}
			return false;
		}
	}
	
	public static class DFGRegisterMapping {
		public Map<Integer, DFGInfo> registerMapping = new HashMap<Integer, DFGInfo>();
		
		public DFGRegisterMapping(DFGRegisterMapping info) {
			for(Map.Entry<Integer, DFGInfo> entry : info.registerMapping.entrySet()) {
				registerMapping.put(entry.getKey(), new DFGInfo(entry.getValue()));
			}
		}
		
		public DFGRegisterMapping() {
		}
		
		public int hashCode() {
			return 323444 + registerMapping.hashCode(); 
		}
			
		public boolean equals(Object o) {
			if(o instanceof DFGRegisterMapping) {
				DFGRegisterMapping ot = (DFGRegisterMapping)o;
				return this.registerMapping.equals(ot.registerMapping);
			}
			return false;
		}
	}
	
	public static class DFGReadWriteTracking {
		public final DFGRegisterMapping readWriteInfo;
		public final DFGRegisterMapping writeInfo;
		
		public DFGReadWriteTracking(DFGRegisterMapping readInfo, DFGRegisterMapping writeInfo) {
			this.readWriteInfo = readInfo;
			this.writeInfo = writeInfo;
		}
		
		public DFGReadWriteTracking() {
			readWriteInfo = new DFGRegisterMapping();
			writeInfo = new DFGRegisterMapping();
		}
		
		public DFGReadWriteTracking(DFGReadWriteTracking registerInfo) {
			readWriteInfo = new DFGRegisterMapping(registerInfo.readWriteInfo);
			writeInfo = new DFGRegisterMapping(registerInfo.writeInfo);
		}

		public int hashCode() {
			return 452345 + readWriteInfo.hashCode() ^ writeInfo.hashCode(); 
		}
			
		public boolean equals(Object o) {
			if(o instanceof DFGReadWriteTracking) {
				DFGReadWriteTracking ot = (DFGReadWriteTracking)o;
				return this.readWriteInfo.equals(ot.readWriteInfo) && this.writeInfo.equals(ot.writeInfo);
			}
			return false;
		}
	}
	
	public static void populateDFG(CFGNode root, Map<Integer, DFGNode> argumentRegisters) {
		DFGReadWriteTracking registerInfo = new DFGReadWriteTracking(new DFGRegisterMapping(), new DFGRegisterMapping());
		
		for(Map.Entry<Integer, DFGNode> entry : argumentRegisters.entrySet()) {
			registerInfo.writeInfo.registerMapping.put(entry.getKey(), new DFGInfo(entry.getValue()));
		}
		
		populatePartialDFGFromRecursion(root, registerInfo, null);
		
		Set<DFGNode> allDFGNodes = new HashSet<DFGNode>();
		collectAllDFGNodes(root, allDFGNodes);
		
		populateNextModifiedRead(allDFGNodes);
	}
	
	/**
	 * This method should never be called outside the context of a call to populateDFG.
	 * This should only ever be called from CFGNode.propogateRegisterInfo(...)
	 * 
	 * @param root
	 * @param registerInfo
	 * @param endNodes
	 */
	protected static void populatePartialDFGFromRecursion(CFGNode root, DFGReadWriteTracking registerInfo, Set<CFGNode> endNodes) {
		if(DEBUG) {
			System.err.println("Populating DFG for" + root);
			System.err.println("With written types:");
			for(Map.Entry<Integer, DFGInfo> entry : registerInfo.writeInfo.registerMapping.entrySet()) {
				System.err.println("\t" + entry.getKey() + ": " + entry.getValue().type);
			}
			System.err.println("And read types:");
			for(Map.Entry<Integer, DFGInfo> entry : registerInfo.readWriteInfo.registerMapping.entrySet()) {
				System.err.println("\t" + entry.getKey() + ": " + entry.getValue().type);
			}
			if(endNodes != null) {
				System.err.println("Ending on:");
				for(CFGNode n : endNodes) {
					System.err.println("\t" + n);
				}
			}
		}
		
		if(endNodes == null) {
			endNodes = new HashSet<CFGNode>();
		}
		
		Set<Pair<CFGNode, DFGReadWriteTracking>> fringe = new HashSet<Pair<CFGNode, DFGReadWriteTracking>>();
		fringe.add(new Pair<CFGNode, DFGReadWriteTracking>(root, registerInfo));
		Set<Pair<CFGNode, DFGReadWriteTracking>> processed = new HashSet<Pair<CFGNode, DFGReadWriteTracking>>();
		
		while(!fringe.isEmpty()) {
			Pair<CFGNode, DFGReadWriteTracking> anEntry = fringe.iterator().next();
			CFGNode node = anEntry.first();
			processed.add(anEntry);
			fringe.remove(anEntry);
			
			if(DEBUG) { System.err.println("Processing node: "+node); }
			
			node.propogateRegisterInfo(anEntry.second());
			
			if(!endNodes.contains(node)) {
				if(DEBUG) { System.err.println("Continue processing"); }
				
				Set<CFGNode> nodes = new HashSet<CFGNode>();
				node.getScopeNextNodes(nodes);
				for(CFGNode next : nodes) {
					Pair<CFGNode, DFGReadWriteTracking> pair = new Pair<CFGNode, DFGReadWriteTracking>(next, node.getEndRegisterInfo());
					if(!processed.contains(pair)) {
						fringe.add(pair);
					}
				}
			}
		}
	}
	
	private static void populateNextModifiedRead(Set<DFGNode> allDFGNodes) {
		for(DFGNode node : allDFGNodes) {
			if(node.isAssignment) {
				for(DFGNode lastModified : node.lastModified) {
					lastModified.nextModified.add(node);
				}
				for(DFGNode lastReadOrWrite : node.lastReadOrWrite) {
					if(!lastReadOrWrite.isAssignment) {
						node.lastRead.add(lastReadOrWrite);
					}
				}
			}
			else {
				for(DFGNode lastReadOrWrite : node.lastReadOrWrite) {
					if(!lastReadOrWrite.isAssignment) {
						node.lastRead.add(lastReadOrWrite);
					}
					lastReadOrWrite.nextRead.add(node);
				}
			}
		}
	}

	private static void collectAllDFGNodes(CFGNode root, final Set<DFGNode> allDFGNodes) {
		CFGIterator.iterateCFGForwards(new CFGIterator.CFGNodeCallback() {
			@Override
			public boolean process(CFGNode node) {
				node.gatherDFGNodesInto(allDFGNodes);
				return true;
			}
		}, root, null);
	}

	public static DFGReadWriteTracking mergeRegisterInfo(DFGReadWriteTracking info1, DFGReadWriteTracking info2) {	
		if(info1 == null && info2 == null) {
			return new DFGReadWriteTracking();
		}
		else if(info1 == null) {
			return info2;
		}
		else if(info2 == null) {
			return info1;
		}
		else {
			return new DFGReadWriteTracking(mergeRegisterMappings(info1.readWriteInfo, info2.readWriteInfo),
									        mergeRegisterMappings(info1.writeInfo,     info2.writeInfo));
		}
	}
	
	private static DFGRegisterMapping mergeRegisterMappings(DFGRegisterMapping info1, DFGRegisterMapping info2) {		
		if(info1 == null && info2 == null) {
			return new DFGRegisterMapping();
		}
		else if(info1 == null) {
			return info2;
		}
		else if(info2 == null) {
			return info1;
		}
		else {
			DFGRegisterMapping merged = new DFGRegisterMapping();
			
			merged.registerMapping.putAll(info1.registerMapping);
			
			for(Map.Entry<Integer, DFGInfo> entry : info2.registerMapping.entrySet()) {
				DFGInfo info = merged.registerMapping.get(entry.getKey());
				if(info != null) {
					merged.registerMapping.put(entry.getKey(), mergeInfo(info, entry.getValue()));
				}
				else {
					merged.registerMapping.put(entry.getKey(), entry.getValue());
				}
			}
			
			return merged;
		}
	}

	private static DFGInfo mergeInfo(DFGInfo info1, DFGInfo info2) {		
		Set<DFGNode> nodes = new HashSet<DFGNode>();
		nodes.addAll(info1.lastNodes);
		nodes.addAll(info2.lastNodes);
		return new DFGInfo(mergeTypes(info1.type, info2.type), nodes);
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

	public static DFGReadWriteTracking propogateDFGThroughBytecodes(List<Bytecode> instructions, DFGReadWriteTracking registerInfo, Set<DFGNode> dfgNodes) {
		if(DEBUG) { System.err.println("Propogate types through bytecodes"); }
		
		DFGReadWriteTracking runningState = new DFGReadWriteTracking(registerInfo);
		
		for(Bytecode b : instructions) {
			if(DEBUG) { System.err.println("Propogating: "+b.getCodeString()); }
			
			Set<Integer> readRegisters = new HashSet<Integer>();
			Set<Pair<Integer, Type>> writtenRegisters = new HashSet<Pair<Integer, Type>>();
			
			b.getRegisterSummary(writtenRegisters, readRegisters);
			
			for(int register : readRegisters) {
				DFGInfo writeState = runningState.writeInfo.registerMapping.get(register);
				DFGInfo readWriteState = runningState.readWriteInfo.registerMapping.get(register);
				
				if(writeState == null) {
					throw new RuntimeException("Internal Inconsistancy, Read register never assigned: "+register);
				}
				
				DFGNode node = b.readDFGNodes.get(register);
				if(node == null) {
					node = new DFGNode(b, register, writeState.type, false);
					b.readDFGNodes.put(register, node);
				}
				node.type = mergeTypes(node.type, writeState.type);
				
				node.lastModified.addAll(writeState.lastNodes);
				if(readWriteState != null) {
					node.lastReadOrWrite.addAll(readWriteState.lastNodes);
				}
				
				runningState.readWriteInfo.registerMapping.put(register, new DFGInfo(node));
			}
			
			for(Pair<Integer, Type> pair : writtenRegisters) {				
				int register = pair.first();
				Type type = pair.second();
				
				DFGInfo writeState = runningState.writeInfo.registerMapping.get(register);
				DFGInfo readWriteState = runningState.readWriteInfo.registerMapping.get(register);
				
				DFGNode node = b.writtenDFGNodes.get(register);
				if(node == null) {
					node = new DFGNode(b, register, type, true);
					b.writtenDFGNodes.put(register, node);
				}
				node.type = mergeTypes(node.type, type);
								
				if(writeState != null) {
					node.lastModified.addAll(writeState.lastNodes);
				}
				if(readWriteState != null) {
					node.lastReadOrWrite.addAll(readWriteState.lastNodes);
				}

				runningState.writeInfo.registerMapping.put(register, new DFGInfo(node));
				runningState.readWriteInfo.registerMapping.put(register, new DFGInfo(node));
			}
			
			// FIXME: this assumes all writes happen after reads
			
			if(dfgNodes != null) {
				dfgNodes.addAll(b.readDFGNodes.values());
				dfgNodes.addAll(b.writtenDFGNodes.values());
			}
		}
		
		return runningState;
	}

	public static DFGReadWriteTracking propogateDFGThroughBytecode(Bytecode bytecode, DFGReadWriteTracking registerInfo) {
		List<Bytecode> instructions = new ArrayList<Bytecode>();
		instructions.add(bytecode);
		
		return propogateDFGThroughBytecodes(instructions, registerInfo, null);
	}
}
