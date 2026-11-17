package com.example.lib.crypto

import java.io.ByteArrayOutputStream

/**
 * Pure-Kotlin RFC 4648 Base64 encoder/decoder safe across Android API 24+ and plain JVM unit tests.
 */
object PureBase64 {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    private val DECODE_TABLE = IntArray(128) { -1 }.apply {
        for (i in ALPHABET.indices) this[ALPHABET[i].code] = i
        this['-'.code] = 62
        this['_'.code] = 63
    }

    fun encode(data: ByteArray): String {
        if (data.isEmpty()) return ""
        val out = StringBuilder(((data.size + 2) / 3) * 4)
        var i = 0
        while (i < data.size) {
            val b0 = data[i].toInt() and 0xFF
            val has1 = i + 1 < data.size
            val has2 = i + 2 < data.size
            val b1 = if (has1) data[i + 1].toInt() and 0xFF else 0
            val b2 = if (has2) data[i + 2].toInt() and 0xFF else 0
            val triple = (b0 shl 16) or (b1 shl 8) or b2
            out.append(ALPHABET[(triple ushr 18) and 0x3F])
            out.append(ALPHABET[(triple ushr 12) and 0x3F])
            out.append(if (has1) ALPHABET[(triple ushr 6) and 0x3F] else '=')
            out.append(if (has2) ALPHABET[triple and 0x3F] else '=')
            i += 3
        }
        return out.toString()
    }

    fun decode(input: String): ByteArray {
        val out = ByteArrayOutputStream()
        var acc = 0
        var bits = 0
        for (ch in input) {
            if (ch == '=') break
            if (ch.code >= 128) continue
            val val6 = DECODE_TABLE[ch.code]
            if (val6 < 0) continue
            acc = (acc shl 6) or val6
            bits += 6
            if (bits >= 8) {
                bits -= 8
                out.write((acc ushr bits) and 0xFF)
            }
        }
        return out.toByteArray()
    }
}
