package wyts.io;

import java.io.IOException;

import wyil.lang.NameID;
import wyil.lang.Type;
import wyil.util.Pair;
import wyjvm.io.BinaryOutputStream;
import wyts.lang.Node;
import wyts.util.TypeBuilder;

/**
 * The BinaryBuilder converts a type into a short binary string.
 * 
 * @author djp
 * 
 */
public class BinaryTypeWriter implements TypeBuilder {		
	private final BinaryOutputStream writer;	
	
	public BinaryTypeWriter(BinaryOutputStream writer) {
		this.writer = writer;			
	}
	
	public void initialise(int numNodes) {
		try {				
			writeLength(numNodes);
		} catch(IOException e) {
			throw new RuntimeException("internal failure",e);
		}
	}

	public void buildPrimitive(int index, Type.Leaf t) {
		try {
			if(t == Type.T_ANY) {
				writeKind(State.K_ANY );
			} else if(t == Type.T_VOID) {
				writeKind(State.K_VOID);
			} else if(t == Type.T_NULL) {
				writeKind(State.K_NULL);
			} else if(t == Type.T_BOOL) {
				writeKind(State.K_BOOL);			
			} else if(t == Type.T_BYTE) {			
				writeKind(State.K_BYTE);		
			} else if(t == Type.T_CHAR) {			
				writeKind(State.K_CHAR);		
			} else if(t == Type.T_INT) {			
				writeKind(State.K_INT);		
			} else if(t == Type.T_REAL) {
				writeKind(State.K_RATIONAL);			
			} else if(t == Type.T_STRING) {
				writeKind(State.K_STRING);			
			} else {
				throw new RuntimeException("unknown type encountered: " + t);		
			}
		} catch(IOException e) {
			throw new RuntimeException("internal failure",e);
		}			
	}

	public void buildExistential(int index, NameID name) {
		try {
			writeKind(State.K_EXISTENTIAL);				
			writeIdentifier(name.module().toString());
			writeIdentifier(name.name());				
		} catch(IOException e) {
			throw new RuntimeException("internal failure",e);
		}
	}

	public void buildSet(int index, int element) {
		try {
			writeKind(State.K_SET);			
			writeNode(element);
		} catch(IOException e) {
			throw new RuntimeException("internal failure",e);
		}
	}

	public void buildList(int index, int element) {
		try {
			writeKind(State.K_LIST);
			writeNode(element);
		} catch(IOException e) {
			throw new RuntimeException("internal failure",e);
		}
	}

	public void buildProcess(int index, int element) {
		try {
			writeKind(State.K_PROCESS);	
			writeNode(element);				
		} catch(IOException e) {
			throw new RuntimeException("internal failure",e);
		}
	}

	public void buildDictionary(int index, int key, int value) {
		try {
			writeKind(State.K_DICTIONARY);
			writeNode(key);
			writeNode(value);
		} catch(IOException e) {
			throw new RuntimeException("internal failure",e);
		}
	}

	public void buildTuple(int index, int... elements) {
		try {
			writeKind(State.K_TUPLE);
			writeLength(elements.length);
			for(int e : elements) {					
				writeNode(e);					
			}	
		} catch(IOException e) {
			throw new RuntimeException("internal failure",e);
		}
	}

	public void buildRecord(int index, Pair<String, Integer>... fields) {
		try {				
			writeKind(State.K_RECORD);
			// FIXME: bug here if number of entries > 64K
			writeLength(fields.length);
			for(Pair<String,Integer> p : fields) {
				writeIdentifier(p.first());										
				writeNode(p.second());					
			}			
		} catch(IOException e) {
			throw new RuntimeException("internal failure",e);
		}
	}

	public void buildFunction(int index, int ret,
			int... parameters) {
		try {				
			writeKind(State.K_FUNCTION);				
			writeNode(ret);
			writeLength(parameters.length);
			for (int p : parameters) {
				writeNode(p);
			}
		} catch (IOException e) {
			throw new RuntimeException("internal failure", e);
		}
	}

	public void buildMethod(int index, int receiver, int ret,
			int... parameters) {
		try {										
			if(receiver == -1) {
				// indicates headless method
				writeKind(State.K_HEADLESS);
			} else {
				writeKind(State.K_METHOD);
				writeNode(receiver);
			}
			writeNode(ret);
			writeLength(parameters.length);
			for (int p : parameters) {
				writeNode(p);
			}
		} catch (IOException e) {
			throw new RuntimeException("internal failure", e);
		}
	}
	
	public void buildUnion(int index, int... bounds) {
		try {				
			writeKind(State.K_UNION);			
			writeLength(bounds.length);
			for(int b : bounds) {
				writeNode(b);
			}	
		} catch(IOException e) {
			throw new RuntimeException("internal failure",e);
		}
	}
	
	public void buildIntersection(int index, int... bounds) {
		try {				
			writeKind(State.K_INTERSECTION);			
			writeLength(bounds.length);
			for(int b : bounds) {
				writeNode(b);
			}	
		} catch(IOException e) {
			throw new RuntimeException("internal failure",e);
		}
	}
	
	public void buildDifference(int index, int left, int right) {
		try {
			writeKind(State.K_DIFFERENCE);
			writeNode(left);
			writeNode(right);
		} catch(IOException e) {
			throw new RuntimeException("internal failure",e);
		}
	}
	
	protected void writeKind(int kind) throws IOException {
		writer.write_un(kind,5);
	}
	
	protected void writeLength(int len) throws IOException {
		writer.write_uv(len);
	}
	
	protected void writeNode(int node) throws IOException {
		writer.write_uv(node);			
	}
	
	/**
	 * An identifier is a string made up of characters from
	 * [A-Za-z_][A-Za-z0-9_]*
	 * 
	 * @param identifier
	 * @throws IOException
	 */
	protected void writeIdentifier(String id) throws IOException {			
		writeLength(id.length());
		for(int i=0;i!=id.length();++i) {
			writer.write_un(id.charAt(i),7);
		}
	}		
}	
