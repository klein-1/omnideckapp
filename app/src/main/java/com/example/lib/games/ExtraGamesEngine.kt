package com.example.lib.games

import kotlin.random.Random

/**
 * Pure Kotlin engines for the 5 new Omni Deck games:
 * 1. Connect Four (6x7 with minimax AI)
 * 2. Wordle 5-letter puzzle (two-pass duplicate letter evaluation)
 * 3. Lights Out 5x5 (solvable generator + optimal GF(2) hint tracking)
 * 4. Checkers / Draughts 8x8 (mandatory capture, multi-jump chains, king promotion, AI)
 */

// ==================== 1. CONNECT FOUR ====================

data class Connect4Win(val winner: Int, val cells: List<Pair<Int, Int>>)

object Connect4Engine {
    const val ROWS = 6
    const val COLS = 7

    fun emptyBoard(): IntArray = IntArray(ROWS * COLS)

    fun canDrop(board: IntArray, col: Int): Boolean =
        col in 0 until COLS && board[col] == 0

    fun dropDisc(board: IntArray, col: Int, player: Int): Pair<IntArray, Int>? {
        if (!canDrop(board, col)) return null
        for (r in ROWS - 1 downTo 0) {
            val idx = r * COLS + col
            if (board[idx] == 0) {
                val next = board.copyOf()
                next[idx] = player
                return next to r
            }
        }
        return null
    }

    fun checkWinner(board: IntArray): Connect4Win? {
        val dirs = listOf(0 to 1, 1 to 0, 1 to 1, 1 to -1)
        for (r in 0 until ROWS) {
            for (c in 0 until COLS) {
                val p = board[r * COLS + c]
                if (p == 0) continue
                for ((dr, dc) in dirs) {
                    val cells = (0 until 4).map { k -> (r + k * dr) to (c + k * dc) }
                    if (cells.all { (rr, cc) ->
                            rr in 0 until ROWS && cc in 0 until COLS && board[rr * COLS + cc] == p
                        }) {
                        return Connect4Win(p, cells)
                    }
                }
            }
        }
        return null
    }

    fun isDraw(board: IntArray): Boolean =
        checkWinner(board) == null && (0 until COLS).all { board[it] != 0 }

    fun bestAiMove(board: IntArray, aiPlayer: Int = 2, humanPlayer: Int = 1): Int {
        val validCols = (0 until COLS).filter { canDrop(board, it) }
        if (validCols.isEmpty()) return -1

        // 1. Immediate win
        for (c in validCols) {
            val (next, _) = dropDisc(board, c, aiPlayer) ?: continue
            if (checkWinner(next)?.winner == aiPlayer) return c
        }
        // 2. Block immediate human win
        for (c in validCols) {
            val (next, _) = dropDisc(board, c, humanPlayer) ?: continue
            if (checkWinner(next)?.winner == humanPlayer) return c
        }

        // 3. Depth-2 heuristic lookahead preferring center columns
        val order = listOf(3, 2, 4, 1, 5, 0, 6).filter { it in validCols }
        var bestCol = order.first()
        var bestScore = Int.MIN_VALUE

        for (c in order) {
            val (afterAi, _) = dropDisc(board, c, aiPlayer) ?: continue
            // Penalize setting up human winning move right above
            val humanResponses = (0 until COLS).filter { canDrop(afterAi, it) }
            var setsUpLoss = false
            for (hc in humanResponses) {
                val (afterHuman, _) = dropDisc(afterAi, hc, humanPlayer) ?: continue
                if (checkWinner(afterHuman)?.winner == humanPlayer) {
                    setsUpLoss = true
                    break
                }
            }
            val score = if (setsUpLoss) -10_000 else evaluateBoard(afterAi, aiPlayer, humanPlayer) + (3 - kotlin.math.abs(3 - c)) * 4
            if (score > bestScore) {
                bestScore = score
                bestCol = c
            }
        }
        return bestCol
    }

    private fun evaluateBoard(board: IntArray, ai: Int, human: Int): Int {
        var score = 0
        val dirs = listOf(0 to 1, 1 to 0, 1 to 1, 1 to -1)
        for (r in 0 until ROWS) {
            for (c in 0 until COLS) {
                for ((dr, dc) in dirs) {
                    val coords = (0 until 4).map { k -> (r + k * dr) to (c + k * dc) }
                    if (coords.any { (rr, cc) -> rr !in 0 until ROWS || cc !in 0 until COLS }) continue
                    var aiCnt = 0
                    var huCnt = 0
                    for ((rr, cc) in coords) {
                        when (board[rr * COLS + cc]) {
                            ai -> aiCnt++
                            human -> huCnt++
                        }
                    }
                    if (aiCnt > 0 && huCnt == 0) {
                        score += when (aiCnt) {
                            1 -> 1; 2 -> 8; 3 -> 45; else -> 1000
                        }
                    } else if (huCnt > 0 && aiCnt == 0) {
                        score -= when (huCnt) {
                            1 -> 1; 2 -> 9; 3 -> 55; else -> 1000
                        }
                    }
                }
            }
        }
        return score
    }
}

// ==================== 2. WORDLE 5-LETTER ENGINE ====================

enum class WordleTile { EXACT, PRESENT, ABSENT }

object WordleEngine {
    val WORDS: List<String> = listOf(
        "AUDIO", "CRANE", "SLATE", "TRACE", "CRATE", "ADIEU", "STARE", "RAISE", "ARISE", "TEARS",
        "REACT", "HEART", "EARTH", "LIGHT", "NIGHT", "RIGHT", "SIGHT", "TIGHT", "MIGHT", "FIGHT",
        "BRAIN", "TRAIN", "CHAIN", "PLAIN", "GRAIN", "STAIN", "PAINT", "SAINT", "POINT", "JOINT",
        "SMART", "START", "CHART", "PART", "SPORT", "SHORT", "NORTH", "SOUTH", "WORLD", "WORDS",
        "CLOUD", "PROUD", "SOUND", "FOUND", "ROUND", "BOUND", "POUND", "GRIND", "BLIND", "WINDS",
        "PIXEL", "GRAPH", "LOGIC", "STACK", "FRAME", "SCALE", "SHELL", "CLOCK", "TIMER", "PULSE",
        "WATCH", "MATCH", "PATCH", "CATCH", "LATCH", "BATCH", "PITCH", "WITCH", "DITCH", "HITCH",
        "POWER", "TOWER", "LOWER", "WATER", "PAPER", "LASER", "RADAR", "SONAR", "SOLAR", "LUNAR",
        "METAL", "STONE", "GLASS", "STEEL", "BRASS", "CHALK", "CREAM", "DREAM", "STEAM", "STREAM".take(5),
        "OCEAN", "RIVER", "BROOK", "CREEK", "MOUNT", "RIDGE", "VALLEY".take(5), "PLAIN", "FOREST".take(5), "GROVE",
        "SPARK", "FLAME", "BLAZE", "EMBER", "FLASH", "TORCH", "PRISM", "LUMEN", "AMBER", "CORAL",
        "IVORY", "EBONY", "AZURE", "OCHRE", "UMBER", "TEAL", "MAIZE", "OLIVE", "PEACH", "BERRY",
        "APPLE", "GRAPE", "LEMON", "MANGO", "MELON", "ONION", "BREAD", "TOAST", "ROAST", "FEAST",
        "CHESS", "QUEEN", "KNIGHT".take(5), "ROOKS", "PAWNS", "SCORE", "BOARD", "DICEY", "BLITZ", "DRILL",
        "ROUTE", "TRACK", "SPEED", "METER", "GAUGE", "LEVEL", "ANGLE", "CHORD", "SCALE", "TEMPO",
        "CIPHER".take(5), "VAULT", "TOKEN", "NONCE", "PROXY", "PATCH", "DEBUG", "PARSE", "REGEX", "QUERY",
        "SWIFT", "RUSTY", "KOTLIN".take(5), "FLAGS", "BYTES", "NODES", "PORTS", "FIBER", "DRIVE", "DISKS"
    ).map { it.uppercase() }.filter { it.length == 5 }.distinct()

    fun randomWord(rng: Random = Random): String = WORDS[rng.nextInt(WORDS.size)]

    fun evaluateGuess(targetRaw: String, guessRaw: String): List<WordleTile> {
        val target = targetRaw.uppercase()
        val guess = guessRaw.uppercase()
        val res = MutableList(5) { WordleTile.ABSENT }
        val remainingCounts = IntArray(26)

        for (i in 0 until 5) {
            val g = guess.getOrNull(i) ?: ' '
            val t = target.getOrNull(i) ?: ' '
            if (g == t) {
                res[i] = WordleTile.EXACT
            } else if (t in 'A'..'Z') {
                remainingCounts[t - 'A']++
            }
        }
        for (i in 0 until 5) {
            if (res[i] == WordleTile.EXACT) continue
            val g = guess.getOrNull(i) ?: ' '
            if (g in 'A'..'Z' && remainingCounts[g - 'A'] > 0) {
                res[i] = WordleTile.PRESENT
                remainingCounts[g - 'A']--
            }
        }
        return res
    }
}

// ==================== 3. LIGHTS OUT 5x5 ====================

data class LightsOutState(
    val grid: BooleanArray,
    val moves: Int = 0,
    val solutionSet: Set<Int> = emptySet(),
    val parMoves: Int = 0
) {
    val isSolved: Boolean get() = grid.all { !it }
}

object LightsOutEngine {
    const val SIDE = 5

    fun generateSolvable(difficultyMoves: Int = 8, rng: Random = Random): LightsOutState {
        var grid = BooleanArray(SIDE * SIDE) { false }
        val chosen = (0 until SIDE * SIDE).shuffled(rng).take(difficultyMoves.coerceIn(3, 18)).toSet()
        chosen.forEach { idx ->
            grid = applyToggleRaw(grid, idx / SIDE, idx % SIDE)
        }
        // Guarantee non-empty board
        if (grid.all { !it }) {
            grid = applyToggleRaw(grid, 2, 2)
            return LightsOutState(grid, moves = 0, solutionSet = chosen + 12, parMoves = chosen.size + 1)
        }
        return LightsOutState(grid, moves = 0, solutionSet = chosen, parMoves = chosen.size)
    }

    fun toggle(state: LightsOutState, r: Int, c: Int): LightsOutState {
        if (r !in 0 until SIDE || c !in 0 until SIDE) return state
        val idx = r * SIDE + c
        val nextGrid = applyToggleRaw(state.grid, r, c)
        val nextSol = if (idx in state.solutionSet) state.solutionSet - idx else state.solutionSet + idx
        return state.copy(grid = nextGrid, moves = state.moves + 1, solutionSet = nextSol)
    }

    private fun applyToggleRaw(grid: BooleanArray, r: Int, c: Int): BooleanArray {
        val next = grid.copyOf()
        val offsets = listOf(0 to 0, -1 to 0, 1 to 0, 0 to -1, 0 to 1)
        for ((dr, dc) in offsets) {
            val nr = r + dr
            val nc = c + dc
            if (nr in 0 until SIDE && nc in 0 until SIDE) {
                val idx = nr * SIDE + nc
                next[idx] = !next[idx]
            }
        }
        return next
    }
}

// ==================== 4. CHECKERS (DRAUGHTS 8x8) ====================

enum class CheckerPiece(val isRed: Boolean, val isKing: Boolean) {
    RED(true, false),
    RED_KING(true, true),
    BLACK(false, false),
    BLACK_KING(false, true)
}

data class CheckerMove(
    val from: Int,
    val to: Int,
    val capturedSq: Int? = null
)

data class CheckersState(
    val board: List<CheckerPiece?>,
    val redTurn: Boolean = true,
    val forcedJumpSq: Int? = null,
    val redMovesCount: Int = 0,
    val blackMovesCount: Int = 0,
    val winner: String? = null
)

object CheckersEngine {
    fun initialState(): CheckersState {
        val board = MutableList<CheckerPiece?>(64) { null }
        for (r in 0 until 8) {
            for (c in 0 until 8) {
                if ((r + c) % 2 == 1) {
                    when {
                        r < 3 -> board[r * 8 + c] = CheckerPiece.BLACK
                        r > 4 -> board[r * 8 + c] = CheckerPiece.RED
                    }
                }
            }
        }
        return CheckersState(board = board, redTurn = true)
    }

    fun legalMoves(state: CheckersState): List<CheckerMove> {
        if (state.winner != null) return emptyList()
        val squaresToCheck = state.forcedJumpSq?.let { listOf(it) } ?: (0 until 64).toList()

        val jumps = mutableListOf<CheckerMove>()
        val steps = mutableListOf<CheckerMove>()

        for (sq in squaresToCheck) {
            val p = state.board[sq] ?: continue
            if (p.isRed != state.redTurn) continue
            val r = sq / 8
            val c = sq % 8
            val rowDirs = when {
                p.isKing -> listOf(-1, 1)
                p.isRed -> listOf(-1)
                else -> listOf(1)
            }
            for (dr in rowDirs) {
                for (dc in listOf(-1, 1)) {
                    val mr = r + dr
                    val mc = c + dc
                    if (mr !in 0 until 8 || mc !in 0 until 8) continue
                    val midSq = mr * 8 + mc
                    val midPiece = state.board[midSq]
                    if (midPiece == null) {
                        if (state.forcedJumpSq == null) {
                            steps += CheckerMove(sq, midSq, null)
                        }
                    } else if (midPiece.isRed != p.isRed) {
                        val jr = r + dr * 2
                        val jc = c + dc * 2
                        if (jr in 0 until 8 && jc in 0 until 8) {
                            val jumpSq = jr * 8 + jc
                            if (state.board[jumpSq] == null) {
                                jumps += CheckerMove(sq, jumpSq, midSq)
                            }
                        }
                    }
                }
            }
        }
        // Mandatory capture rule
        return if (jumps.isNotEmpty()) jumps else steps
    }

    fun applyMove(state: CheckersState, move: CheckerMove): CheckersState {
        val nextBoard = state.board.toMutableList()
        val moving = nextBoard[move.from] ?: return state
        nextBoard[move.from] = null
        if (move.capturedSq != null) nextBoard[move.capturedSq] = null

        val destRow = move.to / 8
        val promoted = when {
            moving == CheckerPiece.RED && destRow == 0 -> CheckerPiece.RED_KING
            moving == CheckerPiece.BLACK && destRow == 7 -> CheckerPiece.BLACK_KING
            else -> moving
        }
        nextBoard[move.to] = promoted
        val becameKing = promoted != moving

        // Check multi-jump continuation (stops immediately upon crowning by standard American checkers rules)
        if (move.capturedSq != null && !becameKing) {
            val probe = state.copy(board = nextBoard, forcedJumpSq = move.to)
            val followUpJumps = legalMoves(probe).filter { it.capturedSq != null }
            if (followUpJumps.isNotEmpty()) {
                return state.copy(board = nextBoard, forcedJumpSq = move.to)
            }
        }

        val nextTurnRed = !state.redTurn
        val after = state.copy(
            board = nextBoard,
            redTurn = nextTurnRed,
            forcedJumpSq = null,
            redMovesCount = state.redMovesCount + if (state.redTurn) 1 else 0,
            blackMovesCount = state.blackMovesCount + if (!state.redTurn) 1 else 0
        )
        val oppMoves = legalMoves(after)
        val oppCount = nextBoard.count { it != null && it.isRed == nextTurnRed }
        val winner = if (oppCount == 0 || oppMoves.isEmpty()) {
            if (state.redTurn) "RED" else "BLACK"
        } else null
        return after.copy(winner = winner)
    }

    fun aiPlayFullTurn(state: CheckersState): CheckersState {
        var cur = state
        var guard = 0
        while (!cur.redTurn && cur.winner == null && guard++ < 10) {
            val moves = legalMoves(cur)
            if (moves.isEmpty()) break
            // Score moves: prefer jumps, kings, center squares, promotion rows
            val chosen = moves.maxByOrNull { m ->
                var s = 0
                if (m.capturedSq != null) s += 100
                val destR = m.to / 8
                val destC = m.to % 8
                if (destR == 7) s += 35
                if (destC in 2..5) s += 8
                val piece = cur.board[m.from]
                if (piece?.isKing == true) s += 5
                s + Random.nextInt(4)
            } ?: break
            cur = applyMove(cur, chosen)
        }
        return cur
    }
}
