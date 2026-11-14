package com.mtgscanner.widget

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.mtgscanner.MainActivity

/** A "Scan cards" tile in the phone's quick settings panel: opens the app straight into the scanner. */
class ScanTileService : TileService() {
    override fun onStartListening() {
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(CollectionWidget.EXTRA_OPEN, "scan")
        // Unlock first when the panel was opened from the lock screen.
        val open = {
            if (Build.VERSION.SDK_INT >= 34) {
                // Its own request code: the same activity with 7 is the price-move note, whose "home" this would replace.
                val pending = PendingIntent.getActivity(this, 40, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                startActivityAndCollapse(pending)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        }
        if (isLocked) unlockAndRun { open() } else open()
    }
}
