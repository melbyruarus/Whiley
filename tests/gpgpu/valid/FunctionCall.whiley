import * from whiley.lang.*

public int timestwo(int a):
	return 2*a

public int myFunc(int a, int b):
	return timestwo(a)+b

public void ::main(System.Console sys):
	outlist = 0..10
	tuple = (1, 100)
	for number in 0..10:
		outlist[number] = myFunc(number, number)
		x, y = tuple
		outlist[number] = outlist[number] + x + y

	sys.out.println(outlist)