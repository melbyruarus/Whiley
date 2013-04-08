package whiley.gpgpu.OpenCL;

import static org.jocl.CL.*;


import org.jocl.*;

import whiley.gpu.OpenCL.Devices.*;
import whiley.gpu.OpenCL.Exceptions.*;

public class Context extends AbstractOpenCLObject {
	private cl_context context;
	private final DeviceDependancy devices;
	
	public Context(DeviceDependancy deviceList) throws ContextInitilizationException {
		this.devices = deviceList;
		
		int err[] = new int[1];
		
		this.context = clCreateContext((cl_context_properties)null, deviceList.count(), deviceList.asArray(), null, null, err);
	    if(err[0] != CL_SUCCESS) {
	    	throw new ContextInitilizationException(err[0], "Failed to create a compute context");
	    }
	}
	
	protected cl_context getRawContext() {
		return context;
	}
	
	public DeviceDependancy getDevices() {
		return devices;
	}
	
	@Override
	public void dealloc() {
		if(context != null) {
			clReleaseContext(context);
			context = null;
		}
	}
}
