package com.deepfish.pet

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import com.deepfish.pet.chat.ChatOverlay

class PetService : Service(), PetWindowHost {

    companion object {
        const val ACTION_START = "com.deepfish.pet.START"
        const val ACTION_STOP = "com.deepfish.pet.STOP"
        const val ACTION_SHOW_CHAT = "com.deepfish.pet.SHOW_CHAT"
        const val ACTION_TOGGLE_TOUCH = "com.deepfish.pet.TOGGLE_TOUCH"
        const val CHANNEL_ID = "deepfish_pet"
        private const val NOTIFICATION_ID = 1

        @Volatile
        var instance: PetService? = null
            private set

        fun isRunning(): Boolean = instance != null

        fun broadcastCommand(context: Context, command: String) {
            instance?.onCommand(command)
        }
    }

    private var windowManager: WindowManager? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var petView: PetView? = null
    private var chatOverlay: ChatOverlay? = null

    private var scale = 1f
    private val density by lazy { resources.displayMetrics.density }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(@Suppress("UNUSED_PARAMETER") context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    petView?.stopWalk()
                }
                Intent.ACTION_USER_PRESENT, Intent.ACTION_SCREEN_ON -> {
                    petView?.stopWalk()
                    val action = if (Math.random() > 0.5) "stretch" else "startle"
                    petView?.onWakeEvent(action)
                }
            }
        }
    }
    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        registerScreenReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_SHOW_CHAT -> openChat()
            ACTION_TOGGLE_TOUCH -> toggleTouchThrough()
            else -> {
                val settings = Prefs.settings(this)
                scale = settings.scale
                showPet()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        instance = null
        chatOverlay?.hide()
        chatOverlay = null
        unregisterReceiver(screenReceiver)
        petView?.let { windowManager?.removeView(it) }
        petView = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "大肥鱼桌宠",
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = "桌宠常驻通知"
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, PetService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val touchIntent = PendingIntent.getService(
            this, 2,
            Intent(this, PetService::class.java).setAction(ACTION_TOGGLE_TOUCH),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val chatIntent = PendingIntent.getService(
            this, 3,
            Intent(this, PetService::class.java).setAction(ACTION_SHOW_CHAT),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val touchOn = Prefs.settings(this).touchThrough
        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("蓝色大肥鱼桌宠")
            .setContentText(if (touchOn) "已开启防误触（触摸穿透）" else "我在这里陪着你哦～")
            .setContentIntent(pending)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_menu_send,
                "聊天",
                chatIntent
            )
            .addAction(
                android.R.drawable.ic_lock_lock,
                if (touchOn) "关闭防误触" else "防误触",
                touchIntent
            )
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "隐藏桌宠", stopIntent)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setCategory(Notification.CATEGORY_SERVICE)
        }
        return builder.build()
    }

    private fun toggleTouchThrough() {
        val current = Prefs.settings(this).touchThrough
        Prefs.saveSettings(
            this,
            Prefs.settings(this).copy(touchThrough = !current)
        )
        petView?.let { view ->
            setTouchThrough(!current, view)
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm?.notify(NOTIFICATION_ID, buildNotification())
    }

    fun applyTouchThrough(on: Boolean) {
        val view = petView ?: return
        setTouchThrough(on, view)
    }

    private fun setTouchThrough(on: Boolean, view: PetView) {
        val params = layoutParams ?: return
        if (on) {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        } else {
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        }
        windowManager?.updateViewLayout(view, params)
    }

    private fun registerScreenReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenReceiver, filter)
        }
    }

    private fun showPet() {
        if (petView != null) return
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val settings = Prefs.settings(this)

        val baseWidth = (120 * density).toInt()
        val charHeight = baseWidth * 832f / 768f
        val baseHeight = charHeight.toInt() + (76 * density).toInt()
        val width = (baseWidth * scale).toInt()
        val height = (baseHeight * scale).toInt()

        val params = WindowManager.LayoutParams(
            width,
            height,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }

        val view = PetView(this)
        view.host = this
        view.freeWalk = settings.freeWalk
        view.interactiveZones = settings.interactiveZones
        view.spicyLines = settings.spicyLines
        view.behaviorIntensity = settings.behaviorIntensity
        if (settings.touchThrough) {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }

        try {
            wm.addView(view, params)
        } catch (e: Exception) {
            return
        }

        petView = view
        windowManager = wm
        layoutParams = params
    }

    private fun clampToScreen(x: Int, y: Int, w: Int, h: Int): Pair<Int, Int> {
        val bounds = applicationContext.resources.displayMetrics
        val screenW = bounds.widthPixels
        val screenH = bounds.heightPixels
        val maxX = (screenW - w).coerceAtLeast(0)
        val maxY = (screenH - h).coerceAtLeast(0)
        return Pair(x.coerceIn(0, maxX), y.coerceIn(0, maxY))
    }

    override fun moveWindow(dx: Int, dy: Int) {
        val params = layoutParams ?: return
        val view = petView ?: return
        if (!view.isAttachedToWindow) return
        val targetX = params.x + dx
        val targetY = params.y + dy
        val (nx, ny) = clampToScreen(targetX, targetY, params.width, params.height)
        params.x = nx
        params.y = ny
        windowManager?.updateViewLayout(view, params)
    }

    override fun walkStep(dx: Int, dy: Int): Boolean {
        val params = layoutParams ?: return true
        val view = petView ?: return true
        val targetX = params.x + dx
        val targetY = params.y + dy
        val (nx, ny) = clampToScreen(targetX, targetY, params.width, params.height)
        val boundary = nx != targetX || ny != targetY
        if (!boundary) {
            params.x = nx
            params.y = ny
            windowManager?.updateViewLayout(view, params)
        }
        return boundary
    }

    override fun openChat() {
        val overlay = chatOverlay ?: ChatOverlay(this).also { chatOverlay = it }
        overlay.toggle()
    }

    override fun onTap() {
        // handled by PetView itself (click line)
    }

    fun onCommand(command: String) {
        petView?.onCommand(command)
    }

    fun getPetView(): PetView = petView ?: throw IllegalStateException("桌宠未运行")
}
