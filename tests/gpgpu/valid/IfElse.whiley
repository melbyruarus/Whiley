import * from whiley.lang.*

public void ::main(System.Console sys):
	outlist = 0..20
	otherlist = 10..30

	for number in 0..20:
		if number < 5:
			outlist[number] = 1
		else if number < 10:
			outlist[number] = 2
		else if number < 12:
			outlist[number] = 3
		else if number < 15:
			outlist[number] = 4
		else if number < 18:
			outlist[number] = 5
		else:
			outlist[number] = 6

	sys.out.println(outlist)