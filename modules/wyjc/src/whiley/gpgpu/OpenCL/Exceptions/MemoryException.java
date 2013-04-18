package whiley.gpgpu.OpenCL.Exceptions;

public class MemoryException extends OpenCLException {
	private static final long serialVersionUID = 1L;

	public MemoryException(int i, String string) {
		super(i, string);
	}
}
