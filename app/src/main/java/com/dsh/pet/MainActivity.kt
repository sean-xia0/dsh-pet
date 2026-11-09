package com.dsh.pet

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val ll = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(56, 120, 56, 56)
        }

        ll.addView(title("鲸娘桌宠 v2"))
        ll.addView(body(
            "\n① 悬浮窗权限  ② 麦克风权限（说话用）  ③ 启动桌宠\n\n" +
                "手势：\n" +
                "· 戳左脸 / 右脸 = 被戳（12% 冒爱心）\n" +
                "· 1 秒内连点 3 下 = 挠痒痒\n" +
                "· 双击 = 打开 DSH\n" +
                "· 按住 0.55 秒 = 说话，识别完发给 DSH\n" +
                "· 拖动 = 换位置（会记住）\n" +
                "· 5 分钟没互动 = 睡着，戳一下会醒\n\n" +
                "换素材：素材目录里的 <动作键>.gif 会覆盖内置版本，\n" +
                "如 idle.gif、poke-left.gif、sleeping.gif … 重启桌宠生效。"
        ))

        ll.addView(btn("① 悬浮窗权限") {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        })
        ll.addView(btn("② 麦克风权限") {
            if (Build.VERSION.SDK_INT >= 23) {
                requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
            }
        })
        ll.addView(btn("③ 启动桌宠") { startPet() })
        ll.addView(btn("重启桌宠") {
            stopService(Intent(this, PetService::class.java))
            startPet()
        })
        ll.addView(btn("停止桌宠") { stopService(Intent(this, PetService::class.java)) })
        ll.addView(btn("导出内置素材（方便替换）") { exportAssets() })

        setContentView(ScrollView(this).apply { addView(ll) })
    }

    private fun startPet() {
        val i = Intent(this, PetService::class.java)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
    }

    private fun exportAssets() {
        Thread {
            val base = getExternalFilesDir(null)
            val dir = if (base != null) File(base, "pet") else File(filesDir, "pet")
            dir.mkdirs()
            var n = 0
            for (key in KEYS) {
                val id = resources.getIdentifier(key.replace('-', '_'), "raw", packageName)
                if (id == 0) continue
                try {
                    resources.openRawResource(id).use { input ->
                        FileOutputStream(File(dir, "$key.gif")).use { out -> input.copyTo(out) }
                    }
                    n++
                } catch (_: Exception) {
                }
            }
            val msg = "已导出 $n 张 GIF 到\n${dir.absolutePath}"
            runOnUiThread { Toast.makeText(this, msg, Toast.LENGTH_LONG).show() }
        }.start()
    }

    private fun title(t: String) = TextView(this).apply {
        text = t
        textSize = 26f
    }

    private fun body(t: String) = TextView(this).apply {
        text = t
        textSize = 15f
        setPadding(0, 20, 0, 32)
    }

    private fun btn(t: String, f: () -> Unit) = Button(this).apply {
        text = t
        textSize = 17f
        setOnClickListener { f() }
    }

    companion object {
        private val KEYS = listOf(
            "idle", "idle-look", "idle-spout", "thinking", "typing", "music",
            "conducting", "building", "error", "happy", "notification",
            "compacting", "carrying", "sleeping", "waking", "poke-left",
            "poke-right", "tickle", "drag", "swim", "smile-hearts", "thumbs-up"
        )
    }
}
