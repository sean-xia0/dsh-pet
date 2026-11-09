package com.dsh.pet

import android.Manifest
import android.app.*
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.speech.*
import android.view.*
import android.widget.*
import kotlin.math.abs

class MainActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val ll = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(56, 140, 56, 56)
        }
        ll.addView(TextView(this).apply { text = "🐳 DSH 桌宠"; textSize = 26f })
        ll.addView(TextView(this).apply {
            text = "\n1) 悬浮窗权限  2) 麦克风权限  3) 启动桌宠\n\n戳一下 = 打开 DSH\n按住 0.5 秒 = 说话发任务\n可拖动"
            textSize = 15f
            setPadding(0, 24, 0, 32)
        })
        ll.addView(btn("① 悬浮窗权限") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        })
        ll.addView(btn("② 麦克风权限") {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        })
        ll.addView(btn("③ 启动桌宠") {
            startForegroundService(Intent(this, PetService::class.java))
        })
        ll.addView(btn("停止桌宠") { stopService(Intent(this, PetService::class.java)) })
        setContentView(ll)
    }
    private fun btn(t: String, f: () -> Unit) = Button(this).apply {
        text = t
        setOnClickListener { f() }
    }
}

class PetService : Service() {
    private lateinit var wm: WindowManager
    private lateinit var pet: TextView
    private lateinit var lp: WindowManager.LayoutParams
    private val h = Handler(Looper.getMainLooper())
    private var x0 = 0f
    private var y0 = 0f
    private var sx = 0
    private var sy = 0
    private var moved = false
    private var longDone = false

    private val longRun = Runnable {
        longDone = true
        startActivity(Intent(this, VoiceActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        notifyFg()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        pet = TextView(this).apply { text = "🐳"; textSize = 42f; setPadding(20, 20, 20, 20) }
        lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 60; y = 700 }
        wm.addView(pet, lp)
        pet.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    x0 = e.rawX; y0 = e.rawY; sx = lp.x; sy = lp.y
                    moved = false; longDone = false
                    h.postDelayed(longRun, 500)
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - x0
                    val dy = e.rawY - y0
                    if (abs(dx) > 14 || abs(dy) > 14) { moved = true; h.removeCallbacks(longRun) }
                    lp.x = sx + dx.toInt(); lp.y = sy + dy.toInt()
                    wm.updateViewLayout(pet, lp)
                }
                else -> {
                    h.removeCallbacks(longRun)
                    if (!moved && !longDone) open()
                }
            }
            true
        }
    }

    private fun open() {
        val i = packageManager.getLaunchIntentForPackage("io.github.jackwener.dsh")
        if (i != null) startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        else Toast.makeText(this, "没找到 DSH", Toast.LENGTH_SHORT).show()
    }

    private fun notifyFg() {
        val ch = "dsh_pet"
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(ch) == null)
                nm.createNotificationChannel(NotificationChannel(ch, "DSH 桌宠", NotificationManager.IMPORTANCE_MIN))
        }
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, ch) else Notification.Builder(this)
        val n = b.setContentTitle("🐳 桌宠在值班")
            .setContentText("戳一下叫我，按住 0.5 秒说话")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34)
            startForeground(1, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(1, n)
    }

    override fun onDestroy() {
        h.removeCallbacks(longRun)
        try { wm.removeView(pet) } catch (_: Exception) {}
        super.onDestroy()
    }
}

class VoiceActivity : Activity() {
    private var sr: SpeechRecognizer? = null

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "这台手机没有语音识别服务", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        sr = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onResults(r: Bundle?) {
                    val t = r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()
                    if (t.isNullOrEmpty()) finish() else send(t)
                }
                override fun onError(e: Int) {
                    Toast.makeText(this@VoiceActivity, "没听清，再来一次", Toast.LENGTH_SHORT).show()
                    finish()
                }
                override fun onReadyForSpeech(p: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(v: Float) {}
                override fun onBufferReceived(b: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(p: Bundle?) {}
                override fun onEvent(t: Int, p: Bundle?) {}
            })
        }
        sr?.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
        })
    }

    private fun send(text: String) {
        try {
            startActivity(Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                setPackage("io.github.jackwener.dsh")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            packageManager.getLaunchIntentForPackage("io.github.jackwener.dsh")
                ?.let { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
        finish()
    }

    override fun onDestroy() {
        sr?.destroy()
        super.onDestroy()
    }
}
  
