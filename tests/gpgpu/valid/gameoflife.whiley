import * from whiley.lang.*

public [[int]] createGrid(int w, int h):
	grid = []
	for y in 0..h:
		row = []
		for x in 0..w:
			row = row + [0]

		grid = grid + [row]

	return grid

public [[int]] parse(int size, int w, int h, string source):
	data = createGrid(size, size)
	ox = (size - w)/2
	oy = (size - h)/2
	length = w * h

	count = 0
	y = 0
	x = 0
	
	while count < length:
		data[(oy + y)][(ox + x)] = source[count] - '0'

		x = x + 1
		if x == w:
			x = 0
			y = y + 1

		count = count + 1

	return data

public void ::debugworld(System.Console sys, [[int]] data, int size):
	for y in 0..size:
		for x in 0..size:
			sys.out.print(data[y][x])
		sys.out.println("")

public void ::main(System.Console sys):
	size = 200
	data1 = parse(size, 13, 13, "0011100011100000000000000010000101000011000010100001100001010000100111000111000000000000000001110001110010000101000011000010100001100001010000100000000000000011100011100")
	data2 = createGrid(size, size)

	start = Time.current()

	count = 0
	while count < 3:
		for x in 0..size:
			for y in 0..size:
				live = 0
				for adjx in -1..2:
					for adjy in -1..2:
						if !((x + adjx) < 0 || (x + adjx) >= size || (y + adjy) < 0 || (y + adjy) >= size):
							live = live + data1[(y + adjy)][(x + adjx)]

				this = data1[y][x]

				if this == 1:
					live = live - 1
					if live < 2 || live > 3:
						data2[y][x] = 0
					else:
						data2[y][x] = 1
				else:
					if live == 3:
						data2[y][x] = 1
					else:
						data2[y][x] = 0

		temp = data1
		data1 = data2
		data2 = temp

		count = count + 1

	if (Time.current() - start) > 1:
		sys.out.println("Too slow to have run on gpu... ("+(Time.current() - start)+"seconds)")
	else:
		debugworld(sys, data1, size)