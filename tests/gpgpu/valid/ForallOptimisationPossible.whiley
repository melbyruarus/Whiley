import * from whiley.lang.*

public void ::main(System.Console sys):
	outlist = 0..10
	for number in 0..10:
		sum = 0
		for i in 0..1000000:
			sum = (sum + i) % 10001
		outlist[number] = sum

	sys.out.println(outlist)