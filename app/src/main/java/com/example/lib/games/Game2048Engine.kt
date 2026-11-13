package com.example.lib.games

import kotlin.random.Random

enum class MoveDir { UP, DOWN, LEFT, RIGHT }

data class Game2048State(
    val board: IntArray = IntArray(16),
    val score: Int = 0,
    val gameOver: Boolean = false,
    val won: Boolean = false
) {
    fun copyState(): Game2048State = copy(board = board.copyOf())
}

object Game2048Engine {
    fun newGame(rng: Random = Random.Default): Game2048State {
        val b = IntArray(16)
        spawnTile(b, rng)
        spawnTile(b, rng)
        return Game2048State(board = b, score = 0)
    }

    private fun spawnTile(board: IntArray, rng: Random): Boolean {
        val empty = board.indices.filter { board[it] == 0 }
        if (empty.isEmpty()) return false
        val idx = empty[rng.nextInt(empty.size)]
        board[idx] = if (rng.nextFloat() < 0.9f) 2 else 4
        return true
    }

    fun move(state: Game2048State, dir: MoveDir, rng: Random = Random.Default): Pair<Game2048State, Boolean> {
        val next = state.board.copyOf()
        var gained = 0
        var moved = false

        fun slideLine(line: IntArray): IntArray {
            val nonZero = line.filter { it != 0 }.toMutableList()
            val merged = mutableListOf<Int>()
            var i = 0
            while (i < nonZero.size) {
                if (i + 1 < nonZero.size && nonZero[i] == nonZero[i + 1]) {
                    val sum = nonZero[i] * 2
                    merged += sum
                    gained += sum
                    i += 2
                } else {
                    merged += nonZero[i]
                    i += 1
                }
            }
            while (merged.size < 4) merged += 0
            return merged.toIntArray()
        }

        for (i in 0 until 4) {
            val orig = IntArray(4) { j ->
                when (dir) {
                    MoveDir.LEFT -> next[i * 4 + j]
                    MoveDir.RIGHT -> next[i * 4 + (3 - j)]
                    MoveDir.UP -> next[j * 4 + i]
                    MoveDir.DOWN -> next[(3 - j) * 4 + i]
                }
            }
            val slid = slideLine(orig)
            if (!orig.contentEquals(slid)) moved = true
            for (j in 0 until 4) {
                when (dir) {
                    MoveDir.LEFT -> next[i * 4 + j] = slid[j]
                    MoveDir.RIGHT -> next[i * 4 + (3 - j)] = slid[j]
                    MoveDir.UP -> next[j * 4 + i] = slid[j]
                    MoveDir.DOWN -> next[(3 - j) * 4 + i] = slid[j]
                }
            }
        }

        if (!moved) return state to false
        spawnTile(next, rng)
        val has2048 = next.any { it >= 2048 }
        val canMove = canMoveAny(next)
        return Game2048State(
            board = next,
            score = state.score + gained,
            gameOver = !canMove,
            won = state.won || has2048
        ) to true
    }

    private fun canMoveAny(b: IntArray): Boolean {
        if (b.any { it == 0 }) return true
        for (r in 0 until 4) {
            for (c in 0 until 4) {
                val v = b[r * 4 + c]
                if (c + 1 < 4 && b[r * 4 + c + 1] == v) return true
                if (r + 1 < 4 && b[(r + 1) * 4 + c] == v) return true
            }
        }
        return false
    }
}

object TicTacToeEngine {
    private val LINES = listOf(
        listOf(0, 1, 2), listOf(3, 4, 5), listOf(6, 7, 8),
        listOf(0, 3, 6), listOf(1, 4, 7), listOf(2, 5, 8),
        listOf(0, 4, 8), listOf(2, 4, 6)
    )

    fun checkWinner(board: CharArray): Char? {
        for (line in LINES) {
            val a = board[line[0]]
            if (a != ' ' && a == board[line[1]] && a == board[line[2]]) return a
        }
        return null
    }

    fun isDraw(board: CharArray): Boolean = checkWinner(board) == null && board.none { it == ' ' }

    fun bestAiMove(board: CharArray, aiChar: Char = 'O', humanChar: Char = 'X'): Int {
        var bestScore = Int.MIN_VALUE
        var bestIdx = -1
        for (i in 0 until 9) {
            if (board[i] == ' ') {
                board[i] = aiChar
                val score = minimax(board, false, aiChar, humanChar, 0)
                board[i] = ' '
                if (score > bestScore) {
                    bestScore = score
                    bestIdx = i
                }
            }
        }
        return bestIdx
    }

    private fun minimax(b: CharArray, isMax: Boolean, ai: Char, human: Char, depth: Int): Int {
        val w = checkWinner(b)
        if (w == ai) return 10 - depth
        if (w == human) return depth - 10
        if (b.none { it == ' ' }) return 0

        var best = if (isMax) Int.MIN_VALUE else Int.MAX_VALUE
        for (i in 0 until 9) {
            if (b[i] == ' ') {
                b[i] = if (isMax) ai else human
                val score = minimax(b, !isMax, ai, human, depth + 1)
                b[i] = ' '
                best = if (isMax) maxOf(best, score) else minOf(best, score)
            }
        }
        return best
    }
}
