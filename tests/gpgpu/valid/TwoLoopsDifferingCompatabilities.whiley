import * from whiley.lang.*

public void ::main(System.Console sys):
	outlist = 0..10

	for num in 0..10:
		sum = 0
		for i in 0..num:
			sum = sum + i
		outlist[num] = sum

	sys.out.print(outlist)