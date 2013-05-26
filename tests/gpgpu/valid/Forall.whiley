import * from whiley.lang.*

public void ::main(System.Console sys):
	outlist = 0..10
	otherlist = 1..10

	for number in 0..10:
		start = number
		for i in otherlist:
			start = start * i
		outlist[number] = start

	sys.out.println(outlist)