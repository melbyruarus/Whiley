package wyocl.openclwriter;

public class NotSupportedByGPGPUException extends RuntimeException {
	private static final long serialVersionUID = 1L;
	
	public NotSupportedByGPGPUException() {
		
	}
	
	public NotSupportedByGPGPUException(String string) {
		super(string);
	}
}
