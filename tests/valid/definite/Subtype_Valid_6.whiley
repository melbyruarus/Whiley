define sr6nat as int where $ > 0
define sr6tup as (sr6nat f, int g) where g > f

void System::main([string] args):
    sr6tup x = (f:1,g:5)
    x.f = 2
    print str(x)
    
