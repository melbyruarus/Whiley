// this is a comment!
void f({int} xs) where |xs| > 0:
    print str(xs)

void System::main([string] args):
    {int} ys
    {int} zs
    ys = {1,2,3}
    zs = ys
    f(zs)
