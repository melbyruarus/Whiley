import * from whiley.lang.*
import * from whiley.gpgpu.*

public void ::main(System.Console sys):
	numLevels = 19
	blockSize = 4
	minBlockCount = 64
	offset = 1000

	fullSize = Math.pow(2, numLevels)

	expect = (fullSize+(offset-1))*(fullSize+offset)/2-(offset-1)*offset/2

	numRuns = 50
	numWarmRuns = 2
	for testNumber in 0..(numRuns+numWarmRuns):
		if numWarmRuns <= 0:
			beginGPUBenchmarking()

		fro = offset..(offset + fullSize)

		numBlocks = |fro| / blockSize

		while numBlocks >= minBlockCount:
			out = 0..numBlocks

			for k in 0..numBlocks:
				blockSum = 0
				for i in 0..blockSize:
					blockSum = blockSum + fro[k * blockSize + i]
				out[k] = blockSum

			fro = out

			numBlocks = |fro| / blockSize

		sum = 0
		n = 0
		while n < |fro|:
			sum = sum + fro[n]
			n = n + 1

		if sum == expect:
			sys.out.println("Ok")
		else:
			sys.out.println("Got: " + sum + " Expecting: " + expect)

		if numWarmRuns <= 0:
			endGPUBenchmarking()
		numWarmRuns = numWarmRuns - 1

	printOutGPUBenchmarkResults()
