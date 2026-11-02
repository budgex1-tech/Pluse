package com.pulse.app

import android.app.*
import android.content.*
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.view.*
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import java.util.concurrent.ConcurrentLinkedQueue

class OverlayService : Service() {
    companion object { val on = mutableStateOf(false) }

    private lateinit var wm: WindowManager
    private val main = Handler(Looper.getMainLooper())
    private var hud: LinearLayout? = null
    private var tv: TextView? = null
    private var gv: GraphView? = null
    private var lp: WindowManager.LayoutParams? = null
    private var collapsed = false
    @Volatile private var alive = false
    @Volatile private var gameOn = false
    private var cpsProc: Process? = null
    private val taps = ConcurrentLinkedQueue<Long>()
    private var layer = ""
    private var lastAlert = 0L
    private var fpsSum = 0.0; private var fpsN = 0; private var peakT = 0f; private var batStart = 0; private var t0 = 0L
    private var lastText = ""

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, f: Int, id: Int): Int {
        when (intent?.action) {
            "PANIC" -> { Thread { Perf.restore() }.start(); stopSelf(); return START_NOT_STICKY }
            "TOGGLE" -> { main.post { toggleCollapse() }; return START_STICKY }
        }
        if (alive) return START_STICKY
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        startNotif()
        alive = true; on.value = true
        Thread { loop() }.start()
        return START_STICKY
    }

    private fun startNotif() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("pulse", "Pulse", NotificationManager.IMPORTANCE_LOW))
        fun pi(a: String) = PendingIntent.getService(this, a.hashCode(), Intent(this, OverlayService::class.java).setAction(a), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(this, "pulse").setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("Pulse running").setContentText("Watching for ${Store.s("pkg")}")
            .addAction(0, "Hide/Show HUD", pi("TOGGLE")).addAction(0, "Panic restore", pi("PANIC")).setOngoing(true).build()
        ServiceCompat.startForeground(this, 1, n, if (Build.VERSION.SDK_INT >= 34) 0x40000000 else 0)
    }

    override fun onDestroy() {
        alive = false; on.value = false
        val was = gameOn
        Thread { if (was) { leave() } }.start()
        main.post { hideHud() }
        super.onDestroy()
    }

    private fun loop() {
        var tick = 0
        while (alive) {
            if (tick % 2 == 0) focus()
            if (gameOn) sample()
            tick++; Thread.sleep(1000)
        }
    }

    private fun focus() {
        val want = if (Store.b("auto")) Sh.run("dumpsys window | grep mCurrentFocus").contains(Store.s("pkg")) else true
        if (want && !gameOn) enter() else if (!want && gameOn) leave()
    }

    private fun enter() {
        gameOn = true; layer = ""; fpsSum = 0.0; fpsN = 0; peakT = 0f; t0 = System.currentTimeMillis(); batStart = batt()
        Perf.apply(); startCps(); main.post { showHud() }
    }

    private fun leave() {
        if (!gameOn) return
        gameOn = false; cpsProc?.destroy(); cpsProc = null
        Perf.restore(); main.post { hideHud() }
        if (fpsN > 0) {
            val mins = (System.currentTimeMillis() - t0) / 60000
            Store.addSession("${java.text.SimpleDateFormat("MMM d HH:mm", java.util.Locale.US).format(java.util.Date(t0))} • ${mins}m • avg ${(fpsSum / fpsN).toInt()} FPS • peak ${peakT}°C • battery -${maxOf(0, batStart - batt())}%")
        }
    }

    private fun batt() = (getSystemService(BATTERY_SERVICE) as BatteryManager).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    private fun temp(): Float = (registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.getIntExtra("temperature", 0) ?: 0) / 10f

    private fun startCps() {
        cpsProc = Sh.stream("getevent -l") ?: return
        val p = cpsProc!!
        Thread {
            try { p.inputStream.bufferedReader().forEachLine { l ->
                if (l.contains("ABS_MT_TRACKING_ID") && !l.contains("ffffffff")) taps.add(System.currentTimeMillis())
            } } catch (e: Throwable) {}
        }.start()
    }

    private fun sample() {
        val lines = mutableListOf<String>()
        var fps = 0f; var low = 0f; var graph = emptyList<Float>()
        if (Store.b("fps") || Store.b("low1") || Store.b("graph")) {
            if (layer.isEmpty()) layer = Sh.run("dumpsys SurfaceFlinger --list").lines().firstOrNull { it.contains(Store.s("pkg")) }?.trim() ?: ""
            if (layer.isNotEmpty()) {
                val ts = Sh.run("dumpsys SurfaceFlinger --latency \"$layer\"").lines().drop(1).mapNotNull { l ->
                    l.trim().split(Regex("\\s+")).getOrNull(1)?.toLongOrNull()?.takeIf { it > 0 && it < Long.MAX_VALUE / 2 }
                }.sorted()
                if (ts.size > 2) {
                    val last = ts.last(); val win = ts.filter { it >= last - 1_000_000_000L }
                    if (win.size > 1) fps = (win.size - 1) / ((win.last() - win.first()) / 1e9f)
                    val d = ts.zipWithNext { a, b -> (b - a) / 1e6f }
                    graph = d.takeLast(60)
                    val sorted = d.sorted(); val p99 = sorted[(sorted.size * 0.99).toInt().coerceAtMost(sorted.size - 1)]
                    if (p99 > 0) low = 1000f / p99
                }
            }
        }
        if (fps > 0) { fpsSum += fps; fpsN++ }
        val now = System.currentTimeMillis(); while (taps.peek()?.let { it < now - 1000 } == true) taps.poll()
        val t = temp(); if (t > peakT) peakT = t
        if (Store.b("fps")) lines += "FPS ${fps.toInt()}" + if (Store.b("low1")) "  1%low ${low.toInt()}" else ""
        else if (Store.b("low1")) lines += "1%low ${low.toInt()}"
        if (Store.b("cps")) lines += "CPS ${taps.size}"
        val row = mutableListOf<String>()
        if (Store.b("temp")) row += "${t}°C"
        if (Store.b("ram")) { val mi = ActivityManager.MemoryInfo(); getSystemService(ActivityManager::class.java).getMemoryInfo(mi); row += "RAM ${mi.availMem / 1048576}MB free" }
        if (Store.b("ping")) { try {
            val o = Runtime.getRuntime().exec(arrayOf("ping", "-c", "1", "-W", "1", Store.s("host"))).inputStream.bufferedReader().readText()
            row += (Regex("time=([\\d.]+)").find(o)?.groupValues?.get(1)?.let { "${it.toFloat().toInt()}ms" } ?: "ping --")
        } catch (e: Throwable) { row += "ping --" } }
        if (row.isNotEmpty()) lines += row.joinToString("  ")
        if (Store.b("alert") && fps > 0 && fps < Store.i("alertFps") && now - lastAlert > 5000) {
            lastAlert = now; (getSystemService(Vibrator::class.java)).vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
        }
        lastText = lines.joinToString("\n")
        main.post { tv?.text = if (collapsed) "●" else lastText; gv?.data = graph; gv?.visibility = if (!collapsed && Store.b("graph")) View.VISIBLE else View.GONE; gv?.invalidate() }
    }

    private fun toggleCollapse() { collapsed = !collapsed; tv?.text = if (collapsed) "●" else lastText; gv?.visibility = if (collapsed) View.GONE else View.VISIBLE }

    private fun showHud() {
        if (hud != null) return
        val t = TextView(this).apply { setTextColor(Color.WHITE); textSize = 12f; typeface = Typeface.MONOSPACE; text = "…" }
        val g = GraphView(this)
        val h = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(24, 16, 24, 16)
            background = GradientDrawable().apply { setColor(0xB3000000.toInt()); cornerRadius = 32f; setStroke(1, 0x33FFFFFF) }
            addView(t); addView(g, LinearLayout.LayoutParams(260, 70))
        }
        val p = WindowManager.LayoutParams(-2, -2, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT)
        p.gravity = Gravity.TOP or Gravity.START; p.x = 40; p.y = 80
        val gd = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean { toggleCollapse(); return true }
        })
        var ox = 0; var oy = 0; var dx = 0f; var dy = 0f
        h.setOnTouchListener { _, e ->
            gd.onTouchEvent(e)
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { ox = p.x; oy = p.y; dx = e.rawX; dy = e.rawY }
                MotionEvent.ACTION_MOVE -> { p.x = ox + (e.rawX - dx).toInt(); p.y = oy + (e.rawY - dy).toInt(); wm.updateViewLayout(h, p) }
            }; true
        }
        try { wm.addView(h, p); hud = h; tv = t; gv = g; lp = p } catch (e: Throwable) {}
    }

    private fun hideHud() { hud?.let { try { wm.removeView(it) } catch (e: Throwable) {} }; hud = null; tv = null; gv = null }
}￼Enter
