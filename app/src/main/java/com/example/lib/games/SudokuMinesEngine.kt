package com.example.lib.games

import kotlin.random.Random

enum class SudokuDifficulty(val label: String, val removals: Int) {
    EASY("Easy", 34),
    MEDIUM("Medium", 45),
    HARD("Hard", 54)
}

data class SudokuPuzzle(
    val puzzle: IntArray, // 81 cells, 0 = blank
    val solution: IntArray // 81 solved cells
)

object SudokuEngine {
    fun generate(difficulty: SudokuDifficulty, rng: Random = Random.Default): SudokuPuzzle {
        val grid = IntArray(81)
        solveGrid(grid, rng)
        val solution = grid.copyOf()
        val puzzle = grid.copyOf()
        val indices = (0 until 81).shuffled(rng)
        for (i in 0 until difficulty.removals.coerceAtMost(60)) {
            puzzle[indices[i]] = 0
        }
        return SudokuPuzzle(puzzle, solution)
    }

    fun isValidMove(grid: IntArray, idx: Int, num: Int): Boolean {
        val r = idx / 9
        val c = idx % 9
        for (i in 0 until 9) {
            if (i != c && grid[r * 9 + i] == num) return false
            if (i != r && grid[i * 9 + c] == num) return false
        }
        val br = (r / 3) * 3
        val bc = (c / 3) * 3
        for (rr in br until br + 3) {
            for (cc in bc until bc + 3) {
                val p = rr * 9 + cc
                if (p != idx && grid[p] == num) return false
            }
        }
        return true
    }

    private fun solveGrid(grid: IntArray, rng: Random): Boolean {
        val emptyIdx = grid.indexOfFirst { it == 0 }
        if (emptyIdx < 0) return true
        val nums = (1..9).shuffled(rng)
        for (n in nums) {
            if (isValidMove(grid, emptyIdx, n)) {
                grid[emptyIdx] = n
                if (solveGrid(grid, rng)) return true
                grid[emptyIdx] = 0
            }
        }
        return false
    }
}

data class MinesweeperCell(
    val isMine: Boolean = false,
    val adjacentMines: Int = 0,
    val revealed: Boolean = false,
    val flagged: Boolean = false
)

data class MinesweeperState(
    val rows: Int = 9,
    val cols: Int = 9,
    val minesCount: Int = 10,
    val cells: List<MinesweeperCell> = emptyList(),
    val generated: Boolean = false,
    val gameOver: Boolean = false,
    val won: Boolean = false
)

object MinesweeperEngine {
    fun createBlank(rows: Int = 9, cols: Int = 9, mines: Int = 10): MinesweeperState {
        return MinesweeperState(
            rows = rows,
            cols = cols,
            minesCount = mines,
            cells = List(rows * cols) { MinesweeperCell() }
        )
    }

    fun reveal(state: MinesweeperState, idx: Int, rng: Random = Random.Default): MinesweeperState {
        if (state.gameOver || state.won) return state
        var cur = state
        if (!cur.generated) {
            cur = placeMines(cur, safeIdx = idx, rng = rng)
        }
        val mutable = cur.cells.toMutableList()
        if (mutable[idx].flagged || mutable[idx].revealed) return cur
        if (mutable[idx].isMine) {
            for (i in mutable.indices) {
                if (mutable[i].isMine) mutable[i] = mutable[i].copy(revealed = true)
            }
            return cur.copy(cells = mutable, gameOver = true)
        }
        floodReveal(mutable, cur.rows, cur.cols, idx)
        val unrevealedSafe = mutable.count { !it.isMine && !it.revealed }
        return cur.copy(cells = mutable, won = unrevealedSafe == 0)
    }

    fun toggleFlag(state: MinesweeperState, idx: Int): MinesweeperState {
        if (state.gameOver || state.won || state.cells[idx].revealed) return state
        val mutable = state.cells.toMutableList()
        mutable[idx] = mutable[idx].copy(flagged = !mutable[idx].flagged)
        return state.copy(cells = mutable)
    }

    private fun placeMines(state: MinesweeperState, safeIdx: Int, rng: Random): MinesweeperState {
        val total = state.rows * state.cols
        val candidates = (0 until total).filter { it != safeIdx }.shuffled(rng).take(state.minesCount).toSet()
        val list = MutableList(total) { i -> MinesweeperCell(isMine = i in candidates) }
        for (r in 0 until state.rows) {
            for (c in 0 until state.cols) {
                val i = r * state.cols + c
                if (!list[i].isMine) {
                    var count = 0
                    for (dr in -1..1) for (dc in -1..1) {
                        val nr = r + dr; val nc = c + dc
                        if (nr in 0 until state.rows && nc in 0 until state.cols) {
                            if (list[nr * state.cols + nc].isMine) count++
                        }
                    }
                    list[i] = list[i].copy(adjacentMines = count)
                }
            }
        }
        return state.copy(cells = list, generated = true)
    }

    private fun floodReveal(cells: MutableList<MinesweeperCell>, rows: Int, cols: Int, start: Int) {
        val queue = ArrayDeque<Int>()
        queue.add(start)
        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            if (cells[cur].revealed || cells[cur].flagged) continue
            cells[cur] = cells[cur].copy(revealed = true)
            if (cells[cur].adjacentMines == 0) {
                val r = cur / cols; val c = cur % cols
                for (dr in -1..1) for (dc in -1..1) {
                    val nr = r + dr; val nc = c + dc
                    if (nr in 0 until rows && nc in 0 until cols) {
                        val ni = nr * cols + nc
                        if (!cells[ni].revealed && !cells[ni].isMine) queue.add(ni)
                    }
                }
            }
        }
    }
}

data class QuizQuestion(
    val country: String,
    val capital: String,
    val region: String,
    val options: List<String>
)

object WorldQuizBank {
    private val CAPITALS = listOf(
        Triple("Japan", "Tokyo", "Asia"),
        Triple("France", "Paris", "Europe"),
        Triple("Brazil", "Brasilia", "South America"),
        Triple("Canada", "Ottawa", "North America"),
        Triple("Australia", "Canberra", "Oceania"),
        Triple("India", "New Delhi", "Asia"),
        Triple("South Korea", "Seoul", "Asia"),
        Triple("Germany", "Berlin", "Europe"),
        Triple("Italy", "Rome", "Europe"),
        Triple("Spain", "Madrid", "Europe"),
        Triple("Portugal", "Lisbon", "Europe"),
        Triple("Switzerland", "Bern", "Europe"),
        Triple("Norway", "Oslo", "Europe"),
        Triple("Sweden", "Stockholm", "Europe"),
        Triple("Finland", "Helsinki", "Europe"),
        Triple("Egypt", "Cairo", "Africa"),
        Triple("Kenya", "Nairobi", "Africa"),
        Triple("Morocco", "Rabat", "Africa"),
        Triple("Nigeria", "Abuja", "Africa"),
        Triple("Argentina", "Buenos Aires", "South America"),
        Triple("Chile", "Santiago", "South America"),
        Triple("Mexico", "Mexico City", "North America"),
        Triple("Turkey", "Ankara", "Europe/Asia"),
        Triple("Thailand", "Bangkok", "Asia"),
        Triple("Vietnam", "Hanoi", "Asia"),
        Triple("Indonesia", "Jakarta", "Asia"),
        Triple("New Zealand", "Wellington", "Oceania"),
        Triple("Poland", "Warsaw", "Europe"),
        Triple("Greece", "Athens", "Europe"),
        Triple("Netherlands", "Amsterdam", "Europe"),
        Triple("United Kingdom", "London", "Europe"),
        Triple("United States", "Washington, D.C.", "North America")
    )

    fun nextQuestion(rng: Random = Random.Default): QuizQuestion {
        val target = CAPITALS[rng.nextInt(CAPITALS.size)]
        val distractors = CAPITALS.filter { it.second != target.second }.shuffled(rng).take(3).map { it.second }
        val options = (distractors + target.second).shuffled(rng)
        return QuizQuestion(target.first, target.second, target.third, options)
    }

    val HANGMAN_WORDS = listOf(
        "ALGORITHM", "TYPOGRAPHY", "MICROPHONE", "ENCRYPTION", "CERTIFICATE",
        "KINETIC", "BAROMETER", "PENDULUM", "SPECTRUM", "HEXAGON",
        "TERMINAL", "LOCALHOST", "WAVEFORM", "CAPACITOR", "QUANTUM",
        "MINIMALIST", "CARTOGRAPHY", "THERMOMETER", "SYNTHESIZER", "TELESCOPE"
    )
}
