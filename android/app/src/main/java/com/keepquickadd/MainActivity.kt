package com.keepquickadd

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.keepquickadd.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { updateUI() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        setupClickListeners()
        updateUI()
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupClickListeners() {
        binding.btnGrantPermission.setOnClickListener { requestOverlayPermission() }
        binding.btnStartWidget.setOnClickListener { startFloatingWidget() }
        binding.btnStopWidget.setOnClickListener { stopFloatingWidget() }
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun updateUI() {
        val hasPermission = Settings.canDrawOverlays(this)
        val isServiceRunning = FloatingWidgetService.isRunning

        if (hasPermission) {
            binding.tvStatus.text = "✓ Overlay permission granted"
            binding.tvStatus.setTextColor(getColor(R.color.primary_dark))
            binding.btnGrantPermission.visibility = View.GONE
            binding.btnStartWidget.visibility = if (isServiceRunning) View.GONE else View.VISIBLE
            binding.btnStopWidget.visibility = if (isServiceRunning) View.VISIBLE else View.GONE
        } else {
            binding.tvStatus.text = "⚠ Overlay permission required"
            binding.tvStatus.setTextColor(getColor(R.color.gray))
            binding.btnGrantPermission.visibility = View.VISIBLE
            binding.btnStartWidget.visibility = View.GONE
            binding.btnStopWidget.visibility = View.GONE
        }
    }

    private fun requestOverlayPermission() {
        overlayPermissionLauncher.launch(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        )
    }

    private fun startFloatingWidget() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Please grant overlay permission first", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(this, FloatingWidgetService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
        else startService(intent)
        updateUI()
        moveTaskToBack(true)
    }

    private fun stopFloatingWidget() {
        stopService(Intent(this, FloatingWidgetService::class.java))
        Toast.makeText(this, "Floating widget stopped", Toast.LENGTH_SHORT).show()
        updateUI()
    }
}
