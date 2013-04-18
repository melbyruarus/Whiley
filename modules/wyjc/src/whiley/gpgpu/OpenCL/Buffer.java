package whiley.gpgpu.OpenCL;

import org.jocl.*;

import whiley.gpgpu.OpenCL.Events.*;
import whiley.gpgpu.OpenCL.Exceptions.*;
import static org.jocl.CL.*;

public class Buffer extends AbstractOpenCLObject {
	private final Context context;
	private cl_mem buffer;
	
	public static int SIZE = Sizeof.cl_mem;
	
	public Buffer(Context context, MemoryFlags flags, long size) throws MemoryException {
		this.context = context;
		
		int err[] = new int[1];
		
		buffer = clCreateBuffer(this.context.getRawContext(), flags.getRawValue(), size, null, err);
		if(err[0] != CL_SUCCESS) {
			throw new MemoryException(err[0], "Failed to allocate device memory");
		}
	}
	
	/**
	 * Perform a synchronous write to the buffer.
	 * 
	 * @param queue The command queue to add this operation to.
	 * @param pointer A pointer to data to copy from.
	 * @param size The size in bytes of the data to copy.
	 * @param waitList Events to wait for before beginning the operation, may be null.
	 * @throws MemoryException
	 */
	public void write(CommandQueue queue, Pointer pointer, int size, EventDependancy waitList) throws MemoryException {
		int err;
		
		assert queue.getContext() == context;
		
		err = clEnqueueWriteBuffer(queue.getRawCommandQueue(), buffer, CL_TRUE, 0, size, pointer, (waitList == null) ? 0 : waitList.count(), waitList.getAsArray(), null);
		if(err != CL_SUCCESS) {
			throw new MemoryException(err, "Failed to write to source buffer");
		}
	}
	
	/**
	 * Perform an asynchronous write to the buffer.
	 * 
	 * @param queue The command queue to add this operation to.
	 * @param pointer A pointer to data to copy from.
	 * @param size The size in bytes of the data to copy.
	 * @param waitList Events to wait for before beginning the operation, may be null.
	 * @param event An event to use for signaling completion, may be null.
	 * @throws MemoryException
	 */
	public void enqueueWrite(CommandQueue queue, Pointer pointer, int size, EventDependancy waitList, Event event) throws MemoryException {
		int err;
		
		assert queue.getContext() == context;
		
		err = clEnqueueWriteBuffer(queue.getRawCommandQueue(), buffer, CL_FALSE, 0, size, pointer, (waitList == null) ? 0 : waitList.count(), (waitList == null) ? null : waitList.getAsArray(), (event == null) ? null : event.getRawEvent());
		if(err != CL_SUCCESS) {
			throw new MemoryException(err, "Failed to write to source buffer");
		}
	}
	
	/**
	 * Perform a synchronous read from the buffer.
	 * 
	 * @param queue The command queue to add this operation to.
	 * @param pointer A pointer to data to copy to.
	 * @param size The size in bytes of the data to copy.
	 * @param waitList Events to wait for before beginning the operation, may be null.
	 * @throws MemoryException
	 */
	public void read(CommandQueue queue, Pointer pointer, int size, EventDependancy waitList) throws MemoryException {
		int err;
		
		assert queue.getContext() == context;
		
		err = clEnqueueReadBuffer(queue.getRawCommandQueue(), buffer, CL_TRUE, 0, size, pointer, (waitList == null) ? 0 : waitList.count(), (waitList == null) ? null : waitList.getAsArray(), null);  
		if(err != CL_SUCCESS) {
			throw new MemoryException(err, "Failed to read from source buffer");
		}
	}
	
	/**
	 * Perform an asynchronous read from the buffer.
	 * 
	 * @param queue The command queue to add this operation to.
	 * @param pointer A pointer to data to copy to.
	 * @param size The size in bytes of the data to copy.
	 * @param waitList Events to wait for before beginning the operation, may be null.
	 * @param event An event to use for signaling completion, may be null.
	 * @throws MemoryException
	 */
	public void enqueueRead(CommandQueue queue, Pointer pointer, int size, EventDependancy waitList, Event event) throws MemoryException {
		int err;
		
		assert queue.getContext() == context;
		
		err = clEnqueueReadBuffer(queue.getRawCommandQueue(), buffer, CL_TRUE, 0, size, pointer, (waitList == null) ? 0 : waitList.count(), (waitList == null) ? null : waitList.getAsArray(), (event == null) ? null : event.getRawEvent());  
		if(err != CL_SUCCESS) {
			throw new MemoryException(err, "Failed to read from source buffer");
		}
	}
	
	@Override
	public void dealloc() {
		if(buffer != null) {
			clReleaseMemObject(buffer);
			buffer = null;
		}
	}
	
	public Pointer getPointer() {
		return Pointer.to(buffer);
	}
}
