import * from whiley.lang.*
import * from whiley.gpgpu.*

public int fakeFloat(int num, int den):
	return (10000 * num) / den

public int multFakeFloat(int a, int b):
	return (a * b) / 10000

public void ::main(System.Console sys):
	size = 1000
	data = 0..(size * size)
	
	numRuns = 50
	numWarmRuns = 2
	for testNumber in 0..(numRuns+numWarmRuns):
		if numWarmRuns <= 0:
			beginGPUBenchmarking()
			
		for index in 0..(size * size):
			y = index / size
			x = index % size

			x1 = multFakeFloat((((-x*fakeFloat(2, 1))/size)+fakeFloat(1, 1)), fakeFloat(15, 10))+fakeFloat(5, 10)
			y1 = multFakeFloat((((y*fakeFloat(2, 1))/size)-fakeFloat(1, 1)), fakeFloat(15, 10))
			
			x0 = 0
			y0 = 0
			iteration = 0
			
			while((multFakeFloat(x0, x0) + multFakeFloat(y0, y0) <= fakeFloat(4, 1)) && (iteration < 1024)):
				xtmp = multFakeFloat(x0, x0) - multFakeFloat(y0, y0) - x1
				y0 = 2*multFakeFloat(x0, y0) + y1
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