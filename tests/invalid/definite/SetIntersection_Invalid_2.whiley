void f({int} xs, {int} ys, {int} zs) where zs == xs ∩ {1,2,3}:
    print str(xs)

void h({int} ys, {int} zs):
    f(ys,zs,ys ∩ zs)

void System::main([string] args):
    h({},{})
    h({1},{1,2})
    h({1,2,3},{3,4,5})
    h({1,2},{3,4,5})
