package whiley.gpgpu;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;

public class Utils {

	public static String resourceAsString(String resourceName) throws IOException {
		InputStream inputStream = "".getClass().getResourceAsStream(resourceName);
		
		return readInputStreamAsString(inputStream);
	}
	
	public static String fileAsString(String filename) throws IOException {
		InputStream inputStream = new FileInputStream(new File(filename));
		
		return readInputStreamAsString(inputStream);
	}

	public static String readInputStreamAsString(InputStream inputStream) throws IOException {
		if(inputStream != null) {
			BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
			
			if(reader != null) {
				StringWriter sw = new StringWriter();
			    String line = null;
			    
				while((line = reader.readLine()) != null) {
					sw.append(line);
					sw.append('\n');
				}
				
				return sw.toString();
			}
		}
		
		throw new FileNotFoundException();
	}
}
