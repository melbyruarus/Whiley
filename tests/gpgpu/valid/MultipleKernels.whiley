import * from whiley.lang.*

public void ::main(System.Console sys):
	outlist = 0..10
	otherlist = 1..11

	for number in 0..10:
		start = number
		for i in otherlist:
			start = start * i
		outlist[number] = start

	for number in 0..10:
		outlist[number] = outlist[number] / (outlist[number] - otherlist[number])

	for number in 0..10:
		outlist[number] = outlist[number] - otherlist[number]

	sys.out.println(outlist)