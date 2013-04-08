package whiley.gpu.OpenCL.Events;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.jocl.*;
import static org.jocl.CL.*;

public class EventList implements EventDependancy {
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
		events.add(e);
		validCachedArray = false;
	}
	
	public void remove(Event e) {
		events.remove(e);
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
}
