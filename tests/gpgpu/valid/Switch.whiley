import * from whiley.lang.*

public void ::main(System.Console sys):
	outlist = 0..10
	for number in 0..10:
		switch number:
			case 1:
				outlist[number] = 20
			case 3:
				outlist[number] = 40
			case 5:
				outlist[number] = 60
			case 7:
				outlist[number] = 80
			case 9:
				outlist[number] = 100
			default:
				outlist[number] = -number

	sys.out.println(outlist)