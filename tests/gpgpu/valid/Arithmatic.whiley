import * from whiley.lang.*

public void ::main(System.Console sys):
	outlist = -10..20

	for number in 0..20:
		outlist[number] = number * (number - 1) % (number * number + 1) / (number + 1) + 10 + 2 * 100

	sys.out.println(outlist)