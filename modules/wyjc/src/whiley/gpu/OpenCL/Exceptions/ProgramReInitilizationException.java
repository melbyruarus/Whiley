package whiley.gpu.OpenCL.Exceptions;


public class ProgramReInitilizationException extends OpenCLException {
	private static final long serialVersionUID = 1L;

	public ProgramReInitilizationException() {
		super("Attempting to initilize a Program which has already been loaded");
	}
}
