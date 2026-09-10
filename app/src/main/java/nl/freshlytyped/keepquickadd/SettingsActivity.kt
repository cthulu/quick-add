package nl.freshlytyped.keepquickadd

import android.os.Bundle
import android.view.LayoutInflater
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.freshlytyped.keepquickadd.calendar.CalendarRepository
import nl.freshlytyped.keepquickadd.calendar.PermissionHelper
import nl.freshlytyped.keepquickadd.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var settings: AppSettings
    private lateinit var repository: KeepRepository
    private lateinit var calendarRepository: CalendarRepository
    private val calendarCheckboxes = mutableMapOf<Long, CheckBox>()
    private var loadedCalendarIds = emptySet<Long>()

    private val calendarPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        if (PermissionHelper.hasCalendarPermissions(this)) loadCalendars()
        else binding.tvCalendarStatus.text = "Calendar permission is required to show calendars."
    }

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
        calendarRepository = CalendarRepository(this)

        binding.etPublishKey.setText(settings.publishKey)
        binding.etSubscribeKey.setText(settings.subscribeKey)
        binding.etApiKey.setText(settings.apiKey)
        binding.etChannel.setText(settings.channel)
        loadListInputs()
        requestCalendarPermissionsIfNeeded()

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
        settings.getLists().forEachIndexed { i, name ->
            addListInput(name, removable = i > 0)
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
            btnRemove.setOnClickListener { binding.listInputsContainer.removeView(row) }
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

    private fun requestCalendarPermissionsIfNeeded() {
        if (PermissionHelper.hasCalendarPermissions(this)) loadCalendars()
        else calendarPermissionLauncher.launch(PermissionHelper.getRequiredPermissions())
    }

    private fun loadCalendars() {
        lifecycleScope.launch(Dispatchers.Default) {
            val calendars = calendarRepository.findAllCalendars()
            withContext(Dispatchers.Main) { showCalendars(calendars) }
        }
    }

    private fun showCalendars(calendars: List<CalendarRepository.CalendarInfo>) {
        calendarCheckboxes.clear()
        binding.calendarInputsContainer.removeAllViews()
        loadedCalendarIds = calendars.map { it.id }.toSet()
        val hiddenIds = settings.hiddenCalendarIds

        if (calendars.isEmpty()) {
            binding.tvCalendarStatus.text = "No writable calendars found."
            return
        }

        binding.tvCalendarStatus.text = "Unselect calendars to hide them from calendar quick add."
        calendars.forEach { calendar ->
            val checkbox = CheckBox(this).apply {
                text = "${calendar.displayName} (${calendar.id})"
                isChecked = calendar.id !in hiddenIds
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            calendarCheckboxes[calendar.id] = checkbox
            binding.calendarInputsContainer.addView(checkbox)
        }
    }

    // --- Connection test ---

    private fun testConnection() {
        val pub = binding.etPublishKey.text.toString().trim()
        val sub = binding.etSubscribeKey.text.toString().trim()
        val api = binding.etApiKey.text.toString().trim()
        val channel = binding.etChannel.text.toString().trim().ifBlank { AppSettings.DEFAULT_CHANNEL }

        if (pub.isEmpty() || sub.isEmpty()) {
            Toast.makeText(this, "Publish + Subscribe keys are required", Toast.LENGTH_SHORT).show()
            return
        }

        binding.tvConnectionStatus.text = "Testing connection..."
        binding.tvConnectionStatus.setTextColor(getColor(R.color.gray))
        binding.btnTestConnection.isEnabled = false

        lifecycleScope.launch {
            val result = repository.testConnection(pub, sub, api, channel)
            binding.btnTestConnection.isEnabled = true
            if (result.isSuccess) {
                binding.tvConnectionStatus.text = "✓ Connection successful"
                binding.tvConnectionStatus.setTextColor(getColor(R.color.primary_dark))
            } else {
                val message = result.exceptionOrNull()
                    ?.let(KeepFailureMapper::messageFor)
                    ?: KeepFailureMapper.messageFor(KeepFailureCategory.SERVER)
                binding.tvConnectionStatus.text = "✗ $message"
                binding.tvConnectionStatus.setTextColor(getColor(android.R.color.holo_red_dark))
            }
        }
    }

    // --- Save ---

    private fun saveSettings() {
        val pub = binding.etPublishKey.text.toString().trim()
        val sub = binding.etSubscribeKey.text.toString().trim()
        val api = binding.etApiKey.text.toString().trim()
        val channel = binding.etChannel.text.toString().trim().ifBlank { AppSettings.DEFAULT_CHANNEL }
        val hiddenCalendarIds = settings.hiddenCalendarIds
            .minus(loadedCalendarIds)
            .plus(calendarCheckboxes.filterValues { !it.isChecked }.keys)

        if (pub.isEmpty() || sub.isEmpty()) {
            Toast.makeText(this, "Publish + Subscribe keys are required", Toast.LENGTH_SHORT).show()
            return
        }

        val lists = collectListNames()
        if (lists.firstOrNull().isNullOrBlank()) {
            Toast.makeText(this, "First list name is required", Toast.LENGTH_SHORT).show()
            return
        }

        settings.publishKey = pub
        settings.subscribeKey = sub
        settings.apiKey = api
        settings.channel = channel
        settings.hiddenCalendarIds = hiddenCalendarIds
        settings.saveLists(lists)
        Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
        finish()
    }
}
