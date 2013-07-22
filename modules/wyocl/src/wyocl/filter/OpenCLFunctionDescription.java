package wyocl.filter;

import java.util.List;

import wyil.lang.Type;
import wyocl.ar.utils.CFGIterator.Entry;

public class OpenCLFunctionDescription {
	private final String name;
	private final List<Argument> params;
	private final List<Entry> entries;
	private final Type returnType;
	
	public OpenCLFunctionDescription(String name, List<Argument> params, List<Entry> entries, Type returnType) {
		this.name = name;
		this.params = params;
		this.entries = entries;
		this.returnType = returnType;
	}

	public String getName() {
		return name;
	}

	public List<Argument> getParams() {
		return params;
	}

	public List<Entry> getEntries() {
		return entries;
	}

	public Type getReturnType() {
		return returnType;
	}
}
