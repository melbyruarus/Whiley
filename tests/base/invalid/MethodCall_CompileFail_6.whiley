

define wmccf6tup as {int x, int y}

wmccf6tup f(System x, int y):
    return {x:1, y:x.get()}

int System::get():
    return 1

void ::main(System.Console sys):
    sys.out.println(Any.toString(f(this),1))
