// A simple chess model
//
// David J. Pearce, 2010

// =============================================================
// Pieces
// =============================================================

define PAWN as 0
define KNIGHT as 1 
define BISHOP as 2
define ROOK as 3
define QUEEN as 4
define KING as 5
define EMPTY as 6 // shouldn't need this
define PIECE_CHARS as [ 'P', 'N', 'B', 'R', 'Q', 'K' ]

define PieceKind as { PAWN, KNIGHT, BISHOP, ROOK, QUEEN, KING, EMPTY }
define Piece as { PieceKind kind, bool colour }

define WHITE_PAWN as { kind: PAWN, colour: true }
define WHITE_KNIGHT as { kind: KNIGHT, colour: true }
define WHITE_BISHOP as { kind: BISHOP, colour: true }
define WHITE_ROOK as { kind: ROOK, colour: true }
define WHITE_QUEEN as { kind: QUEEN, colour: true }
define WHITE_KING as { kind: KING, colour: true }

define BLACK_PAWN as { kind: PAWN, colour: false }
define BLACK_KNIGHT as { kind: KNIGHT, colour: false }
define BLACK_BISHOP as { kind: BISHOP, colour: false }
define BLACK_ROOK as { kind: ROOK, colour: false }
define BLACK_QUEEN as { kind: QUEEN, colour: false }
define BLACK_KING as { kind: KING, colour: false }

// =============================================================
// Positions
// =============================================================

define RowCol as int // where 0 <= $ && $ <= 8
define Pos as { RowCol col, RowCol row } 

// =============================================================
// board
// =============================================================

define Row as [Piece] // where |$| == 8
define Board as [Row] // where |$| == 8

define startingChessBoard as [
    [ BLACK_PAWN,BLACK_PAWN,BLACK_PAWN,BLACK_PAWN,BLACK_PAWN,BLACK_PAWN,BLACK_PAWN,BLACK_PAWN ]
]

// =============================================================
// Moves
// =============================================================

define SingleMove as { Piece piece, Pos from, Pos to }
define SingleTake as { Piece piece, Pos from, Pos to, Piece taken }
define SimpleMove as SingleMove | SingleTake

define CheckMove as { SimpleMove move }
define Move as CheckMove | SimpleMove

// castling
// en passant

// =============================================================
// Valid Move Check
// =============================================================

// The purpose of the validMove method is to check whether or not a
// move is valid on a given board.
bool validMove(Move move, Board board):
    if move ~= SingleMove:
        return validSingleMove(move,board)
    return false

bool validSingleMove(SingleMove move, Board board):
    return false // temporary
