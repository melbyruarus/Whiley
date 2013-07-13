package wyocl.util;

import java.io.PrintWriter;
import java.io.Writer;

public class IndentedPrintWriter extends PrintWriter {
	private int indentLevel = 0;
	private String currentIndentString = "\t";
	private boolean needsIndent = true;
	
	public IndentedPrintWriter(Writer out, int indentLevel) {
		super(out);
		this.indentLevel = indentLevel;
	}

	@Override
	public void write(char[] buf, int off, int len) {
		checkNeedsIndent();
		super.write(buf, off, len);
	}

	@Override
	public void write(char[] buf) {
		checkNeedsIndent();
		super.write(buf);
	}

	@Override
	public void write(int c) {
		checkNeedsIndent();
		super.write(c);
	}

	@Override
	public void write(String s, int off, int len) {
		checkNeedsIndent();
		super.write(s, off, len);
	}

	@Override
	public void write(String s) {
		checkNeedsIndent();
		super.write(s);
	}
	
	@Override
	public void println() {
		super.println();
		needsIndent = true;
	}
	
	private void checkNeedsIndent() {
		if(needsIndent) {
			needsIndent = false;
			writeIndent();
		}
	}
	
	private void writeIndent() {
		for(int n=0;n<indentLevel;n++) {
			super.write(currentIndentString);
		}
	}

	public void indent() {
		indentLevel++;
	}
	
	public void unindent() {
		indentLevel--;
	}
	
	public void setIndent(int indentLevel) {
		this.indentLevel = indentLevel;
	}
	
	public int getIndent() {
		return indentLevel;
	}
	
	public void setIndentString(String currentIndentString) {
		this.currentIndentString = currentIndentString;
	}
	
	public String getIndentString() {
		return currentIndentString;
	}
}
