import * from whiley.lang.*

{int->int} f(int x):
    return {1->x, 3->2}

void ::main(System sys,[string] args):
    sys.out.println(Any.toString(f(1)))
    sys.out.println(Any.toString(f(2)))
    sys.out.println(Any.toString(f(3)))
