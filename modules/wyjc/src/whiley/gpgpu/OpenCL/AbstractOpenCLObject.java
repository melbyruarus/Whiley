package whiley.gpgpu.OpenCL;

public abstract class AbstractOpenCLObject implements OpenCLObject {
	private long retainCount = 1;
	
	public synchronized final void retain() {
		retainCount++;
	}
	
	public synchronized final void release() {
		retainCount--;
		
		if(retainCount == 0) {
			dealloc();
		}
	}
	
	abstract void dealloc();
}
