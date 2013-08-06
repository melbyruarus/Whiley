import * from whiley.lang.*
import * from whiley.gpgpu.*

public void ::main(System.Console sys):
	size = 200
	data = 0..(size * size)

	numRuns = 50
	numWarmRuns = 2
	for testNumber in 0..(numRuns+numWarmRuns):
		if numWarmRuns <= 0:
			beginGPUBenchmarking()

		for x in 0..size:
			x1 = (((-x*2.0/size)+1)*1.5)+0.5
			
			for y in 0..size:
				y1 = (((y*2.0/size)-1)*1.5)
				
				x0 = 0.0
				y0 = 0.0
				iteration = 0
				
				while((x0*x0 + y0*y0 <= 4) && (iteration < 1024)):
					xtmp = x0*x0 - y0*y0 - x1
					y0 = 2*x0*y0 + y1
					x0 = xtmp
					iteration = iteration + 1
				
				data[y*size+x] = iteration

		if numWarmRuns <= 0:
			endGPUBenchmarking()
		numWarmRuns = numWarmRuns - 1

	printOutGPUBenchmarkResults()

	hash = 0
	count = 0
	while count < |data|:
		hash = hash + data[count]
		count = count + 1

	sys.out.println(hash)