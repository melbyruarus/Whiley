package whiley.gpu.OpenCL.Events;

import org.jocl.cl_event;

public interface EventDependancy extends Iterable<Event> {
	/**
	 * The number of dependencies
	 * 
	 * @return
	 */
	public int count();
	
	/**
	 * Get the list of dependencies as an array
	 * @return
	 */
	public cl_event[] getAsArray();
}
