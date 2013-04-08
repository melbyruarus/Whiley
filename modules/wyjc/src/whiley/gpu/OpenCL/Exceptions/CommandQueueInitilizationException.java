package whiley.gpu.OpenCL.Exceptions;

public class CommandQueueInitilizationException extends OpenCLException {
	private static final long serialVersionUID = 1L;

	public CommandQueueInitilizationException(int i) {
		super(i, "Failed to create a command queue");
	}
}
