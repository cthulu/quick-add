package nl.freshlytyped.keepquickadd.calendar

import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.SpannableStringBuilder
import android.view.WindowManager
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
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
    private var currentDraft: CalendarEventDraft? = null

    private var parseJob: Job? = null
    private var inputRevision = 0

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

        setupTextWatcher()
        setupButtons()

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

        val timeStr = formatParsedTime(draft)
        val message = "✓ $timeStr"

        lifecycleScope.launch {
            binding.tvConfirmation.text = message
            binding.tvConfirmation.visibility = android.view.View.VISIBLE
            binding.layoutInput.visibility = android.view.View.GONE

            binding.root.postDelayed({
                finish()
            }, 1500)
        }
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
