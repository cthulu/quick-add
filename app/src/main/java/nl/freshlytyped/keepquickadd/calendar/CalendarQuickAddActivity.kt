package nl.freshlytyped.keepquickadd.calendar

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.CalendarContract
import android.text.Editable
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.MaterialColors
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.freshlytyped.keepquickadd.AppSettings
import nl.freshlytyped.keepquickadd.R
import nl.freshlytyped.keepquickadd.SettingsActivity
import nl.freshlytyped.keepquickadd.databinding.ActivityCalendarQuickAddBinding
import nl.freshlytyped.keepquickadd.databinding.LayoutCalendarFloatingWidgetBinding
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class CalendarQuickAddActivity : AppCompatActivity() {

    private companion object {
        const val PARSE_DEBOUNCE_MS = 120L
        const val EVENT_LOOKUP_DEBOUNCE_MS = 300L
    }

    private val deviceZoneId = ZoneId.systemDefault()

    private lateinit var activityBinding: ActivityCalendarQuickAddBinding
    private lateinit var binding: LayoutCalendarFloatingWidgetBinding
    private lateinit var parserService: DateParserService
    private lateinit var calendarRepository: CalendarRepository
    private lateinit var calendarPreferences: CalendarPreferences
    private lateinit var appSettings: AppSettings
    private var currentDraft: CalendarEventDraft? = null
    private var previewCalendars: List<CalendarRepository.CalendarInfo> = emptyList()
    private var selectedCalendar: CalendarRepository.CalendarInfo? = null

    private var parseJob: Job? = null
    private var eventLookupJob: Job? = null
    private var inputRevision = 0
    private var lookupRevision = 0
    private var permissionsRequested = false
    private var hasRequestedKeyboard = false

    private lateinit var eventPreviewAdapter: EventPreviewAdapter

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        if (PermissionHelper.hasReadCalendarPermission(this)) {
            loadCalendars()
        } else {
            showPermissionDeniedMessage(hasTemporaryDenial())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DynamicColors.applyToActivityIfAvailable(this)

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
        setupEventPreview()

        binding.etEventInput.requestFocus()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val imeVisible = ViewCompat.getRootWindowInsets(binding.etEventInput)
                    ?.isVisible(WindowInsetsCompat.Type.ime()) == true
                if (imeVisible) {
                    WindowCompat.getInsetsController(window, binding.etEventInput)
                        .hide(WindowInsetsCompat.Type.ime())
                } else {
                    confirmDismissIfNeeded()
                }
            }
        })

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
                val text = editable?.toString() ?: ""
                scheduleParse(text)
                scheduleEventLookup(text)
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

    private fun setupEventPreview() {
        eventPreviewAdapter = EventPreviewAdapter()
        binding.previewPanel.rvPreviewEvents.layoutManager = LinearLayoutManager(this)
        binding.previewPanel.rvPreviewEvents.adapter = eventPreviewAdapter
    }

    private fun scheduleEventLookup(input: String) {
        eventLookupJob?.cancel()
        val revision = ++lookupRevision
        eventLookupJob = lifecycleScope.launch {
            delay(EVENT_LOOKUP_DEBOUNCE_MS)
            if (revision != lookupRevision) return@launch

            val parseResult = withContext(Dispatchers.Default) {
                parserService.parse(input, ZonedDateTime.now(deviceZoneId))
            }
            if (revision != lookupRevision) return@launch

            val start = parseResult.start
            val end = parseResult.end
            if (parseResult.state != ParseState.RESOLVED || start == null || end == null) {
                hidePreviewPanel()
                return@launch
            }

            val day = start.toLocalDate()
            val dayStartMillis = day.atStartOfDay(deviceZoneId).toInstant().toEpochMilli()
            val dayEndMillis = day.plusDays(1).atStartOfDay(deviceZoneId).toInstant().toEpochMilli()

            // Read-only shared calendars show up too, so the preview can warn about conflicts there.
            val events = withContext(Dispatchers.Default) {
                calendarRepository.findEventsOnDay(dayStartMillis, dayEndMillis, previewCalendars)
            }

            val selected = withContext(Dispatchers.Default) {
                EventPreviewSelection.select(events, start, end, ZonedDateTime.now(deviceZoneId))
            }

            if (revision != lookupRevision) return@launch

            if (selected.isEmpty()) {
                hidePreviewPanel()
            } else {
                showPreviewPanel(selected, day)
            }
        }
    }

    private fun showPreviewPanel(events: List<CalendarEventItem>, day: LocalDate) {
        binding.previewPanel.tvPreviewDay.text = day.format(DateTimeFormatter.ofPattern("EEEE, MMM d"))
        eventPreviewAdapter.submitList(events)
        binding.previewPanel.root.visibility = View.VISIBLE
    }

    private fun hidePreviewPanel() {
        if (binding.previewPanel.root.visibility == View.VISIBLE) {
            binding.previewPanel.root.visibility = View.GONE
            eventPreviewAdapter.submitList(emptyList())
        }
    }

    private fun applyHighlighting(input: String, result: ParseResult) {
        val editText = binding.etEventInput
        val editable = editText.text
        if (editable.toString() != input) return

        val highlightColor = MaterialColors.getColor(
            this,
            com.google.android.material.R.attr.colorPrimaryContainer,
            ContextCompat.getColor(this, R.color.primary_light)
        )
        val textColor = MaterialColors.getColor(
            this,
            com.google.android.material.R.attr.colorOnPrimaryContainer,
            ContextCompat.getColor(this, R.color.black)
        )

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
        binding.btnSettings.visibility = if (appSettings.hideCalendarSettingsButton) {
            View.GONE
        } else {
            View.VISIBLE
        }
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.btnSave.setOnClickListener { submitEvent() }
        binding.etEventInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submitEvent()
                true
            } else {
                false
            }
        }
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

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Bring up the keyboard once the window is actually focused. Doing this in
        // onWindowFocusChanged (not onCreate) makes it work when the activity is
        // launched via the shortcut trampoline, where onCreate runs before the
        // window reaches a stably focused state.
        if (hasFocus && !hasRequestedKeyboard && ::binding.isInitialized) {
            hasRequestedKeyboard = true
            showKeyboard()
        }
    }

    private fun showKeyboard() {
        binding.etEventInput.requestFocus()
        binding.etEventInput.post {
            WindowCompat.getInsetsController(window, binding.etEventInput)
                .show(WindowInsetsCompat.Type.ime())
        }
    }

    private fun restoreInputFocus() {
        showKeyboard()
    }

    private fun requestCalendarPermissionsIfNeeded() {
        permissionsRequested = true
        if (PermissionHelper.hasReadCalendarPermission(this)) {
            loadCalendars()
        } else {
            permissionLauncher.launch(PermissionHelper.getRequiredPermissions())
        }
    }

    private fun loadCalendars() {
        lifecycleScope.launch(Dispatchers.Default) {
            // All calendars accessible on the device, writable or read-only:
            // they drive the event preview, and the selected one pre-fills the draft.
            val calendars = calendarPreferences.filterHiddenCalendars(
                calendarRepository.findAllCalendars(),
                appSettings.hiddenCalendarIds
            )
            withContext(Dispatchers.Main) {
                previewCalendars = calendars
                selectedCalendar = calendarPreferences.selectCalendar(calendars)

                // Calendars arrived after the first lookup ran with an empty set — re-run it.
                scheduleEventLookup(binding.etEventInput.text.toString())
            }
        }
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

        openCalendarEventDraft(draft)
    }

    private fun openCalendarEventDraft(draft: CalendarEventDraft) {
        val start = draft.parsedStart ?: return
        val end = draft.parsedEnd ?: start.plusHours(1)
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, draft.titleText ?: "Event")
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start.toInstant().toEpochMilli())
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end.toInstant().toEpochMilli())
            selectedCalendar?.id?.let { calendarId ->
                putExtra(CalendarContract.Events.CALENDAR_ID, calendarId)
            }
        }

        try {
            startActivity(intent)
            finish()
        } catch (e: Exception) {
            Log.e("CalendarQuickAdd", "Cannot open calendar event draft", e)
            showError("Cannot open calendar app")
        }
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
            "Calendar permission required to show events",
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

    override fun onDestroy() {
        parseJob?.cancel()
        eventLookupJob?.cancel()
        super.onDestroy()
    }
}
