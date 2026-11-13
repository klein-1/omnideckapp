package com.example.lib.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.IDN
import java.net.InetAddress
import java.net.URI
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.min

data class RedirectHop(
    val step: Int,
    val url: String,
    val statusCode: Int,
    val statusMessage: String,
    val latencyMs: Long,
    val serverHeader: String
)

data class TlsCertSummary(
    val protocol: String,
    val cipherSuite: String,
    val subject: String,
    val issuer: String,
    val validFrom: String,
    val validUntil: String,
    val sha256Fingerprint: String
)

data class SecurityHeaderAudit(
    val header: String,
    val present: Boolean,
    val value: String
)

data class SafeHtmlScan(
    val pageTitle: String,
    val metaDescription: String,
    val hasPasswordForm: Boolean,
    val iframeCount: Int,
    val externalScripts: Int,
    val trackerMatches: List<String>
)

data class UrlInspectionReport(
    val originalUrl: String,
    val finalUrl: String,
    val cleanedUrl: String,
    val strippedParams: List<String>,
    val hops: List<RedirectHop>,
    val punycodeHost: String,
    val hasHomographRisk: Boolean,
    val riskScore: Int, // 0 = clean, 1..100 higher = riskier
    val riskFindings: List<String>,
    val tlsCert: TlsCertSummary?,
    val securityHeaders: List<SecurityHeaderAudit>,
    val htmlScan: SafeHtmlScan?,
    val dnsSummary: String,
    val rdapSummary: String,
    val ipGeoSummary: String,
    val bytesTransferred: Long
)

object UrlInspectorEngine {

    private val TRACKING_PARAMS = setOf(
        "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content", "utm_id",
        "fbclid", "gclid", "dclid", "gbraid", "wbraid", "msclkid", "mc_cid", "mc_eid",
        "si", "igshid", "igsh", "ref", "ref_src", "ref_url", "s", "t", "feature", "spm", "yclid"
    )

    private val SHORTENER_DOMAINS = setOf(
        "bit.ly", "t.co", "tinyurl.com", "goo.gl", "ow.ly", "is.gd", "buff.ly",
        "rebrand.ly", "cutt.ly", "rb.gy", "tiny.cc", "shorturl.at", "youtu.be", "amzn.to"
    )

    private val SUSPICIOUS_TLDS = setOf("zip", "mov", "tk", "ml", "ga", "cf", "gq", "top", "xyz", "click", "loan", "work")

    private val PROTECTED_BRANDS = listOf(
        "google", "paypal", "amazon", "apple", "microsoft", "instagram", "facebook",
        "netflix", "github", "binance", "coinbase", "whatsapp", "youtube", "linkedin", "stripe", "dropbox"
    )

    private val OPEN_REDIRECT_KEYS = setOf("redirect", "redirect_url", "next", "url", "dest", "destination", "target", "return", "return_to")
    private val PHISHING_KEYWORDS = listOf("login", "signin", "verify", "update-account", "wallet", "airdrop", "kyc", "seedphrase", "reset-password")
    private val KNOWN_TRACKERS = listOf("googletagmanager", "google-analytics", "connect.facebook.net", "doubleclick.net", "hotjar", "mixpanel", "segment.com")

    fun normalizeUrl(raw: String): String {
        val t = raw.trim()
        return if (t.startsWith("http://", true) || t.startsWith("https://", true) || t.startsWith("javascript:", true) || t.startsWith("data:", true)) t
        else "https://$t"
    }

    fun stripTrackingParams(url: String): Pair<String, List<String>> {
        val uri = runCatching { URI(url) }.getOrNull() ?: return url to emptyList()
        val query = uri.rawQuery ?: return url to emptyList()
        val removed = mutableListOf<String>()
        val kept = mutableListOf<String>()
        query.split("&").filter { it.isNotEmpty() }.forEach { pair ->
            val key = pair.substringBefore('=').lowercase()
            if (key in TRACKING_PARAMS || key.startsWith("utm_")) removed += key else kept += pair
        }
        val newQuery = if (kept.isEmpty()) null else kept.joinToString("&")
        val rebuilt = URI(uri.scheme, uri.authority, uri.path, newQuery, uri.fragment).toString()
        return rebuilt to removed
    }

    fun levenshtein(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = min(min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost)
            }
        }
        return dp[a.length][b.length]
    }

    fun analyzeHostHeuristics(url: String): Triple<String, Boolean, List<String>> {
        val lower = url.trim().lowercase()
        if (lower.startsWith("javascript:") || lower.startsWith("data:") || lower.startsWith("vbscript:")) {
            return Triple("", true, listOf("Dangerous executable URI scheme (${lower.substringBefore(':')}:)"))
        }
        val uri = runCatching { URI(url) }.getOrNull() ?: return Triple("", false, listOf("Malformed URL syntax"))
        val host = uri.host.orEmpty()
        val puny = runCatching { IDN.toASCII(host) }.getOrDefault(host)
        val findings = mutableListOf<String>()
        var homograph = false

        if (uri.scheme.equals("http", ignoreCase = true)) findings += "Unencrypted HTTP transport (no TLS)"
        if (uri.userInfo != null) findings += "URL contains embedded credentials (@), common phishing trick"
        if (puny.startsWith("xn--") || puny.contains(".xn--")) {
            homograph = true; findings += "Internationalized Punycode domain ($puny) — check for lookalike characters"
        }
        if (host.any { it.code > 127 }) {
            homograph = true; findings += "Non-ASCII Unicode characters in hostname"
        }
        if (Regex("^\\d{1,3}(\\.\\d{1,3}){3}$").matches(host)) findings += "Raw IPv4 host address instead of domain name"
        val tld = host.substringAfterLast('.', "").lowercase()
        if (tld in SUSPICIOUS_TLDS) findings += "High-abuse TLD (.$tld)"
        if (host.count { it == '.' } >= 4) findings += "Deeply nested subdomains (${host.count { it == '.' } + 1} labels)"
        if (host.lowercase() in SHORTENER_DOMAINS) findings += "Known URL shortener domain"

        // Brand typosquatting check on main second-level label
        val labels = host.lowercase().split('.').filter { it.isNotBlank() }
        val sld = if (labels.size >= 2) labels[labels.size - 2] else labels.firstOrNull().orEmpty()
        if (sld.length >= 4) {
            PROTECTED_BRANDS.forEach { brand ->
                val dist = levenshtein(sld, brand)
                if (dist in 1..2 && sld != brand) {
                    homograph = true
                    findings += "Potential brand impersonation / typosquatting of '$brand' (domain label '$sld')"
                } else if (brand in sld && sld != brand) {
                    findings += "Contains brand keyword '$brand' inside non-official domain '$host'"
                }
            }
        }

        // Open-redirect query parameter check
        uri.rawQuery?.split('&')?.forEach { pair ->
            val key = pair.substringBefore('=').lowercase()
            val v = pair.substringAfter('=', "").lowercase()
            if (key in OPEN_REDIRECT_KEYS && ("http" in v || "%2f" in v)) {
                findings += "Open-redirect query parameter '$key=' points to external URL"
            }
        }

        // Phishing lure keywords in path/query
        val pathAndQuery = ((uri.path ?: "") + "?" + (uri.rawQuery ?: "")).lowercase()
        PHISHING_KEYWORDS.firstOrNull { it in pathAndQuery }?.let { kw ->
            findings += "Sensitive lure keyword '$kw' found in URL path/query"
        }

        return Triple(puny, homograph, findings)
    }

    fun parseSafeHtmlSnippet(html: String): SafeHtmlScan {
        val title = Regex("<title[^>]*>([^<]{1,140})</title>", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)?.trim().orEmpty()
        val desc = Regex("""<meta[^>]+name=["']description["'][^>]+content=["']([^"']{1,220})["']""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)?.trim().orEmpty()
        val hasPwd = Regex("""<input[^>]+type=["']?password["']?""", RegexOption.IGNORE_CASE).containsMatchIn(html)
        val iframes = Regex("<iframe\\b", RegexOption.IGNORE_CASE).findAll(html).count()
        val scripts = Regex("""<script[^>]+src=["']""", RegexOption.IGNORE_CASE).findAll(html).count()
        val trackers = KNOWN_TRACKERS.filter { it in html.lowercase() }
        return SafeHtmlScan(title, desc, hasPwd, iframes, scripts, trackers)
    }

    suspend fun inspect(rawUrl: String, checkDns: Boolean = true, checkRdapGeo: Boolean = false): UrlInspectionReport = withContext(Dispatchers.IO) {
        val startUrl = normalizeUrl(rawUrl)
        if (startUrl.startsWith("javascript:", true) || startUrl.startsWith("data:", true)) {
            val (puny, homo, findings) = analyzeHostHeuristics(startUrl)
            return@withContext UrlInspectionReport(
                startUrl, startUrl, startUrl, emptyList(), emptyList(), puny, homo, 95, findings,
                null, emptyList(), null, "Skipped (non-HTTP URI)", "", "", 0L
            )
        }

        val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
            .connectTimeout(8, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).build()

        val hops = mutableListOf<RedirectHop>()
        var currentUrl = startUrl
        var latestCert: TlsCertSummary? = null
        var secHeaders = emptyList<SecurityHeaderAudit>()
        var htmlScan: SafeHtmlScan? = null
        var bytesTotal = 0L

        for (step in 1..15) {
            val t0 = System.currentTimeMillis()
            try {
                val req = Request.Builder().url(currentUrl).header("User-Agent", "Mozilla/5.0 OmniDeck-UrlInspector/1.0").get().build()
                client.newCall(req).execute().use { resp ->
                    val elapsed = System.currentTimeMillis() - t0
                    bytesTotal += 256L
                    resp.handshake?.let { hs ->
                        val leaf = hs.peerCertificates.firstOrNull() as? X509Certificate
                        if (leaf != null) {
                            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                            val sha256 = MessageDigest.getInstance("SHA-256").digest(leaf.encoded).joinToString(":") { "%02X".format(it) }
                            latestCert = TlsCertSummary(
                                hs.tlsVersion.javaName, hs.cipherSuite.javaName,
                                leaf.subjectX500Principal.name, leaf.issuerX500Principal.name,
                                sdf.format(leaf.notBefore), sdf.format(leaf.notAfter), sha256
                            )
                        }
                    }
                    hops += RedirectHop(step, currentUrl, resp.code, resp.message.ifBlank { "HTTP ${resp.code}" }, elapsed, resp.header("Server").orEmpty())
                    val location = resp.header("Location")
                    if (resp.isRedirect && !location.isNullOrBlank()) {
                        currentUrl = runCatching { URI(currentUrl).resolve(location).toString() }.getOrDefault(location)
                    } else {
                        secHeaders = listOf("Strict-Transport-Security", "Content-Security-Policy", "X-Frame-Options", "X-Content-Type-Options", "Referrer-Policy").map { h ->
                            val v = resp.header(h).orEmpty()
                            SecurityHeaderAudit(h, v.isNotBlank(), v)
                        }
                        if (resp.header("Content-Type").orEmpty().contains("html", true)) {
                            val body = resp.body?.string().orEmpty().take(120_000)
                            bytesTotal += body.length
                            htmlScan = parseSafeHtmlSnippet(body)
                        }
                        break
                    }
                }
            } catch (e: Exception) {
                hops += RedirectHop(step, currentUrl, 0, "Error: ${e.message ?: e.javaClass.simpleName}", System.currentTimeMillis() - t0, "")
                break
            }
        }

        val finalUrl = hops.lastOrNull()?.url ?: startUrl
        val (cleaned, stripped) = stripTrackingParams(finalUrl)
        val (puny, homograph, findingsMutable) = analyzeHostHeuristics(finalUrl)
        val findings = findingsMutable.toMutableList()
        if (hops.size > 1) findings += "Redirected ${hops.size - 1} time(s) before landing on destination"
        if (stripped.isNotEmpty()) findings += "Removed ${stripped.size} tracking parameter(s): ${stripped.joinToString(", ")}"
        if (htmlScan?.hasPasswordForm == true) findings += "Page renders a password login form (<input type=password>)"

        val host = runCatching { URI(finalUrl).host }.getOrNull().orEmpty()
        var dnsInfo = "DNS check skipped"
        var rdapInfo = ""
        var geoInfo = ""

        if (checkDns && host.isNotBlank()) {
            dnsInfo = queryDoh(client, host, "A"); bytesTotal += 512L
        }
        if (checkRdapGeo && host.isNotBlank()) {
            rdapInfo = queryRdap(client, host)
            geoInfo = queryIpGeo(client, host)
            bytesTotal += 1024L
        }

        val score = calculateRiskScore(finalUrl, homograph, hops.size, latestCert != null, findings)
        UrlInspectionReport(
            startUrl, finalUrl, cleaned, stripped, hops, puny, homograph, score, findings,
            latestCert, secHeaders, htmlScan, dnsInfo, rdapInfo, geoInfo, bytesTotal
        )
    }

    suspend fun queryDoh(client: OkHttpClient = OkHttpClient(), domain: String, recordType: String = "A"): String = withContext(Dispatchers.IO) {
        try {
            val url = "https://dns.google/resolve?name=${domain.trim()}&type=${recordType.trim().uppercase()}"
            client.newCall(Request.Builder().url(url).get().build()).execute().use { resp ->
                val json = JSONObject(resp.body?.string().orEmpty())
                val status = json.optInt("Status", -1)
                val ans = json.optJSONArray("Answer") ?: return@withContext "DoH Status=$status · No $recordType records"
                if (ans.length() == 0) return@withContext "DoH Status=$status · No $recordType records"
                buildString {
                    for (i in 0 until ans.length()) {
                        val row = ans.getJSONObject(i)
                        append("${row.optString("name")}  TTL=${row.optInt("TTL")}s  ${row.optString("data")}\n")
                    }
                }.trim()
            }
        } catch (e: Exception) {
            "DoH lookup failed: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    private fun queryRdap(client: OkHttpClient, host: String): String = runCatching {
        val parts = host.split('.')
        val apex = if (parts.size >= 2) parts.takeLast(2).joinToString(".") else host
        client.newCall(Request.Builder().url("https://rdap.org/domain/$apex").get().build()).execute().use { resp ->
            if (!resp.isSuccessful) return@runCatching "RDAP HTTP ${resp.code} for $apex"
            val json = JSONObject(resp.body?.string().orEmpty())
            val ldh = json.optString("ldhName", apex)
            val events = json.optJSONArray("events")
            val regDate = buildString {
                if (events != null) {
                    for (i in 0 until events.length()) {
                        val ev = events.getJSONObject(i)
                        if (ev.optString("eventAction") == "registration") append("Registered: ${ev.optString("eventDate").take(10)}")
                        if (ev.optString("eventAction") == "expiration") append(" · Expires: ${ev.optString("eventDate").take(10)}")
                    }
                }
            }
            "RDAP Domain: $ldh${if (regDate.isNotBlank()) " · $regDate" else ""}"
        }
    }.getOrElse { "RDAP unavailable (${it.javaClass.simpleName})" }

    private fun queryIpGeo(client: OkHttpClient, host: String): String = runCatching {
        val ip = InetAddress.getByName(host).hostAddress ?: return@runCatching ""
        client.newCall(Request.Builder().url("https://ipwho.is/$ip").get().build()).execute().use { resp ->
            val json = JSONObject(resp.body?.string().orEmpty())
            val conn = json.optJSONObject("connection")
            "IP: $ip · ${json.optString("country")} (${json.optString("city")}) · ASN: ${conn?.optString("org").orEmpty()}"
        }
    }.getOrDefault("")

    fun calculateRiskScore(
        finalUrl: String,
        homograph: Boolean,
        hopCount: Int,
        hasTls: Boolean,
        findings: List<String>
    ): Int {
        var score = 0
        if (finalUrl.startsWith("javascript:", true) || finalUrl.startsWith("data:", true)) return 95
        if (!hasTls && finalUrl.startsWith("http://", true)) score += 25
        if (homograph) score += 35
        if (hopCount > 3) score += 15
        if (findings.any { "credentials" in it || "IPv4" in it || "Open-redirect" in it }) score += 30
        if (findings.any { "High-abuse TLD" in it }) score += 20
        if (findings.any { "lure keyword" in it || "brand keyword" in it }) score += 15
        return score.coerceIn(0, 100)
    }
}
