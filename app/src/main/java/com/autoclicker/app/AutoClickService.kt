package com.autoclicker.app

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.autoclicker.app.models.SwipeConfig

class AutoClickService : AccessibilityService() {

    companion object {
        private const val TAG = "AutoClickService"
        const val ACTION_START = "com.autoclicker.action.START"
        const val ACTION_STOP = "com.autoclicker.action.STOP"
        const val ACTION_UPDATE_CONFIG = "com.autoclicker.action.UPDATE_CONFIG"
        const val EXTRA_CONFIG = "extra_config"
        var instance: AutoClickService? = null; private set
    }

    private lateinit var swipeEngine: SwipeEngine
    private val mainHandler = Handler(Looper.getMainLooper())

    var statusCallback: ((String) -> Unit)? = null
    var countCallback: ((Long) -> Unit)? = null
    var errorCallback: ((String) -> Unit)? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        swipeEngine = SwipeEngine(this)
        swipeEngine.callback = object : SwipeEngine.EngineCallback {
            override fun onStatusChanged(msg: String) = mainHandler.post { statusCallback?.invoke(msg) }
            override fun onOperationCount(c: Long) = mainHandler.post { countCallback?.invoke(c) }
            override fun onError(e: String) = mainHandler.post { errorCallback?.invoke(e) }
        }
        Log.i(TAG, "Service connected")
        mainHandler.post { statusCallback?.invoke("无障碍服务已连接") }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() { stopEngine() }

    override fun onDestroy() {
        stopEngine(); instance = null; super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY
        when (intent.action) {
            ACTION_START -> {
                val config = if (Build.VERSION.SDK_INT >= 33)
                    intent.getParcelableExtra(EXTRA_CONFIG, SwipeConfig::class.java)
                else
                    intent.getParcelableExtra(EXTRA_CONFIG)
                if (config != null) startEngine(config)
            }
            ACTION_STOP -> stopEngine()
            ACTION_UPDATE_CONFIG -> {
                val config = if (Build.VERSION.SDK_INT >= 33)
                    intent.getParcelableExtra(EXTRA_CONFIG, SwipeConfig::class.java)
                else
                    intent.getParcelableExtra(EXTRA_CONFIG)
                if (config != null) {
                    if (swipeEngine.isActive) { swipeEngine.stop(); swipeEngine.start(config) }
                }
            }
        }
        return START_NOT_STICKY
    }

    fun startEngine(config: SwipeConfig) {
        swipeEngine.start(config); vibrate()
        Log.i(TAG, "Engine started: $config")
        mainHandler.post { statusCallback?.invoke("运行中 (${config.executionMode.name})") }
    }

    fun stopEngine() {
        swipeEngine.stop(); vibrate()
        Log.i(TAG, "Engine stopped")
        mainHandler.post { statusCallback?.invoke("已停止") }
    }

    fun isEngineActive(): Boolean = swipeEngine.isActive

    private fun vibrate() {
        try {
            val v = if (Build.VERSION.SDK_INT >= 31) {
                (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else { getSystemService(VIBRATOR_SERVICE) as Vibrator }
            if (Build.VERSION.SDK_INT >= 26)
                v.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
            else v.vibrate(50)
        } catch (_: Exception) {}
    }
}
