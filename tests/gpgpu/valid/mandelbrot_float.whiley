import * from whiley.lang.*

public void ::printHash([int] data, real start, System.Console sys):
	if Time.current() - start < 0.5:
		hash = 0
		count = 0
		while count < |data|:
			hash = hash + data[count]
			count = count + 1

		sys.out.println(hash)
	else:
		sys.out.println("Too slow")

public void ::main(System.Console sys):
	size = 100
	data = 0..(size * size)

	start = Time.current()

	for x in 0..size:
		x1 = (((-x*2.0/size)+1)*1.5)+0.5
		
		for y in 0..size:
			y1 = (((y*2.0/size)-1)*1.5)
			
			x0 = 0.0
			y0 = 0.0
			iteration = 0
			
			while((x0*x0 + y0*y0 <= 4) && (iteration < 1024)):
				xtmp = x0*x0 - y0*y0 - x1
				y0 = 2*x0*y0 + y1
				x0 = xtmp
				iteration = iteration + 1
			
			data[y*size+x] = iteration
	
	printHash(data, start, sys)
