void f([int] x, int i) where |x| > 0:
    int y
    int z
    if i < 0 || i >= |x|:
        i = 1
    y = x[i]
    z = x[i]
    assert y == z
    print str(y)
    print str(z)

void System::main([string] args):
    [int] arr
    arr = [1,2,3]
    f(arr, 1)
    print str(arr)    
    f(arr, 2)
    print str(arr)
    arr = [123]
    f(arr, 3)
    print str(arr)
    arr = [123,22,2]
    f(arr, -1)
    print str(arr)
    f(arr, 4)
