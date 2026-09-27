package com.tweakcore.app

import android.app.Activity
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.os.Build
import android.os.BatteryManager
import android.content.Context
import org.json.JSONObject

class MainActivity : Activity() {

    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = true
        webView.settings.allowContentAccess = true

        webView.webViewClient = WebViewClient()

        webView.addJavascriptInterface(
            TweakCoreBridge(this),
            "TweakCore"
        )

        webView.loadUrl("file:///android_asset/index.html")

        setContentView(webView)
    }
}

class TweakCoreBridge(
    private val context: Context
) {

    @JavascriptInterface
    fun getSystemInfo(): String {

        val result = JSONObject()

        result.put(
            "device",
            Build.MODEL
        )

        result.put(
            "manufacturer",
            Build.MANUFACTURER
        )

        result.put(
            "android",
            Build.VERSION.RELEASE
        )

        result.put(
            "sdk",
            Build.VERSION.SDK_INT
        )

        result.put(
            "architecture",
            Build.SUPPORTED_ABIS.firstOrNull()
                ?: "unknown"
        )

        result.put(
            "cores",
            Runtime.getRuntime().availableProcessors()
        )

        return result.toString()
    }

    @JavascriptInterface
    fun getDashboardData(): String {

        val system = JSONObject()

        system.put(
            "device",
            Build.MODEL
        )

        system.put(
            "manufacturer",
            Build.MANUFACTURER
        )

        system.put(
            "android",
            Build.VERSION.RELEASE
        )

        system.put(
            "architecture",
            Build.SUPPORTED_ABIS.firstOrNull()
                ?: "unknown"
        )

        system.put(
            "cores",
            Runtime.getRuntime().availableProcessors()
        )

        system.put(
            "ramTotal",
            getTotalRam()
        )

        val memory =
            JSONObject()

        val activityManager =
            context.getSystemService(
                Context.ACTIVITY_SERVICE
            ) as android.app.ActivityManager

        val info =
            android.app.ActivityManager.MemoryInfo()

        activityManager.getMemoryInfo(info)

        val total =
            info.totalMem

        val available =
            info.availMem

        val used =
            total - available

        val usage =
            ((used.toDouble() / total) * 100)
                .toInt()

        memory.put(
            "usagePercent",
            usage
        )

        val battery =
            JSONObject()

        val batteryManager =
            context.getSystemService(
                Context.BATTERY_SERVICE
            ) as BatteryManager

        val batteryLevel =
            batteryManager.getIntProperty(
                BatteryManager.BATTERY_PROPERTY_CAPACITY
            )

        battery.put(
            "battery",
            batteryLevel
        )

        val temperature =
            getBatteryTemperature()

        battery.put(
            "temperature",
            temperature
        )

        val display =
            JSONObject()

        val windowManager =
            context.getSystemService(
                Context.WINDOW_SERVICE
            ) as android.view.WindowManager

        val refreshRate =
            windowManager
                .defaultDisplay
                .refreshRate

        display.put(
            "refreshRate",
            refreshRate
        )

        val performance =
            JSONObject()

        performance.put(
            "profile",
            "BALANCED"
        )

        val result =
            JSONObject()

        result.put(
            "system",
            system
        )

        result.put(
            "cpu",
            JSONObject().apply {
                put(
                    "cores",
                    Runtime.getRuntime()
                        .availableProcessors()
                )
            }
        )

        result.put(
            "memory",
            memory
        )

        result.put(
            "battery",
            battery
        )

        result.put(
            "display",
            display
        )

        result.put(
            "thermal",
            "NORMAL"
        )

        result.put(
            "performance",
            performance
        )

        return result.toString()
    }

    private fun getTotalRam(): Long {

        val manager =
            context.getSystemService(
                Context.ACTIVITY_SERVICE
            ) as android.app.ActivityManager

        val info =
            android.app.ActivityManager.MemoryInfo()

        manager.getMemoryInfo(info)

        return info.totalMem /
                (1024 * 1024)
    }

    private fun getBatteryTemperature(): Float {

        val intent =
            context.registerReceiver(
                null,
                android.content.IntentFilter(
                    android.content.Intent.ACTION_BATTERY_CHANGED
                )
            )

        val temp =
            intent?.getIntExtra(
                BatteryManager.EXTRA_TEMPERATURE,
                0
            ) ?: 0

        return temp / 10f
    }
}