import * from whiley.lang.*

public void ::main(System.Console sys):
	outlist = 0..10
	inlist = 0..10
	inlist[0] = 34
	inlist[1] = 4
	inlist[2] = 56
	inlist[3] = 9
	inlist[4] = 33
	inlist[5] = 12
	for number in inlist:
		outlist[number % |outlist|] = number

	sys.out.println(outlist)