package com.flightchat.keepalive

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.WifiManager
import android.os.PowerManager
import android.util.Log

class BackgroundKeepAlive(
    context: Context,
    private val tag: String
) {
    private val appContext = context.applicationContext
    private val powerManager =
        appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private val wifiManager =
        appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    private val wakeLock: PowerManager.WakeLock? by lazy {
        powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$tag:cpu")
            ?.apply { setReferenceCounted(false) }
    }

    private val wifiLock: WifiManager.WifiLock? by lazy {
        wifiManager?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "$tag:wifi")
            ?.apply { setReferenceCounted(false) }
    }

    @SuppressLint("WakelockTimeout")
    fun acquire() {
        try {
            wakeLock?.takeIf { !it.isHeld }?.acquire()
            wifiLock?.takeIf { !it.isHeld }?.acquire()
            Log.d(tag, "Background keepalive acquired")
        } catch (e: Exception) {
            Log.e(tag, "Acquire keepalive failed: ${e.message}")
        }
    }

    fun release() {
        try {
            wifiLock?.takeIf { it.isHeld }?.release()
            wakeLock?.takeIf { it.isHeld }?.release()
            Log.d(tag, "Background keepalive released")
        } catch (e: Exception) {
            Log.e(tag, "Release keepalive failed: ${e.message}")
        }
    }
}
