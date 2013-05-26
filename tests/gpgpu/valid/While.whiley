import * from whiley.lang.*

public void ::main(System.Console sys):
	outlist = 0..10
	for number in 0..10:
		count = 0
		sum = 0
		while count < number:
			sum = sum + number
			count = count+1
		outlist[number] = sum

	sys.out.println(outlist)