package com.example.lib.dev

import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

object ThreadSplitterEngine {
    fun splitThread(input: String, maxChars: Int = 280, includeCounter: Boolean = true): List<String> {
        val clean = input.trim()
        if (clean.isEmpty()) return emptyList()
        val reserve = if (includeCounter) 8 else 0
        val limit = (maxChars - reserve).coerceAtLeast(40)
        val words = clean.split(Regex("\\s+"))
        val chunks = mutableListOf<String>()
        val cur = StringBuilder()

        for (w in words) {
            if (cur.isEmpty()) {
                if (w.length <= limit) cur.append(w)
                else {
                    w.chunked(limit).forEach { part -> chunks.add(part) }
                }
            } else if (cur.length + 1 + w.length <= limit) {
                cur.append(' ').append(w)
            } else {
                chunks.add(cur.toString())
                cur.clear()
                if (w.length <= limit) cur.append(w)
                else chunks.addAll(w.chunked(limit))
            }
        }
        if (cur.isNotEmpty()) chunks.add(cur.toString())
        if (!includeCounter || chunks.size <= 1) return chunks
        return chunks.mapIndexed { idx, s -> "$s (${idx + 1}/${chunks.size})" }
    }
}

object SerpPreviewEngine {
    // Approximate proportional pixel width at Google SERP 20px title / 14px snippet
    fun estimateTitlePx(title: String): Int {
        var px = 0.0
        for (ch in title) {
            px += when {
                ch.isUpperCase() -> 12.2
                ch in "mwMW@%" -> 14.5
                ch in "iljtI1|.,:;'! " -> 5.2
                else -> 9.6
            }
        }
        return px.toInt()
    }

    fun estimateMetaPx(meta: String): Int {
        var px = 0.0
        for (ch in meta) {
            px += when {
                ch.isUpperCase() -> 8.6
                ch in "mwMW" -> 10.4
                ch in "iljtI1.,:;'! " -> 3.8
                else -> 6.8
            }
        }
        return px.toInt()
    }
}

object SqlFormatterEngine {
    private val CLAUSE_KEYWORDS = listOf(
        "SELECT", "FROM", "WHERE", "INNER JOIN", "LEFT JOIN", "RIGHT JOIN", "FULL JOIN", "JOIN",
        "ON", "GROUP BY", "HAVING", "ORDER BY", "LIMIT", "OFFSET", "INSERT INTO", "VALUES",
        "UPDATE", "SET", "DELETE FROM", "CREATE TABLE", "ALTER TABLE", "UNION ALL", "UNION", "AND", "OR"
    )

    fun formatSql(raw: String): String {
        var s = raw.trim().replace(Regex("\\s+"), " ")
        if (s.isEmpty()) return ""
        for (kw in CLAUSE_KEYWORDS.sortedByDescending { it.length }) {
            val rx = Regex("(?i)\\b${Regex.escape(kw)}\\b")
            s = s.replace(rx) { m -> "\n" + m.value.uppercase() }
        }
        return s.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n") { line ->
                if (line.startsWith("AND ") || line.startsWith("OR ") || line.startsWith("ON ")) "  $line"
                else line
            }
    }

    fun lintSql(raw: String): List<String> {
        val issues = mutableListOf<String>()
        val s = raw.trim()
        if (s.isEmpty()) return listOf("SQL input is empty.")
        var depth = 0
        var inSingle = false
        for (ch in s) {
            if (ch == '\'') inSingle = !inSingle
            if (!inSingle) {
                if (ch == '(') depth++
                else if (ch == ')') {
                    depth--
                    if (depth < 0) issues.add("Unexpected closing parenthesis ')'.")
                }
            }
        }
        if (inSingle) issues.add("Unclosed string literal (').")
        if (depth != 0) issues.add("Unbalanced parentheses (net depth=$depth).")
        if (Regex("(?i)\\bSELECT\\s+\\*").containsMatchIn(s)) {
            issues.add("Advisory: Avoid SELECT * in production queries; project explicit columns.")
        }
        if (Regex("(?i)\\b(DELETE|UPDATE)\\b").containsMatchIn(s) && !Regex("(?i)\\bWHERE\\b").containsMatchIn(s)) {
            issues.add("Warning: DELETE/UPDATE without a WHERE clause affects all rows.")
        }
        if (Regex("(?i)\\bCREATE\\s+TABLE\\b").containsMatchIn(s) && !Regex("(?i)\\bPRIMARY\\s+KEY\\b").containsMatchIn(s)) {
            issues.add("Advisory: CREATE TABLE statement has no PRIMARY KEY.")
        }
        if (issues.isEmpty()) issues.add("OK: Balanced syntax & no common SQL anti-patterns found.")
        return issues
    }
}

object Base32HexEngine {
    private const val B32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    fun encodeBase32(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        val out = StringBuilder()
        var buffer = 0
        var bitsLeft = 0
        for (b in bytes) {
            buffer = (buffer shl 8) or (b.toInt() and 0xFF)
            bitsLeft += 8
            while (bitsLeft >= 5) {
                out.append(B32[(buffer shr (bitsLeft - 5)) and 0x1F])
                bitsLeft -= 5
            }
        }
        if (bitsLeft > 0) {
            out.append(B32[(buffer shl (5 - bitsLeft)) and 0x1F])
        }
        while (out.length % 8 != 0) out.append('=')
        return out.toString()
    }

    fun decodeBase32(input: String): ByteArray {
        val clean = input.uppercase().replace("=", "").replace(Regex("\\s+"), "")
        val out = mutableListOf<Byte>()
        var buffer = 0
        var bitsLeft = 0
        for (ch in clean) {
            val v = B32.indexOf(ch)
            require(v >= 0) { "Invalid Base32 char: $ch" }
            buffer = (buffer shl 5) or v
            bitsLeft += 5
            if (bitsLeft >= 8) {
                out.add(((buffer shr (bitsLeft - 8)) and 0xFF).toByte())
                bitsLeft -= 8
            }
        }
        return out.toByteArray()
    }

    fun hexDump(bytes: ByteArray, bytesPerLine: Int = 8): String {
        if (bytes.isEmpty()) return ""
        return bytes.toList().chunked(bytesPerLine).mapIndexed { lineIdx, chunk ->
            val offset = "%04X".format(lineIdx * bytesPerLine)
            val hex = chunk.joinToString(" ") { "%02X".format(it) }.padEnd(bytesPerLine * 3 - 1)
            val ascii = chunk.map { b ->
                val c = b.toInt() and 0xFF
                if (c in 32..126) c.toChar() else '.'
            }.joinToString("")
            "$offset  $hex  |$ascii|"
        }.joinToString("\n")
    }

    private fun decodeBase64Bytes(b64: String): ByteArray {
        val table = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
        val clean = b64.replace(Regex("\\s+"), "").trimEnd('=')
        val out = mutableListOf<Byte>()
        var buf = 0
        var bits = 0
        for (ch in clean) {
            val v = table.indexOf(ch)
            if (v < 0) continue
            buf = (buf shl 6) or v
            bits += 6
            if (bits >= 8) {
                out.add(((buf shr (bits - 8)) and 0xFF).toByte())
                bits -= 8
            }
        }
        return out.toByteArray()
    }

    fun inspectPemCertificate(pem: String): Result<String> = runCatching {
        val cleanB64 = pem.lines()
            .filter { !it.trim().startsWith("-----") && it.isNotBlank() }
            .joinToString("")
        val der = decodeBase64Bytes(cleanB64)
        val cf = CertificateFactory.getInstance("X.509")
        val cert = cf.generateCertificate(ByteArrayInputStream(der)) as X509Certificate
        val sha256 = MessageDigest.getInstance("SHA-256").digest(cert.encoded)
            .joinToString(":") { "%02X".format(it) }
        buildString {
            appendLine("Subject: ${cert.subjectX500Principal.name}")
            appendLine("Issuer:  ${cert.issuerX500Principal.name}")
            appendLine("Serial:  ${cert.serialNumber.toString(16).uppercase()}")
            appendLine("Valid:   ${cert.notBefore} -> ${cert.notAfter}")
            appendLine("SigAlg:  ${cert.sigAlgName} (v${cert.version})")
            appendLine("SHA-256: $sha256")
        }.trim()
    }
}
