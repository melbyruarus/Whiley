import * from whiley.lang.*

public void ::main(System.Console sys):
	outlist = 0..10
	tuple = (1, 100)
	for number in 0..10:
		x, y = tuple
		outlist[number] = outlist[number] + x + y

	sys.out.println(outlist)