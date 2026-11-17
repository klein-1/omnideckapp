package com.example.lib.dev

import com.example.lib.crypto.PureBase64
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

sealed interface JsonProcessResult {
    data class Valid(val formatted: String, val keyCount: Int, val depthEstimate: Int) : JsonProcessResult
    data class Invalid(val error: String) : JsonProcessResult
}

data class JwtDecoded(
    val headerJson: String,
    val payloadJson: String,
    val signatureB64: String,
    val algorithm: String,
    val subject: String?,
    val issuedAtText: String?,
    val expiresAtText: String?,
    val isExpired: Boolean?,
    val algNoneWarning: Boolean
)

object JsonJwtEngine {
    fun formatJson(raw: String, indentSpaces: Int = 2, sortKeys: Boolean = false): JsonProcessResult {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return JsonProcessResult.Invalid("Input JSON is empty")
        return try {
            when {
                trimmed.startsWith("{") -> {
                    val obj = JSONObject(trimmed)
                    val target = if (sortKeys) sortJsonObject(obj) else obj
                    JsonProcessResult.Valid(
                        formatted = if (indentSpaces == 0) target.toString() else target.toString(indentSpaces),
                        keyCount = countKeys(target),
                        depthEstimate = estimateDepth(target)
                    )
                }
                trimmed.startsWith("[") -> {
                    val arr = JSONArray(trimmed)
                    val target = if (sortKeys) sortJsonArray(arr) else arr
                    JsonProcessResult.Valid(
                        formatted = if (indentSpaces == 0) target.toString() else target.toString(indentSpaces),
                        keyCount = target.length(),
                        depthEstimate = estimateDepth(target)
                    )
                }
                else -> JsonProcessResult.Invalid("JSON must start with '{' or '['")
            }
        } catch (e: JSONException) {
            JsonProcessResult.Invalid(e.message ?: "Malformed JSON syntax")
        }
    }

    private fun sortJsonObject(obj: JSONObject): JSONObject {
        val sorted = JSONObject()
        val keys = obj.keys().asSequence().toList().sorted()
        for (k in keys) {
            val v = obj.get(k)
            sorted.put(
                k,
                when (v) {
                    is JSONObject -> sortJsonObject(v)
                    is JSONArray -> sortJsonArray(v)
                    else -> v
                }
            )
        }
        return sorted
    }

    private fun sortJsonArray(arr: JSONArray): JSONArray {
        val out = JSONArray()
        for (i in 0 until arr.length()) {
            val v = arr.get(i)
            out.put(
                when (v) {
                    is JSONObject -> sortJsonObject(v)
                    is JSONArray -> sortJsonArray(v)
                    else -> v
                }
            )
        }
        return out
    }

    private fun countKeys(obj: JSONObject): Int {
        var count = 0
        for (k in obj.keys()) {
            count++
            val v = obj.opt(k)
            if (v is JSONObject) count += countKeys(v)
        }
        return count
    }

    private fun estimateDepth(node: Any?): Int = when (node) {
        is JSONObject -> {
            var maxChild = 0
            for (k in node.keys()) maxChild = maxOf(maxChild, estimateDepth(node.opt(k)))
            1 + maxChild
        }
        is JSONArray -> {
            var maxChild = 0
            for (i in 0 until node.length()) maxChild = maxOf(maxChild, estimateDepth(node.opt(i)))
            1 + maxChild
        }
        else -> 0
    }

    fun decodeJwt(token: String): Result<JwtDecoded> {
        val parts = token.trim().split(".")
        if (parts.size < 2 || parts.size > 3) {
            return Result.failure(IllegalArgumentException("JWT must contain 2 or 3 dot-separated segments"))
        }
        return try {
            val headerRaw = decodeBase64Url(parts[0])
            val payloadRaw = decodeBase64Url(parts[1])
            val headerObj = JSONObject(headerRaw)
            val payloadObj = JSONObject(payloadRaw)

            val alg = headerObj.optString("alg", "unknown")
            val sub = if (payloadObj.has("sub")) payloadObj.optString("sub") else null
            val iat = if (payloadObj.has("iat")) payloadObj.optLong("iat", -1L) else -1L
            val exp = if (payloadObj.has("exp")) payloadObj.optLong("exp", -1L) else -1L

            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss 'UTC'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val iatText = if (iat > 0) sdf.format(Date(iat * 1000L)) else null
            val expText = if (exp > 0) sdf.format(Date(exp * 1000L)) else null
            val expired = if (exp > 0) (System.currentTimeMillis() / 1000L) > exp else null

            Result.success(
                JwtDecoded(
                    headerJson = headerObj.toString(2),
                    payloadJson = payloadObj.toString(2),
                    signatureB64 = parts.getOrNull(2).orEmpty(),
                    algorithm = alg,
                    subject = sub,
                    issuedAtText = iatText,
                    expiresAtText = expText,
                    isExpired = expired,
                    algNoneWarning = alg.equals("none", ignoreCase = true)
                )
            )
        } catch (e: Exception) {
            Result.failure(IllegalArgumentException("Failed to decode JWT: ${e.message}"))
        }
    }

    private fun decodeBase64Url(segment: String): String {
        val bytes = PureBase64.decode(segment)
        return bytes.decodeToString()
    }
}
