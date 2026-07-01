package nl.freshlytyped.keepquickadd.calendar

import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.freshlytyped.keepquickadd.databinding.ActivityCalendarQuickAddBinding
import nl.freshlytyped.keepquickadd.databinding.LayoutCalendarFloatingWidgetBinding
import java.time.ZonedDateTime

/**
 * Full-screen transparent Activity that hosts the calendar quick-add popup card at the bottom.
 * Using a full-height window is the only reliable way to get adjustResize to push
 * the popup above the keyboard on all Android versions.
 */
class CalendarQuickAddActivity : AppCompatActivity() {

    // activityBinding = the full-screen wrapper; binding = the popup card inside it
    private lateinit var activityBinding: ActivityCalendarQuickAddBinding
    private lateinit var binding: LayoutCalendarFloatingWidgetBinding
    private lateinit var parserService: DateParserService
    private var currentDraft: CalendarEventDraft? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Full-screen window — adjustResize can shrink it when the keyboard
        // appears, which pushes the bottom-gravity popup card up naturally.
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
        )

        activityBinding = ActivityCalendarQuickAddBinding.inflate(layoutInflater)
        setContentView(activityBinding.root)

        // Access the included layout's binding via the generated field on activityBinding.
        binding = activityBinding.popupCard

        // Tapping the dim overlay dismisses the activity.
        activityBinding.dimOverlay.setOnClickListener { finish() }

        parserService = NattyDateParserService()

        setupButtons()

        binding.etEventInput.requestFocus()
        binding.etEventInput.post {
            WindowCompat.getInsetsController(window, binding.etEventInput)
                .show(WindowInsetsCompat.Type.ime())
        }

        // When the keyboard is dismissed (IME inset drops to 0), close the popup.
        // This handles back gesture, swipe-down-to-dismiss keyboard, etc.
        ViewCompat.setOnApplyWindowInsetsListener(activityBinding.root) { v, insets ->
            val imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            if (!imeVisible) finish()
            ViewCompat.onApplyWindowInsets(v, insets)
        }
    }

    private fun setupButtons() {
        binding.btnSave.setOnClickListener { submitEvent() }
        binding.btnCancel.setOnClickListener { finish() }
    }

    private fun submitEvent() {
        val rawInput = binding.etEventInput.text.toString().trim()
        if (rawInput.isEmpty()) {
            Toast.makeText(this, "Please enter event details", Toast.LENGTH_SHORT).show()
            return
        }

        // For now, just show a confirmation and close. Parsing and saving will be added in later steps.
        GlobalScope.launch {
            val parseResult = withContext(Dispatchers.Default) {
                parserService.parse(rawInput, ZonedDateTime.now())
            }

            currentDraft = CalendarEventDraft(
                rawInput = rawInput,
                titleText = parseResult.titleText ?: rawInput,
                parsedStart = parseResult.start,
                parsedEnd = parseResult.end,
                parseState = parseResult.state,
                matchedRanges = parseResult.matchedRanges,
                timezoneId = java.time.ZoneId.systemDefault().id
            )

            // In phase 1, just show confirmation and close
            runOnUiThread {
                binding.tvConfirmation.text = "✓ Received: $rawInput"
                binding.tvConfirmation.visibility = android.view.View.VISIBLE
                binding.layoutInput.visibility = android.view.View.GONE

                // Close after brief delay
                binding.root.postDelayed({
                    finish()
                }, 1500)
            }
        }
    }
}
