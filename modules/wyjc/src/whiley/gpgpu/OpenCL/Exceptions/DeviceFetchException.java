package whiley.gpgpu.OpenCL.Exceptions;

public class DeviceFetchException extends OpenCLException {
	private static final long serialVersionUID = 1L;

	public DeviceFetchException(int i, String string) {
		super(i, string);
	}
}
