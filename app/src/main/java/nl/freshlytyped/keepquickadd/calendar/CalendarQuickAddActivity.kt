package nl.freshlytyped.keepquickadd.calendar

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.method.LinkMovementMethod
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
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
import nl.freshlytyped.keepquickadd.AppSettings
import nl.freshlytyped.keepquickadd.databinding.ActivityCalendarQuickAddBinding
import nl.freshlytyped.keepquickadd.databinding.LayoutCalendarFloatingWidgetBinding
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class CalendarQuickAddActivity : AppCompatActivity() {

    private companion object {
        const val PARSE_DEBOUNCE_MS = 120L
    }

    private val deviceZoneId = ZoneId.systemDefault()

    private lateinit var activityBinding: ActivityCalendarQuickAddBinding
    private lateinit var binding: LayoutCalendarFloatingWidgetBinding
    private lateinit var parserService: DateParserService
    private lateinit var calendarRepository: CalendarRepository
    private lateinit var calendarPreferences: CalendarPreferences
    private lateinit var appSettings: AppSettings
    private var currentDraft: CalendarEventDraft? = null
    private var availableCalendars: List<CalendarRepository.CalendarInfo> = emptyList()
    private var selectedCalendar: CalendarRepository.CalendarInfo? = null

    private var parseJob: Job? = null
    private var inputRevision = 0
    private var permissionsRequested = false
    private var hasObservedVisibleIme = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        if (PermissionHelper.hasCalendarPermissions(this)) {
            loadCalendars()
        } else {
            showPermissionDeniedMessage(hasTemporaryDenial())
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
        activityBinding.dimOverlay.setOnClickListener { confirmDismissIfNeeded() }

        parserService = NattyDateParserService(zoneId = deviceZoneId)
        calendarRepository = CalendarRepository(this)
        calendarPreferences = CalendarPreferences(this)
        appSettings = AppSettings(this)

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
            if (imeVisible) {
                hasObservedVisibleIme = true
            } else if (hasObservedVisibleIme) {
                finish()
            }
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
                currentDraft = null
                scheduleParse(editable?.toString() ?: "")
            }
        }
        binding.etEventInput.addTextChangedListener(textWatcher)
    }

    private fun scheduleParse(input: String) {
        parseJob?.cancel()
        val revision = ++inputRevision
        parseJob = lifecycleScope.launch {
            delay(PARSE_DEBOUNCE_MS)

            val parseResult = withContext(Dispatchers.Default) {
                parserService.parse(input, ZonedDateTime.now(deviceZoneId))
            }

            if (revision == inputRevision && binding.etEventInput.text.toString() == input) {
                applyHighlighting(input, parseResult)
                updateCurrentDraft(input, parseResult)
            }
        }
    }

    private fun applyHighlighting(input: String, result: ParseResult) {
        val editText = binding.etEventInput
        val editable = editText.text
        if (editable.toString() != input) return

        val highlightColor = ContextCompat.getColor(this, R.color.primary_dark)
        val textColor = editText.currentTextColor

        editable.getSpans(0, editable.length, RoundedBackgroundSpan::class.java)
            .forEach { editable.removeSpan(it) }

        for (range in result.matchedRanges) {
            val safeStart = range.first.coerceAtLeast(0).coerceAtMost(input.length)
            val safeEnd = (range.last + 1).coerceAtLeast(0).coerceAtMost(input.length)
            if (safeStart < safeEnd) {
                editable.setSpan(
                    RoundedBackgroundSpan(highlightColor, textColor),
                    safeStart,
                    safeEnd,
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
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
            timezoneId = deviceZoneId.id
        )
    }

    private fun setupButtons() {
        binding.btnSave.setOnClickListener { submitEvent() }
    }

    private fun confirmDismissIfNeeded() {
        if (binding.etEventInput.text.toString().trim().isEmpty()) {
            finish()
            return
        }

        AlertDialog.Builder(this)
            .setMessage("Discard this event?")
            .setPositiveButton("Discard") { _, _ -> finish() }
            .setNegativeButton("Cancel") { _, _ -> restoreInputFocus() }
            .setOnCancelListener { restoreInputFocus() }
            .show()
    }

    private fun restoreInputFocus() {
        binding.etEventInput.requestFocus()
        binding.etEventInput.post {
            WindowCompat.getInsetsController(window, binding.etEventInput)
                .show(WindowInsetsCompat.Type.ime())
        }
    }

    private fun setupCalendarSpinner() {
        binding.spinnerCalendars.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position in availableCalendars.indices) {
                    val calendar = availableCalendars[position]
                    selectedCalendar = calendar
                    calendarPreferences.saveSelectedCalendar(calendar.id)
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
            val calendars = calendarPreferences.filterHiddenCalendars(
                calendarRepository.findAllCalendars(),
                appSettings.hiddenCalendarIds
            )
            withContext(Dispatchers.Main) {
                availableCalendars = calendars
                selectedCalendar = calendarPreferences.selectCalendar(calendars)
                if (calendars.size > 1) {
                    populateCalendarSpinner(calendars)
                    binding.spinnerCalendars.visibility = View.VISIBLE

                    val selectedIndex = calendars.indexOfFirst { it.id == selectedCalendar?.id }
                    if (selectedIndex >= 0) {
                        binding.spinnerCalendars.setSelection(selectedIndex)
                    }
                } else if (calendars.isNotEmpty()) {
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

        if (!draft.hasValidTimeRange()) {
            Toast.makeText(this, "End time must be after start time", Toast.LENGTH_SHORT).show()
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

        createEvent(draft, calendar)
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
                        showEventCreatedFeedback()
                    } else {
                        showError("Failed to create event. Please try again.")
                    }
                }
            } catch (e: Exception) {
                Log.e("CalendarQuickAdd", "Error creating event", e)
                withContext(Dispatchers.Main) {
                    showError("Failed to create event. Please try again.")
                }
            }
        }
    }

    private fun showEventCreatedFeedback() {
        val draft = currentDraft ?: return
        val title = draft.titleText ?: "Event"
        val timeStr = formatParsedTime(draft, useRelativeDate = true)
        val message = "✓ $title\n$timeStr"

        binding.layoutInput.visibility = android.view.View.GONE
        binding.tvConfirmation.text = message
        binding.tvConfirmation.visibility = android.view.View.VISIBLE
        binding.tvConfirmation.movementMethod = LinkMovementMethod.getInstance()
        binding.tvConfirmation.setOnClickListener {
            openCalendarApp()
        }

        binding.root.postDelayed({
            finish()
        }, 2000)
    }

    private fun openCalendarApp() {
        startIntentSafely(
            Intent(Intent.ACTION_VIEW).setData(CalendarIntentUris.dateUri(currentDraft?.parsedStart)),
            "Cannot open calendar app"
        )
    }

    private fun openAppSettings() {
        startIntentSafely(
            Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:$packageName")),
            "Cannot open app settings"
        )
    }

    private fun startIntentSafely(intent: Intent, errorMessage: String) {
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("CalendarQuickAdd", errorMessage, e)
            Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
        }
    }

    private fun hasTemporaryDenial(): Boolean {
        return PermissionHelper.hasTemporaryDenial(this) { permission ->
            ActivityCompat.shouldShowRequestPermissionRationale(this, permission)
        }
    }

    private fun showPermissionDeniedMessage(canRetry: Boolean) {
        Snackbar.make(
            binding.root,
            "Calendar permission required to create events",
            Snackbar.LENGTH_LONG
        )
            .setAction(if (canRetry) "Retry" else "Settings") {
                if (canRetry) {
                    permissionLauncher.launch(PermissionHelper.getRequiredPermissions())
                } else {
                    openAppSettings()
                }
            }
            .show()
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun formatParsedTime(draft: CalendarEventDraft, useRelativeDate: Boolean = false): String {
        if (draft.parsedStart == null) return "No time parsed"

        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        val timeStr = draft.parsedStart.format(timeFormatter)

        val dayStr = if (useRelativeDate) {
            val now = ZonedDateTime.now(deviceZoneId)
            val today = now.toLocalDate()
            val tomorrow = today.plusDays(1)
            val eventDate = draft.parsedStart.toLocalDate()

            when (eventDate) {
                today -> "Today"
                tomorrow -> "Tomorrow"
                else -> draft.parsedStart.format(DateTimeFormatter.ofPattern("EEE, MMM d"))
            }
        } else {
            draft.parsedStart.format(DateTimeFormatter.ofPattern("EEE, MMM d"))
        }

        return if (draft.parsedEnd != null && draft.parsedEnd.isAfter(draft.parsedStart.plusHours(1))) {
            val endStr = draft.parsedEnd.format(timeFormatter)
            "$dayStr at $timeStr - $endStr"
        } else {
            "$dayStr at $timeStr"
        }
    }

    override fun onDestroy() {
        parseJob?.cancel()
        super.onDestroy()
    }
}

internal object CalendarIntentUris {
    fun dateUri(start: ZonedDateTime?): Uri {
        val builder = Uri.parse("content://com.android.calendar/time").buildUpon()
        start?.let { builder.appendPath(it.toInstant().toEpochMilli().toString()) }
        return builder.build()
    }
}
