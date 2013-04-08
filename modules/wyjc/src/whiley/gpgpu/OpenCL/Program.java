package whiley.gpgpu.OpenCL;

import java.util.HashMap;
import java.util.Map;


import org.jocl.*;

import whiley.gpu.OpenCL.Devices.*;
import whiley.gpu.OpenCL.Exceptions.*;
import static org.jocl.CL.*;

public class Program extends AbstractOpenCLObject {
	private final Context context;
	private cl_program program;
	private String source[];
	private boolean compiled = false;
	
	public Program(Context context) {
		this.context = context;
	}
	
	public void loadSource(String source[]) throws ProgramInitilizationException, ProgramReInitilizationException {
		if(this.program == null) {
			this.source = source;
			
			int err[] = new int[1];
			
			this.program = clCreateProgramWithSource(this.context.getRawContext(), this.source.length, this.source, null, err);
		    if(err[0] != CL_SUCCESS) {
		        throw new ProgramInitilizationException(err[0]);
		    }
		}
		else {
			throw new ProgramReInitilizationException();
		}
	}

	public void compileForDevices(DeviceDependancy deviceList) throws ProgramCompilationException {
		if(!this.compiled) {
			int err[] = new int[1];
			
			err[0] = clBuildProgram(program, deviceList.count(), deviceList.asArray(), null, null, null);
			if(err[0] != CL_SUCCESS) {
				Map<Device, byte[]> logs = new HashMap<Device, byte[]>();
				
				for(Device d : deviceList) {
					long len[] = new long[1];
					byte buffer[] = new byte[2048];
					
					clGetProgramBuildInfo(this.program, d.getRawDeviceId(), CL_PROGRAM_BUILD_LOG, buffer.length, Pointer.to(buffer), len);
					
					logs.put(d, buffer);
				}
				
				throw new ProgramCompilationException(err[0], logs, this.source);
			}
			
			this.compiled = true;
		}
	}
	
	public boolean isCompiled() {
		return compiled;
	}

	protected cl_program getRawProgram() {
		return program;
	}

	public Context getContext() {
		return context;
	}
	
	@Override
	public void dealloc() {
		if(program != null) { 
			clReleaseProgram(program);
			program = null;
		}
	}
}
