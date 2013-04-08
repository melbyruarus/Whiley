package whiley.gpu.OpenCL.Devices;

import org.jocl.cl_device_id;

public interface DeviceDependancy extends Iterable<Device> {
	int count();
	cl_device_id[] asArray();
}
