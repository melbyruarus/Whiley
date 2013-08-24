import * from whiley.lang.*

public void ::main(System.Console sys):
	outlist = [1..4, 1..4, 1..4]

	size = |outlist|

	for x in 0..size:
		for y in 0..size:
			outlist[y][x] = outlist[y][x] * 2

	sys.out.println(outlist)