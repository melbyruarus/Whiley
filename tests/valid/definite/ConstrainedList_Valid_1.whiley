[byte] f(int x) where x == 0 || x == 169:
    return [x]

void System::main([string] args):
    [byte] bytes = f(0)
    print str(bytes)

