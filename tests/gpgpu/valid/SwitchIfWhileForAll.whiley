import * from whiley.lang.*

public int timestwo(int a):
	return 2*a

public int myFunc(int a, int b):
	return timestwo(a)+b

public void ::main(System.Console sys):
	inlist = (0..5) + (40..45) + (101..111)
	outlist = 0..20

	for number in 0..20:
		if number < 5:
			outlist[number] = inlist[|inlist|-number-1] * 2 + 1
		else if number < 10:
			switch number:
				case 5:
					outlist[number] = 1000
				case 7:
					outlist[number] = 2000
				default:
					outlist[number] = 0
		else if number < 15:
			sum = 0
			for i in inlist:
				sum = sum + i
			outlist[number] = sum
		else:
			sum = 0
			count = 0
			while count < number:
				sum = sum + count
				count = count + 1
			outlist[number] = sum

	sys.out.println(outlist)