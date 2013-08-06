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

import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.jocl.CL;
import org.jocl.Pointer;
import org.jocl.Sizeof;

import whiley.gpgpu.OpenCL.Buffer;
import whiley.gpgpu.OpenCL.CommandQueue;
import whiley.gpgpu.OpenCL.Context;
import whiley.gpgpu.OpenCL.Kernel;
import whiley.gpgpu.OpenCL.MemoryFlags;
import whiley.gpgpu.OpenCL.Program;
import whiley.gpgpu.OpenCL.Devices.Device;
import whiley.gpgpu.OpenCL.Devices.DeviceList;
import whiley.gpgpu.OpenCL.Devices.DeviceType;
import whiley.gpgpu.OpenCL.Events.Event;
import whiley.gpgpu.OpenCL.Events.EventDependancy;
import whiley.gpgpu.OpenCL.Events.EventList;
import whiley.gpgpu.OpenCL.Exceptions.CommandQueueInitilizationException;
import whiley.gpgpu.OpenCL.Exceptions.ContextInitilizationException;
import whiley.gpgpu.OpenCL.Exceptions.DeviceFetchException;
import whiley.gpgpu.OpenCL.Exceptions.KernelArgumentException;
import whiley.gpgpu.OpenCL.Exceptions.KernelExecutionException;
import whiley.gpgpu.OpenCL.Exceptions.KernelInitilizationException;
import whiley.gpgpu.OpenCL.Exceptions.MemoryException;
import whiley.gpgpu.OpenCL.Exceptions.ProgramCompilationException;
import whiley.gpgpu.OpenCL.Exceptions.ProgramInitilizationException;
import whiley.gpgpu.OpenCL.Exceptions.ProgramReInitilizationException;
import wyjc.runtime.WyList;
import wyjc.runtime.WyRat;
import wyjc.runtime.WyTuple;

public class Util$native {
	private static class GPUReferenceArgument {
		public Buffer buffer;
		public Object whileyObject;

		public GPUReferenceArgument(Buffer buffer, Object whileyObject) {
			this.buffer = buffer;
			this.whileyObject = whileyObject;
		}

		public void releaseOpenCLObject() {
			if (buffer != null) {
				buffer.release();
			}
		}
	}
	
	private static class GPUResult {
		public long totalTime = 0;
		public long initilizationTime = 0;
		public long marshallingTime = 0;
		public long uploadAndMarshallingTime = 0;
		public long computeTime = 0;
		public long downloadTime = 0;
		public long unmarshallingTime = 0;
		public long numberOfRuns = 0;
	}
	
	private static boolean benchmarkingGPU = false;
	private static ArrayList<ArrayList<GPUResult>> allResults = new ArrayList<ArrayList<GPUResult>>();
	private static ArrayList<GPUResult> currentResult;
	
	public static void beginGPUBenchmarking() {		
		benchmarkingGPU = true;
		
		currentResult = new ArrayList<GPUResult>();
		allResults.add(currentResult);
	}
	
	public static void endGPUBenchmarking() {
		benchmarkingGPU = false;
	}
	
	public static void printOutGPUBenchmarkResults() {
		int number = allResults.get(0).size();
		
		String sep = number + ", ";
		for(int n=0;n<number;n++) {
			System.err.print(sep + "total, initialization, marshaling, upload - marshalling, execution, download, unmarshaling, other");
			sep = ", ";
		}
		System.err.println();
		
		for(ArrayList<GPUResult> a : allResults) {
			sep = ", ";
			
			for(GPUResult r : a) {
				System.err.print(sep);
				if(r.numberOfRuns > 0) {
					System.err.print(r.totalTime / r.numberOfRuns);
					System.err.print(", ");
					System.err.print(r.initilizationTime / r.numberOfRuns);
					System.err.print(", ");
					System.err.print(r.marshallingTime / r.numberOfRuns);
					System.err.print(", ");
					System.err.print((r.uploadAndMarshallingTime - r.marshallingTime) / r.numberOfRuns);
					System.err.print(", ");
					System.err.print(r.computeTime / r.numberOfRuns);
					System.err.print(", ");
					System.err.print(r.downloadTime / r.numberOfRuns);
					System.err.print(", ");
					System.err.print(r.unmarshallingTime / r.numberOfRuns);
					System.err.print(", ");
					System.err.print((r.totalTime - r.initilizationTime - r.uploadAndMarshallingTime - r.computeTime - r.downloadTime - r.unmarshallingTime) / r.numberOfRuns);
				}
				else {
					System.err.print("NaN, NaN, NaN, NaN, NaN, NaN, NaN, NaN");
				}
				
				sep = ", ";
			}
			System.err.println();
		}
	}
	
	public static WyList executeWYGPUKernelOverRange(String moduleName, BigInteger kernelID, WyList arguments, BigInteger start, BigInteger end) {
		return executeWYGPUKernelOverRange(moduleName, kernelID.intValue(), arguments, start.intValue(), end.intValue());
	}

	public static WyList executeWYGPUKernelOverRange(String moduleName, int kernelID, WyList arguments, int start, int end) {
		long functionStartTime = benchmarkingGPU ? System.nanoTime() : 0;
		
		if(start == end) {
			return arguments;
		}
				
		try {
			DeviceList devices = DeviceList.devicesOfType(DeviceType.GPU, 1);
			if (devices.count() < 1) {
				System.err.println("Unable to find a device");
				System.exit(1);
			}

			Device device = devices.get(0);
			ByteOrder byteOrder = device.isLittleEndian() ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN;
			Context c = new Context(devices);
			CommandQueue q = new CommandQueue(c, device);
			Program p = new Program(c);
			p.loadSource(new String[] { Utils.fileAsString(moduleName+".cl") });
			p.compileForDevices(devices);
			Kernel k;
			try {
				k = new Kernel(p, "whiley_gpgpu_func_"+kernelID);
			} catch (KernelInitilizationException e) {
				if(e.status == CL.CL_INVALID_KERNEL_NAME) {
					throw new IllegalArgumentException("There does not exist a kernel named whiley_gpgpu_func_"+kernelID+" in module "+moduleName, e);
				}
				else {
					throw e;
				}
			}
			
			long uploadStartTime = benchmarkingGPU ? System.nanoTime() : 0;

			// ------------------------ Begin computation
			// -------------------------

			EventList writeEvents = new EventList();
			List<GPUReferenceArgument> memoryArguments = new ArrayList<GPUReferenceArgument>();

			int argumentCount = 0;
			// Setup all the arguments
			for (Object item : arguments) {
				try {
					setArgument(argumentCount, k, q, item, memoryArguments, null, writeEvents, byteOrder);
				} catch (KernelArgumentException e) {
					if(e.status == CL.CL_INVALID_ARG_INDEX) {
						System.err.println("Wrong arguments to kernel: " + arguments);
						throw new IllegalArgumentException("Incorrect number of arguments to kernel, too many specified, expected " + argumentCount, e);
					}
					else {
						throw e;
					}
				}
				argumentCount++;
			}
			
			long marshallingEndTime = benchmarkingGPU ? System.nanoTime() : 0;
			
			writeEvents.waitForEvents();
			
			long computeStartTime = benchmarkingGPU ? System.nanoTime() : 0;
			
			Event computeEvent = new Event();
			try {
				k.enqueueKernelWithWorkSizes(q, 1, new long[] { start }, new long[] { end }, null, writeEvents, computeEvent);
			}
			catch(KernelExecutionException e) {
				if(e.status == CL.CL_INVALID_KERNEL_ARGS) {
					System.err.println("Wrong arguments to kernel: " + arguments);
					throw new IllegalArgumentException("Incorrect number of arguments to kernel, only got " + argumentCount + " expecting more", e);
				}
				else {
					throw e;
				}
			}
			
			computeEvent.waitForEvent();
			
			long computeEndTime = benchmarkingGPU ? System.nanoTime() : 0;

			EventList readEvents = new EventList();
			HashSet<Runnable> onCompletions = new HashSet<Runnable>();
			WyList resultArray = new WyList();
			for (GPUReferenceArgument item : memoryArguments) {
				getArgument(q, item, computeEvent, readEvents, onCompletions, byteOrder, resultArray);
				argumentCount++;
			}

			readEvents.waitForEvents();
			
			long downloadEndTime = benchmarkingGPU ? System.nanoTime() : 0;
			
			for (Runnable r : onCompletions) {
				r.run();
			}
			
			long unmarshallingEndTime = benchmarkingGPU ? System.nanoTime() : 0;

			// ------------------------ End computation
			// -------------------------

			readEvents.release();
			writeEvents.release();
			computeEvent.release();

			for (GPUReferenceArgument releasable : memoryArguments) {
				releasable.releaseOpenCLObject();
			}

			p.release();
			q.release();
			c.release();
			
			if(benchmarkingGPU) {
				GPUResult r = new GPUResult();
				
				r.totalTime += (System.nanoTime() - functionStartTime);
				r.initilizationTime += (uploadStartTime - functionStartTime);
				r.marshallingTime += (marshallingEndTime - uploadStartTime);
				r.uploadAndMarshallingTime += (computeStartTime - uploadStartTime);
				r.computeTime += (computeEndTime - computeStartTime);
				r.downloadTime += (downloadEndTime - computeEndTime);
				r.unmarshallingTime += (unmarshallingEndTime - downloadEndTime);
				r.numberOfRuns++;
				
				currentResult.add(r);
			}
			
			return resultArray;
		} catch (DeviceFetchException e) {
			throw new RuntimeException(e);
		} catch (ContextInitilizationException e) {
			throw new RuntimeException(e);
		} catch (CommandQueueInitilizationException e) {
			throw new RuntimeException(e);
		} catch (ProgramInitilizationException e) {
			throw new RuntimeException(e);
		} catch (ProgramReInitilizationException e) {
			throw new RuntimeException(e);
		} catch (IOException e) {
			throw new RuntimeException(e);
		} catch (ProgramCompilationException e) {
			throw new RuntimeException(e);
		} catch (KernelExecutionException e) {
			throw new RuntimeException(e);
		} catch (MemoryException e) {
			throw new RuntimeException(e);
		} catch (KernelArgumentException e) {
			throw new RuntimeException(e);
		} catch (KernelInitilizationException e) {
			throw new RuntimeException(e);
		}
	}
	
	@SuppressWarnings("unchecked")
	public static WyList executeWYGPUKernelOverArray(String moduleName, BigInteger kernelID, WyList arguments, WyList sourceList) {
		WyList tempList = new WyList();
		tempList.add(sourceList);
		tempList.addAll(arguments);
				
		WyList result = executeWYGPUKernelOverRange(moduleName, kernelID.intValue(), tempList, 0, sourceList.size());
		
		result.remove(0);
		
		return result;
	}

	private static void getArgument(CommandQueue q, GPUReferenceArgument item, Event waitOn, EventList readEvents, HashSet<Runnable> onCompletions, ByteOrder byteOrder, WyList resultArray) throws MemoryException {
		if (item.whileyObject instanceof WyList) {
			getArgument(q, (WyList) item.whileyObject, item.buffer, waitOn, readEvents, onCompletions, byteOrder, resultArray);
		} else if (item.whileyObject instanceof WyTuple) {
			getArgument(q, (WyTuple) item.whileyObject, item.buffer, waitOn, readEvents, onCompletions, byteOrder, resultArray);
		} else if (item.whileyObject instanceof WyRat) {
			getArgument(q, (WyRat) item.whileyObject, item.buffer, waitOn, readEvents, onCompletions, byteOrder, resultArray);
		} else if (item.whileyObject instanceof BigInteger) {
			getArgument(q, (BigInteger) item.whileyObject, item.buffer, waitOn, readEvents, onCompletions, byteOrder, resultArray);
		} else {
			throw new RuntimeException("Non unmarshabale type encountered: " + item.whileyObject);
		}
	}

	@SuppressWarnings("unchecked")
	private static void getArgument(CommandQueue q, final BigInteger whileyObject, Buffer memory, Event waitOn, EventList readEvents, HashSet<Runnable> onCompletions, ByteOrder byteOrder, final WyList resultArray) throws MemoryException {
		int bufferSize = sizeofType(whileyObject.getClass());
		final ByteBuffer buffer = ByteBuffer.allocate(bufferSize).order(byteOrder);
		Event event = new Event();
		memory.enqueueRead(q, Pointer.to(buffer.array()), bufferSize, waitOn, event);
		readEvents.add(event);
		event.release();
		final int index = resultArray.size();
		resultArray.add(1); // This is a placeholder

		onCompletions.add(new Runnable() {
			@Override
			public void run() {
				resultArray.set(index, BigInteger.valueOf(buffer.asIntBuffer().get()));
			}
		});
	}

	@SuppressWarnings("unchecked")
	private static void getArgument(CommandQueue q, final WyRat whileyObject, Buffer memory, Event waitOn, EventList readEvents, HashSet<Runnable> onCompletions, ByteOrder byteOrder, final WyList resultArray) throws MemoryException {
		int bufferSize = sizeofType(whileyObject.getClass());
		final ByteBuffer buffer = ByteBuffer.allocate(bufferSize).order(byteOrder);
		Event event = new Event();
		memory.enqueueRead(q, Pointer.to(buffer.array()), bufferSize, waitOn, event);
		readEvents.add(event);
		event.release();
		final int index = resultArray.size();
		resultArray.add(1); // This is a placeholder

		onCompletions.add(new Runnable() {
			@Override
			public void run() {
				resultArray.set(index, WyRat.valueOf(buffer.asFloatBuffer().get()));
			}
		});
	}

	@SuppressWarnings("unchecked")
	private static void getArgument(CommandQueue q, final WyList whileyObject, Buffer memory, Event waitOn, EventList readEvents, HashSet<Runnable> onCompletions, ByteOrder byteOrder, WyList resultArray) throws MemoryException {
		// TODO: support empty array

		int bufferSize = sizeofObject(whileyObject);
		final ByteBuffer buffer = ByteBuffer.allocate(bufferSize).order(byteOrder);
		Event event = new Event();
		memory.enqueueRead(q, Pointer.to(buffer.array()), bufferSize, waitOn, event);
		readEvents.add(event);
		event.release();
		resultArray.add(whileyObject);

		onCompletions.add(new Runnable() {
			@Override
			public void run() {
				@SuppressWarnings("rawtypes")
				Class type = whileyObject.get(0).getClass();
				whileyObject.clear();

				int size = buffer.getInt();
				if (type == BigInteger.class) {
					for (int n = 0; n < size; n++) {
						whileyObject.add(BigInteger.valueOf(buffer.getInt()));
					}
				} else if (type == WyRat.class) {
					for (int n = 0; n < size; n++) {
						whileyObject.add(WyRat.valueOf(buffer.getFloat()));
					}
				} else if (type == Boolean.class) {
					for (int n = 0; n < size; n++) {
						whileyObject.add(Boolean.valueOf(buffer.get() == 1));
					}
				} else {
					throw new RuntimeException("Non unmarshabale type encountered: " + type);
				}
			}
		});
	}

	@SuppressWarnings("unchecked")
	private static void getArgument(CommandQueue q, final WyTuple whileyObject, Buffer memory, Event waitOn, EventList readEvents, HashSet<Runnable> onCompletions, ByteOrder byteOrder, WyList resultArray) throws MemoryException {
		// TODO: support empty array

		int bufferSize = sizeofObject(whileyObject);
		final ByteBuffer buffer = ByteBuffer.allocate(bufferSize).order(byteOrder);
		Event event = new Event();
		memory.enqueueRead(q, Pointer.to(buffer.array()), bufferSize, waitOn, event);
		readEvents.add(event);
		event.release();
		resultArray.add(whileyObject);

		onCompletions.add(new Runnable() {
			@Override
			public void run() {
				@SuppressWarnings("rawtypes")
				Class type = whileyObject.get(0).getClass();
				whileyObject.clear();

				int size = buffer.getInt();
				if (type == BigInteger.class) {
					for (int n = 0; n < size; n++) {
						whileyObject.add(BigInteger.valueOf(buffer.getInt()));
					}
				} else if (type == WyRat.class) {
					for (int n = 0; n < size; n++) {
						whileyObject.add(WyRat.valueOf(buffer.getFloat()));
					}
				} else if (type == Boolean.class) {
					for (int n = 0; n < size; n++) {
						whileyObject.add(Boolean.valueOf(buffer.get() == 1));
					}
				} else {
					throw new RuntimeException("Non unmarshabale type encountered: " + type);
				}
			}
		});
	}

	private static void setArgument(int argumentNumber, Kernel kernel, CommandQueue q, Object o, List<GPUReferenceArgument> memoryArguments, EventDependancy waitOn, EventList writeEvents, ByteOrder byteOrder) throws MemoryException, KernelArgumentException {
		if (o instanceof WyList) {
			setArgument(argumentNumber, kernel, q, (WyList) o, memoryArguments, waitOn, writeEvents, byteOrder);
		} else if (o instanceof WyTuple) {
			setArgument(argumentNumber, kernel, q, (WyTuple) o, memoryArguments, waitOn, writeEvents, byteOrder);
		} else if (o instanceof WyRat) {
			setArgument(argumentNumber, kernel, q, (WyRat) o, memoryArguments, waitOn, writeEvents, byteOrder);
		} else if (o instanceof BigInteger) {
			setArgument(argumentNumber, kernel, q, (BigInteger) o, memoryArguments, waitOn, writeEvents, byteOrder);
		} else {
			throw new RuntimeException("Non marshabale type encountered: " + o);
		}
	}

	private static void setArgument(int argumentNumber, Kernel kernel, CommandQueue q, WyTuple tuple, List<GPUReferenceArgument> argumentsToRelease, EventDependancy waitOn, EventList writeEvents, ByteOrder byteOrder) throws MemoryException, KernelArgumentException {
		if (tuple.size() > 0) {
			Object element0 = tuple.get(0);
			@SuppressWarnings("rawtypes")
			Class type = element0.getClass();

			for (Object o : tuple) {
				if (o.getClass() != type) {
					throw new RuntimeException("Unmarshable list encountered (contains a mixture of types): " + tuple);
				}
			}

			int bufferSize = sizeofObject(tuple);
			ByteBuffer buffer = ByteBuffer.allocate(bufferSize).order(byteOrder);
			writeObjectToBytes(tuple, buffer);

			Buffer clmem = new Buffer(q.getContext(), MemoryFlags.READ_WRITE, bufferSize);
			Event event = new Event();
			clmem.enqueueWrite(q, Pointer.to(buffer.array()), bufferSize, waitOn, event);
			argumentsToRelease.add(new GPUReferenceArgument(clmem, tuple));
			writeEvents.add(event);
			event.release();

			kernel.setArgument(argumentNumber, clmem);
		} else {
			// TODO: set the argument to null or something
			return;
		}
	}

	private static void setArgument(int argumentNumber, Kernel kernel, CommandQueue q, WyList list, List<GPUReferenceArgument> argumentsToRelease, EventDependancy waitOn, EventList writeEvents, ByteOrder byteOrder) throws MemoryException, KernelArgumentException {
		if (list.size() > 0) {
			Object element0 = list.get(0);
			@SuppressWarnings("rawtypes")
			Class type = element0.getClass();

			for (Object o : list) {
				if (o.getClass() != type) {
					throw new RuntimeException("Unmarshable list encountered (contains a mixture of types): " + list);
				}
			}

			int bufferSize = sizeofObject(list);
			ByteBuffer buffer = ByteBuffer.allocate(bufferSize).order(byteOrder);
			writeObjectToBytes(list, buffer);

			Buffer clmem = new Buffer(q.getContext(), MemoryFlags.READ_WRITE, bufferSize);
			Event event = new Event();
			clmem.enqueueWrite(q, Pointer.to(buffer.array()), bufferSize, waitOn, event);
			argumentsToRelease.add(new GPUReferenceArgument(clmem, list));
			writeEvents.add(event);
			event.release();

			kernel.setArgument(argumentNumber, clmem);
		} else {
			// TODO: set the argument to null or something
			return;
		}
	}

	private static void setArgument(int argumentNumber, Kernel kernel, CommandQueue q, WyRat rat, List<GPUReferenceArgument> argumentsToRelease, EventDependancy waitOn, EventList writeEvents, ByteOrder byteOrder) throws KernelArgumentException, MemoryException {
		int bufferSize = sizeofType(rat.getClass());
		ByteBuffer buffer = ByteBuffer.allocate(bufferSize).order(byteOrder);
		writeObjectToBytes(rat, buffer);

		Buffer clmem = new Buffer(q.getContext(), MemoryFlags.READ_WRITE, bufferSize);
		Event event = new Event();
		clmem.enqueueWrite(q, Pointer.to(buffer.array()), bufferSize, waitOn, event);
		argumentsToRelease.add(new GPUReferenceArgument(clmem, rat));
		writeEvents.add(event);
		event.release();

		kernel.setArgument(argumentNumber, clmem);
	}

	private static void setArgument(int argumentNumber, Kernel kernel, CommandQueue q, BigInteger integer, List<GPUReferenceArgument> argumentsToRelease, EventDependancy waitOn, EventList writeEvents, ByteOrder byteOrder) throws KernelArgumentException, MemoryException {
		int bufferSize = sizeofType(integer.getClass());
		ByteBuffer buffer = ByteBuffer.allocate(bufferSize).order(byteOrder);
		writeObjectToBytes(integer, buffer);

		Buffer clmem = new Buffer(q.getContext(), MemoryFlags.READ_WRITE, bufferSize);
		Event event = new Event();
		clmem.enqueueWrite(q, Pointer.to(buffer.array()), bufferSize, waitOn, event);
		argumentsToRelease.add(new GPUReferenceArgument(clmem, integer));
		writeEvents.add(event);
		event.release();

		kernel.setArgument(argumentNumber, clmem);
	}

	private static void writeObjectToBytes(Object o, ByteBuffer buffer) {
		if (o instanceof WyList) {
			writeObjectToBytes((WyList) o, buffer);
		} else if (o instanceof WyTuple) {
			writeObjectToBytes((WyTuple) o, buffer);
		}else if (o instanceof WyRat) {
			writeObjectToBytes((WyRat) o, buffer);
		} else if (o instanceof BigInteger) {
			writeObjectToBytes((BigInteger) o, buffer);
		} else if (o instanceof Boolean) {
			writeObjectToBytes((Boolean) o, buffer);
		} else {
			throw new RuntimeException("Non marshabale type encountered: " + o);
		}
	}

	private static void writeObjectToBytes(WyList list, ByteBuffer buffer) {
		buffer.putInt(list.size()); // FIXME: this assumes lots about the format
									// of cl_int

		for (Object o : list) {
			writeObjectToBytes(o, buffer);
		}
	}
	
	private static void writeObjectToBytes(WyTuple tuple, ByteBuffer buffer) {
		buffer.putInt(tuple.size()); // FIXME: this assumes lots about the format
									// of cl_int

		for (Object o : tuple) {
			writeObjectToBytes(o, buffer);
		}
	}

	private static void writeObjectToBytes(WyRat rat, ByteBuffer buffer) {
		buffer.putFloat(rat.floatValue()); // FIXME: this assumes lots about the
											// format of cl_float
	}

	private static void writeObjectToBytes(BigInteger integer, ByteBuffer buffer) {
		buffer.putInt(integer.intValue()); // FIXME: this assumes lots about the
											// format of cl_int
	}
	
	private static void writeObjectToBytes(Boolean bool, ByteBuffer buffer) {
		buffer.put((byte) (((boolean)bool)?1:0)); // FIXME: this assumes lots about the
													// format of cl_uint8
	}

	private static int sizeofObject(WyList list) {
		Object element0 = list.get(0);
		@SuppressWarnings("rawtypes")
		Class type = element0.getClass();

		int typeSize = sizeofType(type);
		int totalSize = typeSize * list.size() + Sizeof.cl_int;

		return totalSize;
	}

	private static int sizeofObject(WyTuple tuple) {
		Object element0 = tuple.get(0);
		@SuppressWarnings("rawtypes")
		Class type = element0.getClass();

		int typeSize = sizeofType(type);
		int totalSize = typeSize * tuple.size() + Sizeof.cl_int;

		return totalSize;
	}

	private static int sizeofType(@SuppressWarnings("rawtypes") Class type) {
		if (type == WyRat.class) {
			return Sizeof.cl_float;
		} else if (type == BigInteger.class) {
			return Sizeof.cl_int;
		} else if (type == Boolean.class) {
			return Sizeof.cl_uint8;
		} else {
			throw new RuntimeException("Non marshabale type encountered: " + type);
		}
	}
}
