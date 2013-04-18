package whiley.gpgpu.OpenCL.Events;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.jocl.*;

import whiley.gpgpu.OpenCL.AbstractOpenCLObject;
import static org.jocl.CL.*;

public class EventList extends AbstractOpenCLObject implements EventDependancy {
	private final List<Event> events = new ArrayList<Event>();
	private boolean validCachedArray = true;
	private cl_event[] cachedArray = new cl_event[0];
	
	@Override
	public int count() {
		return events.size();
	}
	
	@Override
	public cl_event[] getAsArray() {
		if(!validCachedArray) {
			cachedArray = new cl_event[count()];
			int count = 0;
			for(Event e : events) {
				cachedArray[count] = e.getRawEvent();
				count++;
			}
		}
		
		return cachedArray;
	}
	
	public void add(Event e) {
		e.retain();
		events.add(e);
		validCachedArray = false;
	}
	
	public void remove(Event e) {
		if(events.remove(e)) {
			e.release();
		}
		validCachedArray = false;
	}
	
	public Event get(int index) {
		return events.get(index);
	}
	
	public void waitForEvents() {
		clWaitForEvents(count(), getAsArray());
	}
	
	@Override
	public Iterator<Event> iterator() {
		return events.iterator();
	}
	
	@Override
	protected void dealloc() {
		for(Event e : events) {
			e.release();
		}
	}
}
