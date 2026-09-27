package com.tweakcore.android

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.Build
import android.os.Debug
import android.provider.Settings
import android.webkit.JavascriptInterface
import org.json.JSONObject
import java.io.File
import java.util.Locale

class TweakCoreBackend(
    private val activity: Activity
) {

    private val context: Context
        get() = activity.applicationContext

    // =========================================================
    // SYSTEM INFORMATION
    // =========================================================

    @JavascriptInterface
    fun getSystemInfo(): String {
        val activityManager =
            context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        val totalRam = memoryInfo.totalMem / 1024 / 1024
        val freeRam = memoryInfo.availMem / 1024 / 1024
        val usedRam = totalRam - freeRam

        val result = JSONObject()

        result.put("device", Build.MODEL)
        result.put("manufacturer", Build.MANUFACTURER)
        result.put("android", Build.VERSION.RELEASE)
        result.put("sdk", Build.VERSION.SDK_INT)
        result.put("cpu", Build.HARDWARE)
        result.put("architecture", Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown")
        result.put("ramTotal", totalRam)
        result.put("ramUsed", usedRam)
        result.put("ramFree", freeRam)

        return result.toString()
    }

    // =========================================================
    // BATTERY
    // =========================================================

    @JavascriptInterface
    fun getBatteryInfo(): String {
        val batteryManager =
            context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager

        val level = batteryManager.getIntProperty(
            BatteryManager.BATTERY_PROPERTY_CAPACITY
        )

        val temperature = try {
            val intent = context.registerReceiver(
                null,
                android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )

            val temp = intent?.getIntExtra(
                BatteryManager.EXTRA_TEMPERATURE,
                0
            ) ?: 0

            temp / 10.0
        } catch (e: Exception) {
            0.0
        }

        val result = JSONObject()
        result.put("battery", level)
        result.put("temperature", temperature)

        return result.toString()
    }

    // =========================================================
    // MEMORY
    // =========================================================

    @JavascriptInterface
    fun getMemoryInfo(): String {
        val manager =
            context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

        val info = ActivityManager.MemoryInfo()
        manager.getMemoryInfo(info)

        val total = info.totalMem / 1024 / 1024
        val available = info.availMem / 1024 / 1024
        val used = total - available

        val result = JSONObject()

        result.put("total", total)
        result.put("used", used)
        result.put("available", available)
        result.put(
            "usagePercent",
            if (total > 0) ((used.toDouble() / total) * 100).toInt() else 0
        )

        return result.toString()
    }

    // =========================================================
    // CPU INFO
    // =========================================================

    @JavascriptInterface
    fun getCpuInfo(): String {
        val result = JSONObject()

        result.put("cores", Runtime.getRuntime().availableProcessors())
        result.put("architecture", Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown")
        result.put("hardware", Build.HARDWARE)
        result.put("board", Build.BOARD)

        return result.toString()
    }

    // =========================================================
    // FPS ESTIMATION
    // =========================================================

    @JavascriptInterface
    fun getDisplayInfo(): String {
        val result = JSONObject()

        val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.display
        } else {
            @Suppress("DEPRECATION")
            activity.windowManager.defaultDisplay
        }

        val refreshRate = display?.refreshRate ?: 60f

        result.put("refreshRate", refreshRate)
        result.put("estimatedMaxFps", refreshRate.toInt())

        return result.toString()
    }

    // =========================================================
    // PERFORMANCE PROFILE
    // =========================================================

    @JavascriptInterface
    fun getPerformanceProfile(): String {
        val battery = getBatteryInfo()
        val memory = getMemoryInfo()

        val batteryJson = JSONObject(battery)
        val memoryJson = JSONObject(memory)

        val temperature =
            batteryJson.optDouble("temperature", 0.0)

        val ramUsage =
            memoryJson.optInt("usagePercent", 0)

        val profile = when {
            temperature >= 42 ->
                "THERMAL"

            ramUsage >= 90 ->
                "MEMORY_HIGH"

            temperature >= 38 ->
                "WARM"

            else ->
                "NORMAL"
        }

        val result = JSONObject()

        result.put("profile", profile)
        result.put("temperature", temperature)
        result.put("ramUsage", ramUsage)

        return result.toString()
    }

    // =========================================================
    // SAFE OPTIMIZATION
    // =========================================================

    @JavascriptInterface
    fun getOptimizationStatus(): String {
        val result = JSONObject()

        result.put("gameMode", "SYSTEM_CONTROLLED")
        result.put("backgroundOptimization", "AVAILABLE")
        result.put("thermalProtection", true)
        result.put("memoryMonitoring", true)
        result.put("fpsMonitoring", true)

        return result.toString()
    }

    // =========================================================
    // OPEN ANDROID SETTINGS
    // =========================================================

    @JavascriptInterface
    fun openBatterySettings() {
        try {
            val intent = Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
            activity.startActivity(intent)
        } catch (e: Exception) {
            val intent = Intent(Settings.ACTION_SETTINGS)
            activity.startActivity(intent)
        }
    }

    @JavascriptInterface
    fun openDisplaySettings() {
        try {
            val intent = Intent(Settings.ACTION_DISPLAY_SETTINGS)
            activity.startActivity(intent)
        } catch (e: Exception) {
            val intent = Intent(Settings.ACTION_SETTINGS)
            activity.startActivity(intent)
        }
    }

    // =========================================================
    // DEVICE NAME
    // =========================================================

    @JavascriptInterface
    fun getDeviceName(): String {
        return Build.MODEL ?: "Android Device"
    }

    // =========================================================
    // ANDROID VERSION
    // =========================================================

    @JavascriptInterface
    fun getAndroidVersion(): String {
        return Build.VERSION.RELEASE ?: "Unknown"
    }

    // =========================================================
    // CPU CORE COUNT
    // =========================================================

    @JavascriptInterface
    fun getCpuCores(): Int {
        return Runtime.getRuntime().availableProcessors()
    }

    // =========================================================
    // RAM USAGE
    // =========================================================

    @JavascriptInterface
    fun getRamUsagePercent(): Int {
        val manager =
            context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

        val info = ActivityManager.MemoryInfo()
        manager.getMemoryInfo(info)

        if (info.totalMem <= 0) return 0

        val used = info.totalMem - info.availMem

        return ((used.toDouble() / info.totalMem) * 100)
            .toInt()
            .coerceIn(0, 100)
    }

    // =========================================================
    // THERMAL STATUS
    // =========================================================

    @JavascriptInterface
    fun getThermalStatus(): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return "UNKNOWN"
        }

        val powerManager =
            context.getSystemService(Context.POWER_SERVICE)
                    as android.os.PowerManager

        return when (powerManager.currentThermalStatus) {
            android.os.PowerManager.THERMAL_STATUS_NONE ->
                "NORMAL"

            android.os.PowerManager.THERMAL_STATUS_LIGHT ->
                "LIGHT"

            android.os.PowerManager.THERMAL_STATUS_MODERATE ->
                "MODERATE"

            android.os.PowerManager.THERMAL_STATUS_SEVERE ->
                "SEVERE"

            android.os.PowerManager.THERMAL_STATUS_CRITICAL ->
                "CRITICAL"

            android.os.PowerManager.THERMAL_STATUS_EMERGENCY ->
                "EMERGENCY"

            android.os.PowerManager.THERMAL_STATUS_SHUTDOWN ->
                "SHUTDOWN"

            else ->
                "UNKNOWN"
        }
    }

    // =========================================================
    // FULL DASHBOARD DATA
    // =========================================================

    @JavascriptInterface
    fun getDashboardData(): String {
        val result = JSONObject()

        result.put("system", JSONObject(getSystemInfo()))
        result.put("battery", JSONObject(getBatteryInfo()))
        result.put("memory", JSONObject(getMemoryInfo()))
        result.put("cpu", JSONObject(getCpuInfo()))
        result.put("display", JSONObject(getDisplayInfo()))
        result.put("performance", JSONObject(getPerformanceProfile()))
        result.put("thermal", getThermalStatus())

        return result.toString()
    }
}