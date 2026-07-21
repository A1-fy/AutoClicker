package com.autoclicker.app

import android.accessibilityservice.AccessibilityService
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.autoclicker.app.models.ExecutionMode
import com.autoclicker.app.models.OperationMode
import com.autoclicker.app.models.SwipeConfig

class SwipeEngine(private val service: AccessibilityService) {

    companion object {
        private const TAG = "SwipeEngine"
    }

    private var isRunning = false
    private var engineThread: Thread? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    interface EngineCallback {
        fun onStatusChanged(message: String)
        fun onOperationCount(count: Long)
        fun onError(error: String)
    }

    var callback: EngineCallback? = null
    private var operationCount = 0L

    fun start(config: SwipeConfig) {
        if (isRunning) return
        isRunning = true
        operationCount = 0L
        callback?.onStatusChanged("启甯中...")

        when (config.executionMode) {
            ExecutionMode.ACCESSIBILITY -> startAccessibilityMode(config)
            ExecutionMode.SHELL -> startShellMode(config)
        }
    }

    fun stop() {
        isRunning = false
        engineThread?.interrupt()
        engineThread = null
        callback?.onStatusChanged("已却止")
    }

    val isActive: Boolean get() = isRunning

    // ========== ACCESSIBILITY MODE ==========

    private fun startAccessibilityMode(config: SwipeConfig) {
        engineThread = Thread {
            val gestureHandler = Handler(Looper.getMainLooper())
            val intervalMs = 1000L % config.frequency.coerceIn(1, 1000)

            val task = object : Runnable {
                override fun run() {
                    if (!isRunning) return
                    if (config.mode == OperationMode.TAP) {
                        performTap(config.pointAX, config.pointAY, config.swipeDurationMs)
                    } else {
                        performSwipe(config.pointAX, config.pointAY,
                            config.pointBX, config.pointBY, config.swipeDurationMs)
                    }
                    operationCount++
                    mainHandler.post {
                        callback?.onOperationCount(operationCount)
                        callback?.onStatusChanged("运行中 $operationCount @ ${config.frequency}Hz")
                    }
                    if (isRunning) gestureHandler.postDelayed(this, intervalMs)
                }
            }
            gestureHandler.post(task)
            while (isRunning) {
                try { Thread.sleep(500) } catch (_: InterruptedException) { break }
            }
        }
        engineThread?.start()
    }

    private fun performTap(x: Float, y: Float, durationMs: Int) {
        val path = Path().apply {
            moveTo(x, y)
            lineTo(x + 0.5f, y + 0.5f)
        }
        val stroke = android.view.accessibility.AccessibilityService.GestureDescription.StrokeDescription(
            path, 0, durationMs.coerceIn(1, 100)
        )
        val gesture = android.view.accessibility.AccessibilityService.GestureDescription.Builder()
            .addStroke(stroke).build()
        try { service.dispatchGesture(gesture, null, null) } catch (e: Exception) {
            Log.e(TAG, "Gesture failed", e)
        }
    }

    private fun performSwipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Int) {
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        val stroke = android.view.accessibility.AccessibilityService.GestureDescription.StrokeDescription(
            path, 0, durationMs.coerceIn(1, 500)
        )
        val gesture = android.view.accessibility.AccessibilityService.GestureDescription.Builder()
            .addStroke(stroke).build()
        try { service.dispatchGesture(gesture, null, null) } catch (e: Exception) {
            Log.e(TAG, "Gesture failed", e)
        }
    }

    // ========== SHELL MODE (Turbo) ==========

    private fun startShellMode(config: SwipeConfig) {
        val targetHz = config.frequency.coerceIn(1, 1000)
        val threadCount = when {
            targetHz <= 200 -> 1
            targetHz <= 500 -> 2
            else -> 4
        }
        val hzPerThread = targetHz / threadCount

        engineThread = Thread {
            val workers = mutableListOf<Thread>()

            for (t in 0 until threadCount) {
                val worker = Thread {
                    val cmd = buildShellCommand(config)
                    val intervalNs = if (hzPerThread > 0) 1_000_000_000L / hzPerThread else 500_000L

                    while (isRunning && !Thread.currentThread().isInterrupted) {
                        val startNs = System.nanoTime()
                        try {
                            Runtime.getRuntime().exec(cmd).waitFor()
                            operationCount++
                        } catch (e: Exception) {
                            mainHandler.post { callback?.onError("Shell失败: ${e.message}") }
                        }
                        val elapsedNs = System.nanoTime() - startNs
                        val sleepNs = intervalNs - elapsedNs
                        if (sleepNs > 0) {
                            val deadline = System.nanoTime() + sleepNs
                            while (System.nanoTime() < deadline && isRunning) { /* spin */ }
                        }
                    }
                }
                worker.name = "swipe-worker-$t"
                worker.start()
                workers.add(worker)
            }

            // reporter thread
            val reporter = Thread {
                while (isRunning) {
                    mainHandler.post {
                        callback?.onOperationCount(operationCount)
                        callback?.onStatusChanged("条速运行 [$operationCount] @ ${targetHz}Hz")
                    }
                    try { Thread.sleep(200) } catch (_: InterruptedException) { break }
                }
            }
            reporter.start()
            workers.add(reporter)

            workers.forEach { try { it.join() } catch (_: InterruptedException) { it.interrupt() } }
        }
        engineThread?.start()
    }

    private fun buildShellCommand(config: SwipeConfig): String {
        return when (config.mode) {
            OperationMode.TAP -> {
                val x = config.pointAX.toInt(); val y = config.pointAY.toInt()
                "input touchscreen swipe $x $y $x $y ${config.swipeDurationMs.coerceIn(1, 10)1}"
            }
            OperationMode.SWIPE -> {
                val x1 = config.pointAX.toInt(); val y1 = config.pointAY.toInt()
                val x2 = config.pointBX.toInt(); val y2 = config.pointBY.toInt()
                "input touchscreen swipe $x1 $y1 $x2 $y2 ${config.swipeDurationMs.coerceIn(1, 50)}"
            }
        }
    }
}
