package whiley.gpu.OpenCL.Exceptions;

import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.Map;

import whiley.gpu.OpenCL.Devices.Device;


public class ProgramCompilationException extends OpenCLException {
	private static final long serialVersionUID = 1L;
	
	public static String descriptionFromSourceCode(String source[]) {
		StringWriter sw = new StringWriter();
		
		long count = 0;
		for(String s : source) {
			count++;
			
			sw.append("=========== Source file (");
			sw.append(Long.toString(count));
			sw.append(" of ");
			sw.append(Long.toString(source.length));
			sw.append("): ===========\n");
			sw.append(s);
		}
		
		return sw.toString();
	}
	
	public ProgramCompilationException(int reason, Map<Device, byte[]> logs, String source[]) {
		super(reason, "Failed to build program executable.\n: ===========\n" + descriptionFromLogs(logs) + descriptionFromSourceCode(source));
	}

	private static String descriptionFromLogs(Map<Device, byte[]> logs) {
		StringWriter sw = new StringWriter();
		
		for(Map.Entry<Device, byte[]> entry : logs.entrySet()) {			
			sw.append("=========== Build Log (");
			sw.append(entry.getKey().deviceDescription());
			sw.append("): ===========\n");
			sw.append(new String(entry.getValue(), Charset.forName("UTF-8")));
		}
		
		return sw.toString();
	}
}
