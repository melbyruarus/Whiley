package whiley.gpgpu.OpenCL;

import static org.jocl.CL.*;

public enum MemoryFlags {
	READ_WRITE(CL_MEM_READ_WRITE),
	WRITE_ONLY(CL_MEM_WRITE_ONLY),
	READ_ONLY(CL_MEM_READ_ONLY),
	USE_HOST_PTR(CL_MEM_USE_HOST_PTR),
	ALLOC_HOST_PTR(CL_MEM_ALLOC_HOST_PTR),
	COPY_HOST_PTR(CL_MEM_COPY_HOST_PTR),
	HOST_WRITE_ONLY(CL_MEM_HOST_WRITE_ONLY),
	HOST_READ_ONLY(CL_MEM_HOST_READ_ONLY),
	HOST_NO_ACCESS(CL_MEM_HOST_NO_ACCESS);
	
	private long value;
	private MemoryFlags(long value) { this.value = value; }
	public long getRawValue() { return value; }
}
