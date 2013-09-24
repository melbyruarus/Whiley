import * from whiley.lang.*
import * from whiley.gpgpu.*

public [[int]] createGrid(int w, int h):
	grid = []
	for y in 0..h:
		row = []
		for x in 0..w:
			row = row + [0]

		grid = grid + [row]

	return grid

public void ::printHash(int size, [[int]] data, System.Console sys):
	hash = 0
	for y in 0..size:
		for x in 0..size:
			hash = hash + data[y][x]

	sys.out.println(hash)

public void ::main(System.Console sys):
	size = 1000
	data = createGrid(size, size)
	
	numRuns = 50
	numWarmRuns = 2
	for testNumber in 0..(numRuns+numWarmRuns):
		if numWarmRuns <= 0:
			beginGPUBenchmarking()
			
		for x in 0..size:
			x1 = multFakeFloat((((-x*fakeFloat(2, 1))/size)+fakeFloat(1, 1)), fakeFloat(15, 10))+fakeFloat(5, 10)

			for y in 0..size:
				y1 = multFakeFloat((((y*fakeFloat(2, 1))/size)-fakeFloat(1, 1)), fakeFloat(15, 10))
				
				x0 = 0
				y0 = 0
				iteration = 0
				
				while((multFakeFloat(x0, x0) + multFakeFloat(y0, y0) <= fakeFloat(4, 1)) && (iteration < 1024)):
					xtmp = multFakeFloat(x0, x0) - multFakeFloat(y0, y0) - x1
					y0 = 2*multFakeFloat(x0, y0) + y1
					x0 = xtmp
					iteration = iteration + 1
				
				data[y][x] = iteration

		if numWarmRuns <= 0:
			endGPUBenchmarking()
		numWarmRuns = numWarmRuns - 1

	printOutGPUBenchmarkResults()

	printHash(size, data, sys)

public int fakeFloat(int num, int den):
	return (10000 * num) / den

public int multFakeFloat(int a, int b):
	return (a * b) / 10000