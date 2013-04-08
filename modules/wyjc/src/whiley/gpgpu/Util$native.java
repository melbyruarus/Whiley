// Copyright (c) 2013, Melby Ruarus (melby@ecs.vuw.ac.nz)
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are met:
//    * Redistributions of source code must retain the above copyright
//      notice, this list of conditions and the following disclaimer.
//    * Redistributions in binary form must reproduce the above copyright
//      notice, this list of conditions and the following disclaimer in the
//      documentation and/or other materials provided with the distribution.
//    * Neither the name of the <organization> nor the
//      names of its contributors may be used to endorse or promote products
//      derived from this software without specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
// ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
// WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
// DISCLAIMED. IN NO EVENT SHALL MELBY RUARUS BE LIABLE FOR ANY
// DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
// (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
// LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
// ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
// (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
// SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

package whiley.gpgpu;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;

import whiley.gpgpu.OpenCL.Buffer;
import whiley.gpgpu.OpenCL.CommandQueue;
import whiley.gpgpu.OpenCL.Context;
import whiley.gpgpu.OpenCL.Kernel;
import whiley.gpgpu.OpenCL.MemoryFlags;
import whiley.gpgpu.OpenCL.Program;
import whiley.gpu.OpenCL.Devices.DeviceList;
import whiley.gpu.OpenCL.Devices.DeviceType;
import whiley.gpu.OpenCL.Events.Event;
import wyjc.runtime.WyList;

import org.jocl.Pointer;
import org.jocl.Sizeof;

public class Util$native {
	public static void executeWYGPUKernel(WyList sourceList, WyList arguments) {
		System.out.println("List: "+sourceList + " Arguments: "+arguments);
		
		try {
			DeviceList devices = DeviceList.devicesOfType(DeviceType.GPU, 1);
			if(devices.count() < 1) {
				System.err.println("Unable to find a device");
				System.exit(1);
			}
			
			Context c = new Context(devices);
			CommandQueue q = new CommandQueue(c, devices.get(0));
			Program p = new Program(c);
			p.loadSource(new String[]{Utils.fileAsString("Mandelbrot.cl")});
			p.compileForDevices(devices);
			Kernel k = new Kernel(p, "mandelbrot");
			
			// ------------------------ Begin computation -------------------------
			
			int px = 500;
			int py = 500;
			int pixelCount = px * py;
			int maxColor = (int)(Math.pow(2, 8)-1);
			float centerX = -0.5f;
			float centerY = 0f;
			float zoom = 1f;
			
			float data[] = new float[8];
			BufferedImage i = new BufferedImage(px, py, BufferedImage.TYPE_4BYTE_ABGR);
			byte image[] = ((DataBufferByte)i.getRaster().getDataBuffer()).getData();
			
			Buffer input = new Buffer(c, MemoryFlags.READ_ONLY, data.length * Float.SIZE/Byte.SIZE);
			Buffer output = new Buffer(c, MemoryFlags.WRITE_ONLY, pixelCount * Sizeof.cl_char4);
			
			long start = System.currentTimeMillis();
			
			data[0] = px;
			data[1] = 1024;
			data[2] = centerY;
			data[3] = zoom;
			data[4] = py;
			data[5] = centerX;
			data[6] = 2;
			data[7] = maxColor;
			
			Event writeEvent = new Event();
			input.enqueueWrite(q, Pointer.to(data), data.length * Float.SIZE/Byte.SIZE, null, writeEvent);
			k.setArgument(0, input);
			k.setArgument(1, output);
			
			Event computeEvent = new Event();
			k.enqueueKernelWithWorkSizes(q, 1, null, new long[]{pixelCount}, null, writeEvent, computeEvent);
			
			Event readEvent = new Event();
			output.enqueueRead(q, Pointer.to(image), pixelCount * Sizeof.cl_char4, computeEvent, readEvent);
			
			readEvent.waitForEvent();
			
			System.out.println("Computation took: " + (System.currentTimeMillis() - start) / 1000.0f);
			
			// ------------------------ End computation -------------------------
			
			writeEvent.release();
			computeEvent.release();
			readEvent.release();
			
			input.release();
			output.release();
			
			p.release();
			q.release();
			c.release();
		}
		catch(Throwable t) {
			t.printStackTrace();
		}
	}
}
