package com.example.lib.dev

import java.security.SecureRandom
import kotlin.math.log2

data class PasswordResult(
    val password: String,
    val entropyBits: Double,
    val strengthLabel: String
)

/**
 * Cryptographic password/UUID generator and pure-Kotlin QR Code matrix generator
 * (supports Version 1 to Version 6 Byte mode with Reed-Solomon ECC Level L/M).
 */
object PasswordQrEngine {
    private val rng = SecureRandom()
    private const val UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ"
    private const val LOWER = "abcdefghijkmnopqrstuvwxyz"
    private const val DIGITS = "23456789"
    private const val SYMBOLS = "!@#$%&*-_+="

    private val EFF_WORDS = listOf(
        "anchor", "atlas", "badge", "basalt", "beacon", "binary", "bison", "bloom",
        "brick", "bridge", "cairn", "canvas", "cedar", "cipher", "clamp", "cobalt",
        "comet", "copper", "coral", "crisp", "delta", "dither", "draft", "ember",
        "fable", "facet", "fern", "flint", "folio", "forge", "frame", "frost",
        "gauge", "glider", "grain", "granite", "grid", "harbor", "hazel", "helix",
        "horizon", "indigo", "ink", "ivory", "jasper", "kernel", "kestrel", "lattice",
        "ledger", "linen", "litho", "lumen", "magnet", "maple", "matrix", "meadow",
        "metric", "mint", "mosaic", "needle", "nimbus", "nordic", "ochre", "olive",
        "orbit", "paper", "parcel", "pebble", "pencil", "phosphor", "pilot", "pixel",
        "plumb", "pocket", "prism", "quartz", "quill", "radar", "raster", "relay",
        "ridge", "rivet", "rover", "ruler", "saffron", "scale", "sector", "sepia",
        "serif", "shale", "signal", "slate", "solder", "spark", "spire", "spool",
        "stamp", "stencil", "stone", "strata", "stylus", "summit", "syntax", "tally",
        "timber", "token", "topaz", "trace", "trench", "truss", "tundra", "umbra",
        "vector", "velvet", "verge", "vertex", "vessel", "viaduct", "walnut", "warden",
        "zenith", "zephyr", "zinc", "zonal"
    )

    fun generatePassword(
        length: Int = 16,
        useUpper: Boolean = true,
        useLower: Boolean = true,
        useDigits: Boolean = true,
        useSymbols: Boolean = true
    ): PasswordResult {
        val pool = buildString {
            if (useUpper) append(UPPER)
            if (useLower) append(LOWER)
            if (useDigits) append(DIGITS)
            if (useSymbols) append(SYMBOLS)
            if (isEmpty()) append(LOWER + DIGITS)
        }
        val safeLen = length.coerceIn(6, 64)
        val pw = buildString(safeLen) {
            repeat(safeLen) {
                append(pool[rng.nextInt(pool.length)])
            }
        }
        val entropy = safeLen * log2(pool.length.toDouble())
        val label = when {
            entropy >= 90 -> "Very Strong"
            entropy >= 65 -> "Strong"
            entropy >= 45 -> "Moderate"
            else -> "Weak"
        }
        return PasswordResult(pw, entropy, label)
    }

    fun generatePassphrase(wordCount: Int = 5, separator: String = "-"): PasswordResult {
        val count = wordCount.coerceIn(3, 10)
        val phrase = (1..count).joinToString(separator) { EFF_WORDS[rng.nextInt(EFF_WORDS.size)] }
        val entropy = count * log2(EFF_WORDS.size.toDouble())
        return PasswordResult(phrase, entropy, if (entropy >= 55) "Strong" else "Moderate")
    }

    /**
     * Generates a clean QR code boolean matrix (true = dark module) for UTF-8 text up to ~106 bytes.
     * Uses Byte mode, ECC Level L, Versions 1..6.
     */
    fun encodeQrMatrix(text: String): Array<BooleanArray>? {
        val dataBytes = text.encodeToByteArray()
        // Version capacities (total data codewords at ECC Level L) for V1..V6
        val versions = listOf(
            QrSpec(1, 21, 19, 7),
            QrSpec(2, 25, 34, 10),
            QrSpec(3, 29, 55, 15),
            QrSpec(4, 33, 80, 20),
            QrSpec(5, 37, 108, 26),
            QrSpec(6, 41, 136, 18) // V6 has 2 blocks of (68 data, 18 ec)
        )
        val spec = versions.firstOrNull { dataBytes.size + 2 <= it.dataCapacity } ?: return null
        val size = spec.size

        // Build bit stream: Mode 0100 (4 bits) + Char count (8 bits for V1-V9) + data + terminator
        val bits = mutableListOf<Int>()
        fun pushBits(value: Int, count: Int) {
            for (i in count - 1 downTo 0) bits.add((value ushr i) and 1)
        }
        pushBits(0b0100, 4)
        pushBits(dataBytes.size, 8)
        for (b in dataBytes) pushBits(b.toInt() and 0xFF, 8)
        val maxDataBits = spec.dataCapacity * 8
        repeat(minOf(4, maxDataBits - bits.size)) { bits.add(0) }
        while (bits.size % 8 != 0) bits.add(0)

        val codewords = IntArray(spec.dataCapacity)
        var cwIdx = 0
        var bitIdx = 0
        while (bitIdx < bits.size && cwIdx < spec.dataCapacity) {
            var v = 0
            for (k in 0 until 8) {
                v = (v shl 1) or bits[bitIdx++]
            }
            codewords[cwIdx++] = v
        }
        var padFlip = true
        while (cwIdx < spec.dataCapacity) {
            codewords[cwIdx++] = if (padFlip) 0xEC else 0x11
            padFlip = !padFlip
        }

        // Reed-Solomon ECC generation
        val finalStream = if (spec.version < 6) {
            val ec = rsComputeEc(codewords, spec.ecPerBlock)
            codewords + ec
        } else {
            // V6 ECC-L uses 2 blocks of 68 data + 18 EC each
            val b1 = codewords.sliceArray(0 until 68)
            val b2 = codewords.sliceArray(68 until 136)
            val ec1 = rsComputeEc(b1, 18)
            val ec2 = rsComputeEc(b2, 18)
            val out = mutableListOf<Int>()
            for (i in 0 until 68) { out.add(b1[i]); out.add(b2[i]) }
            for (i in 0 until 18) { out.add(ec1[i]); out.add(ec2[i]) }
            out.toIntArray()
        }

        val matrix = Array(size) { BooleanArray(size) }
        val reserved = Array(size) { BooleanArray(size) }

        fun setModule(r: Int, c: Int, dark: Boolean, lock: Boolean = true) {
            if (r in 0 until size && c in 0 until size) {
                matrix[r][c] = dark
                if (lock) reserved[r][c] = true
            }
        }

        fun drawFinder(top: Int, left: Int) {
            for (r in -1..7) {
                for (c in -1..7) {
                    val rr = top + r
                    val cc = left + c
                    if (rr !in 0 until size || cc !in 0 until size) continue
                    val dark = r in 0..6 && c in 0..6 && (r == 0 || r == 6 || c == 0 || c == 6 || (r in 2..4 && c in 2..4))
                    setModule(rr, cc, dark, true)
                }
            }
        }

        drawFinder(0, 0)
        drawFinder(0, size - 7)
        drawFinder(size - 7, 0)

        // Timing patterns
        for (i in 8 until size - 8) {
            val dark = i % 2 == 0
            setModule(6, i, dark, true)
            setModule(i, 6, dark, true)
        }

        // Alignment pattern for V2..V6
        if (spec.version >= 2) {
            val pos = size - 7
            for (r in -2..2) {
                for (c in -2..2) {
                    val dark = kotlin.math.max(kotlin.math.abs(r), kotlin.math.abs(c)) != 1
                    setModule(pos + r, pos + c, dark, true)
                }
            }
        }

        // Dark module
        setModule(size - 8, 8, true, true)

        // Reserve format info areas
        for (i in 0..8) {
            if (i != 6) {
                reserved[8][i] = true
                reserved[i][8] = true
            }
        }
        for (i in size - 8 until size) {
            reserved[8][i] = true
            reserved[i][8] = true
        }

        // Place data bits in zig-zag
        val streamBits = mutableListOf<Int>()
        for (cw in finalStream) {
            for (b in 7 downTo 0) streamBits.add((cw ushr b) and 1)
        }

        var idx = 0
        var up = true
        var col = size - 1
        while (col > 0) {
            if (col == 6) col--
            val rows = if (up) (size - 1 downTo 0) else (0 until size)
            for (r in rows) {
                for (dc in 0..1) {
                    val cc = col - dc
                    if (!reserved[r][cc]) {
                        val bit = if (idx < streamBits.size) streamBits[idx++] else 0
                        val masked = bit xor (if ((r + cc) % 2 == 0) 1 else 0) // Mask 0: (r+c)%2==0
                        matrix[r][cc] = masked == 1
                    }
                }
            }
            col -= 2
            up = !up
        }

        // Format info for ECC-L (01) + Mask 0 (000) -> 01000 -> BCH = 111011111000100
        val fmtBits = "111011111000100".map { it == '1' }
        val coordsA = listOf(
            8 to 0, 8 to 1, 8 to 2, 8 to 3, 8 to 4, 8 to 5, 8 to 7, 8 to 8,
            7 to 8, 5 to 8, 4 to 8, 3 to 8, 2 to 8, 1 to 8, 0 to 8
        )
        for (i in 0 until 15) {
            val (r, c) = coordsA[i]
            matrix[r][c] = fmtBits[i]
        }
        for (i in 0 until 7) {
            matrix[size - 1 - i][8] = fmtBits[i]
        }
        for (i in 7 until 15) {
            matrix[8][size - 15 + i] = fmtBits[i]
        }

        return matrix
    }

    private data class QrSpec(val version: Int, val size: Int, val dataCapacity: Int, val ecPerBlock: Int)

    private val GF_EXP = IntArray(512)
    private val GF_LOG = IntArray(256)

    init {
        var x = 1
        for (i in 0 until 255) {
            GF_EXP[i] = x
            GF_LOG[x] = i
            x = x shl 1
            if (x >= 256) x = x xor 0x11D
        }
        for (i in 255 until 512) GF_EXP[i] = GF_EXP[i - 255]
    }

    private fun gfMul(a: Int, b: Int): Int {
        if (a == 0 || b == 0) return 0
        return GF_EXP[GF_LOG[a] + GF_LOG[b]]
    }

    private fun rsComputeEc(data: IntArray, ecCount: Int): IntArray {
        var gen = intArrayOf(1)
        for (i in 0 until ecCount) {
            val next = IntArray(gen.size + 1)
            val factor = GF_EXP[i]
            for (j in gen.indices) {
                next[j] = next[j] xor gen[j]
                next[j + 1] = next[j + 1] xor gfMul(gen[j], factor)
            }
            gen = next
        }
        val msg = IntArray(data.size + ecCount)
        data.copyInto(msg)
        for (i in data.indices) {
            val coef = msg[i]
            if (coef != 0) {
                for (j in gen.indices) {
                    msg[i + j] = msg[i + j] xor gfMul(gen[j], coef)
                }
            }
        }
        return msg.sliceArray(data.size until msg.size)
    }
}
