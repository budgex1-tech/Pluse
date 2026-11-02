package com.pulse.app
import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat

class HudTile : TileService() {
    override fun onStartListening() { sync() }
    override fun onClick() {
        if (OverlayService.on.value) stopService(Intent(this, OverlayService::class.java))
        else ContextCompat.startForegroundService(this, Intent(this, OverlayService::class.java))
        qsTile?.apply { state = if (OverlayService.on.value) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE; updateTile() }
    }
    private fun sync() { qsTile?.apply { state = if (OverlayService.on.value) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE; label = "Pulse HUD"; updateTile() } }
}￼Enter
