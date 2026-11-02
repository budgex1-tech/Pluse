package com.pulse.app
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateMapOf
import org.json.JSONArray
import org.json.JSONObject

object Store {
    lateinit var sp: SharedPreferences
    val bools = mutableStateMapOf<String, Boolean>()
    val ints = mutableStateMapOf<String, Int>()
    val strs = mutableStateMapOf<String, String>()
    private val defB = mapOf("fps" to true, "cps" to true, "low1" to true, "graph" to true, "temp" to true, "ram" to true,
        "ping" to false, "alert" to true, "auto" to true, "res" to true, "game" to true, "compile" to true, "kill" to true,
        "bg" to true, "refresh" to true, "anim" to true, "dnd" to true, "sustain" to false, "touch" to true, "trim" to true, "cache" to false)
    private val defI = mapOf("scale" to 80, "alertFps" to 30, "hz" to 60)
    private val defS = mapOf("pkg" to "com.mojang.minecraftpe", "host" to "1.1.1.1",
        "white" to "com.google.android.inputmethod.latin,com.google.android.dialer,moe.shizuku.privileged.api")

    fun init(c: Context) {
        if (::sp.isInitialized) return
        sp = c.getSharedPreferences("pulse", 0)
        defB.forEach { (k, v) -> bools[k] = sp.getBoolean(k, v) }
        defI.forEach { (k, v) -> ints[k] = sp.getInt(k, v) }
        defS.forEach { (k, v) -> strs[k] = sp.getString(k, v)!! }
    }
    fun b(k: String) = bools[k] ?: false
    fun i(k: String) = ints[k] ?: 0
    fun s(k: String) = strs[k] ?: ""
    fun setB(k: String, v: Boolean) { bools[k] = v; sp.edit().putBoolean(k, v).apply() }
    fun setI(k: String, v: Int) { ints[k] = v; sp.edit().putInt(k, v).apply() }
    fun setS(k: String, v: String) { strs[k] = v; sp.edit().putString(k, v).apply() }

    fun export(): String {
        val o = JSONObject()
        bools.forEach { (k, v) -> o.put(k, v) }; ints.forEach { (k, v) -> o.put(k, v) }; strs.forEach { (k, v) -> o.put(k, v) }
        return o.toString()
    }
    fun import(t: String): Boolean = try {
        val o = JSONObject(t)
        o.keys().forEach { k ->
            when { defB.containsKey(k) -> setB(k, o.getBoolean(k)); defI.containsKey(k) -> setI(k, o.getInt(k)); defS.containsKey(k) -> setS(k, o.getString(k)) }
        }; true
    } catch (e: Throwable) { false }

    fun history(): List<String> {
        val a = JSONArray(sp.getString("hist", "[]")); return (0 until a.length()).map { a.getString(it) }.reversed()
    }
    fun addSession(line: String) {
        val a = JSONArray(sp.getString("hist", "[]")); a.put(line)
        val out = JSONArray(); for (i in maxOf(0, a.length() - 30) until a.length()) out.put(a.getString(i))
        sp.edit().putString("hist", out.toString()).apply()
    }
}￼Enter
