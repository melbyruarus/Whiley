package whiley.gpgpu.OpenCL;

import static org.jocl.CL.*;


import org.jocl.*;

import whiley.gpgpu.OpenCL.Devices.*;
import whiley.gpgpu.OpenCL.Events.*;
import whiley.gpgpu.OpenCL.Exceptions.*;

public class CommandQueue extends AbstractOpenCLObject {
	private cl_command_queue commandQueue;
	private final Context context;
	private final Device device;
	
	public CommandQueue(Context context, Device device) throws CommandQueueInitilizationException {
		this.context = context;
		this.device = device;
		
		int err[] = new int[1];
		
		this.commandQueue = clCreateCommandQueue(this.context.getRawContext(), device.getRawDeviceId(), 0, err);
	    if(err[0] != CL_SUCCESS) {
	    	throw new CommandQueueInitilizationException(err[0]);
	    }
	}
	
	/**
	 * Enqueue a barrier. This barrier will cause all operations submitted
	 * after this point to be delayed until the barrier condition has been
	 * satisfied. If a list of events is provided the barrier will wait until
	 * these have all completed, however if waitList is null then the barrier
	 * will wait until all prior operations have been completed
	 * 
	 * @param waitList A list of events to wait for, may be null.
	 * @param event An event to use to monitor the state of the barrier, may be null.
	 */
	public void euqueueBarrier(EventList waitList, Event event) {
		clEnqueueBarrierWithWaitList(commandQueue, (waitList == null) ? 0 : waitList.count(), (waitList == null) ? null : waitList.getAsArray(), (event == null) ? null : event.getRawEvent());
	}
	
	protected cl_command_queue getRawCommandQueue() {
		return commandQueue;
	}
	
	@Override
	public void dealloc() {
		if(commandQueue != null) {
			clReleaseCommandQueue(commandQueue);
			commandQueue = null;
		}
	}

	public Context getContext() {
		return context;
	}

	public Device getDevice() {
		return device;
	}
}
