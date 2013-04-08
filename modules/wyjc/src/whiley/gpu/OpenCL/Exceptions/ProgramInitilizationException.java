package whiley.gpu.OpenCL.Exceptions;


public class ProgramInitilizationException extends OpenCLException {
	private static final long serialVersionUID = 1L;

	public ProgramInitilizationException(int i) {
		super(i, "Failed to create compute program");
	}
}
