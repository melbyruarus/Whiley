

define R1 as { real x }

[int] f([real] xs):
    return ([real]) xs

void ::main(System.Console sys):
    sys.out.println(Any.toString(f([1.0,2.0,3.0])))
    
