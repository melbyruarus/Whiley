void f([int] ls) where no { i in {-1,0,1,2,3} | i >= 0 && i < |ls| && ls[i] < 0}:
    print str(ls)

void g([int] ls) where |ls| > 0:
    f(ls)

void System::main([string] args):
    g([-1,1,2,3])
