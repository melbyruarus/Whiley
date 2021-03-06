import * from whiley.lang.*

public [[int]] createGrid(int w, int h):
	grid = []
	for y in 0..h:
		row = []
		for x in 0..w:
			row = row + [0]

		grid = grid + [row]

	return grid

public void ::printHash(int size, [[int]] data, real start, System.Console sys):
	if Time.current() - start < 0.5:
		hash = 0
		for y in 0..size:
			for x in 0..size:
				hash = hash + data[y][x]

		sys.out.println(hash)
	else:
		sys.out.println("Too slow")

public void ::main(System.Console sys):
	size = 100
	data = createGrid(size, size)

	start = Time.current()

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

	printHash(size, data, start, sys)

public int fakeFloat(int num, int den):
	return (10000 * num) / den

public int multFakeFloat(int a, int b):
	return (a * b) / 10000