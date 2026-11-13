package com.example.lib.text

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class EncryptedEnvelope(
    val cipherBase64: String,
    val ivBase64: String,
    val saltBase64: String
)

object TextEncodersEngine {

    fun encryptAesGcm(plainText: String, passphrase: String): EncryptedEnvelope {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val key = deriveAesKey(passphrase, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return EncryptedEnvelope(
            cipherBase64 = Base64.encodeToString(encrypted, Base64.NO_WRAP),
            ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP),
            saltBase64 = Base64.encodeToString(salt, Base64.NO_WRAP)
        )
    }

    fun decryptAesGcm(cipherBase64: String, ivBase64: String, saltBase64: String, passphrase: String): Result<String> {
        return runCatching {
            val salt = Base64.decode(saltBase64.trim(), Base64.DEFAULT)
            val iv = Base64.decode(ivBase64.trim(), Base64.DEFAULT)
            val payload = Base64.decode(cipherBase64.trim(), Base64.DEFAULT)
            val key = deriveAesKey(passphrase, salt)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            String(cipher.doFinal(payload), Charsets.UTF_8)
        }
    }

    private fun deriveAesKey(passphrase: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, 65_536, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val bytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(bytes, "AES")
    }

    enum class StylishMode(val label: String) {
        BOLD_SERIF("Bold Serif"),
        ITALIC_SERIF("Italic Serif"),
        MONOSPACE("Monospace"),
        DOUBLE_STRUCK("Double-Struck"),
        SCRIPT("Script"),
        FRAKTUR("Fraktur"),
        SMALL_CAPS("Small Caps"),
        BUBBLE("Circled Bubble"),
        FULLWIDTH("Fullwidth")
    }

    fun stylizeText(input: String, mode: StylishMode): String {
        return when (mode) {
            StylishMode.FULLWIDTH -> input.map { c ->
                when (c) {
                    ' ' -> '\u3000'
                    in '!'..'~' -> (c.code + 0xFEE0).toChar()
                    else -> c
                }
            }.joinToString("")
            StylishMode.SMALL_CAPS -> {
                val map = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀsᴛᴜᴠᴡxʏᴢ"
                input.map { c ->
                    val lo = c.lowercaseChar()
                    if (lo in 'a'..'z') map[lo - 'a'] else c
                }.joinToString("")
            }
            StylishMode.BUBBLE -> input.map { c ->
                when (c) {
                    in 'A'..'Z' -> (0x24B6 + (c - 'A')).toChar()
                    in 'a'..'z' -> (0x24D0 + (c - 'a')).toChar()
                    in '1'..'9' -> (0x2460 + (c - '1')).toChar()
                    '0' -> '\u24EA'
                    else -> c
                }
            }.joinToString("")
            StylishMode.BOLD_SERIF -> mapMathBlock(input, 0x1D400, 0x1D41A, 0x1D7CE)
            StylishMode.ITALIC_SERIF -> mapMathBlock(input, 0x1D434, 0x1D44E, null)
            StylishMode.MONOSPACE -> mapMathBlock(input, 0x1D670, 0x1D68A, 0x1D7F6)
            StylishMode.DOUBLE_STRUCK -> mapMathBlock(input, 0x1D538, 0x1D552, 0x1D7D8)
            StylishMode.SCRIPT -> mapMathBlock(input, 0x1D4D0, 0x1D4EA, null)
            StylishMode.FRAKTUR -> mapMathBlock(input, 0x1D56C, 0x1D586, null)
        }
    }

    private fun mapMathBlock(input: String, upperBase: Int, lowerBase: Int, digitBase: Int?): String {
        val sb = StringBuilder()
        input.forEach { c ->
            when {
                c in 'A'..'Z' -> sb.append(Character.toChars(upperBase + (c - 'A')))
                c in 'a'..'z' -> sb.append(Character.toChars(lowerBase + (c - 'a')))
                digitBase != null && c in '0'..'9' -> sb.append(Character.toChars(digitBase + (c - '0')))
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    private val BANNER_FONT: Map<Char, List<String>> = mapOf(
        'A' to listOf(" ██ ", "█  █", "████", "█  █", "█  █"),
        'B' to listOf("███ ", "█  █", "███ ", "█  █", "███ "),
        'C' to listOf(" ███", "█   ", "█   ", "█   ", " ███"),
        'D' to listOf("███ ", "█  █", "█  █", "█  █", "███ "),
        'E' to listOf("████", "█   ", "███ ", "█   ", "████"),
        'F' to listOf("████", "█   ", "███ ", "█   ", "█   "),
        'G' to listOf(" ███", "█   ", "█ ██", "█  █", " ███"),
        'H' to listOf("█  █", "█  █", "████", "█  █", "█  █"),
        'I' to listOf("███", " █ ", " █ ", " █ ", "███"),
        'J' to listOf("  ██", "   █", "   █", "█  █", " ██ "),
        'K' to listOf("█  █", "█ █ ", "██  ", "█ █ ", "█  █"),
        'L' to listOf("█   ", "█   ", "█   ", "█   ", "████"),
        'M' to listOf("█   █", "██ ██", "█ █ █", "█   █", "█   █"),
        'N' to listOf("█  █", "██ █", "█ ██", "█  █", "█  █"),
        'O' to listOf(" ██ ", "█  █", "█  █", "█  █", " ██ "),
        'P' to listOf("███ ", "█  █", "███ ", "█   ", "█   "),
        'Q' to listOf(" ██ ", "█  █", "█  █", "█ ██", " ███"),
        'R' to listOf("███ ", "█  █", "███ ", "█ █ ", "█  █"),
        'S' to listOf(" ███", "█   ", " ██ ", "   █", "███ "),
        'T' to listOf("█████", "  █  ", "  █  ", "  █  ", "  █  "),
        'U' to listOf("█  █", "█  █", "█  █", "█  █", " ██ "),
        'V' to listOf("█   █", "█   █", " █ █ ", " █ █ ", "  █  "),
        'W' to listOf("█   █", "█   █", "█ █ █", "██ ██", "█   █"),
        'X' to listOf("█  █", " ██ ", " ██ ", " ██ ", "█  █"),
        'Y' to listOf("█   █", " █ █ ", "  █  ", "  █  ", "  █  "),
        'Z' to listOf("████", "  █ ", " █  ", "█   ", "████"),
        '0' to listOf(" ██ ", "█  █", "█  █", "█  █", " ██ "),
        '1' to listOf(" █ ", "██ ", " █ ", " █ ", "███"),
        '2' to listOf("███ ", "   █", " ██ ", "█   ", "████"),
        '3' to listOf("███ ", "   █", " ██ ", "   █", "███ "),
        '4' to listOf("█  █", "█  █", "████", "   █", "   █"),
        '5' to listOf("████", "█   ", "███ ", "   █", "███ "),
        '6' to listOf(" ███", "█   ", "███ ", "█  █", " ██ "),
        '7' to listOf("████", "   █", "  █ ", " █  ", " █  "),
        '8' to listOf(" ██ ", "█  █", " ██ ", "█  █", " ██ "),
        '9' to listOf(" ██ ", "█  █", " ███", "   █", "███ "),
        ' ' to listOf("  ", "  ", "  ", "  ", "  ")
    )

    fun generateAsciiBanner(text: String): String {
        val clean = text.uppercase().take(12)
        if (clean.isEmpty()) return ""
        val rows = Array(5) { StringBuilder() }
        clean.forEach { ch ->
            val glyph = BANNER_FONT[ch] ?: BANNER_FONT[' ']!!
            for (r in 0 until 5) {
                rows[r].append(glyph[r]).append(' ')
            }
        }
        return rows.joinToString("\n") { it.toString().trimEnd() }
    }

    fun generateLoremIpsum(paragraphs: Int = 2): String {
        val pool = listOf(
            "Precision typographic grids establish calm hierarchy across every screen without ornamental noise.",
            "Deterministic engineering prioritizes local computation, explicit state, and verifiable outputs.",
            "Modular software instruments serve immediate user intent through minimal latency and clear affordances.",
            "Clean structural hairlines organize complex toolsets into predictable spatial rhythms.",
            "Offline resilience ensures uninterrupted utility regardless of network availability or external services."
        )
        return (0 until paragraphs.coerceIn(1, 10)).joinToString("\n\n") { idx ->
            pool[idx % pool.size] + " " + pool[(idx + 2) % pool.size]
        }
    }
}
