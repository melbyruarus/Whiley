package whiley.gpu.OpenCL.Exceptions;


public class ContextInitilizationException extends OpenCLException { // TODO: create more specilized exceptions
	private static final long serialVersionUID = 1L;
	
	public ContextInitilizationException(int i, String string) {
		super(i, string);
	}
}
