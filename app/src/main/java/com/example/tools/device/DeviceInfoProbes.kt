package com.example.tools.device

import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.location.GnssStatus
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.util.Size
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

data class CpuBenchResult(
    val primesFound: Int,
    val singleCoreMs: Long,
    val multiCoreMs: Long,
    val score: Int
)

data class StorageIoBenchResult(
    val testSizeMb: Int,
    val writeMs: Long,
    val readMs: Long,
    val writeMbPerSec: Double,
    val readMbPerSec: Double
)

data class CameraLensSpec(
    val cameraId: String,
    val facing: String,
    val megapixels: Double,
    val focalLengthsMm: String,
    val apertures: String,
    val isoRange: String,
    val hasRaw: Boolean,
    val hasOis: Boolean
)

data class ThermalZoneRow(
    val name: String,
    val tempCelsius: Float
)

data class GnssSatRow(
    val svid: Int,
    val constellation: String,
    val cn0DbHz: Float,
    val elevationDeg: Float,
    val azimuthDeg: Float,
    val usedInFix: Boolean
)

data class HardwareDiagnosticItem(
    val subsystem: String,
    val passed: Boolean,
    val detail: String
)

data class PublicIpAsnResult(
    val ip: String,
    val city: String,
    val region: String,
    val country: String,
    val isp: String,
    val asn: String,
    val timezone: String,
    val error: String? = null
)

data class PortScanEntry(
    val port: Int,
    val service: String,
    val open: Boolean,
    val latencyMs: Long
)

object DeviceInfoProbes {

    fun runCpuPrimeBenchmark(): CpuBenchResult {
        val limit = 160_000
        fun countPrimesUpTo(n: Int): Int {
            var count = 0
            for (v in 2..n) {
                var prime = true
                var d = 2
                while (d * d <= v) {
                    if (v % d == 0) {
                        prime = false
                        break
                    }
                    d++
                }
                if (prime) count++
            }
            return count
        }

        val t0 = System.nanoTime()
        val primes = countPrimesUpTo(limit)
        val singleMs = ((System.nanoTime() - t0) / 1_000_000L).coerceAtLeast(1L)

        val cores = Runtime.getRuntime().availableProcessors().coerceIn(1, 16)
        val t1 = System.nanoTime()
        val threads = (0 until cores).map { idx ->
            Thread {
                val start = (idx * limit / cores).coerceAtLeast(2)
                val end = ((idx + 1) * limit / cores)
                for (v in start..end) {
                    var p = true
                    var d = 2
                    while (d * d <= v) {
                        if (v % d == 0) {
                            p = false
                            break
                        }
                        d++
                    }
                    if (p && v < 0) println(v)
                }
            }.apply { start() }
        }
        threads.forEach { it.join() }
        val multiMs = ((System.nanoTime() - t1) / 1_000_000L).coerceAtLeast(1L)
        val score = ((100_000.0 / singleMs) * 0.45 + (100_000.0 / multiMs) * 0.55).toInt()
        return CpuBenchResult(primes, singleMs, multiMs, score)
    }

    fun runStorageSequentialBenchmark(cacheDir: File, sizeMb: Int = 12): StorageIoBenchResult {
        val file = File(cacheDir, "omnideck_io_bench.tmp")
        val block = ByteArray(64 * 1024) { idx -> (idx * 31 + 7).toByte() }
        val iterations = (sizeMb * 1024 * 1024) / block.size
        return try {
            val tw0 = System.nanoTime()
            FileOutputStream(file).use { out ->
                repeat(iterations) { out.write(block) }
                out.fd.sync()
            }
            val writeMs = ((System.nanoTime() - tw0) / 1_000_000L).coerceAtLeast(1L)

            val readBuf = ByteArray(64 * 1024)
            var totalRead = 0L
            val tr0 = System.nanoTime()
            FileInputStream(file).use { inp ->
                while (true) {
                    val r = inp.read(readBuf)
                    if (r <= 0) break
                    totalRead += r
                }
            }
            val readMs = ((System.nanoTime() - tr0) / 1_000_000L).coerceAtLeast(1L)
            val actualMb = totalRead.toDouble() / (1024.0 * 1024.0)
            StorageIoBenchResult(
                testSizeMb = sizeMb,
                writeMs = writeMs,
                readMs = readMs,
                writeMbPerSec = (actualMb * 1000.0) / writeMs,
                readMbPerSec = (actualMb * 1000.0) / readMs
            )
        } finally {
            runCatching { file.delete() }
        }
    }

    fun queryCamera2Capabilities(context: Context): List<CameraLensSpec> = runCatching {
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return emptyList()
        cm.cameraIdList.mapNotNull { id ->
            runCatching {
                val chars = cm.getCameraCharacteristics(id)
                val facing = when (chars.get(CameraCharacteristics.LENS_FACING)) {
                    CameraCharacteristics.LENS_FACING_BACK -> "Rear"
                    CameraCharacteristics.LENS_FACING_FRONT -> "Front"
                    CameraCharacteristics.LENS_FACING_EXTERNAL -> "External"
                    else -> "Lens"
                }
                val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                val sizes = map?.getOutputSizes(android.graphics.ImageFormat.JPEG)
                val best = sizes?.maxByOrNull { it.width.toLong() * it.height.toLong() }
                val mp = if (best != null) (best.width * best.height) / 1_000_000.0 else 0.0
                val focals = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                    ?.joinToString(", ") { "%.1fmm".format(it) } ?: "—"
                val apertures = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)
                    ?.joinToString(", ") { "f/%.1f".format(it) } ?: "—"
                val iso = chars.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
                val isoStr = if (iso != null) "${iso.lower}–${iso.upper}" else "—"
                val caps = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)?.toSet().orEmpty()
                val hasRaw = CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW in caps
                val oisModes = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)?.toSet().orEmpty()
                val hasOis = CameraCharacteristics.LENS_OPTICAL_STABILIZATION_MODE_ON in oisModes
                CameraLensSpec(id, facing, mp, focals, apertures, isoStr, hasRaw, hasOis)
            }.getOrNull()
        }
    }.getOrDefault(emptyList())

    fun queryMountsSummary(): List<String> = runCatching {
        File("/proc/mounts").readLines()
            .filter { line ->
                line.startsWith("/dev/") || line.contains(" /data ") || line.contains(" /system ") || line.contains(" /storage")
            }
            .take(12)
            .map { line ->
                val parts = line.split(" ")
                if (parts.size >= 4) {
                    val opts = parts[3].split(",").take(2).joinToString(",")
                    "${parts[1]} (${parts[2]}) · $opts"
                } else line.take(64)
            }
    }.getOrElse {
        listOf("SELinux restricted /proc/mounts — /data statFs active")
    }

    fun queryLauncherAppCount(context: Context): Int = runCatching {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        context.packageManager.queryIntentActivities(intent, 0).size
    }.getOrDefault(0)

    fun queryThermalZones(): List<ThermalZoneRow> = runCatching {
        val root = File("/sys/class/thermal")
        if (!root.exists() || !root.canRead()) return emptyList()
        root.listFiles { f -> f.name.startsWith("thermal_zone") }
            ?.sortedBy { it.name }
            ?.mapNotNull { zone ->
                runCatching {
                    val type = File(zone, "type").readText().trim()
                    val raw = File(zone, "temp").readText().trim().toFloatOrNull() ?: return@mapNotNull null
                    val celsius = if (raw > 200f) raw / 1000f else raw
                    if (celsius in -40f..130f) ThermalZoneRow(type, celsius) else null
                }.getOrNull()
            }
            ?.take(12)
            .orEmpty()
    }.getOrDefault(emptyList())

    fun runHardwareDiagnosticChecklist(context: Context): List<HardwareDiagnosticItem> {
        val items = mutableListOf<HardwareDiagnosticItem>()
        val cores = Runtime.getRuntime().availableProcessors()
        items += HardwareDiagnosticItem("CPU Cores", cores >= 2, "$cores logical processors online")

        val stat = runCatching { StatFs(Environment.getDataDirectory().path) }.getOrNull()
        val freeMb = (stat?.availableBytes ?: 0L) / (1024L * 1024L)
        items += HardwareDiagnosticItem("Internal Storage", freeMb > 256, "$freeMb MB free on /data")

        val bat = context.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val health = bat?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1
        val tempC = (bat?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
        items += HardwareDiagnosticItem(
            "Battery Subsystem",
            health == BatteryManager.BATTERY_HEALTH_GOOD && tempC < 45f,
            "Temp ${"%.1f".format(tempC)}°C"
        )

        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val hasAccel = sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null
        val hasGyro = sm?.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null
        val sensorCount = sm?.getSensorList(Sensor.TYPE_ALL)?.size ?: 0
        items += HardwareDiagnosticItem("Motion Sensors", hasAccel, "$sensorCount sensors · Accel=$hasAccel Gyro=$hasGyro")

        val cams = queryCamera2Capabilities(context)
        items += HardwareDiagnosticItem("Camera2 Pipeline", cams.isNotEmpty(), "${cams.size} lenses enumerated")

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val caps = cm?.activeNetwork?.let { cm.getNetworkCapabilities(it) }
        val netOk = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        items += HardwareDiagnosticItem("Network Stack", netOk, if (netOk) "Internet capability active" else "Offline / no transport")

        return items
    }

    fun constellationLabel(type: Int): String = when (type) {
        GnssStatus.CONSTELLATION_GPS -> "GPS"
        GnssStatus.CONSTELLATION_GLONASS -> "GLO"
        GnssStatus.CONSTELLATION_GALILEO -> "GAL"
        GnssStatus.CONSTELLATION_BEIDOU -> "BDS"
        GnssStatus.CONSTELLATION_QZSS -> "QZS"
        GnssStatus.CONSTELLATION_SBAS -> "SBAS"
        6 -> "IRN"
        else -> "OTH"
    }

    fun formatLatLonDmsUtm(lat: Double, lon: Double): Triple<String, String, String> {
        fun toDms(value: Double, pos: Char, neg: Char): String {
            val hemi = if (value >= 0) pos else neg
            val absV = abs(value)
            val d = floor(absV).toInt()
            val mFloat = (absV - d) * 60.0
            val m = floor(mFloat).toInt()
            val s = (mFloat - m) * 60.0
            return "%d°%02d'%05.2f\"%c".format(d, m, s, hemi)
        }
        val dms = "${toDms(lat, 'N', 'S')}  ${toDms(lon, 'E', 'W')}"
        val zone = (floor((lon + 180.0) / 6.0).toInt() + 1).coerceIn(1, 60)
        val letter = if (lat >= 0) 'N' else 'S'

        // Standard WGS84 UTM projection
        val a = 6378137.0
        val e2 = 0.00669438
        val k0 = 0.9996
        val latRad = lat * PI / 180.0
        val lonRad = lon * PI / 180.0
        val lonOrigin = ((zone - 1) * 6 - 180 + 3) * PI / 180.0
        val ep2 = e2 / (1.0 - e2)
        val nVal = a / sqrt(1.0 - e2 * sin(latRad) * sin(latRad))
        val tVal = tan(latRad) * tan(latRad)
        val cVal = ep2 * cos(latRad) * cos(latRad)
        val aVal = cos(latRad) * (lonRad - lonOrigin)
        val mVal = a * (
            (1 - e2 / 4 - 3 * e2 * e2 / 64 - 5 * e2 * e2 * e2 / 256) * latRad -
                (3 * e2 / 8 + 3 * e2 * e2 / 32 + 45 * e2 * e2 * e2 / 1024) * sin(2 * latRad) +
                (15 * e2 * e2 / 256 + 45 * e2 * e2 * e2 / 1024) * sin(4 * latRad) -
                (35 * e2 * e2 * e2 / 3072) * sin(6 * latRad)
            )
        val easting = k0 * nVal * (aVal + (1 - tVal + cVal) * aVal * aVal * aVal / 6 +
            (5 - 18 * tVal + tVal * tVal + 72 * cVal - 58 * ep2) * Math.pow(aVal, 5.0) / 120) + 500000.0
        var northing = k0 * (mVal + nVal * tan(latRad) * (aVal * aVal / 2 +
            (5 - tVal + 9 * cVal + 4 * cVal * cVal) * Math.pow(aVal, 4.0) / 24 +
            (61 - 58 * tVal + tVal * tVal + 600 * cVal - 330 * ep2) * Math.pow(aVal, 6.0) / 720))
        if (lat < 0) northing += 10000000.0

        val dd = "%.6f, %.6f".format(lat, lon)
        val utm = "Zone $zone$letter ${easting.toLong()}E ${northing.toLong()}N"
        return Triple(dd, dms, utm)
    }

    val COMMON_PORTS: List<Pair<Int, String>> = listOf(
        21 to "FTP",
        22 to "SSH",
        25 to "SMTP",
        53 to "DNS",
        80 to "HTTP",
        110 to "POP3",
        143 to "IMAP",
        443 to "HTTPS",
        445 to "SMB",
        554 to "RTSP",
        3306 to "MySQL",
        5432 to "PostgreSQL",
        6379 to "Redis",
        8080 to "HTTP-Alt",
        8443 to "HTTPS-Alt"
    )

    fun scanCommonPorts(host: String, ports: List<Pair<Int, String>> = COMMON_PORTS): List<PortScanEntry> {
        val target = host.trim()
        if (target.isEmpty()) return emptyList()
        return ports.map { (port, svc) ->
            val t0 = System.nanoTime()
            val ok = runCatching {
                Socket().use { s -> s.connect(InetSocketAddress(target, port), 350) }
            }.isSuccess
            val ms = ((System.nanoTime() - t0) / 1_000_000L).coerceAtLeast(1L)
            PortScanEntry(port, svc, ok, ms)
        }
    }

    fun fetchPublicIpAsn(): PublicIpAsnResult = runCatching {
        val conn = (URL("https://ipwho.is/").openConnection() as HttpURLConnection).apply {
            connectTimeout = 5000
            readTimeout = 5000
            setRequestProperty("User-Agent", "OmniDeck-NetProbe/1.0")
        }
        val body = conn.inputStream.bufferedReader().readText()
        val json = JSONObject(body)
        if (!json.optBoolean("success", true)) {
            PublicIpAsnResult("", "", "", "", "", "", "", json.optString("message", "Lookup failed"))
        } else {
            val connObj = json.optJSONObject("connection")
            val tzObj = json.optJSONObject("timezone")
            PublicIpAsnResult(
                ip = json.optString("ip"),
                city = json.optString("city"),
                region = json.optString("region"),
                country = json.optString("country"),
                isp = connObj?.optString("isp").orEmpty(),
                asn = connObj?.optInt("asn", 0)?.takeIf { it > 0 }?.let { "AS$it" } ?: "—",
                timezone = tzObj?.optString("id").orEmpty()
            )
        }
    }.getOrElse {
        PublicIpAsnResult("", "", "", "", "", "", "", it.message ?: "Network error")
    }
}
