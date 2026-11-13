package com.example.services.server

import android.content.Context
import android.net.Uri
import android.os.PowerManager
import android.util.Base64
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.math.BigInteger
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext

data class ServerLogEntry(
    val timestamp: String,
    val method: String,
    val path: String,
    val status: Int,
    val clientIp: String,
    val bytesOut: Long,
    val payloadPreview: String = ""
)

data class CustomMockRoute(
    val method: String, // GET, POST, PUT, DELETE
    val path: String,   // e.g. /api/users
    val statusCode: Int = 200,
    val delayMs: Int = 0,
    val jsonBody: String = """{"ok":true}"""
)

data class LocalServerStatus(
    val running: Boolean = false,
    val useHttps: Boolean = false,
    val port: Int = 8080,
    val localUrls: List<String> = emptyList(),
    val rootLabel: String = "App WebRoot",
    val tlsSha256Fingerprint: String = "",
    val activeRequests: Long = 0L,
    val corsEnabled: Boolean = true,
    val wakeLockHeld: Boolean = false,
    val error: String? = null
)

object LocalHttpServerEngine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pool = Executors.newCachedThreadPool()

    private var serverSocket: ServerSocket? = null
    private var rootSafUri: Uri? = null
    private var appContext: Context? = null
    private var authUser: String = ""
    private var authPass: String = ""
    private var requireAuth: Boolean = false
    private var enableCors: Boolean = true
    private var rateLimitPerMin: Int = 0
    private var ipAllowCsv: String = ""
    private var ipBlockCsv: String = ""
    private var wakeLock: PowerManager.WakeLock? = null
    private var lastEchoPayload: String = """{"status":"ready","engine":"omni-deck-shttps"}"""

    private val customRoutes = mutableListOf<CustomMockRoute>()
    private val ipHitWindow = ConcurrentHashMap<String, MutableList<Long>>()

    private val _status = MutableStateFlow(LocalServerStatus())
    val status: StateFlow<LocalServerStatus> = _status.asStateFlow()

    private val _logs = MutableStateFlow<List<ServerLogEntry>>(emptyList())
    val logs: StateFlow<List<ServerLogEntry>> = _logs.asStateFlow()

    private val _routes = MutableStateFlow<List<CustomMockRoute>>(emptyList())
    val routes: StateFlow<List<CustomMockRoute>> = _routes.asStateFlow()

    fun findFreePort(): Int = runCatching { ServerSocket(0).use { it.localPort } }.getOrDefault(8080)

    fun addMockRoute(route: CustomMockRoute) {
        synchronized(customRoutes) {
            customRoutes.removeAll { it.method.equals(route.method, true) && it.path == route.path }
            customRoutes += route
            _routes.value = customRoutes.toList()
        }
    }

    fun removeMockRoute(method: String, path: String) {
        synchronized(customRoutes) {
            customRoutes.removeAll { it.method.equals(method, true) && it.path == path }
            _routes.value = customRoutes.toList()
        }
    }

    fun getLocalIpAddresses(): List<String> {
        val list = mutableListOf<String>()
        runCatching {
            val ifaces = NetworkInterface.getNetworkInterfaces()
            while (ifaces.hasMoreElements()) {
                val iface = ifaces.nextElement()
                if (!iface.isUp || iface.isLoopback) continue
                val addrs = iface.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress) addr.hostAddress?.let { list += it }
                }
            }
        }
        if (list.isEmpty()) list += "127.0.0.1"
        return list.distinct()
    }

    fun start(
        context: Context,
        port: Int,
        useHttps: Boolean,
        safTreeUri: Uri?,
        enableAuth: Boolean,
        username: String,
        password: String,
        corsEnabled: Boolean = true,
        rateLimitRpm: Int = 0,
        ipAllowList: String = "",
        ipBlockList: String = "",
        keepWakeLock: Boolean = false
    ) {
        stop()
        appContext = context.applicationContext
        rootSafUri = safTreeUri
        requireAuth = enableAuth
        authUser = username.trim()
        authPass = password
        enableCors = corsEnabled
        rateLimitPerMin = rateLimitRpm.coerceAtLeast(0)
        ipAllowCsv = ipAllowList
        ipBlockCsv = ipBlockList

        if (keepWakeLock) {
            runCatching {
                val pm = context.applicationContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
                wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "OmniDeck::LocalHttpServer")?.apply {
                    acquire(60 * 60 * 1000L)
                }
            }
        }

        val safePort = port.coerceIn(1024, 65535)
        scope.launch {
            try {
                var fingerprint = ""
                val socket = if (useHttps) {
                    val (sslCtx, fp) = createSelfSignedSslContext()
                    fingerprint = fp
                    sslCtx.serverSocketFactory.createServerSocket(safePort)
                } else {
                    ServerSocket(safePort)
                }
                serverSocket = socket

                val rootName = if (safTreeUri != null) {
                    DocumentFile.fromTreeUri(context.applicationContext, safTreeUri)?.name ?: "Shared Folder"
                } else {
                    ensureDefaultSandboxIndex(context.applicationContext)
                    "App Sandbox (/webroot)"
                }

                val scheme = if (useHttps) "https" else "http"
                val urls = getLocalIpAddresses().map { "$scheme://$it:$safePort" }
                _status.value = LocalServerStatus(
                    running = true, useHttps = useHttps, port = safePort, localUrls = urls,
                    rootLabel = rootName, tlsSha256Fingerprint = fingerprint,
                    corsEnabled = enableCors, wakeLockHeld = wakeLock?.isHeld == true
                )
                appendLog("SYS", "/start", 200, "local", 0L)

                while (!socket.isClosed) {
                    val client = socket.accept()
                    pool.execute { handleClient(client) }
                }
            } catch (e: Exception) {
                if (_status.value.running || serverSocket == null) {
                    _status.value = _status.value.copy(running = false, error = e.message ?: e.javaClass.simpleName)
                }
            }
        }
    }

    fun stop() {
        runCatching { serverSocket?.close() }
        serverSocket = null
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wakeLock = null
        _status.value = _status.value.copy(running = false, wakeLockHeld = false)
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    private fun ensureDefaultSandboxIndex(ctx: Context): File {
        val dir = File(ctx.filesDir, "webroot").apply { mkdirs() }
        val readme = File(dir, "README.txt")
        if (!readme.exists()) readme.writeText("Omni Deck Local Server WebRoot\nAdd files or pick any device folder via SAF.\n")
        return dir
    }

    private fun handleClient(socket: Socket) {
        socket.use { sock ->
            sock.soTimeout = 15_000
            val input = BufferedInputStream(sock.getInputStream())
            val out = sock.getOutputStream()
            val clientIp = sock.inetAddress.hostAddress ?: "client"

            val headerBytes = readUntilDoubleCrlf(input) ?: return
            val lines = String(headerBytes, Charsets.UTF_8).split("\r\n")
            val requestLine = lines.firstOrNull()?.split(" ") ?: return
            if (requestLine.size < 2) return

            val method = requestLine[0].uppercase()
            val rawTarget = requestLine[1]
            val rawPath = rawTarget.substringBefore('?')
            val query = rawTarget.substringAfter('?', "")
            val headers = mutableMapOf<String, String>()
            lines.drop(1).forEach { line ->
                val idx = line.indexOf(':')
                if (idx > 0) headers[line.substring(0, idx).trim().lowercase()] = line.substring(idx + 1).trim()
            }

            // CORS preflight
            if (method == "OPTIONS" && enableCors) {
                writeResponse(out, 204, "No Content", "text/plain", ByteArray(0), mapOf(
                    "Access-Control-Allow-Methods" to "GET, POST, PUT, DELETE, OPTIONS",
                    "Access-Control-Allow-Headers" to "*"
                ))
                appendLog(method, rawPath, 204, clientIp, 0L)
                return
            }

            // IP Allow/Block check
            val blocked = ipBlockCsv.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            val allowed = ipAllowCsv.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            if (clientIp in blocked || (allowed.isNotEmpty() && clientIp !in allowed && clientIp != "127.0.0.1")) {
                val msg = "403 Forbidden (IP policy)".toByteArray()
                writeResponse(out, 403, "Forbidden", "text/plain", msg)
                appendLog(method, rawPath, 403, clientIp, msg.size.toLong())
                return
            }

            // Rate Limit check
            if (rateLimitPerMin > 0) {
                val now = System.currentTimeMillis()
                val hits = ipHitWindow.getOrPut(clientIp) { mutableListOf() }
                val allowedHit = synchronized(hits) {
                    hits.removeAll { now - it > 60_000L }
                    if (hits.size >= rateLimitPerMin) false else { hits += now; true }
                }
                if (!allowedHit) {
                    val msg = "429 Too Many Requests".toByteArray()
                    writeResponse(out, 429, "Too Many Requests", "text/plain", msg)
                    appendLog(method, rawPath, 429, clientIp, msg.size.toLong())
                    return
                }
            }

            if (requireAuth) {
                val expected = "Basic " + Base64.encodeToString("$authUser:$authPass".toByteArray(), Base64.NO_WRAP)
                if (headers["authorization"] != expected) {
                    val body = "401 Unauthorized".toByteArray()
                    writeResponse(out, 401, "Unauthorized", "text/plain", body, mapOf("WWW-Authenticate" to "Basic realm=\"OmniDeck\""))
                    appendLog(method, rawPath, 401, clientIp, body.size.toLong())
                    return
                }
            }

            val decodedPath = URLDecoder.decode(rawPath, "UTF-8")
            val contentLength = headers["content-length"]?.toIntOrNull()?.coerceIn(0, 32 * 1024 * 1024) ?: 0
            val bodyBytes = if (contentLength > 0) readExact(input, contentLength) else ByteArray(0)
            val bodyPreview = if (bodyBytes.isNotEmpty() && bodyBytes.size < 4096) String(bodyBytes, Charsets.UTF_8).take(180) else ""

            // Custom Dynamic Mock Routes
            val matchedRoute = synchronized(customRoutes) {
                customRoutes.firstOrNull { it.method.equals(method, true) && it.path == decodedPath }
            }
            if (matchedRoute != null) {
                if (matchedRoute.delayMs > 0) runCatching { Thread.sleep(matchedRoute.delayMs.coerceAtMost(5000).toLong()) }
                val res = matchedRoute.jsonBody.toByteArray()
                writeResponse(out, matchedRoute.statusCode, "OK", "application/json; charset=utf-8", res)
                appendLog(method, decodedPath, matchedRoute.statusCode, clientIp, res.size.toLong(), bodyPreview)
                return
            }

            if (decodedPath == "/api/health") {
                val res = """{"status":"ok","engine":"omni-deck-shttps","https":${_status.value.useHttps}}""".toByteArray()
                writeResponse(out, 200, "OK", "application/json", res)
                appendLog(method, decodedPath, 200, clientIp, res.size.toLong())
                return
            }
            if (decodedPath == "/api/echo" || decodedPath.startsWith("/webhook/")) {
                if (method == "POST" || method == "PUT") lastEchoPayload = String(bodyBytes, Charsets.UTF_8).ifBlank { "{}" }
                val res = """{"method":"$method","path":${quoteJson(decodedPath)},"query":${quoteJson(query)},"payload":${quoteJson(lastEchoPayload)}}""".toByteArray()
                writeResponse(out, 200, "OK", "application/json", res)
                appendLog(method, decodedPath, 200, clientIp, res.size.toLong(), lastEchoPayload.take(180))
                return
            }

            val ctx = appContext ?: return
            val safUri = rootSafUri
            if (decodedPath == "/__zip") {
                val zipBytes = buildDirectoryZip(ctx, safUri)
                writeResponse(out, 200, "OK", "application/zip", zipBytes, mapOf("Content-Disposition" to "attachment; filename=\"omnideck_webroot.zip\""))
                appendLog(method, decodedPath, 200, clientIp, zipBytes.size.toLong())
                return
            }

            if (safUri != null) handleSafRequest(ctx, safUri, method, decodedPath, query, headers, bodyBytes, out, clientIp)
            else handleLocalDirRequest(ensureDefaultSandboxIndex(ctx), method, decodedPath, query, headers, bodyBytes, out, clientIp)
        }
    }

    private fun buildDirectoryZip(ctx: Context, safUri: Uri?): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            if (safUri == null) {
                val root = ensureDefaultSandboxIndex(ctx)
                root.walkTopDown().filter { it.isFile }.take(120).forEach { f ->
                    val rel = f.relativeTo(root).path
                    zos.putNextEntry(ZipEntry(rel))
                    zos.write(f.readBytes())
                    zos.closeEntry()
                }
            } else {
                val rootDoc = DocumentFile.fromTreeUri(ctx, safUri)
                rootDoc?.listFiles()?.filter { it.isFile }?.take(80)?.forEach { f ->
                    zos.putNextEntry(ZipEntry(f.name ?: "file"))
                    ctx.contentResolver.openInputStream(f.uri)?.use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }
        }
        return baos.toByteArray()
    }

    private fun handleLocalDirRequest(
        rootDir: File, method: String, path: String, query: String, headers: Map<String, String>,
        bodyBytes: ByteArray, out: OutputStream, clientIp: String
    ) {
        val cleanRel = path.trimStart('/').replace("..", "")
        val target = if (cleanRel.isEmpty()) rootDir else File(rootDir, cleanRel)

        if (method == "POST" && path == "/__action") {
            val params = parseQueryParams(String(bodyBytes, Charsets.UTF_8))
            val dirRel = params["dir"].orEmpty().trimStart('/').replace("..", "")
            val parentDir = if (dirRel.isEmpty()) rootDir else File(rootDir, dirRel)
            when (params["action"].orEmpty()) {
                "mkdir" -> params["name"].orEmpty().replace("/", "").takeIf { it.isNotBlank() }?.let { File(parentDir, it).mkdirs() }
                "delete" -> params["name"].orEmpty().replace("/", "").takeIf { it.isNotBlank() }?.let { File(parentDir, it).deleteRecursively() }
                "rename" -> {
                    val o = params["old"].orEmpty().replace("/", ""); val n = params["new"].orEmpty().replace("/", "")
                    if (o.isNotBlank() && n.isNotBlank()) File(parentDir, o).renameTo(File(parentDir, n))
                }
                "save_text" -> {
                    val n = params["name"].orEmpty().replace("/", "")
                    if (n.isNotBlank()) File(parentDir, n).writeText(params["content"].orEmpty())
                }
            }
            writeResponse(out, 303, "See Other", "text/plain", ByteArray(0), mapOf("Location" to "/$dirRel"))
            appendLog(method, path, 303, clientIp, 0L)
            return
        }

        if (method == "POST" && path == "/__upload") {
            val boundary = headers["content-type"].orEmpty().substringAfter("boundary=", "").trim().trim('"')
            val targetDirRel = parseQueryParams(query)["dir"].orEmpty().trimStart('/').replace("..", "")
            val destDir = if (targetDirRel.isEmpty()) rootDir else File(rootDir, targetDirRel)
            if (boundary.isNotEmpty()) {
                extractMultipartFiles(bodyBytes, boundary).forEach { (fname, data) ->
                    val safeName = fname.substringAfterLast('/').substringAfterLast('\\').ifBlank { "upload.bin" }
                    File(destDir, safeName).writeBytes(data)
                }
            }
            writeResponse(out, 303, "See Other", "text/plain", ByteArray(0), mapOf("Location" to "/$targetDirRel"))
            appendLog(method, path, 303, clientIp, 0L)
            return
        }

        if (!target.exists()) {
            val msg = "404 Not Found: $path".toByteArray()
            writeResponse(out, 404, "Not Found", "text/plain", msg)
            appendLog(method, path, 404, clientIp, msg.size.toLong())
            return
        }

        if (target.isDirectory) {
            val editTarget = parseQueryParams(query)["edit"]
            if (!editTarget.isNullOrBlank()) {
                val f = File(target, editTarget.replace("/", ""))
                val html = buildTextEditorHtml(cleanRel, editTarget, if (f.exists() && f.isFile) f.readText() else "").toByteArray()
                writeResponse(out, 200, "OK", "text/html; charset=utf-8", html)
                appendLog(method, path, 200, clientIp, html.size.toLong())
                return
            }
            val items = target.listFiles()?.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() }).orEmpty()
            val html = buildDirectoryHtml("/$cleanRel", items.map { ServerFileItem(it.name, it.isDirectory, it.length()) }).toByteArray()
            writeResponse(out, 200, "OK", "text/html; charset=utf-8", html)
            appendLog(method, path, 200, clientIp, html.size.toLong())
        } else {
            val data = target.readBytes()
            writeResponse(out, 200, "OK", guessMime(target.name), data)
            appendLog(method, path, 200, clientIp, data.size.toLong())
        }
    }

    private fun handleSafRequest(
        ctx: Context, treeUri: Uri, method: String, path: String, query: String, headers: Map<String, String>,
        bodyBytes: ByteArray, out: OutputStream, clientIp: String
    ) {
        val rootDoc = DocumentFile.fromTreeUri(ctx, treeUri)
        if (rootDoc == null) { writeResponse(out, 500, "Server Error", "text/plain", "500 SAF Root unavailable".toByteArray()); return }
        val segments = path.trim('/').split('/').filter { it.isNotBlank() && it != ".." }
        var current: DocumentFile? = rootDoc
        for (seg in segments) current = current?.findFile(seg)

        if (method == "POST" && path == "/__action") {
            val params = parseQueryParams(String(bodyBytes, Charsets.UTF_8))
            val dirRel = params["dir"].orEmpty().trim('/')
            var dirDoc: DocumentFile? = rootDoc
            dirRel.split('/').filter { it.isNotBlank() }.forEach { dirDoc = dirDoc?.findFile(it) }
            when (params["action"]) {
                "mkdir" -> params["name"]?.takeIf { it.isNotBlank() }?.let { dirDoc?.createDirectory(it) }
                "delete" -> params["name"]?.takeIf { it.isNotBlank() }?.let { dirDoc?.findFile(it)?.delete() }
                "rename" -> {
                    val o = params["old"].orEmpty(); val n = params["new"].orEmpty()
                    if (o.isNotBlank() && n.isNotBlank()) dirDoc?.findFile(o)?.renameTo(n)
                }
                "save_text" -> {
                    val name = params["name"].orEmpty(); val content = params["content"].orEmpty()
                    val f = dirDoc?.findFile(name) ?: dirDoc?.createFile("text/plain", name)
                    f?.uri?.let { ctx.contentResolver.openOutputStream(it, "wt")?.use { os -> os.write(content.toByteArray()) } }
                }
            }
            writeResponse(out, 303, "See Other", "text/plain", ByteArray(0), mapOf("Location" to "/$dirRel"))
            appendLog(method, path, 303, clientIp, 0L)
            return
        }

        if (method == "POST" && path == "/__upload") {
            val boundary = headers["content-type"].orEmpty().substringAfter("boundary=", "").trim().trim('"')
            val dirRel = parseQueryParams(query)["dir"].orEmpty().trim('/')
            var dirDoc: DocumentFile? = rootDoc
            dirRel.split('/').filter { it.isNotBlank() }.forEach { dirDoc = dirDoc?.findFile(it) }
            if (boundary.isNotEmpty() && dirDoc != null) {
                extractMultipartFiles(bodyBytes, boundary).forEach { (fname, data) ->
                    val safeName = fname.substringAfterLast('/').substringAfterLast('\\').ifBlank { "upload.bin" }
                    dirDoc?.findFile(safeName)?.delete()
                    val created = dirDoc?.createFile(guessMime(safeName), safeName)
                    created?.uri?.let { ctx.contentResolver.openOutputStream(it)?.use { os -> os.write(data) } }
                }
            }
            writeResponse(out, 303, "See Other", "text/plain", ByteArray(0), mapOf("Location" to "/$dirRel"))
            appendLog(method, path, 303, clientIp, 0L)
            return
        }

        val doc = current
        if (doc == null || !doc.exists()) {
            val msg = "404 Not Found".toByteArray()
            writeResponse(out, 404, "Not Found", "text/plain", msg)
            appendLog(method, path, 404, clientIp, msg.size.toLong())
            return
        }

        if (doc.isDirectory) {
            val editName = parseQueryParams(query)["edit"]
            if (!editName.isNullOrBlank()) {
                val f = doc.findFile(editName)
                val txt = f?.uri?.let { ctx.contentResolver.openInputStream(it)?.use { ins -> ins.readBytes().decodeToString() } }.orEmpty()
                val html = buildTextEditorHtml(segments.joinToString("/"), editName, txt).toByteArray()
                writeResponse(out, 200, "OK", "text/html; charset=utf-8", html)
                appendLog(method, path, 200, clientIp, html.size.toLong())
                return
            }
            val items = doc.listFiles().map { ServerFileItem(it.name ?: "item", it.isDirectory, it.length()) }
                .sortedWith(compareBy<ServerFileItem> { !it.isDir }.thenBy { it.name.lowercase() })
            val html = buildDirectoryHtml("/" + segments.joinToString("/"), items).toByteArray()
            writeResponse(out, 200, "OK", "text/html; charset=utf-8", html)
            appendLog(method, path, 200, clientIp, html.size.toLong())
        } else {
            val bytes = ctx.contentResolver.openInputStream(doc.uri)?.use { it.readBytes() } ?: ByteArray(0)
            writeResponse(out, 200, "OK", doc.type ?: guessMime(doc.name.orEmpty()), bytes)
            appendLog(method, path, 200, clientIp, bytes.size.toLong())
        }
    }

    private data class ServerFileItem(val name: String, val isDir: Boolean, val size: Long)

    private fun buildDirectoryHtml(currentPath: String, entries: List<ServerFileItem>): String {
        val cleanDir = currentPath.trim('/')
        val parentHref = if (cleanDir.isEmpty()) null else "/" + cleanDir.substringBeforeLast('/', "")
        val rows = buildString {
            if (parentHref != null) append("""<tr><td><a href="$parentHref">.. (Parent Directory)</a></td><td>DIR</td><td>—</td></tr>""")
            entries.forEach { item ->
                val href = (if (cleanDir.isEmpty()) "" else "/$cleanDir") + "/" + item.name
                val kind = if (item.isDir) "DIR" else "${(item.size + 1023) / 1024} KB"
                val editLink = if (!item.isDir && isEditableText(item.name)) """ <a class="btn" href="?edit=${item.name}">Edit</a>""" else ""
                append(
                    """<tr><td><a href="$href">${if (item.isDir) "📁 " else "📄 "}${escapeHtml(item.name)}</a></td><td>$kind</td><td>$editLink <form style="display:inline" method="post" action="/__action"><input type="hidden" name="action" value="delete"/><input type="hidden" name="dir" value="$cleanDir"/><input type="hidden" name="name" value="${escapeHtml(item.name)}"/><button class="btn danger" type="submit">Del</button></form></td></tr>"""
                )
            }
        }
        return """
        <!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
        <title>Omni Deck Server — ${escapeHtml(currentPath)}</title>
        <style>body{font-family:system-ui,-apple-system,sans-serif;background:#F7F5F0;color:#141413;margin:0;padding:20px;max-width:920px;margin:auto}
        h1{font-size:20px;margin:0 0 6px}.sub{color:#5A5750;font-size:13px;margin-bottom:16px}.bar{display:flex;gap:8px;flex-wrap:wrap;margin-bottom:16px;padding:12px;background:#fff;border:1px solid #DCD8CE;border-radius:6px}
        table{width:100%;border-collapse:collapse;background:#fff;border:1px solid #DCD8CE}th,td{padding:10px 12px;border-bottom:1px solid #ECE9E1;text-align:left;font-size:14px}
        a{color:#0052FF;text-decoration:none}.btn{padding:4px 8px;border:1px solid #DCD8CE;background:#F7F5F0;border-radius:4px;cursor:pointer;font-size:12px}.danger{color:#E60026}input[type=text]{padding:6px 8px;border:1px solid #DCD8CE;border-radius:4px}</style>
        </head><body>
          <h1>Omni Deck Local Server</h1>
          <div class="sub">Path: <b>${escapeHtml(currentPath.ifBlank { "/" })}</b> · <a href="/__zip">⬇ Download ZIP Archive</a></div>
          <div class="bar">
            <form method="post" action="/__upload?dir=$cleanDir" enctype="multipart/form-data"><input type="file" name="file" required/><button class="btn" type="submit">Upload</button></form>
            <form method="post" action="/__action"><input type="hidden" name="action" value="mkdir"/><input type="hidden" name="dir" value="$cleanDir"/><input type="text" name="name" placeholder="New folder" required/><button class="btn" type="submit">Mkdir</button></form>
          </div>
          <table><tr><th>Name</th><th>Size</th><th>Actions</th></tr>$rows</table>
        </body></html>""".trimIndent()
    }

    private fun buildTextEditorHtml(dirRel: String, fileName: String, content: String): String = """
        <!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
        <title>Edit ${escapeHtml(fileName)}</title>
        <style>body{font-family:monospace;background:#F7F5F0;padding:20px;max-width:860px;margin:auto}textarea{width:100%;height:65vh;padding:12px;font-family:monospace;border:1px solid #DCD8CE}</style></head>
        <body><h2>Editing ${escapeHtml(fileName)}</h2>
        <form method="post" action="/__action"><input type="hidden" name="action" value="save_text"/><input type="hidden" name="dir" value="$dirRel"/><input type="hidden" name="name" value="${escapeHtml(fileName)}"/>
        <textarea name="content">${escapeHtml(content)}</textarea><br/><br/><button type="submit">Save</button> <a href="/$dirRel" style="margin-left:12px">Cancel</a></form></body></html>""".trimIndent()

    private fun isEditableText(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in setOf("txt", "md", "json", "html", "css", "js", "xml", "csv", "log", "kt", "py", "yaml", "yml")

    private fun guessMime(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "html", "htm" -> "text/html; charset=utf-8"; "css" -> "text/css; charset=utf-8"; "js" -> "application/javascript; charset=utf-8"
        "json" -> "application/json; charset=utf-8"; "txt", "md", "log", "csv" -> "text/plain; charset=utf-8"
        "png" -> "image/png"; "jpg", "jpeg" -> "image/jpeg"; "webp" -> "image/webp"; "gif" -> "image/gif"; "svg" -> "image/svg+xml"
        "pdf" -> "application/pdf"; "mp4" -> "video/mp4"; "mp3" -> "audio/mpeg"; "zip" -> "application/zip"
        else -> "application/octet-stream"
    }

    private fun writeResponse(out: OutputStream, code: Int, reason: String, contentType: String, body: ByteArray, extraHeaders: Map<String, String> = emptyMap()) {
        val sb = StringBuilder().apply {
            append("HTTP/1.1 $code $reason\r\nContent-Type: $contentType\r\nContent-Length: ${body.size}\r\nConnection: close\r\n")
            if (enableCors) append("Access-Control-Allow-Origin: *\r\n")
            extraHeaders.forEach { (k, v) -> append("$k: $v\r\n") }
            append("\r\n")
        }
        out.write(sb.toString().toByteArray(Charsets.UTF_8))
        if (body.isNotEmpty()) out.write(body)
        out.flush()
    }

    private fun readUntilDoubleCrlf(input: InputStream): ByteArray? {
        val out = ByteArrayOutputStream(); var state = 0
        while (out.size() < 65536) {
            val b = input.read(); if (b == -1) return null; out.write(b)
            state = when { b == '\r'.code && (state == 0 || state == 2) -> state + 1; b == '\n'.code && (state == 1 || state == 3) -> state + 1; b == '\r'.code -> 1; else -> 0 }
            if (state == 4) return out.toByteArray()
        }
        return out.toByteArray()
    }

    private fun readExact(input: InputStream, count: Int): ByteArray {
        val buf = ByteArray(count); var off = 0
        while (off < count) { val n = input.read(buf, off, count - off); if (n == -1) break; off += n }
        return if (off == count) buf else buf.copyOf(off)
    }

    private fun parseQueryParams(raw: String): Map<String, String> = buildMap {
        raw.split('&').filter { it.isNotBlank() }.forEach { pair ->
            val idx = pair.indexOf('=')
            if (idx > 0) put(URLDecoder.decode(pair.substring(0, idx), "UTF-8"), URLDecoder.decode(pair.substring(idx + 1), "UTF-8"))
        }
    }

    private fun extractMultipartFiles(body: ByteArray, boundary: String): List<Pair<String, ByteArray>> {
        val rawStr = String(body, Charsets.ISO_8859_1); val sep = "--$boundary"
        return buildList {
            rawStr.split(sep).forEach { part ->
                val headerEnd = part.indexOf("\r\n\r\n")
                if (headerEnd > 0) {
                    val hdr = part.substring(0, headerEnd)
                    val fname = Regex("filename=\"([^\"]+)\"").find(hdr)?.groupValues?.get(1)
                    if (!fname.isNullOrBlank()) {
                        var content = part.substring(headerEnd + 4)
                        if (content.endsWith("\r\n")) content = content.dropLast(2)
                        add(fname to content.toByteArray(Charsets.ISO_8859_1))
                    }
                }
            }
        }
    }

    private fun escapeHtml(s: String): String = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    private fun quoteJson(s: String): String = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""

    private fun appendLog(method: String, path: String, status: Int, ip: String, bytesOut: Long, payloadPreview: String = "") {
        val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        _logs.value = (listOf(ServerLogEntry(time, method, path, status, ip, bytesOut, payloadPreview)) + _logs.value).take(100)
        _status.value = _status.value.copy(activeRequests = _status.value.activeRequests + 1)
    }

    private fun createSelfSignedSslContext(): Pair<SSLContext, String> {
        val kp = KeyPairGenerator.getInstance("RSA").apply { initialize(2048, SecureRandom()) }.generateKeyPair()
        val certBytes = buildSelfSignedX509Der(kp.public.encoded, kp.private)
        val cert = CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(certBytes)) as X509Certificate
        val sha256 = MessageDigest.getInstance("SHA-256").digest(cert.encoded).joinToString(":") { "%02X".format(it) }
        val ks = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null, null); setKeyEntry("omnideck", kp.private, charArrayOf('o', 'm', 'n', 'i'), arrayOf(cert)) }
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(ks, charArrayOf('o', 'm', 'n', 'i')) }
        val ssl = SSLContext.getInstance("TLS").apply { init(kmf.keyManagers, null, SecureRandom()) }
        return ssl to sha256
    }

    private fun buildSelfSignedX509Der(spkiDer: ByteArray, privateKey: PrivateKey): ByteArray {
        val now = Date(); val later = Date(now.time + 365L * 24 * 3600 * 1000)
        val utcFmt = SimpleDateFormat("yyMMddHHmmss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        fun derTag(tag: Int, content: ByteArray): ByteArray {
            val out = ByteArrayOutputStream(); out.write(tag); val len = content.size
            when { len < 128 -> out.write(len); len < 256 -> { out.write(0x81); out.write(len) }; else -> { out.write(0x82); out.write((len shr 8) and 0xFF); out.write(len and 0xFF) } }
            out.write(content); return out.toByteArray()
        }
        fun derSeq(vararg items: ByteArray) = derTag(0x30, items.fold(ByteArrayOutputStream()) { acc, b -> acc.apply { write(b) } }.toByteArray())
        fun derSet(vararg items: ByteArray) = derTag(0x31, items.fold(ByteArrayOutputStream()) { acc, b -> acc.apply { write(b) } }.toByteArray())
        val sha256RsaOid = derTag(0x06, byteArrayOf(0x2A, 0x86.toByte(), 0x48, 0x86.toByte(), 0xF7.toByte(), 0x0D, 0x01, 0x01, 0x0B))
        val algId = derSeq(sha256RsaOid, byteArrayOf(0x05, 0x00))
        val cnOid = derTag(0x06, byteArrayOf(0x55, 0x04, 0x03))
        val name = derSeq(derSet(derSeq(cnOid, derTag(0x0C, "OmniDeck Local Server".toByteArray(Charsets.UTF_8)))))
        val version = derTag(0xA0, derTag(0x02, BigInteger.valueOf(2).toByteArray()))
        val serial = derTag(0x02, BigInteger(64, SecureRandom()).abs().add(BigInteger.ONE).toByteArray())
        val validity = derSeq(derTag(0x17, utcFmt.format(now).toByteArray(Charsets.US_ASCII)), derTag(0x17, utcFmt.format(later).toByteArray(Charsets.US_ASCII)))
        val tbs = derSeq(version, serial, algId, name, validity, name, spkiDer)
        val sig = Signature.getInstance("SHA256withRSA").run { initSign(privateKey); update(tbs); sign() }
        return derSeq(tbs, algId, derTag(0x03, byteArrayOf(0x00) + sig))
    }
}
