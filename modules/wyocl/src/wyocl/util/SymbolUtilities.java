package wyocl.util;

import java.io.IOException;

import wybs.io.BinaryOutputStream;
import wyil.lang.Type;
import wyrl.io.JavaIdentifierOutputStream;

public class SymbolUtilities {
	public static String nameMangle(String name, Type.FunctionOrMethod ft) {				
		return name + "_" + typeMangle(ft);
	}
		
	public static String typeMangle(Type type) { // TODO: use something nicer
		try {	
			JavaIdentifierOutputStream jout = new JavaIdentifierOutputStream();
			BinaryOutputStream binout = new BinaryOutputStream(jout);		
			Type.BinaryWriter tm = new Type.BinaryWriter(binout);
			tm.write(type);
			binout.close(); // force flush	
			return jout.toString();
		} catch(IOException e) {
			throw new RuntimeException(e);
		}
	}
}
