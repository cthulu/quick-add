package nl.freshlytyped.keepquickadd.calendar

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.SpannableStringBuilder
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.freshlytyped.keepquickadd.R
import nl.freshlytyped.keepquickadd.databinding.ActivityCalendarQuickAddBinding
import nl.freshlytyped.keepquickadd.databinding.LayoutCalendarFloatingWidgetBinding
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class CalendarQuickAddActivity : AppCompatActivity() {

    private lateinit var activityBinding: ActivityCalendarQuickAddBinding
    private lateinit var binding: LayoutCalendarFloatingWidgetBinding
    private lateinit var parserService: DateParserService
    private lateinit var calendarRepository: CalendarRepository
    private var currentDraft: CalendarEventDraft? = null
    private var availableCalendars: List<CalendarRepository.CalendarInfo> = emptyList()
    private var selectedCalendar: CalendarRepository.CalendarInfo? = null

    private var parseJob: Job? = null
    private var inputRevision = 0
    private var permissionsRequested = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (allGranted) {
            loadCalendars()
        } else {
            showPermissionDeniedMessage()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
        )

        activityBinding = ActivityCalendarQuickAddBinding.inflate(layoutInflater)
        setContentView(activityBinding.root)

        binding = activityBinding.popupCard
        activityBinding.dimOverlay.setOnClickListener { finish() }

        parserService = NattyDateParserService()
        calendarRepository = CalendarRepository(this)

        setupTextWatcher()
        setupButtons()
        setupCalendarSpinner()

        binding.etEventInput.requestFocus()
        binding.etEventInput.post {
            WindowCompat.getInsetsController(window, binding.etEventInput)
                .show(WindowInsetsCompat.Type.ime())
        }

        ViewCompat.setOnApplyWindowInsetsListener(activityBinding.root) { v, insets ->
            val imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            if (!imeVisible) finish()
            ViewCompat.onApplyWindowInsets(v, insets)
        }

        if (!permissionsRequested) {
            requestCalendarPermissionsIfNeeded()
        }
    }

    private var textWatcher: android.text.TextWatcher? = null

    private fun setupTextWatcher() {
        textWatcher = object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(editable: Editable?) {
                scheduleParse(editable?.toString() ?: "")
            }
        }
        binding.etEventInput.addTextChangedListener(textWatcher)
    }

    private fun scheduleParse(input: String) {
        parseJob?.cancel()
        parseJob = lifecycleScope.launch {
            delay(150)

            val revision = ++inputRevision
            val parseResult = withContext(Dispatchers.Default) {
                parserService.parse(input, ZonedDateTime.now())
            }

            if (revision == inputRevision) {
                applyHighlighting(input, parseResult)
                updateCurrentDraft(input, parseResult)
            }
        }
    }

    private fun applyHighlighting(input: String, result: ParseResult) {
        val editText = binding.etEventInput
        val cursorPosition = editText.selectionStart
        val cursorEnd = editText.selectionEnd

        val spannable = SpannableStringBuilder(input)
        val highlightColor = ContextCompat.getColor(this, R.color.primary_dark)
        val textColor = editText.currentTextColor

        for (range in result.matchedRanges) {
            val safeStart = range.first.coerceAtLeast(0).coerceAtMost(input.length)
            val safeEnd = (range.last + 1).coerceAtLeast(0).coerceAtMost(input.length)
            if (safeStart < safeEnd) {
                spannable.setSpan(
                    RoundedBackgroundSpan(highlightColor, textColor),
                    safeStart,
                    safeEnd,
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }

        runOnUiThread {
            editText.removeTextChangedListener(textWatcher)
            editText.setText(spannable)
            try {
                editText.setSelection(
                    cursorPosition.coerceAtMost(input.length),
                    cursorEnd.coerceAtMost(input.length)
                )
            } catch (e: Exception) {
                // Handle case where selection is out of bounds
            }
            editText.addTextChangedListener(textWatcher)
        }
    }

    private fun updateCurrentDraft(input: String, result: ParseResult) {
        currentDraft = CalendarEventDraft(
            rawInput = input,
            titleText = result.titleText ?: input.takeIf { it.isNotEmpty() },
            parsedStart = result.start,
            parsedEnd = result.end,
            parseState = result.state,
            matchedRanges = result.matchedRanges,
            timezoneId = java.time.ZoneId.systemDefault().id
        )
    }

    private fun setupButtons() {
        binding.btnSave.setOnClickListener { submitEvent() }
    }

    private fun setupCalendarSpinner() {
        binding.spinnerCalendars.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position in availableCalendars.indices) {
                    selectedCalendar = availableCalendars[position]
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                selectedCalendar = null
            }
        }
    }

    private fun requestCalendarPermissionsIfNeeded() {
        permissionsRequested = true
        if (!PermissionHelper.hasCalendarPermissions(this)) {
            permissionLauncher.launch(PermissionHelper.getRequiredPermissions())
        } else {
            loadCalendars()
        }
    }

    private fun loadCalendars() {
        lifecycleScope.launch(Dispatchers.Default) {
            val calendars = calendarRepository.findAllCalendars()
            withContext(Dispatchers.Main) {
                availableCalendars = calendars
                if (calendars.size > 1) {
                    populateCalendarSpinner(calendars)
                    binding.spinnerCalendars.visibility = View.VISIBLE
                    selectedCalendar = calendars.firstOrNull()
                } else if (calendars.isNotEmpty()) {
                    selectedCalendar = calendars.first()
                    binding.spinnerCalendars.visibility = View.GONE
                } else {
                    showError("No writable calendars found")
                }
            }
        }
    }

    private fun populateCalendarSpinner(calendars: List<CalendarRepository.CalendarInfo>) {
        val calendarNames = calendars.map { it.displayName }
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            calendarNames
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerCalendars.adapter = adapter
    }

    private fun submitEvent() {
        val draft = currentDraft
        val rawInput = binding.etEventInput.text.toString().trim()

        if (rawInput.isEmpty()) {
            Toast.makeText(this, "Please enter event details", Toast.LENGTH_SHORT).show()
            return
        }

        if (draft?.parseState != ParseState.RESOLVED) {
            Toast.makeText(this, "Please include a date/time", Toast.LENGTH_SHORT).show()
            return
        }

        if (!PermissionHelper.hasCalendarPermissions(this)) {
            requestCalendarPermissionsIfNeeded()
            return
        }

        proceedWithEventCreation()
    }

    private fun proceedWithEventCreation() {
        val draft = currentDraft ?: return
        
        val calendar = selectedCalendar
        if (calendar == null) {
            showError("No calendar selected. Please ensure you have at least one calendar configured.")
            return
        }

        EventConfirmationDialog(
            context = this,
            title = draft.titleText ?: "Event",
            startTime = draft.parsedStart!!,
            endTime = draft.parsedEnd,
            calendarName = calendar.displayName,
            onConfirm = {
                createEvent(draft, calendar)
            },
            onCancel = {
                // User cancelled, stay in activity
            }
        )
    }

    private fun createEvent(
        draft: CalendarEventDraft,
        calendar: CalendarRepository.CalendarInfo
    ) {
        lifecycleScope.launch(Dispatchers.Default) {
            try {
                val eventId = calendarRepository.insertEvent(
                    calendarId = calendar.id,
                    title = draft.titleText ?: "Event",
                    startTime = draft.parsedStart!!,
                    endTime = draft.parsedEnd,
                    timezone = draft.timezoneId
                )

                withContext(Dispatchers.Main) {
                    if (eventId != null) {
                        showEventCreatedFeedback(eventId, calendar.displayName)
                    } else {
                        showError("Failed to create event. Please try again.")
                    }
                }
            } catch (e: Exception) {
                Log.e("CalendarQuickAdd", "Error creating event", e)
                withContext(Dispatchers.Main) {
                    showError("Error: ${e.message}")
                }
            }
        }
    }

    private fun showEventCreatedFeedback(eventId: Long, calendarName: String) {
        val timeStr = formatParsedTime(currentDraft ?: return)
        val message = "✓ Event created in $calendarName"

        binding.layoutInput.visibility = android.view.View.GONE
        binding.tvConfirmation.text = message
        binding.tvConfirmation.visibility = android.view.View.VISIBLE

        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
            .setAction("Open") {
                openCalendarApp()
            }
            .show()

        binding.root.postDelayed({
            finish()
        }, 2000)
    }

    private fun openCalendarApp() {
        try {
            val intent = Intent(Intent.ACTION_VIEW)
                .setData(Uri.parse("content://com.android.calendar/time"))
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("CalendarQuickAdd", "Cannot open calendar app", e)
            Toast.makeText(this, "Cannot open calendar app", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showPermissionDeniedMessage() {
        Snackbar.make(
            binding.root,
            "Calendar permission required to create events",
            Snackbar.LENGTH_LONG
        )
            .setAction("Settings") {
                openAppSettings()
            }
            .show()
    }

    private fun openAppSettings() {
        try {
            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:$packageName"))
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("CalendarQuickAdd", "Cannot open app settings", e)
            Toast.makeText(this, "Cannot open app settings", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun formatParsedTime(draft: CalendarEventDraft): String {
        if (draft.parsedStart == null) return "No time parsed"

        val formatter = DateTimeFormatter.ofPattern("EEE, MMM d 'at' h:mm a")
        val startStr = draft.parsedStart.format(formatter)

        return if (draft.parsedEnd != null && draft.parsedEnd.isAfter(draft.parsedStart.plusHours(1))) {
            val endStr = draft.parsedEnd.format(DateTimeFormatter.ofPattern("h:mm a"))
            "$startStr - $endStr"
        } else {
            startStr
        }
    }

    override fun onDestroy() {
        parseJob?.cancel()
        super.onDestroy()
    }
}
