import * from whiley.lang.*
import * from whiley.gpgpu.*

public [int] parse(int size, int w, int h, string source):
	data = 0..(size * size)
	count = 0
	length = w * h
	x = 0
	y = 0
	ox = (size - w)/2
	oy = (size - h)/2

	while count < size * size:
		data[count] = 0
		count = count + 1

	count = 0

	while count < length:
		data[(oy + y) * size + (ox + x)] = source[count] - '0'

		x = x + 1
		if x == w:
			x = 0
			y = y + 1

		count = count + 1

	return data

public void ::debugworld(System.Console sys, [int] data, int size):
	x = 0
	while x < size:
		y = 0
		while y < size:
			sys.out.print(data[y * size + x])
			y = y + 1
		sys.out.println("")
		x = x + 1

public void ::main(System.Console sys):
	size = 200
	data1 = parse(size, 13, 13, "0011100011100000000000000010000101000011000010100001100001010000100111000111000000000000000001110001110010000101000011000010100001100001010000100000000000000011100011100")
	data2 = 0..(size * size)

	numRuns = 50
	numWarmRuns = 2
	for testNumber in 0..(numRuns+numWarmRuns):
		if numWarmRuns <= 0:
			beginGPUBenchmarking()

		count = 0
		while count < 3:
			for index in 0..(size * size):
				y = index / size
				x = index % size
				
				live = 0
				for adjx in -1..2:
					for adjy in -1..2:
						if !((x + adjx) < 0 || (x + adjx) >= size || (y + adjy) < 0 || (y + adjy) >= size):
							live = live + data1[(y + adjy) * size + (x + adjx)]

				this = data1[y * size + x]

				if this == 1:
					live = live - 1
					if live < 2 || live > 3:
						data2[y * size + x] = 0
					else:
						data2[y * size + x] = 1
				else:
					if live == 3:
						data2[y * size + x] = 1
					else:
						data2[y * size + x] = 0

			temp = data1
			data1 = data2
			data2 = temp

			count = count + 1

		if numWarmRuns <= 0:
			endGPUBenchmarking()
		numWarmRuns = numWarmRuns - 1

	printOutGPUBenchmarkResults()
	debugworld(sys, data1, size)

