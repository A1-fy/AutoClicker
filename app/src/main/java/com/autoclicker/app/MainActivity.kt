package com.autoclicker.app

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.autoclicker.app.models.ExecutionMode
import com.autoclicker.app.models.OperationMode
import com.autoclicker.app.models.SwipeConfig
import com.google.android.material.chip.Chip
import com.google.android.material.tabs.TabLayout

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val OVERLAY_PERMISSION_REQUEST = 100
    }

    private lateinit var serviceStatus: TextView
    private lateinit var openSettings: Button
    private lateinit var modeTabs: TabLayout
    private lateinit var pointAText: TextView
    private lateinit var pointBText: TextView
    private lateinit var setPointA: Button
    private lateinit var setPointB: Button
    private lateinit var frequencyLabel: TextView
    private lateinit var frequencySeekBar: SeekBar
    private lateinit var durationLabel: TextView
    private lateinit var durationSeekBar: SeekBar
    private lateinit var startStopButton: Button
    private lateinit var statusText: TextView
    private lateinit var modeNormal: Chip
    private lateinit var modeTurbo: Chip

    private var pointAX = 500f
    private var pointAY = 1000f
    private var pointBX = 500f
    private var pointBY = 1500f
    private var isRunning = false
    private var currentMode = OperationMode.SWIPE
    private var currentExecutionMode = ExecutionMode.ACCESSIBILITY

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initViews()
        initTabs()
        initListeners()
        registerPointReceiver()
        checkPermissions()
        updateServiceStatus()
    }

    override fun onResume() {
        super.onResume()
        updateServiceStatus()
        AutoClickService.instance?.let { svc ->
            svc.statusCallback = ::onServiceStatus
            svc.countCallback = ::onOperationCount
            svc.errorCallback = ::onEngineError
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        AutoClickService.instance?.let { svc ->
            svc.statusCallback = null
            svc.countCallback = null
            svc.errorCallback = null
        }
        try { unregisterReceiver(pointReceiver) } catch (_: Exception) {}
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OVERLAY_PERMISSION_REQUEST) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Overlay permission granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Overlay permission denied", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun initViews() {
        serviceStatus = findViewById(R.id.serviceStatus)
        openSettings = findViewById(R.id.openSettings)
        modeTabs = findViewById(R.id.modeTabs)
        pointAText = findViewById(R.id.pointAText)
        pointBText = findViewById(R.id.pointBText)
        setPointA = findViewById(R.id.setPointA)
        setPointB = findViewById(R.id.setPointB)
        frequencyLabel = findViewById(R.id.frequencyLabel)
        frequencySeekBar = findViewById(R.id.frequencySeekBar)
        durationLabel = findViewById(R.id.durationLabel)
        durationSeekBar = findViewById(R.id.durationSeekBar)
        startStopButton = findViewById(R.id.startStopButton)
        statusText = findViewById(R.id.statusText)
        modeNormal = findViewById(R.id.modeNormal)
        modeTurbo = findViewById(R.id.modeTurbo)
    }

    private fun initTabs() {
        modeTabs.addTab(modeTabs.newTab().setText("Tap Mode"))
        modeTabs.addTab(modeTabs.newTab().setText("Swipe Mode"))
        modeTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentMode = when (tab?.position) {
                    0 -> OperationMode.TAP
                    1 -> OperationMode.SWIPE
                    else -> OperationMode.SWIPE
                }
                val vis = if (currentMode == OperationMode.SWIPE) android.view.View.VISIBLE else android.view.View.GONE
                durationLabel.visibility = vis
                durationSeekBar.visibility = vis
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun initListeners() {
        openSettings.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        setPointA.setOnClickListener {
            if (checkOverlayPermission()) {
                OverlayService.start(this, "A")
            }
        }

        setPointB.setOnClickListener {
            if (checkOverlayPermission()) {
                OverlayService.start(this, "B")
            }
        }

        frequencySeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seek: SeekBar?, value: Int, fromUser: Boolean) {
                val freq = if (value == 0) 1 else value
                frequencyLabel.text = "Frequency: " + freq + " Hz"
            }
            override fun onStartTrackingTouch(seek: SeekBar?) {}
            override fun onStopTrackingTouch(seek: SeekBar?) {}
        })

        durationSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seek: SeekBar?, value: Int, fromUser: Boolean) {
                val d = value.coerceAtLeast(1)
                durationLabel.text = "Swipe duration: " + d + " ms"
            }
            override fun onStartTrackingTouch(seek: SeekBar?) {}
            override fun onStopTrackingTouch(seek: SeekBar?) {}
        })

        modeNormal.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) currentExecutionMode = ExecutionMode.ACCESSIBILITY
        }
        modeTurbo.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) currentExecutionMode = ExecutionMode.SHELL
        }
        modeNormal.isChecked = true

        startStopButton.setOnClickListener {
            if (isRunning) stopOperation() else startOperation()
        }

        updatePointDisplay()
    }

    private val pointReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == OverlayService.ACTION_POINT_SELECTED) {
                val x = intent.getFloatExtra(OverlayService.EXTRA_POINT_X, 0f)
                val y = intent.getFloatExtra(OverlayService.EXTRA_POINT_Y, 0f)
                val type = intent.getStringExtra(OverlayService.EXTRA_POINT_TYPE) ?: "A"
                if (type == "A") {
                    pointAX = x; pointAY = y
                } else {
                    pointBX = x; pointBY = y
                }
                updatePointDisplay()
                Toast.makeText(this@MainActivity,
                    "Set point " + type + " (" + x.toInt() + ", " + y.toInt() + ")",
                    Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun registerPointReceiver() {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Context.RECEIVER_NOT_EXPORTED else 0
        registerReceiver(pointReceiver, IntentFilter(OverlayService.ACTION_POINT_SELECTED), flags)
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) checkOverlayPermission()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 200)
        }
    }

    private fun checkOverlayPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            AlertDialog.Builder(this)
                .setTitle("Need overlay permission")
                .setMessage("Coordinate picker needs to draw over other apps")
                .setPositiveButton("Settings") { _, _ ->
                    startActivityForResult(
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + packageName)),
                        OVERLAY_PERMISSION_REQUEST
                    )
                }
                .setNegativeButton("Cancel", null)
                .show()
            return false
        }
        return true
    }

    private fun updateServiceStatus() {
        val am = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabled = am.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_GENERIC
        ).any { it.resolveInfo.serviceInfo.packageName == packageName }
        serviceStatus.text = if (enabled) "Service connected" else "Please enable accessibility service"
        serviceStatus.setTextColor(if (enabled)
            android.graphics.Color.parseColor("#FF4CAF50")
        else
            android.graphics.Color.parseColor("#FF9E9E9E"))
    }

    private fun startOperation() {
        val svc = AutoClickService.instance
        if (svc == null) {
            statusText.text = "Error: Accessibility service not connected"
            statusText.setTextColor(android.graphics.Color.parseColor("#FFF44336"))
            return
        }
        val config = buildConfig()
        svc.startEngine(config)
        isRunning = true
        startStopButton.text = "STOP"
        startStopButton.setBackgroundColor(android.graphics.Color.parseColor("#FFF44336"))
        statusText.setTextColor(android.graphics.Color.parseColor("#FF4CAF50"))
    }

    private fun stopOperation() {
        AutoClickService.instance?.stopEngine()
        isRunning = false
        startStopButton.text = "START"
        startStopButton.setBackgroundColor(
            android.content.res.ColorStateList.valueOf(
                android.graphics.Color.parseColor("#FF6200EE")))
        statusText.text = "Stopped"
        statusText.setTextColor(android.graphics.Color.parseColor("#FF9E9E9E"))
    }

    private fun buildConfig(): SwipeConfig {
        val freq = frequencySeekBar.progress.coerceIn(1, 1000)
        val duration = durationSeekBar.progress.coerceAtLeast(1)
        return SwipeConfig(
            pointAX = pointAX, pointAY = pointAY,
            pointBX = pointBX, pointBY = pointBY,
            frequency = freq, swipeDurationMs = duration,
            mode = currentMode, executionMode = currentExecutionMode
        )
    }

    private fun onServiceStatus(msg: String) {
        statusText.text = msg
        val color = if (msg.contains("run") || msg.contains("connect"))
            android.graphics.Color.parseColor("#FF4CAF50")
        else android.graphics.Color.parseColor("#FF9E9E9E")
        statusText.setTextColor(color)
    }

    private fun onOperationCount(count: Long) {}
    private fun onEngineError(error: String) {
        statusText.text = "Error: " + error
        statusText.setTextColor(android.graphics.Color.parseColor("#FFF44336"))
    }

    private fun updatePointDisplay() {
        pointAText.text = "Point A: (" + pointAX.toInt() + ", " + pointAY.toInt() + ")"
        pointBText.text = "Point B: (" + pointBX.toInt() + ", " + pointBY.toInt() + ")"
    }
}
