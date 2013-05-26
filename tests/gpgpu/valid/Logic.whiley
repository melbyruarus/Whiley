import * from whiley.lang.*

public void ::main(System.Console sys):
	outlist = 0..20
	otherlist = 0..20

	for number in 0..20:
		if number < 5:
			if outlist[number] == 3:
				outlist[number] = 0
			else:
				outlist[number] = -1
		else if number < 10:
			if outlist[number] == 6 || outlist[number] == 7:
				outlist[number] = 0
			else:
				outlist[number] = -1
		else if number < 12:
			if outlist[number] > 10 && otherlist[number] < 12:
				outlist[number] = 0
			else:
				outlist[number] = -1
		else if number < 15:
			if !(outlist[number] > 14):
				outlist[number] = 0
			else:
				outlist[number] = -1
		else:
			if (outlist[number] < 20) == (otherlist[number] < 19):
				outlist[number] = 0
			else:
				outlist[number] = -1

	sys.out.println(outlist)