package com.dsh.pet

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.ImageDecoder
import android.graphics.PixelFormat
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.Toast
import java.io.File
import kotlin.math.abs
import kotlin.random.Random

/**
 * 鲸娘桌宠：透明悬浮窗里放一张 GIF 像素鲸娘。
 *
 * 手势：戳左脸 / 右脸 = 被戳，1 秒内连点 3 下 = 挠痒痒，
 * 双击 = 打开 DSH，按住 0.55 秒 = 说话发给 DSH，拖动 = 换位置。
 *
 * 素材：优先读 Android/data/com.dsh.pet/files/pet/<动作键>.gif，
 * 没有就用内置 res/raw（同名，- 换成 _）。
 */
class PetService : Service() {

    private lateinit var wm: WindowManager
    private lateinit var pet: ImageView
    private lateinit var lp: WindowManager.LayoutParams
    private val handler = Handler(Looper.getMainLooper())

    private var currentKey = ""
    private var sleeping = false
    private var lastInteract = SystemClock.uptimeMillis()

    private var downRawX = 0f
    private var downRawY = 0f
    private var startX = 0
    private var startY = 0
    private var moved = false
    private var longFired = false
    private var dragPlayed = false

    private var tapCount = 0
    private var lastTapAt = 0L
    private var tapX = 0f
    private val tapSingle = Runnable { poke() }
    private val tapDouble = Runnable { openDsh() }

    private val longPress = Runnable {
        longFired = true
        try {
            startActivity(
                Intent(this, VoiceActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
        }
    }

    private val ticker = object : Runnable {
        override fun run() {
            val idleNow = currentKey == KEY_IDLE
            when {
                sleeping -> Unit
                idleNow && SystemClock.uptimeMillis() - lastInteract > SLEEP_AFTER_MS -> {
                    sleeping = true
                    play("sleeping", true)
                }
                idleNow -> play(IDLE_EXTRAS.random(), false)
            }
            handler.postDelayed(this, TICK_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        keepAliveNotification()

        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "还没给悬浮窗权限，鲸娘起不来", Toast.LENGTH_LONG).show()
            stopSelf()
            return
        }

        wm = getSystemService(WINDOW_SERVICE) as WindowManager

        val w = (resources.displayMetrics.density * PET_DP).toInt()
        val h = w * GIF_H / GIF_W

        pet = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            isClickable = true
        }

        val prefs = getSharedPreferences("pet", MODE_PRIVATE)
        lp = WindowManager.LayoutParams(
            w,
            h,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.getInt("x", 60)
            y = prefs.getInt("y", 700)
        }

        try {
            wm.addView(pet, lp)
        } catch (e: Exception) {
            Toast.makeText(this, "悬浮窗加不上：${e.message}", Toast.LENGTH_LONG).show()
            stopSelf()
            return
        }

        play(KEY_IDLE, true)
        handler.postDelayed(ticker, TICK_MS)

        pet.setOnTouchListener { v, e -> onPetTouch(v, e) }
    }

    private fun onPetTouch(v: View, e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = e.rawX
                downRawY = e.rawY
                startX = lp.x
                startY = lp.y
                moved = false
                longFired = false
                dragPlayed = false
                handler.postDelayed(longPress, LONG_PRESS_MS)
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = e.rawX - downRawX
                val dy = e.rawY - downRawY
                if (!moved && (abs(dx) > 16f || abs(dy) > 16f)) {
                    moved = true
                    handler.removeCallbacks(longPress)
                }
                if (moved) {
                    if (!dragPlayed) {
                        dragPlayed = true
                        play("drag", true)
                    }
                    lp.x = startX + dx.toInt()
                    lp.y = startY + dy.toInt()
                    wm.updateViewLayout(pet, lp)
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPress)
                lastInteract = SystemClock.uptimeMillis()
                if (moved) {
                    getSharedPreferences("pet", MODE_PRIVATE).edit()
                        .putInt("x", lp.x)
                        .putInt("y", lp.y)
                        .apply()
                    if (currentKey == "drag") backToIdle()
                } else if (!longFired) {
                    onTap(e.x, v.width)
                }
            }
        }
        return true
    }

    private fun onTap(x: Float, width: Int) {
        val now = SystemClock.uptimeMillis()
        if (now - lastTapAt > TAP_GAP_MS) tapCount = 0
        tapCount += 1
        lastTapAt = now
        tapX = x
        when {
            tapCount >= 3 -> {
                handler.removeCallbacks(tapSingle)
                handler.removeCallbacks(tapDouble)
                tapCount = 0
                lastInteract = now
                setAnim("tickle")
            }
            tapCount == 2 -> {
                handler.removeCallbacks(tapSingle)
                handler.postDelayed(tapDouble, TAP_DELAY_MS)
            }
            else -> handler.postDelayed(tapSingle, TAP_DELAY_MS)
        }
    }

    private fun poke() {
        lastInteract = SystemClock.uptimeMillis()
        if (sleeping) {
            sleeping = false
            setAnim("waking")
            return
        }
        if (Random.nextInt(100) < HEART_CHANCE) {
            setAnim("smile-hearts")
            return
        }
        setAnim(if (tapX < pet.width / 2f) "poke-left" else "poke-right")
    }

    private fun setAnim(key: String) = play(key, false)

    private fun backToIdle() {
        if (sleeping) play("sleeping", true) else play(KEY_IDLE, true)
    }

    /** 播一段动画；loop=true 一直循环，false 播完自动回待机。 */
    private fun play(key: String, loop: Boolean) {
        val d = decode(key)
        if (d == null) {
            if (key != KEY_IDLE) play(KEY_IDLE, true)
            return
        }
        currentKey = key
        pet.setImageDrawable(d)
        if (d is AnimatedImageDrawable) {
            if (loop) {
                d.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
            } else {
                d.repeatCount = 0
                d.setAnimationCallback(object : AnimatedImageDrawable.AnimationCallback() {
                    override fun onAnimationEnd(drawable: Drawable?) {
                        if (currentKey == key) backToIdle()
                    }
                })
            }
            d.start()
        } else if (!loop) {
            handler.postDelayed({ if (currentKey == key) backToIdle() }, 2000L)
        }
    }

    private fun decode(key: String): Drawable? {
        return try {
            val base = getExternalFilesDir(null)
            val dir = if (base != null) File(base, "pet") else File(filesDir, "pet")
            val user = File(dir, "$key.gif")
            if (user.isFile) {
                ImageDecoder.decodeDrawable(ImageDecoder.createSource(user))
            } else {
                val id = resources.getIdentifier(key.replace('-', '_'), "raw", packageName)
                if (id == 0) null
                else ImageDecoder.decodeDrawable(ImageDecoder.createSource(resources, id))
            }
        } catch (e: Throwable) {
            null
        }
    }

    private fun openDsh() {
        val i = packageManager.getLaunchIntentForPackage("io.github.jackwener.dsh")
        if (i != null) {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                startActivity(i)
            } catch (_: Exception) {
            }
        } else {
            Toast.makeText(this, "没找到 DSH", Toast.LENGTH_SHORT).show()
        }
    }

    private fun keepAliveNotification() {
        val id = "dsh_pet"
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(id) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(id, "DSH 鲸娘桌宠", NotificationManager.IMPORTANCE_MIN)
                )
            }
        }
        val n = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, id)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
            .setContentTitle("鲸娘在值班")
            .setContentText("戳脸、双击开 DSH、按住 0.5 秒说话")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        try {
            if (this::pet.isInitialized) wm.removeView(pet)
        } catch (_: Exception) {
        }
        super.onDestroy()
    }

    companion object {
        const val KEY_IDLE = "idle"
        const val PET_DP = 104f
        const val GIF_W = 552
        const val GIF_H = 528
        private const val TICK_MS = 15000L
        private const val SLEEP_AFTER_MS = 5 * 60 * 1000L
        private const val LONG_PRESS_MS = 550L
        private const val TAP_GAP_MS = 420L
        private const val TAP_DELAY_MS = 300L
        private const val HEART_CHANCE = 12
        private val IDLE_EXTRAS = listOf(
            "idle-look", "idle-spout", "swim", "music", "conducting", "building"
        )
    }
}
