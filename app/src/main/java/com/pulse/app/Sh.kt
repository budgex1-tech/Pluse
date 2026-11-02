package com.pulse.app
import rikka.shizuku.Shizuku

object Sh {
    fun ready() = try { Shizuku.pingBinder() && Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED } catch (e: Throwable) { false }
    fun running() = try { Shizuku.pingBinder() } catch (e: Throwable) { false }
    private fun proc(cmd: String): Process? = try {
        val m = Shizuku::class.java.getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
        m.isAccessible = true
        m.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
    } catch (e: Throwable) { null }
    fun run(cmd: String): String = try {
        val p = proc(cmd); val out = p?.inputStream?.bufferedReader()?.readText() ?: ""; p?.waitFor(); out
    } catch (e: Throwable) { "" }
    fun stream(cmd: String): Process? = proc(cmd)
}￼Enter
