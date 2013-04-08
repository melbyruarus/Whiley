package whiley.gpu.OpenCL.Exceptions;

public class KernelArgumentException extends OpenCLException {
	private static final long serialVersionUID = 1L;

	public KernelArgumentException(int i, String string) {
		super(i, string);
	}
}
