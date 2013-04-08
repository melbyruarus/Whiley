package whiley.gpu.OpenCL.Devices;

import java.util.Iterator;

import org.jocl.*;

public class Device implements DeviceDependancy {
	private final cl_device_id deviceId;
	private cl_device_id deviceArray[] = null;
	private final DeviceType deviceType;
	
	protected Device(cl_device_id deviceId, DeviceType deviceType) {
		this.deviceId = deviceId;
		this.deviceType = deviceType;
	}
	
	public cl_device_id getRawDeviceId() {
		return deviceId;
	}
	
	public DeviceType getDeviceType() {
		return deviceType;
	}

	@Override
	public int count() {
		return 1;
	}

	@Override
	public cl_device_id[] asArray() {
		if(deviceArray == null) {
			deviceArray = new cl_device_id[]{deviceId};
		}
		
		return deviceArray;
	}

	public String deviceDescription() {
		return deviceId.toString();
	}

	@Override
	public Iterator<Device> iterator() {
		final Device d = this;
		
		return new Iterator<Device>() {
			private boolean hasNext = true;
			
			@Override
			public boolean hasNext() {
				return hasNext;
			}

			@Override
			public Device next() {
				return d;
			}

			@Override
			public void remove() {
				throw new UnsupportedOperationException();
			}
		};
	}
}
