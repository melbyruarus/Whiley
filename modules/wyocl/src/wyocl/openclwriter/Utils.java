package wyocl.openclwriter;

import java.io.PrintWriter;

public class Utils {
	public static void writeIndents(PrintWriter writer, int indentationLevel) {
		for(int i=0;i<indentationLevel;i++) {
			writer.print('\t');
		}
	}
}
