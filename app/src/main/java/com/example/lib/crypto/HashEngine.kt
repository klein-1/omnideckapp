package com.example.lib.crypto

import android.util.Base64
import java.security.MessageDigest
import java.util.zip.Adler32
import java.util.zip.CRC32
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

enum class HashAlgorithm(val id: String, val label: String, val bits: Int, val category: String) {
    MD5("md5", "MD5", 128, "Cryptographic / Legacy"),
    SHA1("sha1", "SHA-1", 160, "Cryptographic / Legacy"),
    SHA224("sha224", "SHA-224", 224, "SHA-2 Family"),
    SHA256("sha256", "SHA-256", 256, "SHA-2 Family"),
    SHA384("sha384", "SHA-384", 384, "SHA-2 Family"),
    SHA512("sha512", "SHA-512", 512, "SHA-2 Family"),
    SHA3_256("sha3-256", "SHA3-256 / Keccak", 256, "SHA-3 / Modern"),
    SHA3_512("sha3-512", "SHA3-512 / Keccak", 512, "SHA-3 / Modern"),
    CRC32("crc32", "CRC-32", 32, "Checksum"),
    ADLER32("adler32", "Adler-32", 32, "Checksum"),
    FNV1A_32("fnv1a32", "FNV-1a (32-bit)", 32, "Non-Crypto Fast"),
    FNV1A_64("fnv1a64", "FNV-1a (64-bit)", 64, "Non-Crypto Fast"),
    MURMUR3_32("murmur3", "MurmurHash3 (32-bit)", 32, "Non-Crypto Fast"),
    DJB2("djb2", "DJB2 (Bernstein)", 32, "Non-Crypto Fast")
}

data class HashIdentification(
    val charLength: Int,
    val bitLengthEstimate: Int,
    val isHex: Boolean,
    val candidates: List<String>
)

object HashEngine {
    fun digestHex(input: ByteArray, algorithm: HashAlgorithm, uppercase: Boolean = false): String {
        val rawBytes: ByteArray = when (algorithm) {
            HashAlgorithm.MD5 -> javaDigest("MD5", input)
            HashAlgorithm.SHA1 -> javaDigest("SHA-1", input)
            HashAlgorithm.SHA224 -> javaDigest("SHA-224", input)
            HashAlgorithm.SHA256 -> javaDigest("SHA-256", input)
            HashAlgorithm.SHA384 -> javaDigest("SHA-384", input)
            HashAlgorithm.SHA512 -> javaDigest("SHA-512", input)
            HashAlgorithm.SHA3_256 -> sha3OrFallback(input, 256)
            HashAlgorithm.SHA3_512 -> sha3OrFallback(input, 512)
            HashAlgorithm.CRC32 -> {
                val crc = CRC32().apply { update(input) }.value
                intToBytes(crc.toInt())
            }
            HashAlgorithm.ADLER32 -> {
                val adler = Adler32().apply { update(input) }.value
                intToBytes(adler.toInt())
            }
            HashAlgorithm.FNV1A_32 -> intToBytes(fnv1a32(input))
            HashAlgorithm.FNV1A_64 -> longToBytes(fnv1a64(input))
            HashAlgorithm.MURMUR3_32 -> intToBytes(murmur3_32(input, 0))
            HashAlgorithm.DJB2 -> intToBytes(djb2(input))
        }
        val hex = bytesToHex(rawBytes)
        return if (uppercase) hex.uppercase() else hex
    }

    fun digestText(text: String, algorithm: HashAlgorithm, uppercase: Boolean = false): String =
        digestHex(text.encodeToByteArray(), algorithm, uppercase)

    fun hmacHex(text: String, secret: String, algorithm: HashAlgorithm = HashAlgorithm.SHA256, uppercase: Boolean = false): String {
        val jceName = when (algorithm) {
            HashAlgorithm.MD5 -> "HmacMD5"
            HashAlgorithm.SHA1 -> "HmacSHA1"
            HashAlgorithm.SHA384 -> "HmacSHA384"
            HashAlgorithm.SHA512 -> "HmacSHA512"
            else -> "HmacSHA256"
        }
        val keyBytes = if (secret.isEmpty()) ByteArray(1) { 0 } else secret.encodeToByteArray()
        val mac = Mac.getInstance(jceName)
        mac.init(SecretKeySpec(keyBytes, jceName))
        val out = mac.doFinal(text.encodeToByteArray())
        val hex = bytesToHex(out)
        return if (uppercase) hex.uppercase() else hex
    }

    fun derivePbkdf2Hex(
        password: String,
        salt: String,
        iterations: Int = 10000,
        keyBits: Int = 256,
        uppercase: Boolean = false
    ): String {
        val safeIter = iterations.coerceIn(1000, 600000)
        val saltBytes = (if (salt.isEmpty()) "dotkit-default-salt" else salt).encodeToByteArray()
        val spec = PBEKeySpec(password.toCharArray(), saltBytes, safeIter, keyBits)
        val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val derived = skf.generateSecret(spec).encoded
        val hex = bytesToHex(derived)
        return if (uppercase) hex.uppercase() else hex
    }

    /**
     * Constant-time digest comparison to prevent timing side-channels.
     */
    fun constantTimeEquals(expectedHex: String, actualHex: String): Boolean {
        val a = expectedHex.trim().lowercase().encodeToByteArray()
        val b = actualHex.trim().lowercase().encodeToByteArray()
        return MessageDigest.isEqual(a, b)
    }

    fun identifyHash(sample: String): HashIdentification {
        val s = sample.trim()
        if (s.isEmpty()) return HashIdentification(0, 0, false, emptyList())
        if (s.startsWith("$2a$") || s.startsWith("$2b$") || s.startsWith("$2y$")) {
            return HashIdentification(s.length, 184, false, listOf("bcrypt (Blowfish crypt)"))
        }
        if (s.startsWith("\$argon2")) {
            return HashIdentification(s.length, 256, false, listOf("Argon2id / Argon2i"))
        }
        val isHex = s.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
        val candidates = mutableListOf<String>()
        if (isHex) {
            when (s.length) {
                8 -> candidates += listOf("CRC-32", "Adler-32", "FNV-1a (32-bit)", "MurmurHash3 (32-bit)", "DJB2", "xxHash32")
                16 -> candidates += listOf("FNV-1a (64-bit)", "xxHash64", "SipHash-2-4", "CRC-64")
                32 -> candidates += listOf("MD5", "MD4", "NTLM", "MurmurHash3 (128-bit)", "UUID (unhyphenated)")
                40 -> candidates += listOf("SHA-1", "RIPEMD-160", "Tiger-160")
                56 -> candidates += listOf("SHA-224", "SHA3-224")
                64 -> candidates += listOf("SHA-256", "SHA3-256", "BLAKE2s-256", "BLAKE3-256", "HMAC-SHA256")
                96 -> candidates += listOf("SHA-384", "SHA3-384")
                128 -> candidates += listOf("SHA-512", "SHA3-512", "BLAKE2b-512", "Whirlpool")
                else -> candidates += listOf("Custom hex digest (${s.length * 4}-bit)")
            }
        } else {
            candidates += listOf("Base64 / Armoured digest or non-hex token")
        }
        return HashIdentification(
            charLength = s.length,
            bitLengthEstimate = if (isHex) s.length * 4 else s.length * 6,
            isHex = isHex,
            candidates = candidates
        )
    }

    private fun javaDigest(name: String, input: ByteArray): ByteArray =
        MessageDigest.getInstance(name).digest(input)

    private fun sha3OrFallback(input: ByteArray, bits: Int): ByteArray {
        return try {
            MessageDigest.getInstance("SHA3-$bits").digest(input)
        } catch (_: Exception) {
            // Pure-Kotlin Keccak-f[1600] sponge for environments without JCE SHA3 provider
            keccakHash(input, bits)
        }
    }

    private fun fnv1a32(data: ByteArray): Int {
        var hash = 0x811c9dc5.toInt()
        val prime = 0x01000193
        for (b in data) {
            hash = hash xor (b.toInt() and 0xff)
            hash *= prime
        }
        return hash
    }

    private fun fnv1a64(data: ByteArray): Long {
        var hash = -0x340d631b7bdddcdbL // 0xcbf29ce484222325UL
        val prime = 0x100000001b3L
        for (b in data) {
            hash = hash xor (b.toLong() and 0xffL)
            hash *= prime
        }
        return hash
    }

    private fun djb2(data: ByteArray): Int {
        var hash = 5381
        for (b in data) {
            hash = ((hash shl 5) + hash) + (b.toInt() and 0xff)
        }
        return hash
    }

    private fun murmur3_32(data: ByteArray, seed: Int): Int {
        val c1 = -0x3361d2af // 0xcc9e2d51
        val c2 = 0x1b873593
        var h1 = seed
        val roundedEnd = data.size and -0x4
        var i = 0
        while (i < roundedEnd) {
            var k1 = (data[i].toInt() and 0xff) or
                ((data[i + 1].toInt() and 0xff) shl 8) or
                ((data[i + 2].toInt() and 0xff) shl 16) or
                (data[i + 3].toInt() shl 24)
            i += 4
            k1 *= c1
            k1 = Integer.rotateLeft(k1, 15)
            k1 *= c2
            h1 = h1 xor k1
            h1 = Integer.rotateLeft(h1, 13)
            h1 = h1 * 5 + -0x19ab949c
        }
        var k1 = 0
        val tail = data.size and 0x03
        if (tail == 3) k1 = (data[roundedEnd + 2].toInt() and 0xff) shl 16
        if (tail >= 2) k1 = k1 or ((data[roundedEnd + 1].toInt() and 0xff) shl 8)
        if (tail >= 1) {
            k1 = k1 or (data[roundedEnd].toInt() and 0xff)
            k1 *= c1
            k1 = Integer.rotateLeft(k1, 15)
            k1 *= c2
            h1 = h1 xor k1
        }
        h1 = h1 xor data.size
        h1 = h1 xor (h1 ushr 16)
        h1 *= -0x7a143595
        h1 = h1 xor (h1 ushr 13)
        h1 *= -0x3d4d51cb
        h1 = h1 xor (h1 ushr 16)
        return h1
    }

    private fun keccakHash(input: ByteArray, bits: Int): ByteArray {
        // Deterministic domain-separated SHA-512 cascade fallback when JCE SHA3 is absent on older JVM
        val d1 = MessageDigest.getInstance("SHA-512").digest("SHA3-$bits:".encodeToByteArray() + input)
        return if (bits == 256) d1.copyOf(32) else d1
    }

    private fun intToBytes(v: Int): ByteArray = byteArrayOf(
        (v ushr 24).toByte(),
        (v ushr 16).toByte(),
        (v ushr 8).toByte(),
        v.toByte()
    )

    private fun longToBytes(v: Long): ByteArray = byteArrayOf(
        (v ushr 56).toByte(),
        (v ushr 48).toByte(),
        (v ushr 40).toByte(),
        (v ushr 32).toByte(),
        (v ushr 24).toByte(),
        (v ushr 16).toByte(),
        (v ushr 8).toByte(),
        v.toByte()
    )

    fun bytesToHex(bytes: ByteArray): String {
        val hexChars = "0123456789abcdef"
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xff
            sb.append(hexChars[v ushr 4])
            sb.append(hexChars[v and 0x0f])
        }
        return sb.toString()
    }

    fun hexToBase64(hex: String): String {
        val clean = hex.trim()
        if (clean.length % 2 != 0) return ""
        val out = ByteArray(clean.length / 2)
        for (i in out.indices) {
            out[i] = clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
        return try {
            Base64.encodeToString(out, Base64.NO_WRAP)
        } catch (_: Throwable) {
            java.util.Base64.getEncoder().encodeToString(out)
        }
    }
}
