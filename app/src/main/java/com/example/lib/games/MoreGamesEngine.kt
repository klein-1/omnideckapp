package com.example.lib.games

import kotlin.random.Random

// ==================== 15-PUZZLE ENGINE ====================

object FifteenPuzzleEngine {
    val SOLVED: List<Int> = (1..15).toList() + 0

    fun isSolved(board: List<Int>): Boolean = board == SOLVED

    fun isSolvable(board: List<Int>): Boolean {
        if (board.size != 16) return false
        var inversions = 0
        for (i in 0 until 16) {
            val a = board[i]
            if (a == 0) continue
            for (j in i + 1 until 16) {
                val b = board[j]
                if (b != 0 && a > b) inversions++
            }
        }
        val blankIdx = board.indexOf(0)
        val blankRowFromBottom = 4 - (blankIdx / 4) // 1..4 from bottom
        return (inversions + blankRowFromBottom) % 2 == 1
    }

    fun shuffledSolvable(rng: Random = Random.Default): List<Int> {
        val list = SOLVED.toMutableList()
        do {
            list.shuffle(rng)
        } while (!isSolvable(list) || isSolved(list))
        return list
    }

    fun canSlide(board: List<Int>, idx: Int): Boolean {
        val blank = board.indexOf(0)
        if (blank < 0 || idx !in 0..15) return false
        val r1 = idx / 4; val c1 = idx % 4
        val r2 = blank / 4; val c2 = blank % 4
        return kotlin.math.abs(r1 - r2) + kotlin.math.abs(c1 - c2) == 1
    }

    fun slide(board: List<Int>, idx: Int): List<Int>? {
        if (!canSlide(board, idx)) return null
        val blank = board.indexOf(0)
        val next = board.toMutableList()
        next[blank] = next[idx]
        next[idx] = 0
        return next
    }
}

// ==================== TOWER OF HANOI ENGINE ====================

object HanoiEngine {
    fun initialPegs(disks: Int): List<List<Int>> {
        val n = disks.coerceIn(3, 7)
        return listOf((n downTo 1).toList(), emptyList(), emptyList())
    }

    fun optimalMoves(disks: Int): Int = (1 shl disks.coerceIn(1, 10)) - 1

    fun canMove(pegs: List<List<Int>>, from: Int, to: Int): Boolean {
        if (from == to || from !in 0..2 || to !in 0..2) return false
        val src = pegs[from]
        if (src.isEmpty()) return false
        val disk = src.last()
        val dst = pegs[to]
        return dst.isEmpty() || dst.last() > disk
    }

    fun move(pegs: List<List<Int>>, from: Int, to: Int): List<List<Int>>? {
        if (!canMove(pegs, from, to)) return null
        return pegs.mapIndexed { idx, peg ->
            when (idx) {
                from -> peg.dropLast(1)
                to -> peg + pegs[from].last()
                else -> peg
            }
        }
    }

    fun isSolved(pegs: List<List<Int>>, disks: Int): Boolean =
        pegs[2].size == disks
}

// ==================== REVERSI (OTHELLO) ENGINE ====================

object ReversiEngine {
    // 0 = empty, 1 = Dark (Black), 2 = Light (White)
    private val DIRS = listOf(
        -1 to -1, -1 to 0, -1 to 1,
        0 to -1, 0 to 1,
        1 to -1, 1 to 0, 1 to 1
    )

    private val POS_WEIGHTS = intArrayOf(
        100, -20, 10, 5, 5, 10, -20, 100,
        -20, -50, -2, -2, -2, -2, -50, -20,
        10, -2, 3, 1, 1, 3, -2, 10,
        5, -2, 1, 1, 1, 1, -2, 5,
        5, -2, 1, 1, 1, 1, -2, 5,
        10, -2, 3, 1, 1, 3, -2, 10,
        -20, -50, -2, -2, -2, -2, -50, -20,
        100, -20, 10, 5, 5, 10, -20, 100
    )

    fun initialBoard(): IntArray {
        val b = IntArray(64)
        b[3 * 8 + 3] = 2
        b[3 * 8 + 4] = 1
        b[4 * 8 + 3] = 1
        b[4 * 8 + 4] = 2
        return b
    }

    fun flippedSquares(board: IntArray, pos: Int, player: Int): List<Int> {
        if (pos !in 0..63 || board[pos] != 0) return emptyList()
        val opp = if (player == 1) 2 else 1
        val r0 = pos / 8; val c0 = pos % 8
        val allFlips = mutableListOf<Int>()
        for ((dr, dc) in DIRS) {
            val ray = mutableListOf<Int>()
            var r = r0 + dr
            var c = c0 + dc
            while (r in 0..7 && c in 0..7) {
                val p = r * 8 + c
                when (board[p]) {
                    opp -> ray.add(p)
                    player -> {
                        if (ray.isNotEmpty()) allFlips.addAll(ray)
                        break
                    }
                    else -> break
                }
                r += dr; c += dc
            }
        }
        return allFlips
    }

    fun legalMoves(board: IntArray, player: Int): List<Int> =
        (0..63).filter { flippedSquares(board, it, player).isNotEmpty() }

    fun applyMove(board: IntArray, pos: Int, player: Int): IntArray? {
        val flips = flippedSquares(board, pos, player)
        if (flips.isEmpty()) return null
        val next = board.copyOf()
        next[pos] = player
        flips.forEach { next[it] = player }
        return next
    }

    fun bestAiMove(board: IntArray, aiPlayer: Int): Int? {
        val moves = legalMoves(board, aiPlayer)
        if (moves.isEmpty()) return null
        return moves.maxByOrNull { mv ->
            val flips = flippedSquares(board, mv, aiPlayer).size
            POS_WEIGHTS[mv] + flips * 2
        }
    }

    fun counts(board: IntArray): Pair<Int, Int> {
        var p1 = 0; var p2 = 0
        board.forEach {
            if (it == 1) p1++ else if (it == 2) p2++
        }
        return p1 to p2
    }
}

// ==================== NONOGRAM (5x5 PICROSS) ENGINE ====================

data class NonogramPuzzle(
    val size: Int,
    val solution: BooleanArray,
    val rowClues: List<List<Int>>,
    val colClues: List<List<Int>>
)

object NonogramEngine {
    fun cluesForLine(bits: List<Boolean>): List<Int> {
        val out = mutableListOf<Int>()
        var run = 0
        for (b in bits) {
            if (b) run++
            else if (run > 0) { out.add(run); run = 0 }
        }
        if (run > 0) out.add(run)
        return if (out.isEmpty()) listOf(0) else out
    }

    fun generatePuzzle(size: Int = 5, rng: Random = Random.Default): NonogramPuzzle {
        val sol = BooleanArray(size * size) { rng.nextFloat() < 0.58f }
        if (sol.none { it }) sol[0] = true
        val rows = (0 until size).map { r -> cluesForLine((0 until size).map { c -> sol[r * size + c] }) }
        val cols = (0 until size).map { c -> cluesForLine((0 until size).map { r -> sol[r * size + c] }) }
        return NonogramPuzzle(size, sol, rows, cols)
    }

    fun isSolved(puzzle: NonogramPuzzle, filled: BooleanArray): Boolean {
        val s = puzzle.size
        for (r in 0 until s) {
            if (cluesForLine((0 until s).map { c -> filled[r * s + c] }) != puzzle.rowClues[r]) return false
        }
        for (c in 0 until s) {
            if (cluesForLine((0 until s).map { r -> filled[r * s + c] }) != puzzle.colClues[c]) return false
        }
        return true
    }
}

// ==================== MAZE GENERATOR & BFS SOLVER ====================

data class MazeGrid(
    val rows: Int,
    val cols: Int,
    // bitmask per cell: 1=Top, 2=Right, 4=Bottom, 8=Left open passage
    val passages: IntArray
)

object MazeEngine {
    const val TOP = 1
    const val RIGHT = 2
    const val BOTTOM = 4
    const val LEFT = 8

    fun generate(rows: Int = 9, cols: Int = 9, rng: Random = Random.Default): MazeGrid {
        val passages = IntArray(rows * cols)
        val visited = BooleanArray(rows * cols)
        val stack = ArrayDeque<Int>()
        stack.addLast(0)
        visited[0] = true

        while (stack.isNotEmpty()) {
            val cur = stack.last()
            val r = cur / cols; val c = cur % cols
            val neighbors = mutableListOf<Triple<Int, Int, Int>>() // nextIdx, wallCur, wallNext
            if (r > 0 && !visited[(r - 1) * cols + c]) neighbors.add(Triple((r - 1) * cols + c, TOP, BOTTOM))
            if (c < cols - 1 && !visited[r * cols + (c + 1)]) neighbors.add(Triple(r * cols + (c + 1), RIGHT, LEFT))
            if (r < rows - 1 && !visited[(r + 1) * cols + c]) neighbors.add(Triple((r + 1) * cols + c, BOTTOM, TOP))
            if (c > 0 && !visited[r * cols + (c - 1)]) neighbors.add(Triple(r * cols + (c - 1), LEFT, RIGHT))

            if (neighbors.isEmpty()) {
                stack.removeLast()
            } else {
                val (nxt, wCur, wNxt) = neighbors[rng.nextInt(neighbors.size)]
                passages[cur] = passages[cur] or wCur
                passages[nxt] = passages[nxt] or wNxt
                visited[nxt] = true
                stack.addLast(nxt)
            }
        }
        return MazeGrid(rows, cols, passages)
    }

    fun shortestPathBfs(maze: MazeGrid, start: Int, goal: Int): List<Int> {
        val total = maze.rows * maze.cols
        if (start !in 0 until total || goal !in 0 until total) return emptyList()
        val parent = IntArray(total) { -1 }
        val seen = BooleanArray(total)
        val q = ArrayDeque<Int>()
        q.addLast(start)
        seen[start] = true

        while (q.isNotEmpty()) {
            val cur = q.removeFirst()
            if (cur == goal) break
            val r = cur / maze.cols; val c = cur % maze.cols
            val mask = maze.passages[cur]
            val nexts = mutableListOf<Int>()
            if ((mask and TOP) != 0 && r > 0) nexts.add((r - 1) * maze.cols + c)
            if ((mask and RIGHT) != 0 && c < maze.cols - 1) nexts.add(r * maze.cols + (c + 1))
            if ((mask and BOTTOM) != 0 && r < maze.rows - 1) nexts.add((r + 1) * maze.cols + c)
            if ((mask and LEFT) != 0 && c > 0) nexts.add(r * maze.cols + (c - 1))

            for (n in nexts) {
                if (!seen[n]) {
                    seen[n] = true
                    parent[n] = cur
                    q.addLast(n)
                }
            }
        }
        if (!seen[goal]) return emptyList()
        val path = mutableListOf<Int>()
        var cur = goal
        while (cur != -1) {
            path.add(cur)
            if (cur == start) break
            cur = parent[cur]
        }
        return path.reversed()
    }
}

// ==================== BLACKJACK & CARDS HELPERS ====================

data class PlayingCard(val rank: Int, val suit: Int) { // rank 1..13, suit 0..3 (♠ ♥ ♦ ♣)
    fun label(): String {
        val r = when (rank) {
            1 -> "A"; 11 -> "J"; 12 -> "Q"; 13 -> "K"; else -> rank.toString()
        }
        val s = when (suit) {
            0 -> "♠"; 1 -> "♥"; 2 -> "♦"; else -> "♣"
        }
        return "$r$s"
    }

    val isRed: Boolean get() = suit == 1 || suit == 2
}

object BlackjackEngine {
    fun newShuffledDeck(rng: Random = Random.Default): List<PlayingCard> =
        (0..3).flatMap { s -> (1..13).map { r -> PlayingCard(r, s) } }.shuffled(rng)

    fun handScore(hand: List<PlayingCard>): Int {
        var sum = 0
        var aces = 0
        for (c in hand) {
            when {
                c.rank == 1 -> { sum += 11; aces++ }
                c.rank >= 10 -> sum += 10
                else -> sum += c.rank
            }
        }
        while (sum > 21 && aces > 0) {
            sum -= 10
            aces--
        }
        return sum
    }
}
