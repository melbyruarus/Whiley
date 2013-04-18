package whiley.gpgpu.OpenCL.Devices;

import static org.jocl.CL.*;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import whiley.gpgpu.OpenCL.Exceptions.*;

import org.jocl.*;

public class DeviceList implements DeviceDependancy {
	public final List<Device> devices;
	private boolean validCachedArray = true;
	private cl_device_id[] cachedArray = new cl_device_id[0];
	
	public static DeviceList devicesOfType(DeviceType deviceType, int count) throws DeviceFetchException {
		int err[] = new int[1];
		
		cl_device_id deviceIds[] = new cl_device_id[count];
		err[0] = clGetDeviceIDs(new cl_platform_id(), deviceType.getRawType(), count, deviceIds, (int [])null);
	    if(err[0] != CL_SUCCESS) {			
			throw new DeviceFetchException(err[0], "Failed to create a device group");
	    }
	    
	    ArrayList<Device> d = new ArrayList<Device>();
	    for(cl_device_id id : deviceIds) {
	    	d.add(new Device(id, deviceType));
	    }
	    
	    return new DeviceList(d, deviceIds);
	}
	
	protected DeviceList(List<Device> devices, cl_device_id[] cachedArray) {
		this.devices = devices;
		this.validCachedArray = true;
		this.cachedArray = cachedArray;
	}

	@Override
	public int count() {
		return devices.size();
	}

	@Override
	public cl_device_id[] asArray() {
		if(!validCachedArray) {
			cachedArray = new cl_device_id[devices.size()];
			
			int index = 0;
			for(Device d : devices) {
				cachedArray[index++] = d.getRawDeviceId();
			}
		}
		
		return cachedArray;
	}

	@Override
	public Iterator<Device> iterator() {
		return devices.iterator();
	}

	public Device get(int index) {
		return devices.get(index);
	}
}
