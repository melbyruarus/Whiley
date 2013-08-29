package wyocl.ar;

import java.util.HashSet;
import java.util.Set;

import wyil.lang.Type;

public class DFGNode {
	public interface DFGNodeCause {

		void gatherReadDFGNodes(Set<DFGNode> readNodes);
		void gatherWrittenDFGNodes(Set<DFGNode> writtenNodes);
	}
	
	public final Set<DFGNode> lastModified = new HashSet<DFGNode>();
	public final Set<DFGNode> nextModified = new HashSet<DFGNode>();
	public final Set<DFGNode> lastRead = new HashSet<DFGNode>();
	public final Set<DFGNode> nextRead = new HashSet<DFGNode>();
	public final Set<DFGNode> lastReadOrWrite = new HashSet<DFGNode>();
	public final DFGNodeCause cause;
	public final int register;
	public final boolean isAssignment;
	public Type type;
	
	public DFGNode(DFGNodeCause cause, int register, Type type, boolean isAssignment) {
		this.cause = cause;
		this.register = register;
		this.type = type;
		this.isAssignment = isAssignment;
	}

	public String getSummary() {
		String ret = this.hashCode() + " register(" + register + ") type("+type+")";
		String sep = " lastModifed(";
		for(DFGNode n : lastModified) {
			ret += sep + n.hashCode();
			sep = ", ";
		}
		if(!lastModified.isEmpty()) {
			ret += ")";
		}
		
		sep = " lastRead(";
		for(DFGNode n : lastRead) {
			ret += sep + n.hashCode();
			sep = ", ";
		}
		if(!lastRead.isEmpty()) {
			ret += ")";
		}
		
		sep = " nextModified(";
		for(DFGNode n : nextModified) {
			ret += sep + n.hashCode();
			sep = ", ";
		}
		if(!nextModified.isEmpty()) {
			ret += ")";
		}
		
		sep = " nextRead(";
		for(DFGNode n : nextRead) {
			ret += sep + n.hashCode();
			sep = ", ";
		}
		if(!nextRead.isEmpty()) {
			ret += ")";
		}
		
		return ret;
	}
}