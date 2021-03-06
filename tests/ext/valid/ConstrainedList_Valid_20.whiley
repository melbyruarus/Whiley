import println from whiley.lang.System

define nat as int where $ >= 0

define ONE_CENT as 0
define FIVE_CENTS as 1
define TEN_CENTS as 2
define TWENTY_CENTS as 3
define FIFTY_CENTS as 4
define ONE_DOLLAR as 5  // 1 dollar
define FIVE_DOLLARS as 6  // 5 dollars
define TEN_DOLLARS as 7 // 10 dollars

define Value as [
    1,
    5,
    10,
    20,
    50,
    100,
    500,
    1000
]

/**
 * Define the notion of cash as an array of coins / notes
 */
define Cash as [nat] where |$| == |Value|

Cash Cash([nat] coins) requires all { c in coins | c < |Value| }:
    cash = [0,0,0,0,0,0,0,0]
    i = 0
    while i < |coins| where i >= 0 && |cash| == |Value| && all {c in cash | c >= 0}:
        c = coins[i]
        cash[c] = cash[c] + 1
        i = i + 1
    return cash

void ::main(System.Console sys):
    cash = Cash([ONE_DOLLAR,FIVE_CENTS])
    sys.out.println(Any.toString(cash))
    cash = Cash([FIVE_DOLLARS,TEN_CENTS,FIFTY_CENTS])
    sys.out.println(Any.toString(cash))
    cash = Cash([ONE_DOLLAR,ONE_DOLLAR,TWENTY_CENTS])
    sys.out.println(Any.toString(cash))







    
    
    