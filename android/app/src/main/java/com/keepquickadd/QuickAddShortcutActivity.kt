package com.keepquickadd

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * Transparent activity that launches the floating widget directly.
 * Can be added as a shortcut to the launcher for quick access.
 */
class QuickAddShortcutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check if we have overlay permission
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(
                this,
                "Please grant overlay permission in the main app first",
                Toast.LENGTH_LONG
            ).show()
            // Open main activity to grant permission
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        // Start or show the floating widget
        if (!FloatingWidgetService.isRunning) {
            val intent = Intent(this, FloatingWidgetService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }

        // Close this activity immediately - it's just a launcher
        finish()
    }
}
