package com.example.lib.games

enum class PieceColor { WHITE, BLACK; fun opposite() = if (this == WHITE) BLACK else WHITE }
enum class PieceType(val whiteGlyph: String, val blackGlyph: String, val value: Int) {
    PAWN("♙", "♟", 100),
    KNIGHT("♘", "♞", 320),
    BISHOP("♗", "♝", 330),
    ROOK("♖", "♜", 500),
    QUEEN("♕", "♛", 900),
    KING("♔", "♚", 20000)
}

data class ChessPiece(val color: PieceColor, val type: PieceType) {
    fun symbol(): String = if (color == PieceColor.WHITE) type.whiteGlyph else type.blackGlyph
}

data class ChessMove(val from: Int, val to: Int, val promotion: PieceType? = null)

data class ChessGameState(
    val board: List<ChessPiece?>, // 64 squares, index 0 = top-left (a8), 63 = h1
    val turn: PieceColor = PieceColor.WHITE,
    val whiteKingMoved: Boolean = false,
    val blackKingMoved: Boolean = false,
    val wRookAMoved: Boolean = false,
    val wRookHMoved: Boolean = false,
    val bRookAMoved: Boolean = false,
    val bRookHMoved: Boolean = false,
    val enPassantSquare: Int? = null,
    val inCheck: Boolean = false,
    val isCheckmate: Boolean = false,
    val isStalemate: Boolean = false,
    val moveHistory: List<String> = emptyList()
)

object ChessEngine {

    fun initialState(): ChessGameState {
        val board = MutableList<ChessPiece?>(64) { null }
        val backRank = listOf(PieceType.ROOK, PieceType.KNIGHT, PieceType.BISHOP, PieceType.QUEEN, PieceType.KING, PieceType.BISHOP, PieceType.KNIGHT, PieceType.ROOK)
        for (c in 0 until 8) {
            board[c] = ChessPiece(PieceColor.BLACK, backRank[c])
            board[8 + c] = ChessPiece(PieceColor.BLACK, PieceType.PAWN)
            board[48 + c] = ChessPiece(PieceColor.WHITE, PieceType.PAWN)
            board[56 + c] = ChessPiece(PieceColor.WHITE, backRank[c])
        }
        return ChessGameState(board = board)
    }

    fun legalMovesForSquare(state: ChessGameState, from: Int): List<ChessMove> {
        val piece = state.board.getOrNull(from) ?: return emptyList()
        if (piece.color != state.turn) return emptyList()
        return pseudoMovesForSquare(state, from, includeCastling = true).filter { move ->
            val next = applyMoveUnchecked(state, move)
            !isKingInCheck(next.board, piece.color)
        }
    }

    fun allLegalMoves(state: ChessGameState): List<ChessMove> {
        val out = mutableListOf<ChessMove>()
        for (sq in 0 until 64) {
            if (state.board[sq]?.color == state.turn) {
                out += legalMovesForSquare(state, sq)
            }
        }
        return out
    }

    fun makeMove(state: ChessGameState, move: ChessMove): ChessGameState {
        val next = applyMoveUnchecked(state, move)
        val opp = state.turn.opposite()
        val oppInCheck = isKingInCheck(next.board, opp)
        val oppMoves = allLegalMoves(next.copy(turn = opp))
        val notation = "${squareName(move.from)}→${squareName(move.to)}" + if (oppInCheck) (if (oppMoves.isEmpty()) "#" else "+") else ""
        return next.copy(
            turn = opp,
            inCheck = oppInCheck,
            isCheckmate = oppInCheck && oppMoves.isEmpty(),
            isStalemate = !oppInCheck && oppMoves.isEmpty(),
            moveHistory = state.moveHistory + notation
        )
    }

    fun bestAiMove(state: ChessGameState, depth: Int = 2): ChessMove? {
        val moves = allLegalMoves(state)
        if (moves.isEmpty()) return null
        val maximizing = state.turn == PieceColor.WHITE
        var bestMove = moves.first()
        var bestVal = if (maximizing) Int.MIN_VALUE else Int.MAX_VALUE
        for (m in moves) {
            val child = makeMove(state, m)
            val v = alphaBeta(child, depth - 1, Int.MIN_VALUE, Int.MAX_VALUE)
            if (maximizing) {
                if (v > bestVal) { bestVal = v; bestMove = m }
            } else {
                if (v < bestVal) { bestVal = v; bestMove = m }
            }
        }
        return bestMove
    }

    private fun alphaBeta(state: ChessGameState, depth: Int, alphaInit: Int, betaInit: Int): Int {
        if (state.isCheckmate) return if (state.turn == PieceColor.WHITE) -100000 - depth else 100000 + depth
        if (state.isStalemate) return 0
        if (depth <= 0) return evaluateBoard(state.board)

        val moves = allLegalMoves(state)
        var alpha = alphaInit
        var beta = betaInit
        return if (state.turn == PieceColor.WHITE) {
            var v = Int.MIN_VALUE
            for (m in moves) {
                v = maxOf(v, alphaBeta(makeMove(state, m), depth - 1, alpha, beta))
                alpha = maxOf(alpha, v)
                if (beta <= alpha) break
            }
            v
        } else {
            var v = Int.MAX_VALUE
            for (m in moves) {
                v = minOf(v, alphaBeta(makeMove(state, m), depth - 1, alpha, beta))
                beta = minOf(beta, v)
                if (beta <= alpha) break
            }
            v
        }
    }

    private fun evaluateBoard(board: List<ChessPiece?>): Int {
        var score = 0
        for (i in 0 until 64) {
            val p = board[i] ?: continue
            val r = i / 8; val c = i % 8
            val centerBonus = if (r in 3..4 && c in 3..4) 20 else if (r in 2..5 && c in 2..5) 8 else 0
            val v = p.type.value + centerBonus
            score += if (p.color == PieceColor.WHITE) v else -v
        }
        return score
    }

    private fun applyMoveUnchecked(state: ChessGameState, move: ChessMove): ChessGameState {
        val board = state.board.toMutableList()
        val moving = board[move.from] ?: return state
        val fromR = move.from / 8; val fromC = move.from % 8
        val toR = move.to / 8; val toC = move.to % 8

        // En passant capture
        if (moving.type == PieceType.PAWN && move.to == state.enPassantSquare && board[move.to] == null) {
            val capIdx = fromR * 8 + toC
            board[capIdx] = null
        }

        // Castling rook shift
        if (moving.type == PieceType.KING && kotlin.math.abs(toC - fromC) == 2) {
            if (toC == 6) { // Kingside
                board[fromR * 8 + 5] = board[fromR * 8 + 7]
                board[fromR * 8 + 7] = null
            } else if (toC == 2) { // Queenside
                board[fromR * 8 + 3] = board[fromR * 8]
                board[fromR * 8] = null
            }
        }

        // Promotion
        val finalPiece = if (moving.type == PieceType.PAWN && (toR == 0 || toR == 7)) {
            ChessPiece(moving.color, move.promotion ?: PieceType.QUEEN)
        } else moving

        board[move.to] = finalPiece
        board[move.from] = null

        val nextEp = if (moving.type == PieceType.PAWN && kotlin.math.abs(toR - fromR) == 2) {
            ((fromR + toR) / 2) * 8 + fromC
        } else null

        return state.copy(
            board = board,
            whiteKingMoved = state.whiteKingMoved || (moving.color == PieceColor.WHITE && moving.type == PieceType.KING),
            blackKingMoved = state.blackKingMoved || (moving.color == PieceColor.BLACK && moving.type == PieceType.KING),
            wRookAMoved = state.wRookAMoved || move.from == 56 || move.to == 56,
            wRookHMoved = state.wRookHMoved || move.from == 63 || move.to == 63,
            bRookAMoved = state.bRookAMoved || move.from == 0 || move.to == 0,
            bRookHMoved = state.bRookHMoved || move.from == 7 || move.to == 7,
            enPassantSquare = nextEp
        )
    }

    fun isKingInCheck(board: List<ChessPiece?>, color: PieceColor): Boolean {
        val kingSq = board.indexOfFirst { it?.color == color && it.type == PieceType.KING }
        if (kingSq < 0) return false
        return isSquareAttacked(board, kingSq, byColor = color.opposite())
    }

    private fun isSquareAttacked(board: List<ChessPiece?>, targetSq: Int, byColor: PieceColor): Boolean {
        val fakeState = ChessGameState(board = board, turn = byColor)
        for (sq in 0 until 64) {
            if (board[sq]?.color == byColor) {
                if (pseudoMovesForSquare(fakeState, sq, includeCastling = false).any { it.to == targetSq }) return true
            }
        }
        return false
    }

    private fun pseudoMovesForSquare(state: ChessGameState, from: Int, includeCastling: Boolean): List<ChessMove> {
        val piece = state.board[from] ?: return emptyList()
        val r = from / 8; val c = from % 8
        val out = mutableListOf<ChessMove>()

        fun addIfValid(nr: Int, nc: Int, canCapture: Boolean = true, mustCapture: Boolean = false): Boolean {
            if (nr !in 0..7 || nc !in 0..7) return false
            val to = nr * 8 + nc
            val target = state.board[to]
            return if (target == null) {
                if (!mustCapture) out += ChessMove(from, to)
                true
            } else {
                if (canCapture && target.color != piece.color) out += ChessMove(from, to)
                false
            }
        }

        when (piece.type) {
            PieceType.PAWN -> {
                val dir = if (piece.color == PieceColor.WHITE) -1 else 1
                val startRow = if (piece.color == PieceColor.WHITE) 6 else 1
                val oneR = r + dir
                if (oneR in 0..7 && state.board[oneR * 8 + c] == null) {
                    out += ChessMove(from, oneR * 8 + c)
                    val twoR = r + dir * 2
                    if (r == startRow && state.board[twoR * 8 + c] == null) {
                        out += ChessMove(from, twoR * 8 + c)
                    }
                }
                for (dc in listOf(-1, 1)) {
                    val nc = c + dc
                    if (oneR in 0..7 && nc in 0..7) {
                        val to = oneR * 8 + nc
                        val target = state.board[to]
                        if ((target != null && target.color != piece.color) || to == state.enPassantSquare) {
                            out += ChessMove(from, to)
                        }
                    }
                }
            }
            PieceType.KNIGHT -> {
                val jumps = listOf(-2 to -1, -2 to 1, -1 to -2, -1 to 2, 1 to -2, 1 to 2, 2 to -1, 2 to 1)
                jumps.forEach { (dr, dc) -> addIfValid(r + dr, c + dc) }
            }
            PieceType.BISHOP -> slideDirs(r, c, listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1), ::addIfValid)
            PieceType.ROOK -> slideDirs(r, c, listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1), ::addIfValid)
            PieceType.QUEEN -> slideDirs(r, c, listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1, -1 to 0, 1 to 0, 0 to -1, 0 to 1), ::addIfValid)
            PieceType.KING -> {
                for (dr in -1..1) for (dc in -1..1) {
                    if (dr != 0 || dc != 0) addIfValid(r + dr, c + dc)
                }
                if (includeCastling) {
                    if (piece.color == PieceColor.WHITE && !state.whiteKingMoved && from == 60 && !isKingInCheck(state.board, PieceColor.WHITE)) {
                        if (!state.wRookHMoved && state.board[61] == null && state.board[62] == null &&
                            !isSquareAttacked(state.board, 61, PieceColor.BLACK) && !isSquareAttacked(state.board, 62, PieceColor.BLACK)
                        ) out += ChessMove(60, 62)
                        if (!state.wRookAMoved && state.board[59] == null && state.board[58] == null && state.board[57] == null &&
                            !isSquareAttacked(state.board, 59, PieceColor.BLACK) && !isSquareAttacked(state.board, 58, PieceColor.BLACK)
                        ) out += ChessMove(60, 58)
                    } else if (piece.color == PieceColor.BLACK && !state.blackKingMoved && from == 4 && !isKingInCheck(state.board, PieceColor.BLACK)) {
                        if (!state.bRookHMoved && state.board[5] == null && state.board[6] == null &&
                            !isSquareAttacked(state.board, 5, PieceColor.WHITE) && !isSquareAttacked(state.board, 6, PieceColor.WHITE)
                        ) out += ChessMove(4, 6)
                        if (!state.bRookAMoved && state.board[3] == null && state.board[2] == null && state.board[1] == null &&
                            !isSquareAttacked(state.board, 3, PieceColor.WHITE) && !isSquareAttacked(state.board, 2, PieceColor.WHITE)
                        ) out += ChessMove(4, 2)
                    }
                }
            }
        }
        return out
    }

    private inline fun slideDirs(r: Int, c: Int, dirs: List<Pair<Int, Int>>, step: (Int, Int, Boolean, Boolean) -> Boolean) {
        dirs.forEach { (dr, dc) ->
            var nr = r + dr; var nc = c + dc
            while (nr in 0..7 && nc in 0..7) {
                if (!step(nr, nc, true, false)) break
                nr += dr; nc += dc
            }
        }
    }

    private fun squareName(sq: Int): String = "${'a' + (sq % 8)}${8 - (sq / 8)}"
}
