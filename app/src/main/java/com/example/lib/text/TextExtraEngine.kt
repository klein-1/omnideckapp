package com.example.lib.text

import org.json.JSONArray
import org.json.JSONObject
import java.net.IDN
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import java.util.UUID
import kotlin.math.log2

enum class CipherKind(val label: String) {
    CAESAR("Caesar"),
    VIGENERE("Vigenère"),
    ATBASH("Atbash"),
    RAIL_FENCE("Rail Fence")
}

data class AsymKeyBundle(
    val algorithm: String,
    val publicKeyPem: String,
    val privateKeyPem: String,
    val publicKey: PublicKey,
    val privateKey: PrivateKey
)

object TextExtraEngine {
    private val rng = SecureRandom()

    // ==================== 1. CLASSICAL CIPHERS ====================
    fun caesar(input: String, shift: Int, decrypt: Boolean = false): String {
        val s = ((if (decrypt) -shift else shift) % 26 + 26) % 26
        return buildString(input.length) {
            for (ch in input) {
                when (ch) {
                    in 'A'..'Z' -> append(('A'.code + (ch.code - 'A'.code + s) % 26).toChar())
                    in 'a'..'z' -> append(('a'.code + (ch.code - 'a'.code + s) % 26).toChar())
                    else -> append(ch)
                }
            }
        }
    }

    fun caesarBruteForce(cipher: String): List<Pair<Int, String>> =
        (1..25).map { shift -> shift to caesar(cipher, shift, decrypt = true) }

    fun vigenere(input: String, key: String, decrypt: Boolean = false): String {
        val cleanKey = key.uppercase().filter { it in 'A'..'Z' }
        if (cleanKey.isEmpty()) return input
        var ki = 0
        return buildString(input.length) {
            for (ch in input) {
                if (ch in 'A'..'Z' || ch in 'a'..'z') {
                    val base = if (ch.isUpperCase()) 'A'.code else 'a'.code
                    val k = cleanKey[ki % cleanKey.length].code - 'A'.code
                    val delta = if (decrypt) 26 - k else k
                    append((base + (ch.code - base + delta) % 26).toChar())
                    ki++
                } else {
                    append(ch)
                }
            }
        }
    }

    fun atbash(input: String): String = buildString(input.length) {
        for (ch in input) {
            when (ch) {
                in 'A'..'Z' -> append(('Z'.code - (ch.code - 'A'.code)).toChar())
                in 'a'..'z' -> append(('z'.code - (ch.code - 'a'.code)).toChar())
                else -> append(ch)
            }
        }
    }

    fun railFence(input: String, rails: Int, decrypt: Boolean = false): String {
        val rCount = rails.coerceIn(2, 12)
        if (input.length <= 1) return input
        if (!decrypt) {
            val buckets = Array(rCount) { StringBuilder() }
            var r = 0
            var dir = 1
            for (ch in input) {
                buckets[r].append(ch)
                if (r == 0) dir = 1 else if (r == rCount - 1) dir = -1
                r += dir
            }
            return buckets.joinToString("") { it.toString() }
        } else {
            val pattern = IntArray(input.length)
            var r = 0
            var dir = 1
            for (i in input.indices) {
                pattern[i] = r
                if (r == 0) dir = 1 else if (r == rCount - 1) dir = -1
                r += dir
            }
            val railChars = Array(rCount) { mutableListOf<Char>() }
            var idx = 0
            for (targetRail in 0 until rCount) {
                for (p in pattern) {
                    if (p == targetRail && idx < input.length) {
                        railChars[targetRail].add(input[idx++])
                    }
                }
            }
            val railPos = IntArray(rCount)
            return buildString(input.length) {
                for (p in pattern) {
                    val list = railChars[p]
                    if (railPos[p] < list.size) append(list[railPos[p]++])
                }
            }
        }
    }

    // ==================== 2. ZERO-WIDTH STEGANOGRAPHY ====================
    private const val ZW_ZERO = '\u200B'
    private const val ZW_ONE = '\u200C'
    private const val ZW_SEP = '\u200D'
    private const val ZW_GUARD = '\uFEFF'

    fun embedZeroWidth(carrier: String, secret: String): String {
        if (secret.isEmpty()) return carrier
        val encodedSecret = buildString {
            append(ZW_GUARD)
            secret.toByteArray(Charsets.UTF_8).forEachIndexed { i, b ->
                val unsigned = b.toInt() and 0xFF
                for (bit in 7 downTo 0) {
                    append(if (((unsigned ushr bit) and 1) == 1) ZW_ONE else ZW_ZERO)
                }
                if (i < secret.toByteArray(Charsets.UTF_8).lastIndex) append(ZW_SEP)
            }
            append(ZW_GUARD)
        }
        val cleanCarrier = carrier.ifBlank { "Shared note." }
        val splitAt = (cleanCarrier.length / 2).coerceAtLeast(1)
        return cleanCarrier.substring(0, splitAt) + encodedSecret + cleanCarrier.substring(splitAt)
    }

    fun extractZeroWidth(stegoText: String): String {
        val bitsOnly = stegoText.filter { it == ZW_ZERO || it == ZW_ONE }
        if (bitsOnly.length < 8) return ""
        val byteCount = bitsOnly.length / 8
        val out = ByteArray(byteCount)
        for (i in 0 until byteCount) {
            var v = 0
            for (b in 0 until 8) {
                v = (v shl 1) or if (bitsOnly[i * 8 + b] == ZW_ONE) 1 else 0
            }
            out[i] = v.toByte()
        }
        return out.toString(Charsets.UTF_8)
    }

    // ==================== 3. SLUG, HTML, UNICODE, IDN ====================
    fun toSlug(input: String): String =
        input.lowercase()
            .replace(Regex("[^a-z0-9\\s-]"), " ")
            .trim()
            .replace(Regex("[\\s-]+"), "-")

    fun encodeHtmlEntities(input: String): String = buildString {
        for (ch in input) {
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> if (ch.code > 127) append("&#${ch.code};") else append(ch)
            }
        }
    }

    fun decodeHtmlEntities(input: String): String {
        var s = input
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&nbsp;", " ")
        s = Regex("&#(\\d+);").replace(s) { m ->
            val cp = m.groupValues[1].toIntOrNull()
            if (cp != null && cp in 1..0x10FFFF) String(Character.toChars(cp)) else m.value
        }
        return s
    }

    fun escapeUnicode(input: String): String = buildString {
        for (ch in input) {
            if (ch.code in 32..126) append(ch)
            else append("\\u%04x".format(ch.code))
        }
    }

    fun unescapeUnicode(input: String): String =
        Regex("\\\\u([0-9a-fA-F]{4})").replace(input) { m ->
            m.groupValues[1].toInt(16).toChar().toString()
        }

    fun toPunycodeDomain(domain: String): String =
        runCatching { IDN.toASCII(domain.trim()) }.getOrDefault(domain)

    fun fromPunycodeDomain(domain: String): String =
        runCatching { IDN.toUnicode(domain.trim()) }.getOrDefault(domain)

    // ==================== 4. LINE MASTER & EXTRACTORS ====================
    fun processLines(input: String, mode: String): String {
        val lines = input.lines()
        return when (mode) {
            "SORT_AZ" -> lines.sorted().joinToString("\n")
            "SORT_ZA" -> lines.sortedDescending().joinToString("\n")
            "SORT_LEN" -> lines.sortedBy { it.length }.joinToString("\n")
            "SHUFFLE" -> lines.shuffled(rng).joinToString("\n")
            "DEDUPE" -> lines.distinct().joinToString("\n")
            "TRIM" -> lines.map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")
            "NUMBER" -> lines.mapIndexed { idx, l -> "${idx + 1}. $l" }.joinToString("\n")
            "REVERSE" -> lines.reversed().joinToString("\n")
            "EMAILS" -> Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}").findAll(input).map { it.value }.distinct().joinToString("\n")
            "URLS" -> Regex("https?://[^\\s\"'<>]+").findAll(input).map { it.value }.distinct().joinToString("\n")
            "IPS" -> Regex("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b").findAll(input).map { it.value }.distinct().joinToString("\n")
            else -> input
        }
    }

    // ==================== 5. NATO PHONETIC, BRAILLE, LEET ====================
    private val NATO_MAP = mapOf(
        'A' to "Alfa", 'B' to "Bravo", 'C' to "Charlie", 'D' to "Delta", 'E' to "Echo",
        'F' to "Foxtrot", 'G' to "Golf", 'H' to "Hotel", 'I' to "India", 'J' to "Juliett",
        'K' to "Kilo", 'L' to "Lima", 'M' to "Mike", 'N' to "November", 'O' to "Oscar",
        'P' to "Papa", 'Q' to "Quebec", 'R' to "Romeo", 'S' to "Sierra", 'T' to "Tango",
        'U' to "Uniform", 'V' to "Victor", 'W' to "Whiskey", 'X' to "X-ray", 'Y' to "Yankee",
        'Z' to "Zulu", '0' to "Zero", '1' to "One", '2' to "Two", '3' to "Three",
        '4' to "Four", '5' to "Five", '6' to "Six", '7' to "Seven", '8' to "Eight", '9' to "Niner"
    )

    private val BRAILLE_MAP = mapOf(
        'A' to '⠁', 'B' to '⠃', 'C' to '⠉', 'D' to '⠙', 'E' to '⠑', 'F' to '⠋', 'G' to '⠛',
        'H' to '⠓', 'I' to '⠊', 'J' to '⠚', 'K' to '⠅', 'L' to '⠇', 'M' to '⠍', 'N' to '⠝',
        'O' to '⠕', 'P' to '⠏', 'Q' to '⠟', 'R' to '⠗', 'S' to '⠎', 'T' to '⠞', 'U' to '⠥',
        'V' to '⠧', 'W' to '⠺', 'X' to '⠭', 'Y' to '⠽', 'Z' to '⠵', ' ' to ' '
    )

    fun toNato(input: String): String =
        input.uppercase().mapNotNull { ch ->
            when {
                ch in NATO_MAP -> NATO_MAP[ch]
                ch == ' ' -> "/"
                else -> null
            }
        }.joinToString(" ")

    fun toBraille(input: String): String =
        input.uppercase().map { BRAILLE_MAP[it] ?: it }.joinToString("")

    fun toLeetspeak(input: String): String = buildString {
        for (ch in input) {
            append(
                when (ch.uppercaseChar()) {
                    'A' -> '4'; 'E' -> '3'; 'G' -> '6'; 'I' -> '1'; 'O' -> '0'
                    'S' -> '5'; 'T' -> '7'; 'B' -> '8'; 'Z' -> '2'
                    else -> ch
                }
            )
        }
    }

    // ==================== 6. CSV <-> JSON <-> MARKDOWN TABLE ====================
    fun csvToJson(csv: String): Result<String> = runCatching {
        val rows = parseCsvRows(csv)
        require(rows.size >= 2) { "Provide header row + at least 1 data row." }
        val headers = rows.first()
        val arr = JSONArray()
        rows.drop(1).forEach { row ->
            val obj = JSONObject()
            headers.forEachIndexed { idx, h -> obj.put(h.trim(), row.getOrElse(idx) { "" }) }
            arr.put(obj)
        }
        arr.toString(2)
    }

    fun jsonToCsv(jsonStr: String): Result<String> = runCatching {
        val arr = JSONArray(jsonStr.trim())
        require(arr.length() > 0) { "JSON array is empty." }
        val keys = linkedSetOf<String>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val it = obj.keys()
            while (it.hasNext()) keys.add(it.next())
        }
        val keyList = keys.toList()
        buildString {
            appendLine(keyList.joinToString(","))
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                appendLine(keyList.joinToString(",") { k ->
                    val raw = obj.opt(k)?.toString().orEmpty()
                    if (raw.contains(',') || raw.contains('"')) "\"${raw.replace("\"", "\"\"")}\"" else raw
                })
            }
        }.trim()
    }

    fun csvToMarkdownTable(csv: String): Result<String> = runCatching {
        val rows = parseCsvRows(csv)
        require(rows.isNotEmpty()) { "CSV is empty." }
        val headers = rows.first()
        buildString {
            appendLine("| " + headers.joinToString(" | ") + " |")
            appendLine("| " + headers.joinToString(" | ") { "---" } + " |")
            rows.drop(1).forEach { r ->
                val padded = headers.indices.map { i -> r.getOrElse(i) { "" } }
                appendLine("| " + padded.joinToString(" | ") + " |")
            }
        }.trim()
    }

    private fun parseCsvRows(csv: String): List<List<String>> =
        csv.lines().filter { it.isNotBlank() }.map { line ->
            val fields = mutableListOf<String>()
            val cur = StringBuilder()
            var inQuotes = false
            for (ch in line) {
                when {
                    ch == '"' -> inQuotes = !inQuotes
                    ch == ',' && !inQuotes -> { fields.add(cur.toString().trim()); cur.clear() }
                    else -> cur.append(ch)
                }
            }
            fields.add(cur.toString().trim())
            fields
        }

    // ==================== 7. ASYMMETRIC KEYS & SIGNATURES ====================
    private fun pemWrap(label: String, bytes: ByteArray): String {
        val b64 = Base64.getEncoder().encodeToString(bytes).chunked(64).joinToString("\n")
        return "-----BEGIN $label-----\n$b64\n-----END $label-----"
    }

    fun generateAsymKeyPair(useEcdsa: Boolean = true): AsymKeyBundle {
        val kpg = if (useEcdsa) {
            KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1"), rng) }
        } else {
            KeyPairGenerator.getInstance("RSA").apply { initialize(2048, rng) }
        }
        val kp = kpg.generateKeyPair()
        val label = if (useEcdsa) "ECDSA P-256" else "RSA-2048"
        return AsymKeyBundle(
            algorithm = label,
            publicKeyPem = pemWrap("PUBLIC KEY", kp.public.encoded),
            privateKeyPem = pemWrap("PRIVATE KEY", kp.private.encoded),
            publicKey = kp.public,
            privateKey = kp.private
        )
    }

    fun signPayload(message: String, privateKey: PrivateKey, isEc: Boolean): String {
        val sig = Signature.getInstance(if (isEc) "SHA256withECDSA" else "SHA256withRSA")
        sig.initSign(privateKey, rng)
        sig.update(message.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(sig.sign())
    }

    fun verifyPayload(message: String, sigBase64: String, publicKey: PublicKey, isEc: Boolean): Boolean =
        runCatching {
            val sigBytes = Base64.getDecoder().decode(sigBase64.trim())
            val sig = Signature.getInstance(if (isEc) "SHA256withECDSA" else "SHA256withRSA")
            sig.initVerify(publicKey)
            sig.update(message.toByteArray(Charsets.UTF_8))
            sig.verify(sigBytes)
        }.getOrDefault(false)

    // ==================== 8. BATCH ID & ENTROPY ====================
    private const val CROCKFORD = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    private const val NANO_ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz_-"

    fun generateUlid(timeMs: Long = System.currentTimeMillis()): String {
        val sb = StringBuilder(26)
        var t = timeMs
        val timeChars = CharArray(10)
        for (i in 9 downTo 0) {
            timeChars[i] = CROCKFORD[(t and 31L).toInt()]
            t = t ushr 5
        }
        sb.append(timeChars)
        repeat(16) { sb.append(CROCKFORD[rng.nextInt(32)]) }
        return sb.toString()
    }

    fun generateNanoId(len: Int = 21): String =
        buildString(len) { repeat(len) { append(NANO_ALPHABET[rng.nextInt(NANO_ALPHABET.length)]) } }

    fun generateHexToken(byteCount: Int = 16): String {
        val bytes = ByteArray(byteCount.coerceIn(4, 64))
        rng.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun generateUuidV4(): String = UUID.randomUUID().toString()

    fun shannonEntropyBitsPerChar(text: String): Double {
        if (text.isEmpty()) return 0.0
        val counts = text.groupingBy { it }.eachCount()
        val n = text.length.toDouble()
        return counts.values.sumOf { c ->
            val p = c / n
            -p * log2(p)
        }
    }

    fun sha256Hex(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
