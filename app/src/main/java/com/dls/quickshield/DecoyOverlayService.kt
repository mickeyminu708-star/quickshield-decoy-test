package com.dls.quickshield

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.view.GestureDetector
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView
import com.dls.quickshield.R

class DecoyOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var triggerView: View? = null
    private var fullDecoyView: FrameLayout? = null
    private var isDecoyActive = false

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        setupQuickTrigger()
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
                .setContentText("Shield button par tap/double-tap karke decoy kholein")
                .setSmallIcon(R.drawable.ic_launcher_decoy)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("QuickShield Active")
                .setSmallIcon(R.drawable.ic_launcher_decoy)
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

    private fun setupQuickTrigger() {
        if (triggerView != null) return

        val density = resources.displayMetrics.density
        val sizePx = (52 * density).toInt()

        val bubbleBg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.argb(215, 15, 23, 42))
            setStroke((2 * density).toInt(), Color.argb(230, 56, 189, 248))
        }

        val bubble = TextView(this).apply {
            text = "QS"
            textSize = 14f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = bubbleBg
            elevation = 8f
        }

        val params = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            overlayLayoutType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            x = (6 * density).toInt()
            y = 0
        }

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                showFullDecoy()
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                showFullDecoy()
                return true
            }
        })

        bubble.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (initialTouchX - event.rawX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                        isDragging = true
                        params.x = (initialX + dx).coerceAtLeast(0)
                        params.y = initialY + dy
                        try {
                            windowManager.updateViewLayout(bubble, params)
                        } catch (_: Exception) {
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        bubble.performClick()
                    }
                    true
                }
                else -> false
            }
        }

        triggerView = bubble
        try {
            windowManager.addView(bubble, params)
        } catch (_: Exception) {
        }
    }

    private fun loadBitmapFromUri(uriString: String): Bitmap? {
        return try {
            val uri = Uri.parse(uriString)
            contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun showFullDecoy() {
        if (isDecoyActive) return

        val prefs = getSharedPreferences("quickshield_prefs", Context.MODE_PRIVATE)
        val mode = prefs.getString("decoy_mode", "image") ?: "image"
        val boringText = prefs.getString(
            "decoy_text",
            "Notes: Quarterly Tax & Inventory Compliance Guidelines..."
        ) ?: ""
        val imageUriString = prefs.getString("decoy_image_uri", null)

        val container = object : FrameLayout(this) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    hideFullDecoy()
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
        }.apply {
            setBackgroundColor(Color.BLACK)
            isFocusableInTouchMode = true
            requestFocus()
        }

        val customBitmap = if (!imageUriString.isNullOrEmpty()) {
            loadBitmapFromUri(imageUriString)
        } else {
            null
        }

        if (mode == "text" && customBitmap == null) {
            container.setBackgroundColor(Color.WHITE)
            val scrollView = ScrollView(this)
            val textView = TextView(this).apply {
                text = boringText
                textSize = 16f
                setTextColor(Color.DKGRAY)
                setPadding(48, 80, 48, 80)
            }
            scrollView.addView(textView)
            container.addView(
                scrollView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
        } else {
            val imageView = ImageView(this).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                if (customBitmap != null) {
                    setImageBitmap(customBitmap)
                } else {
                    setImageResource(R.drawable.launcher_decoy)
                }
            }
            container.addView(
                imageView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
        }

        val density = resources.displayMetrics.density
        val closeSize = (48 * density).toInt()
        val closeBg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.argb(190, 20, 20, 20))
        }

        val closeBtn = TextView(this).apply {
            text = "×"
            textSize = 24f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = closeBg
            setOnClickListener {
                hideFullDecoy()
            }
        }
        val closeParams = FrameLayout.LayoutParams(closeSize, closeSize).apply {
            gravity = Gravity.TOP or Gravity.END
            topMargin = (28 * density).toInt()
            rightMargin = (18 * density).toInt()
        }
        container.addView(closeBtn, closeParams)

        val restoreDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                hideFullDecoy()
                return true
            }
        })

        container.setOnTouchListener { _, event ->
            restoreDetector.onTouchEvent(event)
            false
        }

        val fullParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayLayoutType(),
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.OPAQUE
        )

        fullDecoyView = container
        try {
            windowManager.addView(container, fullParams)
            isDecoyActive = true
        } catch (_: Exception) {
            fullDecoyView = null
            isDecoyActive = false
        }
    }

    private fun hideFullDecoy() {
        val viewToRemove = fullDecoyView
        fullDecoyView = null
        isDecoyActive = false
        if (viewToRemove != null) {
            try {
                windowManager.removeViewImmediate(viewToRemove)
            } catch (_: Exception) {
                try {
                    windowManager.removeView(viewToRemove)
                } catch (_: Exception) {
                }
            }
        }
    }

    override fun onDestroy() {
        hideFullDecoy()
        val handleToRemove = triggerView
        triggerView = null
        if (handleToRemove != null) {
            try {
                windowManager.removeViewImmediate(handleToRemove)
            } catch (_: Exception) {
                try {
                    windowManager.removeView(handleToRemove)
                } catch (_: Exception) {
                }
            }
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
