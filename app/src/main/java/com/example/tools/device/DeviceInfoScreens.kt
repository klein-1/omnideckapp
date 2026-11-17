package com.example.tools.device

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.usage.StorageStatsManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaCodecList
import android.media.MediaRecorder
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.Process
import android.os.StatFs
import android.os.storage.StorageManager
import android.view.Choreographer
import android.view.Display
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.design.ChapterHue
import com.example.design.DotMatrixReadout
import com.example.design.DotkitType
import com.example.design.LocalDotkitLook
import com.example.design.PixelBox
import com.example.design.PixelButton
import com.example.design.PixelButtonVariant
import com.example.design.PixelChip
import com.example.design.PixelProgressBar
import com.example.design.PixelStamp
import com.example.design.PixelTextField
import com.example.lib.format.NumberFormatters
import com.example.registry.DataKind
import com.example.registry.ToolMeta
import com.example.services.server.LocalHttpServerEngine
import com.example.tools.common.ToolWorkbenchChrome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

private data class BleDeviceRow(val address: String, val name: String, val rssi: Int)

private data class GpuDetails(val renderer: String, val vendor: String, val glVersion: String)

private data class StorageQuotaDetails(
    val totalBytes: Long,
    val freeBytes: Long,
    val appCodeBytes: Long,
    val appDataBytes: Long,
    val appCacheBytes: Long,
    val source: String
)

private fun queryGpuInfo(): GpuDetails = runCatching {
    val dpy = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
    val vers = IntArray(2)
    EGL14.eglInitialize(dpy, vers, 0, vers, 1)
    val configAttrib = intArrayOf(
        EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
        EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
        EGL14.EGL_NONE
    )
    val configs = arrayOfNulls<EGLConfig>(1)
    val numConfig = IntArray(1)
    EGL14.eglChooseConfig(dpy, configAttrib, 0, configs, 0, 1, numConfig, 0)
    val surfAttrib = intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE)
    val surf = EGL14.eglCreatePbufferSurface(dpy, configs[0], surfAttrib, 0)
    val ctxAttrib = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
    val ctx = EGL14.eglCreateContext(dpy, configs[0], EGL14.EGL_NO_CONTEXT, ctxAttrib, 0)
    EGL14.eglMakeCurrent(dpy, surf, surf, ctx)
    val renderer = GLES20.glGetString(GLES20.GL_RENDERER) ?: "Unknown"
    val vendor = GLES20.glGetString(GLES20.GL_VENDOR) ?: "Unknown"
    val glVer = GLES20.glGetString(GLES20.GL_VERSION) ?: "OpenGL ES"
    EGL14.eglMakeCurrent(dpy, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
    EGL14.eglDestroySurface(dpy, surf)
    EGL14.eglDestroyContext(dpy, ctx)
    EGL14.eglTerminate(dpy)
    GpuDetails(renderer, vendor, glVer)
}.getOrElse { GpuDetails("Unavailable (headless)", Build.HARDWARE, "GLES2+") }

private fun queryRealStorageQuota(context: Context): StorageQuotaDetails {
    var totalB = 0L
    var freeB = 0L
    var appCode = 0L
    var appData = 0L
    var appCache = 0L
    var source = "StatFs"

    if (Build.VERSION.SDK_INT >= 26) {
        runCatching {
            val ssm = context.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
            totalB = ssm.getTotalBytes(StorageManager.UUID_DEFAULT)
            freeB = ssm.getFreeBytes(StorageManager.UUID_DEFAULT)
            val appStats = ssm.queryStatsForPackage(StorageManager.UUID_DEFAULT, context.packageName, Process.myUserHandle())
            appCode = appStats.appBytes
            appData = appStats.dataBytes
            appCache = appStats.cacheBytes
            source = "StorageStatsManager"
        }
    }

    if (totalB <= 0L) {
        val stat = runCatching { StatFs(Environment.getDataDirectory().path) }
            .getOrElse { StatFs(context.filesDir.path) }
        totalB = stat.totalBytes
        freeB = stat.availableBytes
        appData = dirSizeBytes(context.filesDir) + dirSizeBytes(context.getDatabasePath("omnideck.db").parentFile)
        appCache = dirSizeBytes(context.cacheDir)
    }

    return StorageQuotaDetails(totalB, freeB, appCode, appData, appCache, source)
}

private fun dirSizeBytes(dir: File?): Long {
    if (dir == null || !dir.exists()) return 0L
    if (dir.isFile) return dir.length()
    return dir.listFiles()?.sumOf { dirSizeBytes(it) } ?: 0L
}

private fun readCoreFrequenciesMhz(cores: Int): List<String> = (0 until cores).map { c ->
    val curKhz = runCatching { File("/sys/devices/system/cpu/cpu$c/cpufreq/scaling_cur_freq").readText().trim().toLongOrNull() }.getOrNull()
    val maxKhz = runCatching { File("/sys/devices/system/cpu/cpu$c/cpufreq/cpuinfo_max_freq").readText().trim().toLongOrNull() }.getOrNull()
    when {
        curKhz != null && maxKhz != null -> "C$c: ${curKhz / 1000}/${maxKhz / 1000} MHz"
        maxKhz != null -> "C$c: max ${maxKhz / 1000} MHz"
        else -> "C$c: active"
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DeviceInfoScreen(
    meta: ToolMeta,
    onLogNetworkAudit: (toolId: String, host: String, outB: Long, inB: Long, note: String) -> Unit,
    isPinned: Boolean,
    onTogglePin: () -> Unit,
    onBack: () -> Unit,
    onSaveToFiles: (String, DataKind, String) -> Unit,
    onSendToTool: (String, String) -> Unit,
    onStatusToast: (String) -> Unit
) {
    val context = LocalContext.current
    val palette = LocalDotkitLook.current.palette
    val scope = rememberCoroutineScope()
    val hue = ChapterHue.COBALT

    var outputReport by remember(meta.id) { mutableStateOf("") }

    ToolWorkbenchChrome(
        meta = meta,
        isPinned = isPinned,
        onTogglePin = onTogglePin,
        onBack = onBack,
        outputText = outputReport,
        outputFileName = "${meta.id}_report.txt",
        outputKind = DataKind.TEXT,
        onSaveToFiles = onSaveToFiles,
        onSendToTool = onSendToTool,
        helpParagraph = when (meta.id) {
            "devinfo-sys" -> "Probes real device SoC, GPU OpenGL renderer, Display Hz/DPI, per-core CPU scaling frequencies, ActivityManager RAM, StorageStatsManager disk & app quota, codecs, and hardware features."
            "devinfo-bat" -> "Subscribes to Android ACTION_BATTERY_CHANGED telemetry for real voltage, temperature, health, plug source and PowerManager thermal state."
            "devinfo-sensors" -> "Enumerates every hardware/virtual sensor on this Android device, exposes full datasheet metadata, and includes live interactive gauges & waveform scopes."
            "devinfo-net" -> "Inspects active ConnectivityManager link, runs a real TCP socket Ping benchmark, and extracts live GNSS GPS coordinates on permission."
            "devinfo-bt" -> "Scans nearby Bluetooth Low Energy beacons/devices with live RSSI signal strength using Android BluetoothLeScanner."
            "devinfo-screen" -> "Cycles fullscreen solid test colors to reveal stuck/dead subpixels and tracks simultaneous multi-touch finger coordinates."
            else -> "Drives a resonant low-frequency PCM AudioTrack tone sweep to help dislodge moisture or dust from the speaker grille."
        },
        onStatusToast = onStatusToast
    ) {
        when (meta.id) {
            "devinfo-sys" -> {
                var refreshTick by remember { mutableIntStateOf(0) }
                var privacyMasked by remember { mutableStateOf(false) }
                var exportFormat by remember { mutableStateOf("TEXT") } // TEXT, JSON, CSV
                var cpuBench by remember { mutableStateOf<CpuBenchResult?>(null) }
                var diskBench by remember { mutableStateOf<StorageIoBenchResult?>(null) }
                var benchRunning by remember { mutableStateOf(false) }
                var diagResults by remember { mutableStateOf<List<HardwareDiagnosticItem>>(emptyList()) }
                var liveFps by remember { mutableFloatStateOf(0f) }
                var liveFrameMs by remember { mutableFloatStateOf(0f) }

                DisposableEffect(Unit) {
                    var frames = 0
                    var windowStart = System.nanoTime()
                    var lastFrame = windowStart
                    val choreographer = Choreographer.getInstance()
                    val callback = object : Choreographer.FrameCallback {
                        override fun doFrame(frameTimeNanos: Long) {
                            frames++
                            val dtMs = (frameTimeNanos - lastFrame) / 1_000_000f
                            lastFrame = frameTimeNanos
                            if (dtMs in 1f..200f) liveFrameMs = dtMs
                            val elapsed = frameTimeNanos - windowStart
                            if (elapsed >= 500_000_000L) {
                                liveFps = frames * 1_000_000_000f / elapsed.toFloat()
                                frames = 0
                                windowStart = frameTimeNanos
                            }
                            choreographer.postFrameCallback(this)
                        }
                    }
                    choreographer.postFrameCallback(callback)
                    onDispose { choreographer.removeFrameCallback(callback) }
                }

                val gpu = remember { queryGpuInfo() }
                val displayMetrics = context.resources.displayMetrics
                val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
                val activeDisplay = dm?.getDisplay(Display.DEFAULT_DISPLAY)
                val refreshHz = activeDisplay?.refreshRate ?: 60f
                val hdrCaps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && activeDisplay?.isHdr == true) "HDR Supported" else "SDR"
                val codecCount = remember { runCatching { MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.size }.getOrDefault(0) }
                val cameraLenses = remember { DeviceInfoProbes.queryCamera2Capabilities(context) }
                val mountLines = remember(refreshTick) { DeviceInfoProbes.queryMountsSummary() }
                val launcherApps = remember { DeviceInfoProbes.queryLauncherAppCount(context) }

                val pm = context.packageManager
                val featureFlags = remember {
                    listOf(
                        "NFC" to pm.hasSystemFeature(PackageManager.FEATURE_NFC),
                        "BLE" to pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE),
                        "Biometric" to pm.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT),
                        "Vulkan" to pm.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL),
                        "GPS" to pm.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS),
                        "Camera2" to pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY),
                        "USB Host" to pm.hasSystemFeature(PackageManager.FEATURE_USB_HOST)
                    )
                }

                val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                val mi = remember(refreshTick) {
                    ActivityManager.MemoryInfo().also { am?.getMemoryInfo(it) }
                }
                val storage = remember(refreshTick) { queryRealStorageQuota(context) }
                val coreFreqs = remember(refreshTick) { readCoreFrequenciesMhz(Runtime.getRuntime().availableProcessors()) }
                val cpuInfo = remember(refreshTick) {
                    runCatching { File("/proc/cpuinfo").readText().lines().take(18).joinToString("\n") }
                        .getOrDefault("Kernel ${System.getProperty("os.version")} · Arch ${System.getProperty("os.arch")}")
                }
                val suPaths = listOf("/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su", "/vendor/bin/su")
                val rooted = suPaths.any { File(it).exists() } || Build.TAGS?.contains("test-keys") == true
                val vmHeapMaxMb = Runtime.getRuntime().maxMemory() / (1024 * 1024)
                val vmHeapUsedMb = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024)

                val modelLabel = if (privacyMasked) "${Build.MANUFACTURER} [REDACTED-MODEL]" else "${Build.MANUFACTURER} ${Build.MODEL}"
                val boardLabel = if (privacyMasked) "[REDACTED]" else "${Build.BOARD} / ${Build.HARDWARE}"

                LaunchedEffect(refreshTick, privacyMasked, exportFormat, cpuBench, diskBench) {
                    outputReport = when (exportFormat) {
                        "JSON" -> """
                            {
                              "device": "$modelLabel",
                              "android": "${Build.VERSION.RELEASE}",
                              "sdk": ${Build.VERSION.SDK_INT},
                              "board": "$boardLabel",
                              "cores": ${Runtime.getRuntime().availableProcessors()},
                              "gpu": "${gpu.renderer}",
                              "displayPx": "${displayMetrics.widthPixels}x${displayMetrics.heightPixels}",
                              "refreshHz": ${"%.1f".format(refreshHz)},
                              "ramTotalBytes": ${mi.totalMem},
                              "ramAvailBytes": ${mi.availMem},
                              "storageTotalBytes": ${storage.totalBytes},
                              "storageFreeBytes": ${storage.freeBytes},
                              "launcherApps": $launcherApps,
                              "cpuBenchScore": ${cpuBench?.score ?: 0},
                              "diskWriteMBs": ${"%.1f".format(diskBench?.writeMbPerSec ?: 0.0)},
                              "diskReadMBs": ${"%.1f".format(diskBench?.readMbPerSec ?: 0.0)}
                            }
                        """.trimIndent()
                        "CSV" -> buildString {
                            appendLine("key,value")
                            appendLine("device,\"$modelLabel\"")
                            appendLine("android,${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
                            appendLine("board,\"$boardLabel\"")
                            appendLine("cores,${Runtime.getRuntime().availableProcessors()}")
                            appendLine("gpu,\"${gpu.renderer}\"")
                            appendLine("display,${displayMetrics.widthPixels}x${displayMetrics.heightPixels}@${"%.0f".format(refreshHz)}Hz")
                            appendLine("ram_avail,${NumberFormatters.formatBytes(mi.availMem)}")
                            appendLine("storage_free,${NumberFormatters.formatBytes(storage.freeBytes)}")
                            appendLine("launcher_apps,$launcherApps")
                            cpuBench?.let { appendLine("cpu_bench_score,${it.score}") }
                            diskBench?.let { appendLine("disk_rw_mbs,${"%.1f".format(it.writeMbPerSec)}/${"%.1f".format(it.readMbPerSec)}") }
                        }
                        else -> buildString {
                            appendLine("DEVICE: $modelLabel")
                            appendLine("OS: Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}) · Patch: ${Build.VERSION.SECURITY_PATCH}")
                            appendLine("KERNEL: ${System.getProperty("os.version")} · ARCH: ${System.getProperty("os.arch")}")
                            appendLine("SOC / BOARD: $boardLabel · Cores: ${Runtime.getRuntime().availableProcessors()}")
                            appendLine("GPU: ${gpu.renderer} (${gpu.vendor}) · ${gpu.glVersion}")
                            appendLine("DISPLAY: ${displayMetrics.widthPixels}x${displayMetrics.heightPixels} px · ${displayMetrics.densityDpi} dpi · %.1f Hz · $hdrCaps".format(refreshHz))
                            appendLine("RAM: ${NumberFormatters.formatBytes(mi.availMem)} free / ${NumberFormatters.formatBytes(mi.totalMem)} total")
                            appendLine("STORAGE (${storage.source}): ${NumberFormatters.formatBytes(storage.freeBytes)} free / ${NumberFormatters.formatBytes(storage.totalBytes)} total")
                            appendLine("APP QUOTA: App=${NumberFormatters.formatBytes(storage.appCodeBytes)} Data=${NumberFormatters.formatBytes(storage.appDataBytes)} Cache=${NumberFormatters.formatBytes(storage.appCacheBytes)}")
                            appendLine("MEDIA CODECS: $codecCount · LAUNCHER APPS: $launcherApps · VM HEAP: $vmHeapUsedMb / $vmHeapMaxMb MB")
                            cpuBench?.let { appendLine("CPU BENCH: Score ${it.score} (1T ${it.singleCoreMs}ms / MT ${it.multiCoreMs}ms)") }
                            diskBench?.let { appendLine("STORAGE IO (${it.testSizeMb}MB): Write ${"%.1f".format(it.writeMbPerSec)} MB/s · Read ${"%.1f".format(it.readMbPerSec)} MB/s") }
                        }
                    }
                }

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(modelLabel, style = DotkitType.editorialHeading(18), color = palette.ink)
                                Text("Board $boardLabel · Kernel ${System.getProperty("os.version")}", style = DotkitType.monoLabel(10), color = palette.ink2)
                            }
                            PixelStamp(if (rooted) "ROOT INDICATORS" else "VERIFIED", if (rooted) palette.warn else palette.ok)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            PixelChip(if (privacyMasked) "Privacy: Masked" else "Privacy: Full", selected = privacyMasked, chapterHue = hue, onClick = { privacyMasked = !privacyMasked })
                            listOf("TEXT", "JSON", "CSV").forEach { fmt ->
                                PixelChip(fmt, selected = exportFormat == fmt, chapterHue = hue, onClick = { exportFormat = fmt })
                            }
                        }
                        HorizontalDivider(thickness = 1.dp, color = palette.hairline)

                        Text("Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}) · Patch ${Build.VERSION.SECURITY_PATCH}", style = DotkitType.monoData(12), color = palette.ink)
                        Text("ABIs: ${Build.SUPPORTED_ABIS.joinToString()}", style = DotkitType.monoData(11), color = palette.ink2)
                        Text("GPU: ${gpu.renderer} · ${gpu.vendor}", style = DotkitType.monoData(12), color = palette.chapterColor(hue))
                        Text("Display: ${displayMetrics.widthPixels} × ${displayMetrics.heightPixels} · ${"%.0f".format(refreshHz)} Hz · ${displayMetrics.densityDpi} DPI · $hdrCaps", style = DotkitType.monoData(12), color = palette.ink)
                        Text("Live UI Compositor: ${"%.1f".format(liveFps)} FPS (${"%.1f".format(liveFrameMs)} ms/frame) · Launcher Apps: $launcherApps", style = DotkitType.monoData(11), color = palette.ok)
                        Text("JVM Heap: $vmHeapUsedMb / $vmHeapMaxMb MB · Hardware Codecs: $codecCount", style = DotkitType.monoData(11), color = palette.ink2)

                        val ramUsedPct = if (mi.totalMem > 0) 1f - (mi.availMem.toFloat() / mi.totalMem.toFloat()) else 0f
                        Text("RAM: ${NumberFormatters.formatBytes(mi.totalMem - mi.availMem)} used / ${NumberFormatters.formatBytes(mi.totalMem)} total", style = DotkitType.monoLabel(11), color = palette.ink2)
                        PixelProgressBar(progress = ramUsedPct, chapterHue = hue)

                        val storUsedPct = if (storage.totalBytes > 0) 1f - (storage.freeBytes.toFloat() / storage.totalBytes.toFloat()) else 0f
                        Text("Storage (${storage.source}): ${NumberFormatters.formatBytes(storage.freeBytes)} free / ${NumberFormatters.formatBytes(storage.totalBytes)} total", style = DotkitType.monoLabel(11), color = palette.ink2)
                        PixelProgressBar(progress = storUsedPct, chapterHue = ChapterHue.TEAL)
                        Text(
                            "App Quota: APK ${NumberFormatters.formatBytes(storage.appCodeBytes)} · Data ${NumberFormatters.formatBytes(storage.appDataBytes)} · Cache ${NumberFormatters.formatBytes(storage.appCacheBytes)}",
                            style = DotkitType.monoData(11),
                            color = palette.ink2
                        )

                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            featureFlags.forEach { (fname, avail) ->
                                PixelStamp("$fname: ${if (avail) "YES" else "NO"}", if (avail) palette.ok else palette.ink3)
                            }
                        }

                        PixelButton("Refresh Live Specs", onClick = { refreshTick++ }, variant = PixelButtonVariant.OUTLINE, chapterHue = hue, modifier = Modifier.fillMaxWidth())
                    }
                }

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("REAL HARDWARE BENCHMARKS & SELF-CHECK", style = DotkitType.monoLabel(10), color = palette.ink2)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PixelButton(
                                text = if (benchRunning) "Running…" else "CPU Prime Bench",
                                enabled = !benchRunning,
                                chapterHue = hue,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    benchRunning = true
                                    scope.launch {
                                        cpuBench = withContext(Dispatchers.Default) { DeviceInfoProbes.runCpuPrimeBenchmark() }
                                        benchRunning = false
                                    }
                                }
                            )
                            PixelButton(
                                text = if (benchRunning) "Running…" else "Disk 12MB I/O",
                                enabled = !benchRunning,
                                variant = PixelButtonVariant.OUTLINE,
                                chapterHue = hue,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    benchRunning = true
                                    scope.launch {
                                        diskBench = withContext(Dispatchers.IO) { DeviceInfoProbes.runStorageSequentialBenchmark(context.cacheDir, 12) }
                                        benchRunning = false
                                    }
                                }
                            )
                        }
                        cpuBench?.let { c ->
                            Text("CPU Score: ${c.score} · Single-thread: ${c.singleCoreMs} ms · Multi-thread: ${c.multiCoreMs} ms (${c.primesFound} primes)", style = DotkitType.monoData(12), color = palette.ok)
                        }
                        diskBench?.let { d ->
                            Text("Sequential ${d.testSizeMb}MB I/O: Write ${"%.1f".format(d.writeMbPerSec)} MB/s (${d.writeMs}ms) · Read ${"%.1f".format(d.readMbPerSec)} MB/s (${d.readMs}ms)", style = DotkitType.monoData(12), color = palette.chapterColor(hue))
                        }
                        PixelButton(
                            text = "Run One-Tap Hardware Diagnostic Checklist",
                            variant = PixelButtonVariant.OUTLINE,
                            chapterHue = hue,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { diagResults = DeviceInfoProbes.runHardwareDiagnosticChecklist(context) }
                        )
                        if (diagResults.isNotEmpty()) {
                            diagResults.forEach { item ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(item.subsystem, style = DotkitType.bodyUi(13, bold = true), color = palette.ink)
                                        Text(item.detail, style = DotkitType.monoData(11), color = palette.ink2)
                                    }
                                    PixelStamp(if (item.passed) "PASS" else "WARN", if (item.passed) palette.ok else palette.warn)
                                }
                            }
                        }
                    }
                }

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("CAMERA2 LENS MATRIX (${cameraLenses.size})", style = DotkitType.monoLabel(10), color = palette.ink2)
                        if (cameraLenses.isEmpty()) {
                            Text("No Camera2 lens characteristics exposed.", style = DotkitType.monoData(11), color = palette.ink3)
                        } else {
                            cameraLenses.forEach { cam ->
                                Text(
                                    "ID ${cam.cameraId} [${cam.facing}] · ${"%.1f".format(cam.megapixels)}MP · ${cam.focalLengthsMm} · ${cam.apertures} · ISO ${cam.isoRange} · RAW:${if (cam.hasRaw) "Y" else "N"} · OIS:${if (cam.hasOis) "Y" else "N"}",
                                    style = DotkitType.monoData(11),
                                    color = palette.ink
                                )
                            }
                        }
                        HorizontalDivider(thickness = 1.dp, color = palette.hairline)
                        Text("STORAGE MOUNTS (/proc/mounts)", style = DotkitType.monoLabel(10), color = palette.ink2)
                        mountLines.forEach { m -> Text(m, style = DotkitType.monoData(10), color = palette.ink2) }
                    }
                }

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("CPU CORES (${Runtime.getRuntime().availableProcessors()})", style = DotkitType.monoLabel(10), color = palette.ink2)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            coreFreqs.forEach { cf -> PixelStamp(cf, palette.chapterColor(hue)) }
                        }
                        HorizontalDivider(thickness = 1.dp, color = palette.hairline)
                        Text("/proc/cpuinfo", style = DotkitType.monoLabel(10), color = palette.ink2)
                        Text(cpuInfo, style = DotkitType.monoData(11), color = palette.ink)
                    }
                }
            }

            "devinfo-bat" -> {
                var refreshTick by remember { mutableIntStateOf(0) }
                val tempHistory = remember { mutableStateListOf<Float>() }
                val mvHistory = remember { mutableStateListOf<Int>() }
                LaunchedEffect(Unit) {
                    while (true) {
                        delay(2500)
                        refreshTick++
                    }
                }
                val thermalZones = remember(refreshTick) { DeviceInfoProbes.queryThermalZones() }
                val batData = remember(refreshTick) {
                    val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                    val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                    val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
                    val pct = if (level >= 0 && scale > 0) (level * 100) / scale else -1
                    val mv = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
                    val tempC = (intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
                    tempHistory.add(tempC); if (tempHistory.size > 40) tempHistory.removeAt(0)
                    mvHistory.add(mv); if (mvHistory.size > 40) mvHistory.removeAt(0)
                    val tech = intent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "Unknown"
                    val plugged = when (intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) {
                        BatteryManager.BATTERY_PLUGGED_AC -> "AC Charger"
                        BatteryManager.BATTERY_PLUGGED_USB -> "USB"
                        BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
                        else -> "On Battery"
                    }
                    val health = when (intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, 0) ?: 0) {
                        BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
                        BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
                        BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
                        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
                        else -> "Normal"
                    }
                    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                    val thermal = if (Build.VERSION.SDK_INT >= 29 && pm != null) {
                        when (pm.currentThermalStatus) {
                            PowerManager.THERMAL_STATUS_NONE -> "Nominal"
                            PowerManager.THERMAL_STATUS_LIGHT -> "Light Warmth"
                            PowerManager.THERMAL_STATUS_MODERATE -> "Moderate"
                            PowerManager.THERMAL_STATUS_SEVERE -> "Severe"
                            PowerManager.THERMAL_STATUS_CRITICAL -> "Critical"
                            else -> "Elevated"
                        }
                    } else "SDK < 29"

                    val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
                    val currentMa = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)?.let { it / 1000 } ?: 0
                    val wattEst = (mv / 1000.0) * (kotlin.math.abs(currentMa) / 1000.0)

                    outputReport = "Battery=$pct% | ${mv}mV | ${currentMa}mA (${"%.2f".format(wattEst)}W) | ${"%.1f".format(tempC)}°C | $plugged | Health=$health | Tech=$tech | Thermal=$thermal"
                    listOf(
                        "Level" to "$pct%",
                        "Voltage" to "$mv mV",
                        "Current / Power" to "${currentMa} mA (${"%.2f".format(wattEst)} W)",
                        "Temp" to "${"%.1f".format(tempC)} °C",
                        "Source" to plugged,
                        "Health" to health,
                        "Chem" to tech,
                        "Thermal" to thermal
                    )
                }

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        DotMatrixReadout(value = batData.first().second, label = "BATTERY CHARGE & THERMAL", chapterHue = hue)
                        batData.forEach { (k, v) ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(k, style = DotkitType.monoLabel(11), color = palette.ink2)
                                Text(v, style = DotkitType.monoData(13), color = palette.ink)
                            }
                        }
                        if (tempHistory.size > 1) {
                            Text("LIVE TEMP & VOLTAGE HISTORY (${tempHistory.size} samples)", style = DotkitType.monoLabel(10), color = palette.ink2)
                            val accentCol = palette.chapterColor(hue)
                            Canvas(
                                Modifier.fillMaxWidth().height(90.dp).clip(RoundedCornerShape(10.dp)).background(palette.paper).border(1.dp, palette.hairline, RoundedCornerShape(10.dp)).padding(8.dp)
                            ) {
                                val minT = (tempHistory.minOrNull() ?: 20f) - 1f
                                val maxT = (tempHistory.maxOrNull() ?: 40f).coerceAtLeast(minT + 2f)
                                val pT = Path()
                                tempHistory.forEachIndexed { idx, t ->
                                    val x = idx.toFloat() / (tempHistory.size - 1) * size.width
                                    val y = size.height - ((t - minT) / (maxT - minT)) * size.height
                                    if (idx == 0) pT.moveTo(x, y) else pT.lineTo(x, y)
                                }
                                drawPath(pT, palette.warn, style = Stroke(3.5f))

                                val minV = (mvHistory.minOrNull() ?: 3500) - 50
                                val maxV = (mvHistory.maxOrNull() ?: 4400).coerceAtLeast(minV + 100)
                                val pV = Path()
                                mvHistory.forEachIndexed { idx, mv ->
                                    val x = idx.toFloat() / (mvHistory.size - 1) * size.width
                                    val y = size.height - ((mv - minV).toFloat() / (maxV - minV)) * size.height
                                    if (idx == 0) pV.moveTo(x, y) else pV.lineTo(x, y)
                                }
                                drawPath(pV, accentCol, style = Stroke(3.5f))
                            }
                        }
                    }
                }

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("SOC / SYSFS THERMAL ZONES", style = DotkitType.monoLabel(10), color = palette.ink2)
                        if (thermalZones.isEmpty()) {
                            Text("SELinux restricts /sys/class/thermal/ readout on this device; PowerManager status shown above.", style = DotkitType.monoData(11), color = palette.ink3)
                        } else {
                            thermalZones.forEach { z ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(z.name, style = DotkitType.monoData(12), color = palette.ink2)
                                    PixelStamp("${"%.1f".format(z.tempCelsius)} °C", if (z.tempCelsius > 60f) palette.warn else palette.ok)
                                }
                            }
                        }
                    }
                }
            }

            "devinfo-sensors" -> {
                val sm = remember { context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager }
                val allSensors = remember { sm?.getSensorList(Sensor.TYPE_ALL).orEmpty() }
                var selectedSensor by remember { mutableStateOf(allSensors.firstOrNull()) }
                var sensorSearch by remember { mutableStateOf("") }
                var xyz by remember { mutableStateOf(FloatArray(4)) }
                val historyX = remember { mutableStateListOf<Float>() }
                val historyY = remember { mutableStateListOf<Float>() }
                val historyZ = remember { mutableStateListOf<Float>() }
                var samplingUs by remember { mutableIntStateOf(SensorManager.SENSOR_DELAY_UI) }

                DisposableEffect(selectedSensor, samplingUs) {
                    historyX.clear(); historyY.clear(); historyZ.clear()
                    val s = selectedSensor
                    val listener = if (s != null && sm != null) {
                        val l = object : SensorEventListener {
                            override fun onSensorChanged(event: SensorEvent) {
                                val next = FloatArray(4) { idx -> event.values.getOrNull(idx) ?: 0f }
                                xyz = next
                                historyX.add(next[0]); if (historyX.size > 60) historyX.removeAt(0)
                                historyY.add(next[1]); if (historyY.size > 60) historyY.removeAt(0)
                                historyZ.add(next[2]); if (historyZ.size > 60) historyZ.removeAt(0)
                                outputReport = "${s.name}: X=${"%.3f".format(next[0])} Y=${"%.3f".format(next[1])} Z=${"%.3f".format(next[2])}"
                            }
                            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
                        }
                        sm.registerListener(l, s, samplingUs)
                        l
                    } else null
                    onDispose { if (listener != null) sm?.unregisterListener(listener) }
                }

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("PLAYABLE SENSOR EXPLORER (${allSensors.size})", style = DotkitType.monoLabel(11), color = palette.ink2)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                PixelChip("Normal", selected = samplingUs == SensorManager.SENSOR_DELAY_NORMAL, chapterHue = hue, onClick = { samplingUs = SensorManager.SENSOR_DELAY_NORMAL })
                                PixelChip("Fast", selected = samplingUs == SensorManager.SENSOR_DELAY_GAME, chapterHue = hue, onClick = { samplingUs = SensorManager.SENSOR_DELAY_GAME })
                            }
                        }

                        selectedSensor?.let { s ->
                            DotMatrixReadout(
                                value = "${"%.2f".format(xyz[0])} | ${"%.2f".format(xyz[1])} | ${"%.2f".format(xyz[2])}",
                                label = "${s.name} · v${s.version} · ${s.vendor}",
                                chapterHue = hue,
                                sizeSp = 24
                            )
                            Text(
                                text = "Max: ${s.maximumRange} · Res: ${s.resolution} · Power: ${s.power} mA · MinDelay: ${s.minDelay} µs · FIFO: ${s.fifoMaxEventCount} · WakeUp: ${s.isWakeUpSensor}",
                                style = DotkitType.monoData(11),
                                color = palette.ink2
                            )

                            // Interactive Playable Arena per Sensor Type
                            val accentCol = palette.chapterColor(hue)
                            Canvas(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(170.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(palette.paper)
                                    .border(1.dp, palette.hairline, RoundedCornerShape(12.dp))
                                    .padding(8.dp)
                            ) {
                                when (s.type) {
                                    Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GRAVITY, Sensor.TYPE_GYROSCOPE, Sensor.TYPE_LINEAR_ACCELERATION -> {
                                        val cx = size.width / 2f; val cy = size.height / 2f
                                        val r = size.minDimension * 0.44f
                                        drawCircle(palette.hairline, r, Offset(cx, cy), style = Stroke(2f))
                                        drawCircle(palette.hairline, r * 0.45f, Offset(cx, cy), style = Stroke(1.5f))
                                        drawLine(palette.hairline, Offset(cx - r, cy), Offset(cx + r, cy), 1.5f)
                                        drawLine(palette.hairline, Offset(cx, cy - r), Offset(cx, cy + r), 1.5f)
                                        val maxS = s.maximumRange.coerceAtLeast(10f).coerceAtMost(20f)
                                        val bx = cx - (xyz[0] / maxS).coerceIn(-1f, 1f) * r * 0.85f
                                        val by = cy + (xyz[1] / maxS).coerceIn(-1f, 1f) * r * 0.85f
                                        drawCircle(accentCol.copy(alpha = 0.25f), 20.dp.toPx(), Offset(bx, by))
                                        drawCircle(accentCol, 12.dp.toPx(), Offset(bx, by))
                                    }
                                    Sensor.TYPE_MAGNETIC_FIELD -> {
                                        val cx = size.width / 2f; val cy = size.height / 2f
                                        val r = size.minDimension * 0.44f
                                        drawCircle(palette.hairline, r, Offset(cx, cy), style = Stroke(2.5f))
                                        val angle = atan2(xyz[1].toDouble(), xyz[0].toDouble())
                                        drawLine(palette.err, Offset(cx, cy), Offset(cx + (r * 0.85f * cos(angle)).toFloat(), cy + (r * 0.85f * sin(angle)).toFloat()), 6f)
                                    }
                                    else -> {
                                        // 3-Axis Live Waveform Scope
                                        val series = listOf(historyX to palette.err, historyY to accentCol, historyZ to palette.ok)
                                        val maxAbs = (historyX + historyY + historyZ).maxOfOrNull { kotlin.math.abs(it) }?.coerceAtLeast(1f) ?: 1f
                                        series.forEach { (pts, col) ->
                                            if (pts.size > 1) {
                                                val path = Path()
                                                pts.forEachIndexed { idx, v ->
                                                    val x = idx.toFloat() / (pts.size - 1) * size.width
                                                    val y = size.height / 2f - (v / maxAbs) * (size.height * 0.42f)
                                                    if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                                                }
                                                drawPath(path, col, style = Stroke(3.5f))
                                            }
                                        }
                                    }
                                }
                            }
                            if (s.type == Sensor.TYPE_PRESSURE && xyz[0] > 0f) {
                                val altM = 44330.0 * (1.0 - (xyz[0] / 1013.25).pow(1.0 / 5.255))
                                Text("Estimated Barometric Altitude: %.1f m (Sea level ref 1013.25 hPa)".format(altM), style = DotkitType.monoData(12), color = palette.ok)
                            }
                        }

                        PixelTextField(sensorSearch, { sensorSearch = it }, "Filter sensors by name or vendor…", chapterHue = hue)
                        val filteredSensors = remember(allSensors, sensorSearch) {
                            val q = sensorSearch.trim().lowercase()
                            if (q.isEmpty()) allSensors else allSensors.filter { it.name.lowercase().contains(q) || it.vendor.lowercase().contains(q) }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            filteredSensors.forEach { s ->
                                PixelChip(
                                    label = s.name.take(20),
                                    selected = selectedSensor?.name == s.name && selectedSensor?.type == s.type,
                                    chapterHue = hue,
                                    onClick = { selectedSensor = s }
                                )
                            }
                        }
                    }
                }
            }

            "devinfo-net" -> {
                var pingHost by remember { mutableStateOf("1.1.1.1") }
                var pingPort by remember { mutableStateOf("443") }
                var pingOutput by remember { mutableStateOf("") }
                var pinging by remember { mutableStateOf(false) }
                val pingRtts = remember { mutableStateListOf<Long>() }

                var scanHost by remember { mutableStateOf("127.0.0.1") }
                var scanningPorts by remember { mutableStateOf(false) }
                var portResults by remember { mutableStateOf<List<PortScanEntry>>(emptyList()) }

                var ipAsn by remember { mutableStateOf<PublicIpAsnResult?>(null) }
                var loadingIp by remember { mutableStateOf(false) }

                val ips = remember { LocalHttpServerEngine.getLocalIpAddresses() }
                val cm = remember { context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager }
                val caps = remember(cm) { cm?.activeNetwork?.let { cm.getNetworkCapabilities(it) } }
                val netDesc = remember(caps) {
                    when {
                        caps == null -> "Offline / No active network"
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                            val sig = if (Build.VERSION.SDK_INT >= 29) "${caps.signalStrength} dBm" else "Connected"
                            "Wi-Fi ($sig) · Down ${caps.linkDownstreamBandwidthKbps / 1000} Mbps"
                        }
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular · Down ${caps.linkDownstreamBandwidthKbps / 1000} Mbps"
                        else -> "Ethernet / VPN"
                    }
                }

                var hasLocPerm by remember {
                    mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
                }
                var gpsActive by remember { mutableStateOf(false) }
                var lastLocation by remember { mutableStateOf<Location?>(null) }
                var satellites by remember { mutableStateOf<List<GnssSatRow>>(emptyList()) }
                val locLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                    hasLocPerm = granted
                    if (granted) gpsActive = true else onStatusToast("Location permission denied.")
                }

                DisposableEffect(gpsActive, hasLocPerm) {
                    val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                    var listener: LocationListener? = null
                    var gnssCb: GnssStatus.Callback? = null
                    if (gpsActive && hasLocPerm && lm != null) {
                        @SuppressLint("MissingPermission")
                        fun startUpdates() {
                            val l = LocationListener { loc -> lastLocation = loc }
                            listener = l
                            runCatching { lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1500L, 0f, l) }
                            runCatching { lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1500L, 0f, l) }
                            val cb = object : GnssStatus.Callback() {
                                override fun onSatelliteStatusChanged(status: GnssStatus) {
                                    val list = (0 until status.satelliteCount).map { i ->
                                        GnssSatRow(
                                            svid = status.getSvid(i),
                                            constellation = DeviceInfoProbes.constellationLabel(status.getConstellationType(i)),
                                            cn0DbHz = status.getCn0DbHz(i),
                                            elevationDeg = status.getElevationDegrees(i),
                                            azimuthDeg = status.getAzimuthDegrees(i),
                                            usedInFix = status.usedInFix(i)
                                        )
                                    }
                                    satellites = list
                                }
                            }
                            gnssCb = cb
                            runCatching { lm.registerGnssStatusCallback(cb, Handler(Looper.getMainLooper())) }
                        }
                        startUpdates()
                    }
                    onDispose {
                        listener?.let { lm?.removeUpdates(it) }
                        gnssCb?.let { runCatching { lm?.unregisterGnssStatusCallback(it) } }
                    }
                }

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("NETWORK, LOCAL IP & PUBLIC ASN", style = DotkitType.monoLabel(10), color = palette.ink2)
                        Text(netDesc, style = DotkitType.bodyUi(14, bold = true), color = palette.ink)
                        Text("LAN IPs: ${ips.joinToString(", ").ifBlank { "127.0.0.1" }}", style = DotkitType.monoData(12), color = palette.ink2)
                        PixelButton(
                            text = if (loadingIp) "Resolving Public IP & ASN…" else "Lookup Public IP & ASN (ipwho.is)",
                            enabled = !loadingIp,
                            variant = PixelButtonVariant.OUTLINE,
                            chapterHue = hue,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                loadingIp = true
                                scope.launch {
                                    val res = withContext(Dispatchers.IO) { DeviceInfoProbes.fetchPublicIpAsn() }
                                    ipAsn = res
                                    loadingIp = false
                                    if (res.error == null) {
                                        onLogNetworkAudit(meta.id, "ipwho.is", 128L, 512L, "IP ${res.ip} ${res.asn}")
                                        outputReport = "$netDesc\nPublic IP: ${res.ip} (${res.asn} · ${res.isp}) ${res.city}, ${res.country}"
                                    }
                                }
                            }
                        )
                        ipAsn?.let { info ->
                            if (info.error != null) {
                                Text("Lookup error: ${info.error}", style = DotkitType.monoData(11), color = palette.err)
                            } else {
                                Text("IP: ${info.ip} · ${info.asn} (${info.isp})", style = DotkitType.monoData(12), color = palette.ok)
                                Text("Geo: ${info.city}, ${info.region}, ${info.country} · TZ: ${info.timezone}", style = DotkitType.monoData(11), color = palette.ink2)
                            }
                        }
                    }
                }

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("TCP SOCKET PING BENCHMARK", style = DotkitType.monoLabel(10), color = palette.ink2)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PixelTextField(pingHost, { pingHost = it }, "Host (e.g. 1.1.1.1)", modifier = Modifier.weight(2f))
                            PixelTextField(pingPort, { pingPort = it.filter(Char::isDigit) }, "Port", modifier = Modifier.weight(1f))
                        }
                        PixelButton(
                            text = if (pinging) "Pinging (6 probes)..." else "Run Multi-Ping (6x)",
                            enabled = !pinging && pingHost.isNotBlank(),
                            chapterHue = hue,
                            onClick = {
                                pinging = true
                                pingRtts.clear()
                                scope.launch {
                                    val port = pingPort.toIntOrNull()?.coerceIn(1, 65535) ?: 443
                                    var errs = 0
                                    withContext(Dispatchers.IO) {
                                        repeat(6) {
                                            val t0 = System.nanoTime()
                                            runCatching {
                                                Socket().use { s -> s.connect(InetSocketAddress(pingHost.trim(), port), 3000) }
                                            }.onSuccess {
                                                val ms = (System.nanoTime() - t0) / 1_000_000L
                                                withContext(Dispatchers.Main) { pingRtts += ms }
                                            }.onFailure { errs++ }
                                        }
                                    }
                                    val avg = if (pingRtts.isNotEmpty()) pingRtts.average() else 0.0
                                    val jitter = if (pingRtts.size > 1) pingRtts.zipWithNext { a, b -> kotlin.math.abs(a - b) }.average() else 0.0
                                    val res = "Host: ${pingHost.trim()}:$port\nOK: ${pingRtts.size}/6 (Loss ${(errs * 100) / 6}%) · Avg ${"%.1f".format(avg)}ms · Jitter ${"%.1f".format(jitter)}ms"
                                    pingOutput = res
                                    outputReport = "$netDesc\n$res"
                                    onLogNetworkAudit(meta.id, "${pingHost.trim()}:$port", 384L, 384L, "Avg ${"%.1f".format(avg)}ms")
                                    pinging = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (pingRtts.isNotEmpty()) {
                            val barCol = palette.chapterColor(hue)
                            Canvas(Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(8.dp)).background(palette.paper).padding(6.dp)) {
                                val maxRtt = (pingRtts.maxOrNull() ?: 10L).coerceAtLeast(10L).toFloat()
                                val w = size.width / pingRtts.size.coerceAtLeast(1)
                                pingRtts.forEachIndexed { idx, rtt ->
                                    val bh = (rtt / maxRtt) * size.height
                                    drawRect(barCol, Offset(idx * w + 4f, size.height - bh), Size((w - 8f).coerceAtLeast(4f), bh))
                                }
                            }
                        }
                        if (pingOutput.isNotBlank()) Text(pingOutput, style = DotkitType.monoData(12), color = palette.ink)
                    }
                }

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("LAN / HOST COMMON TCP PORT SCANNER", style = DotkitType.monoLabel(10), color = palette.ink2)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            PixelTextField(scanHost, { scanHost = it }, "Host / LAN IP", modifier = Modifier.weight(1f))
                            PixelButton(
                                text = if (scanningPorts) "Scanning…" else "Scan 15 Ports",
                                enabled = !scanningPorts && scanHost.isNotBlank(),
                                variant = PixelButtonVariant.OUTLINE,
                                chapterHue = hue,
                                onClick = {
                                    scanningPorts = true
                                    scope.launch {
                                        val res = withContext(Dispatchers.IO) { DeviceInfoProbes.scanCommonPorts(scanHost) }
                                        portResults = res
                                        val openStr = res.filter { it.open }.joinToString { "${it.port}/${it.service}" }
                                        onLogNetworkAudit(meta.id, scanHost.trim(), 512L, 512L, "Ports open: ${openStr.ifBlank { "none" }}")
                                        scanningPorts = false
                                    }
                                }
                            )
                        }
                        if (portResults.isNotEmpty()) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                portResults.forEach { entry ->
                                    PixelStamp(
                                        "${entry.port} ${entry.service}: ${if (entry.open) "OPEN (${entry.latencyMs}ms)" else "CLOSED"}",
                                        if (entry.open) palette.ok else palette.ink3
                                    )
                                }
                            }
                        }
                    }
                }

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("GNSS SKY-PLOT & COORDINATE FORMATS (DD / DMS / UTM)", style = DotkitType.monoLabel(10), color = palette.ink2)
                        val loc = lastLocation
                        if (loc != null) {
                            val (dd, dms, utm) = remember(loc.latitude, loc.longitude) {
                                DeviceInfoProbes.formatLatLonDmsUtm(loc.latitude, loc.longitude)
                            }
                            DotMatrixReadout(dd, "ALT ${loc.altitude.toInt()}m · ±${loc.accuracy.toInt()}m · SATS ${satellites.count { it.usedInFix }}/${satellites.size}", hue)
                            Text("DMS: $dms", style = DotkitType.monoData(12), color = palette.ink)
                            Text("UTM: $utm", style = DotkitType.monoData(12), color = palette.chapterColor(hue))
                            outputReport = "$netDesc\nDD: $dd\nDMS: $dms\nUTM: $utm"
                        } else {
                            Text(if (gpsActive) "Acquiring satellite / network fix…" else "Tap Start GPS to view Sky-Plot, constellations & DD/DMS/UTM coordinates.", style = DotkitType.bodyUi(13), color = palette.ink2)
                        }
                        if (satellites.isNotEmpty()) {
                            val skyAccent = palette.chapterColor(hue)
                            Canvas(
                                Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(12.dp)).background(palette.paper).border(1.dp, palette.hairline, RoundedCornerShape(12.dp)).padding(8.dp)
                            ) {
                                val cx = size.width / 2f; val cy = size.height / 2f
                                val r = size.minDimension * 0.44f
                                drawCircle(palette.hairline, r, Offset(cx, cy), style = Stroke(2f))
                                drawCircle(palette.hairline, r * 0.66f, Offset(cx, cy), style = Stroke(1.5f))
                                drawCircle(palette.hairline, r * 0.33f, Offset(cx, cy), style = Stroke(1.5f))
                                drawLine(palette.hairline, Offset(cx - r, cy), Offset(cx + r, cy), 1.5f)
                                drawLine(palette.hairline, Offset(cx, cy - r), Offset(cx, cy + r), 1.5f)
                                satellites.forEach { sat ->
                                    val radius = ((90f - sat.elevationDeg.coerceIn(0f, 90f)) / 90f) * r
                                    val rad = Math.toRadians((sat.azimuthDeg - 90f).toDouble())
                                    val sx = cx + (radius * cos(rad)).toFloat()
                                    val sy = cy + (radius * sin(rad)).toFloat()
                                    val col = if (sat.usedInFix) palette.ok else skyAccent
                                    drawCircle(col, if (sat.usedInFix) 9f else 6f, Offset(sx, sy))
                                }
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                satellites.sortedByDescending { it.cn0DbHz }.take(12).forEach { sat ->
                                    PixelStamp("${sat.constellation}#${sat.svid} ${sat.cn0DbHz.toInt()}dBHz", if (sat.usedInFix) palette.ok else palette.ink2)
                                }
                            }
                        }
                        PixelButton(
                            text = if (gpsActive) "Stop GPS & Sky-Plot" else "Start GPS & GNSS Sky-Plot",
                            variant = if (gpsActive) PixelButtonVariant.DESTRUCTIVE else PixelButtonVariant.OUTLINE,
                            chapterHue = hue,
                            onClick = {
                                if (!hasLocPerm) locLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                                else gpsActive = !gpsActive
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            "devinfo-bt" -> {
                val neededPerms = remember {
                    if (Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
                    else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
                }
                var hasPerm by remember {
                    mutableStateOf(neededPerms.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED })
                }
                var scanning by remember { mutableStateOf(false) }
                val found = remember { mutableStateListOf<BleDeviceRow>() }
                val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { map ->
                    hasPerm = map.values.all { it }
                    if (hasPerm) scanning = true else onStatusToast("Bluetooth scan permission denied.")
                }

                DisposableEffect(scanning, hasPerm) {
                    val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
                    val adapter: BluetoothAdapter? = bm?.adapter
                    val scanner = adapter?.bluetoothLeScanner
                    var cb: ScanCallback? = null
                    if (scanning && hasPerm && scanner != null && adapter.isEnabled) {
                        @SuppressLint("MissingPermission")
                        fun beginScan() {
                            cb = object : ScanCallback() {
                                override fun onScanResult(callbackType: Int, result: ScanResult) {
                                    val dev = result.device
                                    val addr = dev.address ?: return
                                    val name = runCatching { dev.name }.getOrNull() ?: result.scanRecord?.deviceName ?: "BLE Device"
                                    val row = BleDeviceRow(addr, name, result.rssi)
                                    val idx = found.indexOfFirst { it.address == addr }
                                    if (idx >= 0) found[idx] = row else found.add(row)
                                    outputReport = found.joinToString("\n") { "${it.name} | ${it.address} | ${it.rssi} dBm" }
                                }
                            }
                            runCatching { scanner.startScan(cb) }.onFailure { onStatusToast("BLE scan failed: ${it.message}") }
                        }
                        beginScan()
                    }
                    onDispose {
                        @SuppressLint("MissingPermission")
                        fun endScan() {
                            cb?.let { runCatching { scanner?.stopScan(it) } }
                        }
                        endScan()
                    }
                }

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("NEARBY BLE BEACONS (${found.size})", style = DotkitType.monoLabel(10), color = palette.ink2)
                            PixelStamp(if (scanning) "SCANNING" else "IDLE", if (scanning) palette.ok else palette.ink3)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PixelButton(
                                text = if (scanning) "Stop Scan" else "Start BLE Scan",
                                variant = if (scanning) PixelButtonVariant.DESTRUCTIVE else PixelButtonVariant.SOLID,
                                chapterHue = hue,
                                onClick = {
                                    if (!hasPerm) permLauncher.launch(neededPerms)
                                    else scanning = !scanning
                                },
                                modifier = Modifier.weight(1f)
                            )
                            PixelButton("Clear", onClick = { found.clear(); outputReport = "" }, variant = PixelButtonVariant.OUTLINE)
                        }
                        if (found.isEmpty()) {
                            Text("Enable Bluetooth & tap Start BLE Scan to discover nearby devices.", style = DotkitType.bodyUi(13), color = palette.ink2)
                        } else {
                            found.sortedByDescending { it.rssi }.forEach { dev ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(palette.paper).padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(dev.name, style = DotkitType.bodyUi(13, bold = true), color = palette.ink)
                                        Text(dev.address, style = DotkitType.monoData(11), color = palette.ink3)
                                    }
                                    PixelStamp("${dev.rssi} dBm", if (dev.rssi > -70) palette.ok else palette.warn)
                                }
                            }
                        }
                    }
                }
            }

            "devinfo-screen" -> {
                var fullScreenTest by remember { mutableStateOf(false) }
                var screenPattern by remember { mutableStateOf("SOLIDS") } // SOLIDS, GRADIENT, GRID, MOTION
                val testColors = listOf(Color.Red, Color.Green, Color.Blue, Color.White, Color.Black, Color.Cyan, Color.Magenta, Color.Yellow)
                var colorIdx by remember { mutableIntStateOf(0) }
                var touchPoints by remember { mutableStateOf<List<Pair<Long, Offset>>>(emptyList()) }
                var maxSeenTouches by remember { mutableIntStateOf(0) }
                var motionTick by remember { mutableFloatStateOf(0f) }

                LaunchedEffect(fullScreenTest, screenPattern) {
                    if (fullScreenTest && screenPattern == "MOTION") {
                        while (true) {
                            motionTick = (motionTick + 0.015f) % 1f
                            delay(16L)
                        }
                    }
                }

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("DISPLAY PANEL DIAGNOSTICS (PIXELS / BANDING / GRID / 120HZ)", style = DotkitType.monoLabel(10), color = palette.ink2)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("SOLIDS" to "Dead Pixels", "GRADIENT" to "Gradient Banding", "GRID" to "Uniformity Grid", "MOTION" to "Refresh Motion").forEach { (k, lbl) ->
                                PixelChip(lbl, selected = screenPattern == k, chapterHue = hue, onClick = { screenPattern = k })
                            }
                        }
                        PixelButton("Launch Fullscreen Display Test", onClick = { colorIdx = 0; fullScreenTest = true }, chapterHue = hue, modifier = Modifier.fillMaxWidth())
                    }
                }

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("MULTI-TOUCH CANVAS", style = DotkitType.monoLabel(10), color = palette.ink2)
                            Text("Active: ${touchPoints.size} · Peak: $maxSeenTouches", style = DotkitType.monoData(12), color = palette.ink)
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(palette.paper3)
                                .border(1.dp, palette.hairline, RoundedCornerShape(12.dp))
                                .pointerInput(Unit) {
                                    awaitPointerEventScope {
                                        while (true) {
                                            val ev = awaitPointerEvent()
                                            val active = ev.changes.filter { it.pressed }.map { it.id.value to it.position }
                                            touchPoints = active
                                            if (active.size > maxSeenTouches) maxSeenTouches = active.size
                                            outputReport = "Multi-touch peak: $maxSeenTouches simultaneous fingers"
                                        }
                                    }
                                }
                        ) {
                            Canvas(Modifier.fillMaxSize()) {
                                touchPoints.forEachIndexed { i, (_, pos) ->
                                    val c = testColors[i % testColors.size]
                                    drawCircle(c.copy(alpha = 0.25f), radius = 58f, center = pos)
                                    drawCircle(c, radius = 58f, center = pos, style = Stroke(4f))
                                    drawLine(c, Offset(pos.x, 0f), Offset(pos.x, size.height), strokeWidth = 1.5f)
                                    drawLine(c, Offset(0f, pos.y), Offset(size.width, pos.y), strokeWidth = 1.5f)
                                }
                            }
                            if (touchPoints.isEmpty()) {
                                Text("Place multiple fingers here simultaneously", style = DotkitType.bodyUi(13), color = palette.ink3, modifier = Modifier.align(Alignment.Center))
                            }
                        }
                    }
                }

                if (fullScreenTest) {
                    Dialog(onDismissRequest = { fullScreenTest = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    when (screenPattern) {
                                        "GRADIENT" -> Brush.horizontalGradient(listOf(Color.Black, testColors[colorIdx], Color.White))
                                        "SOLIDS" -> Brush.horizontalGradient(listOf(testColors[colorIdx], testColors[colorIdx]))
                                        else -> Brush.horizontalGradient(listOf(Color(0xFF101216), Color(0xFF101216)))
                                    }
                                )
                                .clickable {
                                    if (screenPattern == "SOLIDS" || screenPattern == "GRADIENT") {
                                        if (colorIdx < testColors.lastIndex) colorIdx++
                                        else fullScreenTest = false
                                    } else fullScreenTest = false
                                }
                        ) {
                            if (screenPattern == "GRID") {
                                Canvas(Modifier.fillMaxSize()) {
                                    val step = 64f
                                    var x = 0f
                                    while (x < size.width) {
                                        drawLine(Color.White.copy(alpha = 0.65f), Offset(x, 0f), Offset(x, size.height), 1.5f)
                                        x += step
                                    }
                                    var y = 0f
                                    while (y < size.height) {
                                        drawLine(Color.White.copy(alpha = 0.65f), Offset(0f, y), Offset(size.width, y), 1.5f)
                                        y += step
                                    }
                                }
                            } else if (screenPattern == "MOTION") {
                                Canvas(Modifier.fillMaxSize()) {
                                    val yBands = listOf(0.25f, 0.5f, 0.75f)
                                    val speeds = listOf(1f, 2f, 4f)
                                    yBands.forEachIndexed { idx, frac ->
                                        val px = ((motionTick * speeds[idx]) % 1f) * size.width
                                        drawRect(testColors[idx], Offset(px, size.height * frac - 32f), Size(64f, 64f))
                                    }
                                }
                            }
                            Text(
                                text = "Mode: $screenPattern · Tap to cycle/close (${colorIdx + 1}/${testColors.size})",
                                style = DotkitType.monoLabel(11),
                                color = if (screenPattern == "SOLIDS" && (testColors[colorIdx] == Color.White || testColors[colorIdx] == Color.Yellow)) Color.Black else Color.White,
                                modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp)
                            )
                        }
                    }
                }
            }

            else -> { // devinfo-speaker
                var playing by remember { mutableStateOf(false) }
                var freqHz by remember { mutableIntStateOf(165) }
                var stereoChannel by remember { mutableStateOf("BOTH") } // BOTH, LEFT, RIGHT
                var progress by remember { mutableFloatStateOf(0f) }

                var hasMicPerm by remember {
                    mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
                }
                var micTesting by remember { mutableStateOf(false) }
                var micDb by remember { mutableFloatStateOf(-60f) }
                val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                    hasMicPerm = granted
                    if (granted) micTesting = true else onStatusToast("Microphone permission denied.")
                }

                DisposableEffect(playing, freqHz, stereoChannel) {
                    var active = true
                    var track: AudioTrack? = null
                    if (playing) {
                        val sr = 44100
                        val minBuf = AudioTrack.getMinBufferSize(sr, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT).coerceAtLeast(4096)
                        track = AudioTrack.Builder()
                            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                            .setAudioFormat(AudioFormat.Builder().setSampleRate(sr).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
                            .setBufferSizeInBytes(minBuf)
                            .setTransferMode(AudioTrack.MODE_STREAM)
                            .build()
                        track.play()
                        val thread = Thread {
                            val buf = ShortArray(2048)
                            var phase = 0.0
                            var tick = 0
                            while (active) {
                                val modFreq = freqHz + 15.0 * sin(tick * 0.02)
                                val inc = 2.0 * PI * modFreq / sr
                                var i = 0
                                while (i < buf.size) {
                                    val sample = (sin(phase) * 28000).toInt().toShort()
                                    buf[i] = if (stereoChannel == "RIGHT") 0 else sample
                                    buf[i + 1] = if (stereoChannel == "LEFT") 0 else sample
                                    phase += inc
                                    if (phase > 2.0 * PI) phase -= 2.0 * PI
                                    i += 2
                                }
                                runCatching { track.write(buf, 0, buf.size) }
                                tick++
                                progress = (tick % 40) / 40f
                            }
                        }
                        thread.start()
                    }
                    onDispose {
                        active = false
                        runCatching { track?.stop(); track?.release() }
                    }
                }

                DisposableEffect(micTesting, hasMicPerm) {
                    var running = true
                    var recorder: AudioRecord? = null
                    if (micTesting && hasMicPerm) {
                        val sr = 16000
                        val minB = AudioRecord.getMinBufferSize(sr, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT).coerceAtLeast(1024)
                        @SuppressLint("MissingPermission")
                        val ar = AudioRecord(MediaRecorder.AudioSource.MIC, sr, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minB)
                        if (ar.state == AudioRecord.STATE_INITIALIZED) {
                            recorder = ar
                            ar.startRecording()
                            Thread {
                                val sbuf = ShortArray(512)
                                while (running) {
                                    val n = ar.read(sbuf, 0, sbuf.size)
                                    if (n > 0) {
                                        var sumSq = 0.0
                                        for (idx in 0 until n) {
                                            val v = sbuf[idx].toDouble() / 32768.0
                                            sumSq += v * v
                                        }
                                        val rms = sqrt(sumSq / n).coerceAtLeast(1e-5)
                                        micDb = (20.0 * log10(rms)).toFloat().coerceIn(-60f, 0f)
                                    }
                                }
                            }.start()
                        }
                    }
                    onDispose {
                        running = false
                        runCatching { recorder?.stop(); recorder?.release() }
                    }
                }

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        DotMatrixReadout("$freqHz Hz", "SPEAKER EJECTION & STEREO L/R CHECK ($stereoChannel)", hue)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("BOTH" to "Stereo Both", "LEFT" to "Left Only", "RIGHT" to "Right Only").forEach { (k, lbl) ->
                                PixelChip(lbl, selected = stereoChannel == k, chapterHue = hue, onClick = { stereoChannel = k })
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(140, 165, 210, 440, 1000).forEach { hz ->
                                PixelChip("$hz Hz", selected = freqHz == hz, chapterHue = hue, onClick = { freqHz = hz })
                            }
                        }
                        if (playing) PixelProgressBar(progress = progress, chapterHue = hue)
                        PixelButton(
                            text = if (playing) "Stop Speaker Tone" else "Start Speaker Test / Ejection Sweep",
                            variant = if (playing) PixelButtonVariant.DESTRUCTIVE else PixelButtonVariant.SOLID,
                            chapterHue = hue,
                            onClick = {
                                playing = !playing
                                outputReport = "Speaker acoustic test ${freqHz}Hz ($stereoChannel)"
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("MICROPHONE INPUT LOOPBACK LEVEL METER", style = DotkitType.monoLabel(10), color = palette.ink2)
                        val normLevel = ((micDb + 60f) / 60f).coerceIn(0f, 1f)
                        DotMatrixReadout("${"%.1f".format(micDb)} dBFS", if (micTesting) "LIVE MIC CAPSULE INPUT" else "TAP TO MEASURE MIC GAIN", hue, sizeSp = 24)
                        PixelProgressBar(progress = normLevel, chapterHue = ChapterHue.TEAL)
                        PixelButton(
                            text = if (micTesting) "Stop Mic Input Meter" else "Start Mic Input Meter",
                            variant = if (micTesting) PixelButtonVariant.DESTRUCTIVE else PixelButtonVariant.OUTLINE,
                            chapterHue = hue,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                if (!hasMicPerm) micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                else micTesting = !micTesting
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun Build.SDK_INT_COMPAT(): Int = Build.VERSION.SDK_INT
