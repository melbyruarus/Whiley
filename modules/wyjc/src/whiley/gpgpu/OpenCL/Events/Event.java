package whiley.gpgpu.OpenCL.Events;

import java.util.Iterator;

import org.jocl.cl_event;

import whiley.gpgpu.OpenCL.AbstractOpenCLObject;
import static org.jocl.CL.*;

public class Event extends AbstractOpenCLObject implements EventDependancy {
	private final cl_event event = new cl_event();
	private cl_event eventArray[] = null;

	public cl_event getRawEvent() {
		return event;
	}
	
	public void waitForEvent() {
		clWaitForEvents(1, new cl_event[]{event});
	}

	@Override
	public int count() {
		return 1;
	}

	@Override
	public cl_event[] getAsArray() {
		if(eventArray == null) {
			eventArray = new cl_event[]{event};
		}
		return eventArray;
	}

	@Override
	protected void dealloc() {
		if(event != null) {
			clReleaseEvent(event);
		}
	}

	@Override
	public Iterator<Event> iterator() {
		final Event e = this;
		
		return new Iterator<Event>() {
			private boolean hasNext = true;
			
			@Override
			public boolean hasNext() {
				return hasNext;
			}

			@Override
			public Event next() {
				hasNext = false;
				return e;
			}

			@Override
			public void remove() {
				throw new UnsupportedOperationException();
			}
		};
	}
}
