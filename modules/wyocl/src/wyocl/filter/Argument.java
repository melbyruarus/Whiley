package wyocl.filter;

import wyil.lang.Type;

public class Argument implements Comparable<Argument> {
	public final Type type;
	public final int register;
	boolean readonly = true;
	
	public Argument(Object type, int register) {
		this.type = (Type)type;
		this.register = register;
	}
	
	@Override
	public int compareTo(Argument arg0) {
		return register - arg0.register;
	}

	public boolean isReadonly() {
		return readonly;
	}

	public void setReadonly(boolean readonly) {
		this.readonly = readonly;
	}
	
	public String toString() {
		return "Argument("+register+", "+type+")";
	}
}