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
import androidx.lifecycle.lifecycleScope
import com.keepquickadd.databinding.ActivityMainBinding
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: KeepRepository

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { updateUI() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = KeepRepository(this)

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
            R.id.action_refresh -> {
                refreshListsFromServer()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupClickListeners() {
        binding.btnGrantPermission.setOnClickListener { requestOverlayPermission() }
        binding.btnStartWidget.setOnClickListener { startFloatingWidget() }
        binding.btnStopWidget.setOnClickListener { stopFloatingWidget() }
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

        // Show cache age
        val cacheAge = repository.getCacheAge()
        binding.tvCacheStatus.text = when {
            cacheAge < 0 -> "Lists: not loaded yet"
            cacheAge < TimeUnit.MINUTES.toMillis(1) -> "Lists: cached just now"
            cacheAge < TimeUnit.MINUTES.toMillis(60) -> "Lists: cached ${TimeUnit.MILLISECONDS.toMinutes(cacheAge)}m ago"
            else -> "Lists: cache expired"
        }
    }

    private fun refreshListsFromServer() {
        binding.tvCacheStatus.text = "Lists: refreshing..."
        lifecycleScope.launch {
            val result = repository.getLists(forceRefresh = true)
            result.fold(
                onSuccess = { lists ->
                    Toast.makeText(
                        this@MainActivity,
                        "Loaded ${lists.size} lists from server",
                        Toast.LENGTH_SHORT
                    ).show()
                    updateUI()
                },
                onFailure = { error ->
                    Toast.makeText(
                        this@MainActivity,
                        "Failed to refresh: ${error.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    updateUI()
                }
            )
        }
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        overlayPermissionLauncher.launch(intent)
    }

    private fun startFloatingWidget() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Please grant overlay permission first", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(this, FloatingWidgetService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        updateUI()
        moveTaskToBack(true)
    }

    private fun stopFloatingWidget() {
        stopService(Intent(this, FloatingWidgetService::class.java))
        Toast.makeText(this, "Floating widget stopped", Toast.LENGTH_SHORT).show()
        updateUI()
    }
}
