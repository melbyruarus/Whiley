

string rep(char old, char new, string str):
    i = 0
    while i < |str|:
        if str[i] == old:
            str[i] = new
        i = i + 1
    return str    


void ::main(System.Console sys):
    sys.out.println(rep('e','w',"Hello"))
    sys.out.println(rep('H','z',"Hello"))
    sys.out.println(rep('o','1',"Hello"))
