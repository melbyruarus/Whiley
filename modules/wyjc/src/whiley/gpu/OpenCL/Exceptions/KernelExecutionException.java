package whiley.gpu.OpenCL.Exceptions;

public class KernelExecutionException extends OpenCLException {
	private static final long serialVersionUID = 1L;

	public KernelExecutionException(int i, String string) {
		super(i, string);
	}
}
