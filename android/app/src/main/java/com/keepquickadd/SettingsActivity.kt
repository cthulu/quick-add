package com.keepquickadd

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.keepquickadd.databinding.ActivitySettingsBinding
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var settings: AppSettings
    private lateinit var repository: KeepRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.apply {
            title = "Settings"
            setDisplayHomeAsUpEnabled(true)
        }

        settings = AppSettings(this)
        repository = KeepRepository(this)

        // Pre-fill current URL
        binding.etBackendUrl.setText(settings.backendUrl)

        binding.btnTestConnection.setOnClickListener {
            testConnection()
        }

        binding.btnSaveUrl.setOnClickListener {
            saveUrl()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun testConnection() {
        val url = binding.etBackendUrl.text.toString().trim()
        if (url.isEmpty()) {
            Toast.makeText(this, "Please enter a URL", Toast.LENGTH_SHORT).show()
            return
        }

        binding.tvConnectionStatus.text = "Testing connection..."
        binding.tvConnectionStatus.setTextColor(getColor(R.color.gray))
        binding.btnTestConnection.isEnabled = false

        lifecycleScope.launch {
            val result = repository.testConnection(url)
            binding.btnTestConnection.isEnabled = true
            if (result.isSuccess) {
                binding.tvConnectionStatus.text = "✓ Connection successful"
                binding.tvConnectionStatus.setTextColor(getColor(R.color.primary_dark))
            } else {
                binding.tvConnectionStatus.text = "✗ Failed: ${result.exceptionOrNull()?.message}"
                binding.tvConnectionStatus.setTextColor(getColor(android.R.color.holo_red_dark))
            }
        }
    }

    private fun saveUrl() {
        val url = binding.etBackendUrl.text.toString().trim()
        if (url.isEmpty()) {
            Toast.makeText(this, "Please enter a URL", Toast.LENGTH_SHORT).show()
            return
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            Toast.makeText(this, "URL must start with http:// or https://", Toast.LENGTH_SHORT).show()
            return
        }
        settings.backendUrl = url
        // Clear the list cache so it re-fetches from new URL
        repository.clearCache()
        Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
        finish()
    }
}
