package com.dls.quickshield

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView

class DecoyOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var edgeTriggerView: View? = null
    private var fullDecoyView: FrameLayout? = null
    private var isDecoyActive = false

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        setupEdgeTrigger()
    }

    private fun startForegroundNotification() {
        val channelId = "quickshield_overlay_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "QuickShield Privacy Guard",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channelId)
                .setContentTitle("QuickShield Active")
                .setContentText("Edge handle par double-tap karke screen chhupayein")
                .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("QuickShield Active")
                .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
                .build()
        }

        startForeground(101, notification)
    }

    private fun overlayLayoutType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
    }

    private fun setupEdgeTrigger() {
        val density = resources.displayMetrics.density
        val widthPx = (18 * density).toInt()
        val heightPx = (90 * density).toInt()

        val handle = View(this).apply {
            setBackgroundColor(Color.argb(90, 56, 189, 248))
        }

        val params = WindowManager.LayoutParams(
            widthPx,
            heightPx,
            overlayLayoutType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            x = 0
            y = 0
        }

        val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                showFullDecoy()
                return true
            }
        })

        handle.setOnTouchListener { _, event ->
            detector.onTouchEvent(event)
            true
        }

        edgeTriggerView = handle
        windowManager.addView(handle, params)
    }

    private fun showFullDecoy() {
        if (isDecoyActive) return

        val prefs = getSharedPreferences("quickshield_prefs", Context.MODE_PRIVATE)
        val mode = prefs.getString("decoy_mode", "text") ?: "text"
        val boringText = prefs.getString(
            "decoy_text",
            "Notes: Quarterly Tax & Inventory Compliance Guidelines..."
        ) ?: ""
        val imageUriString = prefs.getString("decoy_image_uri", null)

        val container = FrameLayout(this).apply {
            setBackgroundColor(Color.WHITE)
        }

        if (mode == "image" && !imageUriString.isNullOrEmpty()) {
            val imageView = ImageView(this).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                setBackgroundColor(Color.BLACK)
                setImageURI(Uri.parse(imageUriString))
            }
            container.addView(
                imageView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
        } else {
            val scrollView = ScrollView(this)
            val textView = TextView(this).apply {
                text = boringText
                textSize = 16f
                setTextColor(Color.DKGRAY)
                setPadding(48, 64, 48, 64)
            }
            scrollView.addView(textView)
            container.addView(
                scrollView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
        }

        val restoreDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                hideFullDecoy()
                return true
            }
        })

        container.setOnTouchListener { _, event ->
            restoreDetector.onTouchEvent(event)
            true
        }

        val fullParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayLayoutType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.OPAQUE
        )

        fullDecoyView = container
        windowManager.addView(container, fullParams)
        isDecoyActive = true
    }

    private fun hideFullDecoy() {
        if (!isDecoyActive) return
        fullDecoyView?.let {
            windowManager.removeView(it)
        }
        fullDecoyView = null
        isDecoyActive = false
    }

    override fun onDestroy() {
        hideFullDecoy()
        edgeTriggerView?.let {
            windowManager.removeView(it)
        }
        edgeTriggerView = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
