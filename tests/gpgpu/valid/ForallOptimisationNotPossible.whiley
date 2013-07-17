import * from whiley.lang.*

public void ::main(System.Console sys):
	outlist = 0..10
	inlist = 0..10
	inlist[0] = 31
	inlist[1] = 4
	inlist[2] = 55
	inlist[3] = 100
	inlist[4] = 33
	inlist[5] = 12
	for number in inlist:
		outlist[number % |outlist|] = |outlist|

	sys.out.println(outlist)