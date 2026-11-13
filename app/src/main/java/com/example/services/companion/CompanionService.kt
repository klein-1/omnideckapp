package com.example.services.companion

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class CompanionProbeResult(
    val reachable: Boolean,
    val latencyMs: Long,
    val statusMessage: String,
    val bytesTransferred: Long
)

/**
 * Companion server connector (§2.6).
 * Probes the user-configured Companion URL (/api/health) and reports honestly.
 */
object CompanionService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(2500, TimeUnit.MILLISECONDS)
        .readTimeout(2500, TimeUnit.MILLISECONDS)
        .build()

    suspend fun testConnection(baseUrl: String, bearerToken: String): CompanionProbeResult =
        withContext(Dispatchers.IO) {
            val cleanBase = baseUrl.trim().trimEnd('/')
            if (cleanBase.isEmpty() || (!cleanBase.startsWith("http://") && !cleanBase.startsWith("https://"))) {
                return@withContext CompanionProbeResult(
                    reachable = false,
                    latencyMs = 0L,
                    statusMessage = "Invalid URL. Enter http://<host>:8787 in Settings → Companion.",
                    bytesTransferred = 0L
                )
            }
            val start = System.currentTimeMillis()
            try {
                val reqBuilder = Request.Builder().url("$cleanBase/api/health").get()
                if (bearerToken.isNotBlank()) {
                    reqBuilder.header("Authorization", "Bearer ${bearerToken.trim()}")
                }
                client.newCall(reqBuilder.build()).execute().use { resp ->
                    val elapsed = System.currentTimeMillis() - start
                    val bodyStr = resp.body?.string().orEmpty()
                    val bytes = bodyStr.encodeToByteArray().size.toLong()
                    if (resp.isSuccessful) {
                        CompanionProbeResult(
                            reachable = true,
                            latencyMs = elapsed,
                            statusMessage = "Connected (${resp.code} OK in ${elapsed}ms). Engines ready.",
                            bytesTransferred = bytes
                        )
                    } else {
                        CompanionProbeResult(
                            reachable = false,
                            latencyMs = elapsed,
                            statusMessage = "HTTP ${resp.code}: Check Companion bearer token or server route.",
                            bytesTransferred = bytes
                        )
                    }
                }
            } catch (e: Exception) {
                val elapsed = System.currentTimeMillis() - start
                CompanionProbeResult(
                    reachable = false,
                    latencyMs = elapsed,
                    statusMessage = "Companion unreachable at $cleanBase (${e.javaClass.simpleName}). Local tools remain 100% active.",
                    bytesTransferred = 0L
                )
            }
        }
}
