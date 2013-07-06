package wyocl.ar;

import java.util.HashSet;
import java.util.Set;

import wyil.lang.Type;

public class DFGNode {
	public final Set<DFGNode> lastModified = new HashSet<DFGNode>();
	public final DFGNodeCause cause;
	public final int register;
	public Type type;
	
	public interface DFGNodeCause {
	}
	
	public DFGNode(DFGNodeCause cause, int register) {
		this.cause = cause;
		this.register = register;
	}
}