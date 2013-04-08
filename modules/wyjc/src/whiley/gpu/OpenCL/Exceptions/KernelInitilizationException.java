package whiley.gpu.OpenCL.Exceptions;

public class KernelInitilizationException extends OpenCLException {
	private static final long serialVersionUID = 1L;

	public KernelInitilizationException(int i, String string) {
		super(i, string);
	}

	public KernelInitilizationException(String string) {
		super(string);
	}
}
