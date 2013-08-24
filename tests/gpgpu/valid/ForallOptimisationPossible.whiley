import * from whiley.lang.*

public void ::main(System.Console sys):
	start = Time.current()

	outlist = 0..10
	for number in 0..10:
		sum = 0
		for i in 0..1000000:
			sum = (sum + i) % 10001
		outlist[number] = sum

	if Time.current() - start > 0.5:
		sys.out.println("Too slow, unlikely to have run on GPU at " + (Time.current() - start) + " seconds")
	else:
		sys.out.println(outlist)