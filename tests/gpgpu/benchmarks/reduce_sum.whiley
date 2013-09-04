import * from whiley.lang.*
import * from whiley.gpgpu.*

public void ::main(System.Console sys):
	numLevels = 19
	blockSize = 4
	minBlockCount = 64

	fullSize = Math.pow(2, numLevels)

	numRuns = 50
	numWarmRuns = 2
	for testNumber in 0..(numRuns+numWarmRuns):
		if numWarmRuns <= 0:
			beginGPUBenchmarking()

		fro = 1000..(1000 + fullSize)
		n = 0
		while n < |fro|:
			fro[n] = fro[n] % 100
			n = n+1

		levelSize = |fro|
		numBlocks = levelSize / blockSize
		out = 0..numBlocks

		while numBlocks >= minBlockCount:
			for k in 0..numBlocks:
				blockSum = 0
				for i in 0..blockSize:
					blockSum = blockSum + fro[k * blockSize + i]
				out[k] = blockSum

			temp = fro
			fro = out
			out = temp
			levelSize = numBlocks
			numBlocks = levelSize / blockSize

		sum = 0
		n = 0
		while n < levelSize:
			sum = sum + fro[n]
			n = n + 1

		sys.out.println(sum)

		if numWarmRuns <= 0:
			endGPUBenchmarking()
		numWarmRuns = numWarmRuns - 1

	printOutGPUBenchmarkResults()
