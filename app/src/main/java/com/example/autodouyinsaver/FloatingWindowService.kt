package com.example.autodouyinsaver

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.NotificationCompat


class FloatingWindowService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        
        // Android 8.0+ foregorund service required
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "floating_service_channel"
            val channel = NotificationChannel(
                channelId,
                "悬浮窗服务",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
            val notification: Notification = NotificationCompat.Builder(this, channelId)
                .setContentTitle("抖音下载助手")
                .setContentText("悬浮窗运行中")
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .build()
            
            if (Build.VERSION.SDK_INT >= 34) {
                // FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                startForeground(1, notification, 1 shl 30) 
            } else {
                startForeground(1, notification)
            }
        }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createFloatingWidget()
    }

    private fun createFloatingWidget() {
        val sizeInDp = 50
        val scale = resources.displayMetrics.density
        val sizeInPx = (sizeInDp * scale + 0.5f).toInt()

        val imageView = android.widget.ImageView(this).apply {
            setImageResource(R.drawable.logo)
            scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
            layoutParams = android.view.ViewGroup.LayoutParams(sizeInPx, sizeInPx)
            
            // 使用原生轮廓裁剪为正圆形
            outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(view: View, outline: android.graphics.Outline) {
                    outline.setOval(0, 0, view.width, view.height)
                }
            }
            clipToOutline = true
            elevation = 8f * scale
        }
        floatingView = imageView

        val layoutParamsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            sizeInPx,
            sizeInPx,
            layoutParamsType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.START
        params.x = 0
        params.y = 200

        // Handle dragging and clicking
        floatingView.setOnTouchListener(object : View.OnTouchListener {
            private var initialX: Int = 0
            private var initialY: Int = 0
            private var initialTouchX: Float = 0f
            private var initialTouchY: Float = 0f
            private var isDragging = false

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isDragging = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val deltaX = event.rawX - initialTouchX
                        val deltaY = event.rawY - initialTouchY
                        // 增加宽容度到 25 像素，让点击更为“迟钝”不容易误判为拖拽
                        if (Math.abs(deltaX) > 25 || Math.abs(deltaY) > 25) {
                            isDragging = true
                            params.x = initialX + deltaX.toInt()
                            params.y = initialY + deltaY.toInt()
                            windowManager.updateViewLayout(floatingView, params)
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!isDragging) {
                            // 直接调用无障碍服务实例，比广播更可靠
                            val svc = AutoDownloadAccessibilityService.instance
                            if (svc != null) {
                                Toast.makeText(this@FloatingWindowService, "开始自动提取链接...", Toast.LENGTH_SHORT).show()
                                AutoDownloadAccessibilityService.triggerAutomation()
                            } else {
                                Toast.makeText(
                                    this@FloatingWindowService,
                                    "请先开启无障碍服务（设置→无障碍→抖音下载助手）",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                            android.util.Log.d("FloatingWindow", "点击触发，服务实例=${svc != null}")
                        }
                        return true
                    }
                }
                return false
            }
        })

        windowManager.addView(floatingView, params)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::floatingView.isInitialized) {
            windowManager.removeView(floatingView)
        }
    }
}
