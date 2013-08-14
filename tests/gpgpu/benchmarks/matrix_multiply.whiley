import * from whiley.lang.*
import * from whiley.gpgpu.*

define Matrix as {int w, int h, [int] data}

public Matrix newMatrix(int start, int mod, int w, int h):
	num = start
	data = []
	for n in 0..(w * h):
		data = data + [num]
		num = (num + 1) % mod
	return {w: w, h:h, data:data}

public Matrix multiplyMatrix(Matrix one, Matrix two):
	assert one.w == two.h && one.h == two.w

	w = one.w
	w1 = one.w
	w2 = two.w
	h = two.h

	ret = {w:w, h:h, data:0..(w * h)}

	oneData = one.data
	twoData = two.data
	data = ret.data

	for index in 0..(w * h):
		j = index / w
		i = index % w

		result = 0

		for n in 0..w:
			result = result + oneData[j * w1 + n] * twoData[n * w2 + i]

		data[j * w + i] = result

	ret.data = data

	return ret

public void ::printMatrix(System.Console sys, Matrix m):
	for y in 0..m.h:
		sep = ""
		for x in 0..m.w:
			sys.out.print(sep + m.data[y * m.w + x])
			sep = ", "
		sys.out.println("")
	sys.out.println(m.h)

public void ::main(System.Console sys):
	size = 300
	matrix1 = newMatrix(0,100,size,size)
	matrix2 = newMatrix(20,100,size,size)

	numRuns = 50
	numWarmRuns = 2
	for testNumber in 0..(numRuns+numWarmRuns):
		if numWarmRuns <= 0:
			beginGPUBenchmarking()

		printMatrix(sys, multiplyMatrix(matrix1, matrix2))

		if numWarmRuns <= 0:
			endGPUBenchmarking()
		numWarmRuns = numWarmRuns - 1

	printOutGPUBenchmarkResults()
	