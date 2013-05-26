import * from whiley.lang.*

public void ::main(System.Console sys):
	outlist = -10..10

	for number in 0..20:
		outlist[number] = number % 3

	sys.out.println(outlist)