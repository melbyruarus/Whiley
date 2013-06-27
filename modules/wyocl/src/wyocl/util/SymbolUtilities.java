package wyocl.util;

import java.io.IOException;

import wybs.io.BinaryOutputStream;
import wyil.lang.Type;
import wyrl.io.JavaIdentifierOutputStream;

public class SymbolUtilities {
	public static String nameMangle(String name, Type.FunctionOrMethod ft) {				
		try {			
			return name + "$" + typeMangle(ft);
		} catch(IOException e) {
			throw new RuntimeException(e);
		}
	}
		
	public static String typeMangle(Type.FunctionOrMethod ft) throws IOException {		
		JavaIdentifierOutputStream jout = new JavaIdentifierOutputStream();
		BinaryOutputStream binout = new BinaryOutputStream(jout);		
		Type.BinaryWriter tm = new Type.BinaryWriter(binout);
		tm.write(ft);
		binout.close(); // force flush	
		return jout.toString();		
	}
}
