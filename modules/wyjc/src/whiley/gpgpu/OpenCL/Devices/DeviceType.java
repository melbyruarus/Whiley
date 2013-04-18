package whiley.gpgpu.OpenCL.Devices;

import static org.jocl.CL.*;

public enum DeviceType {
	DEFAULT(CL_DEVICE_TYPE_DEFAULT),
	CPU(CL_DEVICE_TYPE_CPU),
	GPU(CL_DEVICE_TYPE_GPU),
	ACCELERATOR(CL_DEVICE_TYPE_ACCELERATOR),
	ALL(CL_DEVICE_TYPE_ALL),
	CUSTOM(CL_DEVICE_TYPE_CUSTOM);
	
	private final long deviceType;
	private DeviceType(long deviceType) { this.deviceType = deviceType; }
	public long getRawType() { return deviceType; }
}
