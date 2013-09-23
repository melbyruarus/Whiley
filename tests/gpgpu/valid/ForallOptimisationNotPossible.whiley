import * from whiley.lang.*

public void ::main(System.Console sys):
	outlist = 0..10
	inlist = 0..10
	inlist[0] = 1
	inlist[1] = 4
	inlist[2] = 5
	inlist[3] = 0
	inlist[4] = 3
	inlist[5] = 2
	for number in inlist:
		outlist[number] = |outlist|

	sys.out.println(outlist)