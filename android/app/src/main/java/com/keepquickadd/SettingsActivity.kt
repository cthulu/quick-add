package com.keepquickadd

import android.os.Bundle
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
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

        binding.etBackendUrl.setText(settings.backendUrl)
        loadListInputs()

        binding.btnAddList.setOnClickListener { addListInput("", removable = true) }
        binding.btnTestConnection.setOnClickListener { testConnection() }
        binding.btnSaveUrl.setOnClickListener { saveSettings() }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    // --- List inputs ---

    private fun loadListInputs() {
        val lists = settings.getLists()
        lists.forEachIndexed { index, name ->
            addListInput(name, removable = index > 0)
        }
    }

    private fun addListInput(name: String, removable: Boolean) {
        val row = LayoutInflater.from(this)
            .inflate(R.layout.item_list_input, binding.listInputsContainer, false)

        val etName = row.findViewById<EditText>(R.id.etListName)
        val btnRemove = row.findViewById<ImageButton>(R.id.btnRemoveList)

        etName.setText(name)

        if (removable) {
            btnRemove.visibility = android.view.View.VISIBLE
            btnRemove.setOnClickListener {
                binding.listInputsContainer.removeView(row)
            }
        } else {
            btnRemove.visibility = android.view.View.INVISIBLE
            etName.hint = "Default list (required)"
        }

        binding.listInputsContainer.addView(row)
    }

    private fun collectListNames(): List<String> {
        val container = binding.listInputsContainer
        return (0 until container.childCount).map { i ->
            val row = container.getChildAt(i)
            row.findViewById<EditText>(R.id.etListName).text.toString().trim()
        }
    }

    // --- Connection test ---

    private fun testConnection() {
        val url = binding.etBackendUrl.text.toString().trim()
        if (url.isEmpty()) { Toast.makeText(this, "Please enter a URL", Toast.LENGTH_SHORT).show(); return }

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

    // --- Save ---

    private fun saveSettings() {
        val url = binding.etBackendUrl.text.toString().trim()
        if (url.isEmpty()) { Toast.makeText(this, "Please enter a URL", Toast.LENGTH_SHORT).show(); return }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            Toast.makeText(this, "URL must start with http:// or https://", Toast.LENGTH_SHORT).show()
            return
        }

        val lists = collectListNames()
        if (lists.firstOrNull().isNullOrBlank()) {
            Toast.makeText(this, "First list name is required", Toast.LENGTH_SHORT).show()
            return
        }

        settings.backendUrl = url
        settings.saveLists(lists)
        Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
        finish()
    }
}
