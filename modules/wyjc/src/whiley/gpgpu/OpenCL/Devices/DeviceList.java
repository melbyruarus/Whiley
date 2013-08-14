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

		int numPlatforms[] = new int[1];
		err[0] = clGetPlatformIDs(0, null, numPlatforms);

		if(err[0] != CL_SUCCESS || numPlatforms[0] == 0) {
			throw new DeviceFetchException(err[0], "Unable to find any platforms");
		}

		cl_platform_id platforms[] = new cl_platform_id[numPlatforms[0]];

		err[0] = clGetPlatformIDs(numPlatforms[0], platforms, null);

		if(err[0] != CL_SUCCESS) {
			throw new DeviceFetchException(err[0], "Unable to get any platforms");
		}

		ArrayList<Device> d = new ArrayList<Device>();

		for(cl_platform_id platformID : platforms) {
//			byte vendor[] = new byte[1024];
//			clGetPlatformInfo(platformID, CL_PLATFORM_VENDOR, 1024, Pointer.to(vendor), null);
//			byte name[] = new byte[1024];
//			clGetPlatformInfo(platformID, CL_PLATFORM_NAME, 1024, Pointer.to(name), null);
//			byte version[] = new byte[1024];
//			clGetPlatformInfo(platformID, CL_PLATFORM_VERSION, 1024, Pointer.to(version), null);

			cl_device_id deviceIds[] = new cl_device_id[count];
			err[0] = clGetDeviceIDs(platformID, deviceType.getRawType(), count, deviceIds, (int [])null);
		    if(err[0] != CL_SUCCESS) {
				throw new DeviceFetchException(err[0], "Failed to create a device group");
		    }

		    for(cl_device_id id : deviceIds) {
		    	d.add(new Device(id, deviceType));
		    }
		}

	    cl_device_id dArray[] = new cl_device_id[d.size()];
	    for(int n=0;n<d.size();n++) {
	    	dArray[n] = d.get(n).getRawDeviceId();
	    }

		return new DeviceList(d, dArray);
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
