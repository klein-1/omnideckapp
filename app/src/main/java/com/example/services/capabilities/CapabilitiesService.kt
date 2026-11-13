package com.example.services.capabilities

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Vibrator
import androidx.compose.runtime.Immutable

@Immutable
data class CapabilityProbeItem(
    val id: String,
    val name: String,
    val available: Boolean,
    val sourceLabel: String,
    val detail: String
)

@Immutable
data class DeviceCapabilitySnapshot(
    val cpuCores: Int,
    val abi: String,
    val androidRelease: String,
    val sdkInt: Int,
    val batteryPercent: Int,
    val isCharging: Boolean,
    val isOnline: Boolean,
    val probes: List<CapabilityProbeItem>
)

/**
 * Probes real device capabilities once and on refresh (§2.4 & §6).
 * Never fabricates sensor, network, or hardware readings.
 */
object CapabilitiesService {
    fun probe(
        context: Context,
        companionReachable: Boolean,
        geminiConfigured: Boolean,
        offlineOnlyAi: Boolean
    ): DeviceCapabilitySnapshot {
        val pm = context.packageManager
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        @Suppress("DEPRECATION")
        val vib = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

        val cores = Runtime.getRuntime().availableProcessors()
        val abi = Build.SUPPORTED_ABIS?.firstOrNull() ?: "unknown"
        val batteryPct = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        val charging = bm?.isCharging ?: false

        val netCaps = cm?.activeNetwork?.let { cm.getNetworkCapabilities(it) }
        val online = netCaps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

        val hasCamera = pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
        val hasFlash = pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)
        val hasMic = pm.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)
        val hasBle = pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
        val hasGps = pm.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS)
        val hasAccel = sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null
        val hasGyro = sm?.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null
        val hasMag = sm?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null
        val hasLight = sm?.getDefaultSensor(Sensor.TYPE_LIGHT) != null
        val hasBaro = sm?.getDefaultSensor(Sensor.TYPE_PRESSURE) != null
        val hasStep = sm?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null
        val hasVibrator = vib?.hasVibrator() == true

        val items = listOf(
            CapabilityProbeItem(
                "camera", "Camera & Torch", hasCamera, "PackageManager",
                if (hasCamera) "Camera present (Flash: ${if (hasFlash) "Yes" else "Screen-light fallback"})" else "No camera hardware detected"
            ),
            CapabilityProbeItem(
                "mic", "Microphone", hasMic, "PackageManager",
                if (hasMic) "Audio capture available (uncalibrated ±5–10 dB)" else "No microphone detected"
            ),
            CapabilityProbeItem(
                "motion", "Accelerometer & Gyro", hasAccel, "SensorManager",
                "Accel: ${if (hasAccel) "OK" else "Missing"} · Gyro: ${if (hasGyro) "OK" else "Missing"}"
            ),
            CapabilityProbeItem(
                "magnetometer", "Magnetometer & Light", hasMag || hasLight, "SensorManager",
                "Mag: ${if (hasMag) "OK" else "N/A"} · Light: ${if (hasLight) "OK" else "N/A"} · Baro: ${if (hasBaro) "OK" else "N/A"}"
            ),
            CapabilityProbeItem(
                "step", "Step Counter", hasStep, "SensorManager",
                if (hasStep) "Hardware step counter present" else "Not exposed; accelerometer estimate when awake"
            ),
            CapabilityProbeItem(
                "geo", "GNSS / GPS", hasGps, "PackageManager",
                if (hasGps) "GNSS receiver present (permission on tap)" else "Network/coarse location only"
            ),
            CapabilityProbeItem(
                "bluetooth", "Bluetooth LE", hasBle, "PackageManager",
                if (hasBle) "BLE GATT Explorer supported" else "BLE hardware unavailable"
            ),
            CapabilityProbeItem(
                "haptics", "Haptic Engine", hasVibrator, "Vibrator",
                if (hasVibrator) "8ms mechanical press ticks available" else "No vibrator motor; visual press only"
            ),
            CapabilityProbeItem(
                "companion", "Companion Server (/server)", companionReachable, "HTTP Probe",
                if (companionReachable) "Connected · yt-dlp / ffmpeg / shttps ready" else "Unreachable · Offline tools unaffected"
            ),
            CapabilityProbeItem(
                "ai", "Gemini AI Layer", geminiConfigured && !offlineOnlyAi, "Settings → AI",
                when {
                    offlineOnlyAi -> "Offline-Only Kill Switch is ON (0 B sent)"
                    geminiConfigured -> "Opt-in Gemini key ready (asks consent first)"
                    else -> "No key set · All local tools work without a key"
                }
            )
        )

        return DeviceCapabilitySnapshot(
            cpuCores = cores,
            abi = abi,
            androidRelease = Build.VERSION.RELEASE ?: "Android",
            sdkInt = Build.VERSION.SDK_INT,
            batteryPercent = batteryPct,
            isCharging = charging,
            isOnline = online,
            probes = items
        )
    }
}
