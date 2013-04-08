package whiley.gpgpu.OpenCL;


import org.jocl.*;

import whiley.gpu.OpenCL.Devices.Device;
import whiley.gpu.OpenCL.Events.*;
import whiley.gpu.OpenCL.Exceptions.KernelArgumentException;
import whiley.gpu.OpenCL.Exceptions.KernelAttributeException;
import whiley.gpu.OpenCL.Exceptions.KernelExecutionException;
import whiley.gpu.OpenCL.Exceptions.KernelInitilizationException;
import static org.jocl.CL.*;

public class Kernel extends AbstractOpenCLObject {
	private final Program program;
	private final String kernelName;
	private cl_kernel kernel;
	
	public Kernel(Program program, String kernelName) throws KernelInitilizationException {
		if(!program.isCompiled()) {
			throw new KernelInitilizationException("Program must be compiled before creating a kernel");
		}
		
		this.program = program;
		this.kernelName = kernelName;
		
		int err[] = new int[1];
		
		this.kernel = clCreateKernel(this.program.getRawProgram(), this.kernelName, err);
	    if(this.kernel== null || err[0] != CL_SUCCESS) {
	        throw new KernelInitilizationException(err[0], "Failed to create compute kernel");
	    }
	}
	
	public void setArgument(int index, int size, Pointer pointer) throws KernelArgumentException {
		int err;
		
		err = clSetKernelArg(kernel, index, size, pointer);
		
		if(err != CL_SUCCESS) {
			throw new KernelArgumentException(err, "Failed to set kernel argument at index: "+index);
		}
	}
	
	public void setArgument(int index, int argument) throws KernelArgumentException {
		setArgument(index, Integer.SIZE/Byte.SIZE, Pointer.to(new int[]{argument}));
	}
	
	public void setArgument(int index, float argument) throws KernelArgumentException {
		setArgument(index, Float.SIZE/Byte.SIZE, Pointer.to(new float[]{argument}));
	}
	
	public void setArgument(int index, Buffer argument) throws KernelArgumentException {
		setArgument(index, Buffer.SIZE, argument.getPointer());
	}
	
	/**
	 * Enqueue a kernel execution.
	 * 
	 * NOTE: Optimum values for localWorkSizes depend on the target hardware and workload.
	 * NOTE: If localWorkSizes is not null then for each dimension n, globalWorkSizes[n] must
	 * be evenly divisible by localWorkSizes[n]
	 * 
	 * @param queue The command queue to add this operation to.
	 * @param dimensions The number of dimensions of the work to process
	 * @param globalWorkOffsets The offsets of the global work for each dimension, may be null.
	 * @param globalWorkSizes The total size of the operation to perform for each dimension, must not be null.
	 * @param localWorkSizes The size to chunk the operation into for each dimension, may be null.
	 * @param waitList A list of events to wait on before beginning, may be null.
	 * @param event An event to use to signal completion or to wait on, may be null.
	 * @throws KernelExecutionException
	 */
	public void enqueueKernelWithWorkSizes(CommandQueue queue, int dimensions, long globalWorkOffsets[], long globalWorkSizes[], long localWorkSizes[], EventDependancy waitList, Event event) throws KernelExecutionException {
		int err;
		
		assert queue.getContext() == program.getContext();
		
		err = clEnqueueNDRangeKernel(queue.getRawCommandQueue(), kernel, dimensions, globalWorkOffsets, globalWorkSizes, localWorkSizes, (waitList == null) ? 0 : waitList.count(), (waitList == null) ? null : waitList.getAsArray(), (event == null) ? null : event.getRawEvent());
		if(err != CL_SUCCESS) {
			throw new KernelExecutionException(err, "Failed to execute kernel");
		}
	}
	
	@Override
	public void dealloc() {
		if(kernel != null) {
			clReleaseKernel(kernel);
			kernel = null;
		}
	}

	public long getLocalWorkGroupSizeForDevice(Device device) throws KernelAttributeException {
		int err;
		
		long localWorkGroupSize[] = new long[1];
	    // FIXME: This may fall over on non-64bit machines 
		err = clGetKernelWorkGroupInfo(kernel, device.getRawDeviceId(), CL_KERNEL_WORK_GROUP_SIZE, Long.SIZE/Byte.SIZE, Pointer.to(localWorkGroupSize), null);
		if(err != CL_SUCCESS) {
			throw new KernelAttributeException(err, "Failed to retrieve kernel work group info");
		}
		
		return localWorkGroupSize[0];
	}
}
