package com.pulse.app

object Perf {
    private fun sp() = Store.sp
    private fun put(cmds: MutableList<String>, ns: String, k: String, v: String) {
        val key = "o_${ns}_$k"
        if (!sp().contains(key)) sp().edit().putString(key, Sh.run("settings get $ns $k").trim()).apply()
        cmds += "settings put $ns $k $v"
    }
    fun apply() {
        val pkg = Store.s("pkg"); val b = Store::b
        val c = mutableListOf<String>(); val ed = sp().edit()
        if (b("res")) {
            val line = Sh.run("wm size").lineSequence().firstOrNull { it.contains("Physical") } ?: ""
            Regex("(\\d+)x(\\d+)").find(line)?.let { m ->
                val s = Store.i("scale")
                c += "wm size ${m.groupValues[1].toInt() * s / 100}x${m.groupValues[2].toInt() * s / 100}"; ed.putBoolean("a_res", true)
            }
        }
        if (b("game")) { c += "cmd game mode performance $pkg"; ed.putBoolean("a_game", true) }
        if (b("compile")) c += "cmd package compile -m speed -f $pkg"
        if (b("trim")) c += "am kill-all"
        if (b("cache")) c += "pm trim-caches 8G"
        if (b("kill") || b("bg")) {
            val wl = Store.s("white").split(",").map { it.trim() }.toSet() + setOf(pkg, "com.pulse.app")
            val t = Sh.run("pm list packages -3").lines().map { it.removePrefix("package:").trim() }.filter { it.isNotEmpty() && it !in wl }
            if (b("kill")) t.forEach { c += "am force-stop $it" }
            if (b("bg")) { t.forEach { c += "cmd appops set $it RUN_ANY_IN_BACKGROUND deny" }; ed.putStringSet("a_bg", t.toSet()) }
        }
        if (b("refresh")) { val hz = Store.i("hz").toString(); put(c, "system", "peak_refresh_rate", hz); put(c, "system", "min_refresh_rate", hz) }
        if (b("anim")) listOf("window_animation_scale", "transition_animation_scale", "animator_duration_scale").forEach { put(c, "global", it, "0") }
        if (b("dnd")) { c += "cmd notification set_dnd priority"; ed.putBoolean("a_dnd", true) }
        put(c, "global", "low_power", "0")
        if (b("sustain")) { c += "cmd power set-fixed-performance-mode-enabled true"; ed.putBoolean("a_sus", true) }
        if (b("touch")) { put(c, "system", "pointer_location", "0"); put(c, "system", "show_touches", "0") }
        ed.apply()
        Sh.run(c.joinToString("; "))
    }
    fun restore() {
        val c = mutableListOf<String>(); val pkg = Store.s("pkg")
        if (sp().getBoolean("a_res", false)) c += "wm size reset"
        if (sp().getBoolean("a_game", false)) c += "cmd game mode standard $pkg"
        if (sp().getBoolean("a_dnd", false)) c += "cmd notification set_dnd off"
        if (sp().getBoolean("a_sus", false)) c += "cmd power set-fixed-performance-mode-enabled false"
        sp().getStringSet("a_bg", emptySet())?.forEach { c += "cmd appops set $it RUN_ANY_IN_BACKGROUND allow" }
        sp().all.keys.filter { it.startsWith("o_") }.forEach { key ->
            val p = key.split("_", limit = 3); val o = sp().getString(key, "null") ?: "null"
            c += if (o == "null" || o.isEmpty()) "settings delete ${p[1]} ${p[2]}" else "settings put ${p[1]} ${p[2]} $o"
        }
        val ed = sp().edit()
        sp().all.keys.filter { it.startsWith("o_") || it.startsWith("a_") }.forEach { ed.remove(it) }
        ed.apply()
        if (c.isNotEmpty()) Sh.run(c.joinToString("; "))
    }
    fun preset(n: Int) {
        val all = listOf("res", "game", "compile", "kill", "bg", "refresh", "anim", "dnd", "trim", "touch", "cache", "sustain")
        val on = when (n) { 0 -> setOf("game", "compile", "dnd"); 1 -> setOf("res", "game", "compile", "kill", "refresh", "dnd", "trim", "touch"); else -> all.toSet() }
        all.forEach { Store.setB(it, it in on) }
        Store.setI("scale", when (n) { 0 -> 100; 1 -> 85; else -> 70 })
    }
}￼Enter
