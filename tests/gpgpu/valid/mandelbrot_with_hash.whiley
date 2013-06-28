import * from whiley.lang.*

public int fakeFloat(int num, int den):
	return (10000 * num) / den

public int multFakeFloat(int a, int b):
	return (a * b) / 10000

public void ::main(System.Console sys):
	size = 100
	side = 0..size
	data = 0..(size * size)

	for x in side:
		x1 = multFakeFloat((((-x*fakeFloat(2, 1))/size)+fakeFloat(1, 1)), fakeFloat(15, 10))+fakeFloat(5, 10)

		for y in side:
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

	hash = 0
	count = 0
	while count < |data|:
		hash = hash + data[count]
		count = count + 1

	sys.out.println(hash)