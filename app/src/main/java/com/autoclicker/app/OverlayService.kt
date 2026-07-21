package com.autoclicker.app

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.*
import com.autoclicker.app.models.OperationMode
import com.autoclicker.app.models.SwipeConfig

class OverlayService : Service() {

    companion object {
        const val TAG = "OverlayService"
        const val ACTION_POINT_SELECTED = "com.autoclicker.action.POINT_SELECTED"
        const val EXTRA_POINT_X = "extra_point_x"
        const val EXTRA_POINT_Y = "extra_point_y"
        const val EXTRA_POINT_TYPE = "extra_point_type"

        fun start(context: Context, pointType: String) {
            val intent = Intent(context, OverlayService::class.java)
            intent.putExtra(EXTRA_POINT_TYPE, pointType)
            if (Build.VERSION_CODES.INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var selectedPoint: PointF? = null
    private var pointType: String = "A"

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        pointType = intent?.getStringExtra(EXTRA_POINT_TYPE) ?: "A"

        val channelId = "overlay_service_channel"
        val channel = android.app.NotificationChannel(
            channelId, "FloatPicker",
            android.app.NotificationManager.IMPORTANCE_LOW
        )
        val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.createNotificationChannel(channel)
        val notification = android.app.Notification.Builder(this, channelId)
            .setContentTitle("Pick Point " + pointType)
            .setContentText("Tap screen to set coordinate")
            .setSmallIcon(android.R.draw.ic_menu_mylocation)
            .build()

        startForeground(1001, notification)
        showOverlay()
        return START_NOT_STICKY
    }

    private fun showOverlay() {
        overlayView = object : View(this) {
            private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.parseColor("#80000000")
                style = Paint.Style.FILL
            }
            private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (pointType == "A")
                    android.graphics.Color.parseColor("#664CAF50")
                else
                    android.graphics.Color.parseColor("#66F44336")
                style = Paint.Style.FILL
            }
            private val markerStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 4f
            }
            private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE
                textSize = 48f
                textAlign = Paint.Align.CENTER
                isFakeBoldText = true
            }
            private val coordPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE
                textSize = 36f
                textAlign = Paint.Align.CENTER
            }
            private val crossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE
                strokeWidth = 3f
            }

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                canvas.drawColor(android.graphics.Color.parseColor("#80000000"))
                val hint = if (pointType == "A") "Set START Point A" else "Set END Point B"
                canvas.drawText(hint, width / 2f, height * 0.15f, textPaint)
                canvas.drawText("Auto-return after tap", width / 2f,
                    height * 0.15f + 60f, textPaint.apply { textSize = 32f })

                selectedPoint?.let { pt ->
                    val cx = pt.x; val cy = pt.y
                    markerStrokePaint.color = if (pointType == "A")
                        android.graphics.Color.parseColor("#FF4CAF50")
                    else
                        android.graphics.Color.parseColor("#FFF44336")
                    canvas.drawCircle(cx, cy, 60f, markerStrokePaint)
                    canvas.drawCircle(cx, cy, 50f, markerPaint)
                    canvas.drawLine(cx - 30f, cy, cx + 30f, cy, crossPaint)
                    canvas.drawLine(cx, cy - 30f, cx, cy + 30f, crossPaint)
                    val coord = "(" + cx.toInt().toString() + ", " + cy.toInt().toString() + ")"
                    canvas.drawText(coord, cx, cy - 80f, coordPaint)
                }
            }
        }.apply {
            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_UP) {
                    selectedPoint = PointF(event.x, event.y)
                    invalidate()
                    Handler(Looper.getMainLooper()).postDelayed({
                        val resultIntent = Intent(ACTION_POINT_SELECTED).apply {
                            putExtra(EXTRA_POINT_X, event.x)
                            putExtra(EXTRA_POINT_Y, event.y)
                            putExtra(EXTRA_POINT_TYPE, pointType)
                        }
                        sendBroadcast(resultIntent)
                        stopSelf()
                    }, 300)
                }
                true
            }
        }

        val params = WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    or WindowManager.LayoutParams.FLAG_FULLSCREEN
                    or WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
            format = PixelFormat.TRANSLUCENT
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
        }
        windowManager.addView(overlayView, params)
    }

    override fun onDestroy() {
        overlayView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        overlayView = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
