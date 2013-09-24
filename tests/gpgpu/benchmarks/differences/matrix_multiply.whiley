import * from whiley.lang.*
import * from whiley.gpgpu.*

define Matrix as {int w, int h, [[real]] data}

public Matrix newMatrix(int start, int mod, int w, int h):
	num = start
	data = []
	for y in 0..h:
		temp = []
		for x in 0..w:
			temp = temp + [(real)num]
			num = (num + 1) % mod
		data = data + [temp]
	return {w: w, h:h, data:data}

public Matrix multiplyMatrix(Matrix one, Matrix two):
	assert one.w == two.h && one.h == two.w

	w = one.w
	w1 = one.w
	w2 = two.w
	h = two.h

	ret = one

	oneData = one.data
	twoData = two.data
	data = ret.data

	for j in 0..h:
		for i in 0..w:
			result = 0.0

			for n in 0..w:
				result = result + oneData[j][n] * twoData[n][i]

			data[j][i] = result

	ret.data = data

	return ret

public void ::printMatrix(System.Console sys, Matrix m):
	for y in 0..m.h:
		sep = ""
		for x in 0..m.w:
			sys.out.print(sep + m.data[y][x])
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
	