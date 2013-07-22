import * from whiley.lang.*

public void ::main(System.Console sys):
	outlist = ([real])(0..10)
	
	for number in 0..10:
		val = number + 0.5
		val = number * 3.14
		outlist[number] = val

	sys.out.println(outlist)