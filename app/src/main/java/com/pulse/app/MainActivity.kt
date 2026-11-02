package com.pulse.app

import android.content.*
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        if (android.os.Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 0)
        setContent { PulseApp() }
    }
}

@Composable fun PulseApp() {
    MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF7C5CFF), background = Color.Black, surface = Color(0xFF0B0B10))) {
        Surface(Modifier.fillMaxSize(), color = Color.Black) {
            var tab by remember { mutableIntStateOf(0) }
            Column {
                Text("Pulse", fontSize = 28.sp, modifier = Modifier.padding(start = 20.dp, top = 40.dp, bottom = 8.dp))
                TabRow(tab, containerColor = Color.Transparent) {
                    listOf("Home", "HUD", "Perf", "History").forEachIndexed { i, t -> Tab(tab == i, { tab = i }, text = { Text(t) }) }
                }
                Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    when (tab) { 0 -> Home(); 1 -> Hud(); 2 -> PerfTab(); else -> History() }
                }
            }
        }
    }
}

@Composable fun Glass(content: @Composable ColumnScope.() -> Unit) {
    val sh = RoundedCornerShape(20.dp)
    Column(Modifier.fillMaxWidth().clip(sh).background(Color(0x14FFFFFF)).border(1.dp, Color(0x22FFFFFF), sh).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

@Composable fun Tog(k: String, label: String, sub: String = "") {
    val h = LocalHapticFeedback.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(label); if (sub.isNotEmpty()) Text(sub, fontSize = 12.sp, color = Color.Gray) }
        Switch(Store.b(k), { Store.setB(k, it); h.performHapticFeedback(HapticFeedbackType.LongPress) })
    }
}

@Composable fun Field(k: String, label: String) {
    OutlinedTextField(Store.s(k), { Store.setS(k, it) }, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth())
}

@Composable fun Home() {
    val ctx = LocalContext.current
    var running by remember { mutableStateOf(false) }; var ready by remember { mutableStateOf(false) }; var overlay by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { while (true) { running = Sh.running(); ready = Sh.ready(); overlay = Settings.canDrawOverlays(ctx); delay(1000) } }
    Glass {
        Text("Setup", fontSize = 18.sp)
        Text(if (!running) "● Shizuku not running — open Shizuku and start it" else if (!ready) "● Shizuku running, permission needed" else "● Shizuku ready", color = if (ready) Color(0xFF4ADE80) else Color(0xFFFBBF24))
        if (running && !ready) Button({ try { Shizuku.requestPermission(1) } catch (e: Throwable) {} }) { Text("Grant Shizuku permission") }
        Text(if (overlay) "● Overlay permission granted" else "● Overlay permission needed", color = if (overlay) Color(0xFF4ADE80) else Color(0xFFFBBF24))
        if (!overlay) Button({ ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))) }) { Text("Grant overlay permission") }
    }
    Glass {
        val on = OverlayService.on.value
        Button({
            if (on) ctx.stopService(Intent(ctx, OverlayService::class.java))
            else ContextCompat.startForegroundService(ctx, Intent(ctx, OverlayService::class.java))
        }, Modifier.fillMaxWidth().height(60.dp)) { Text(if (on) "Stop overlay" else "Launch overlay", fontSize = 18.sp) }
        Text("With Auto-launch on, Pulse waits for ${Store.s("pkg")} to open, applies your profile, and restores everything when you leave.", fontSize = 12.sp, color = Color.Gray)
        OutlinedButton({ Thread { Perf.restore() }.start() }, Modifier.fillMaxWidth()) { Text("Panic: restore everything now") }
    }
    Glass {
        Text("Tips", fontSize = 18.sp)
        Text("• Add the “Pulse HUD” tile to Quick Settings\n• Double-tap the HUD to collapse it to a dot\n• Drag the HUD anywhere\n• The notification has Hide/Show and Panic buttons", fontSize = 13.sp, color = Color.Gray)
    }
}

@Composable fun Hud() {
    Glass {
        Text("HUD modules", fontSize = 18.sp)
        Tog("fps", "FPS counter"); Tog("low1", "1% low FPS"); Tog("graph", "Frame time graph"); Tog("cps", "CPS counter")
        Tog("temp", "Battery temp"); Tog("ram", "Free RAM"); Tog("ping", "Ping", "Uses the host below")
    }
    Glass {
        Tog("alert", "Low-FPS alert", "Vibrates below the threshold")
        Text("Threshold: ${Store.i("alertFps")} FPS", fontSize = 13.sp)
        Slider(Store.i("alertFps").toFloat(), { Store.setI("alertFps", it.toInt()) }, valueRange = 10f..60f)
    }
    Glass {
        Tog("auto", "Auto-launch", "Apply/restore when the game opens/closes")
        Field("pkg", "Game package"); Field("host", "Ping host")
    }
}

@Composable fun PerfTab() {
    Glass {
        Text("Presets", fontSize = 18.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Low", "Balanced", "Max").forEachIndexed { i, n -> OutlinedButton({ Perf.preset(i) }, Modifier.weight(1f)) { Text(n, fontSize = 13.sp) } }
        }
    }
    Glass {
        Text("Biggest gains", fontSize = 18.sp)
        Tog("res", "Resolution scaling"); Text("Scale: ${Store.i("scale")}%", fontSize = 13.sp)
        Slider(Store.i("scale").toFloat(), { Store.setI("scale", it.toInt()) }, valueRange = 50f..100f)
        Tog("game", "Game Mode → Performance", "Android 12+"); Tog("compile", "Pre-compile game (speed)")
    }
    Glass {
        Text("Extras", fontSize = 18.sp)
        Tog("kill", "Kill background apps"); Tog("bg", "Block background activity"); Tog("trim", "Memory trim"); Tog("cache", "Cache cleaner", "Never touches worlds")
        Tog("refresh", "Lock refresh rate"); Text("${Store.i("hz")} Hz", fontSize = 13.sp)
        Slider(Store.i("hz").toFloat(), { Store.setI("hz", (it / 30).toInt() * 30 .coerceAtLeast(30)) }, valueRange = 60f..120f)
        Tog("anim", "Disable animations"); Tog("dnd", "Do Not Disturb + battery saver off")
        Tog("sustain", "Sustained performance", "Best effort, phone dependent"); Tog("touch", "Touch tweaks", "Best effort")
        Field("white", "Never-kill list (comma separated)")
    }
}

@Composable fun History() {
    val ctx = LocalContext.current
    val cm = ctx.getSystemService(ClipboardManager::class.java)
    var msg by remember { mutableStateOf("") }
    Glass {
        Text("Backup", fontSize = 18.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton({ cm.setPrimaryClip(ClipData.newPlainText("pulse", Store.export())); msg = "Copied profile to clipboard" }, Modifier.weight(1f)) { Text("Export") }
            OutlinedButton({
                val t = cm.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                msg = if (Store.import(t)) "Imported" else "Clipboard isn't a Pulse profile"
            }, Modifier.weight(1f)) { Text("Import") }
        }
        if (msg.isNotEmpty()) Text(msg, fontSize = 12.sp, color = Color.Gray)
    }
    Glass {
        Text("Sessions", fontSize = 18.sp)
        val h = Store.history()
        if (h.isEmpty()) Text("No sessions yet", color = Color.Gray) else h.forEach { Text(it, fontSize = 13.sp) }
    }
}￼Enter
