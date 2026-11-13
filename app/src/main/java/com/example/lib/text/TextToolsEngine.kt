package com.example.lib.text

import android.util.Base64
import java.net.URLDecoder
import java.net.URLEncoder

data class DiffLine(
    val kind: Char, // '=', '+', '-'
    val lineNumber: Int,
    val text: String
)

object TextToolsEngine {
    private val MORSE_MAP = mapOf(
        'A' to ".-", 'B' to "-...", 'C' to "-.-.", 'D' to "-..", 'E' to ".",
        'F' to "..-.", 'G' to "--.", 'H' to "....", 'I' to "..", 'J' to ".---",
        'K' to "-.-", 'L' to ".-..", 'M' to "--", 'N' to "-.", 'O' to "---",
        'P' to ".--.", 'Q' to "--.-", 'R' to ".-.", 'S' to "...", 'T' to "-",
        'U' to "..-", 'V' to "...-", 'W' to ".--", 'X' to "-..-", 'Y' to "-.--",
        'Z' to "--..",
        '0' to "-----", '1' to ".----", '2' to "..---", '3' to "...--", '4' to "....-",
        '5' to ".....", '6' to "-....", '7' to "--...", '8' to "---..", '9' to "----.",
        '.' to ".-.-.-", ',' to "--..--", '?' to "..--..", '/' to "-..-.", '-' to "-....-",
        '@' to ".--.-."
    )
    private val REVERSE_MORSE = MORSE_MAP.entries.associate { (k, v) -> v to k }

    fun encodeMorse(text: String): String {
        return text.uppercase().trim().split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .joinToString(" / ") { word ->
                word.mapNotNull { ch -> MORSE_MAP[ch] }.joinToString(" ")
            }
    }

    fun decodeMorse(morse: String): String {
        val words = morse.trim().split(Regex("\\s*/\\s*|\\s{3,}"))
        return words.joinToString(" ") { w ->
            w.trim().split(Regex("\\s+")).mapNotNull { code -> REVERSE_MORSE[code] }.joinToString("")
        }
    }

    fun toBinary(text: String): String =
        text.encodeToByteArray().joinToString(" ") { b ->
            Integer.toBinaryString(b.toInt() and 0xFF).padStart(8, '0')
        }

    fun fromBinary(binary: String): String {
        val tokens = binary.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val bytes = ByteArray(tokens.size)
        for (i in tokens.indices) {
            val v = tokens[i].toIntOrNull(2) ?: throw IllegalArgumentException("Invalid binary token '${tokens[i]}'")
            bytes[i] = v.toByte()
        }
        return bytes.decodeToString()
    }

    fun toBase64(text: String): String = try {
        Base64.encodeToString(text.encodeToByteArray(), Base64.NO_WRAP)
    } catch (_: Throwable) {
        java.util.Base64.getEncoder().encodeToString(text.encodeToByteArray())
    }

    fun fromBase64(b64: String): String = try {
        Base64.decode(b64.trim(), Base64.DEFAULT).decodeToString()
    } catch (_: Throwable) {
        java.util.Base64.getDecoder().decode(b64.trim()).decodeToString()
    }

    fun urlEncode(text: String): String = URLEncoder.encode(text, "UTF-8")

    fun urlDecode(text: String): String = URLDecoder.decode(text, "UTF-8")

    fun rot13(text: String): String = buildString(text.length) {
        for (c in text) {
            when (c) {
                in 'a'..'z' -> append(((c - 'a' + 13) % 26 + 'a'.code).toChar())
                in 'A'..'Z' -> append(((c - 'A' + 13) % 26 + 'A'.code).toChar())
                else -> append(c)
            }
        }
    }

    fun diffLines(leftText: String, rightText: String, ignoreCase: Boolean = false): List<DiffLine> {
        val a = leftText.lines()
        val b = rightText.lines()
        val m = a.size
        val n = b.size
        // LCS DP table
        val dp = Array(m + 1) { IntArray(n + 1) }
        for (i in m - 1 downTo 0) {
            for (j in n - 1 downTo 0) {
                val eq = if (ignoreCase) a[i].equals(b[j], ignoreCase = true) else a[i] == b[j]
                dp[i][j] = if (eq) dp[i + 1][j + 1] + 1 else maxOf(dp[i + 1][j], dp[i][j + 1])
            }
        }
        val out = mutableListOf<DiffLine>()
        var i = 0
        var j = 0
        var lineNum = 1
        while (i < m && j < n) {
            val eq = if (ignoreCase) a[i].equals(b[j], ignoreCase = true) else a[i] == b[j]
            if (eq) {
                out += DiffLine('=', lineNum++, a[i])
                i++; j++
            } else if (dp[i + 1][j] >= dp[i][j + 1]) {
                out += DiffLine('-', lineNum++, a[i++])
            } else {
                out += DiffLine('+', lineNum++, b[j++])
            }
        }
        while (i < m) out += DiffLine('-', lineNum++, a[i++])
        while (j < n) out += DiffLine('+', lineNum++, b[j++])
        return out
    }

    fun topWords(text: String, limit: Int = 6): List<Pair<String, Int>> {
        val counts = mutableMapOf<String, Int>()
        Regex("[A-Za-z0-9']+").findAll(text.lowercase()).forEach { match ->
            val w = match.value
            if (w.length >= 2) counts[w] = (counts[w] ?: 0) + 1
        }
        return counts.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(limit)
            .map { it.key to it.value }
    }
}
