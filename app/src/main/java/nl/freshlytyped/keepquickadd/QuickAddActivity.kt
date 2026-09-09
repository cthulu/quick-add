package nl.freshlytyped.keepquickadd

import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.freshlytyped.keepquickadd.databinding.ActivityQuickAddBinding
import nl.freshlytyped.keepquickadd.databinding.LayoutFloatingWidgetBinding

/**
 * Full-screen transparent Activity that hosts the quick-add popup card at the bottom.
 * Using a full-height window is the only reliable way to get adjustResize to push
 * the popup above the keyboard on all Android versions including 15/16.
 */
class QuickAddActivity : AppCompatActivity() {

    // activityBinding = the full-screen wrapper; binding = the popup card inside it
    private lateinit var activityBinding: ActivityQuickAddBinding
    private lateinit var binding: LayoutFloatingWidgetBinding
    private lateinit var settings: AppSettings
    private lateinit var repository: KeepRepository
    private var lists: List<String> = emptyList()
    private var selectedListIndex: Int = 0
    private var hasObservedVisibleIme = false
    private var isDestroyed = false
    private val requestScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Full-screen window — adjustResize can actually shrink it when the keyboard
        // appears, which pushes the bottom-gravity popup card up naturally.
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
        )

        activityBinding = ActivityQuickAddBinding.inflate(layoutInflater)
        setContentView(activityBinding.root)

        // Access the included layout's binding via the generated field on activityBinding.
        binding = activityBinding.popupCard

        // Tapping the dim overlay dismisses the activity.
        activityBinding.dimOverlay.setOnClickListener { finish() }

        repository = KeepRepository(this)
        settings = AppSettings(this)
        lists = settings.getLists()

        setupSpinner()
        setupButtons()

        binding.etItemText.requestFocus()
        binding.etItemText.post {
            WindowCompat.getInsetsController(window, binding.etItemText)
                .show(WindowInsetsCompat.Type.ime())
        }

        // Ignore the initial hidden-insets dispatch before the requested keyboard is shown.
        // Once the IME has been visible, a later hidden dispatch means the popup was dismissed.
        ViewCompat.setOnApplyWindowInsetsListener(activityBinding.root) { v, insets ->
            val imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            if (imeVisible) {
                hasObservedVisibleIme = true
            } else if (hasObservedVisibleIme) {
                finish()
            }
            ViewCompat.onApplyWindowInsets(v, insets)
        }
    }


    override fun onDestroy() {
        isDestroyed = true
        super.onDestroy()
    }

    // --- Spinner / list picker ---

    private fun setupSpinner() {
        val b = binding
        // Simple ArrayAdapter with custom XML layout — no custom getDropDownView needed.
        // Selected item is highlighted with a tinted background.
        val adapter = ListDropDownAdapter(lists)
        b.spinnerLists.adapter = adapter

        // Restore last selection by name
        val lastName = settings.lastSelectedListName
        val restoredIndex = lastName
            ?.let { name -> lists.indexOfFirst { it == name }.takeIf { it >= 0 } } ?: 0
        b.spinnerLists.setSelection(restoredIndex)
        selectedListIndex = restoredIndex

        b.spinnerLists.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedListIndex = position
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        if (lists.size <= 1) {
            b.btnListPicker.visibility = View.GONE
        } else {
            b.btnListPicker.visibility = View.VISIBLE
            b.spinnerLists.dropDownWidth = android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            b.btnListPicker.setOnClickListener { b.spinnerLists.performClick() }
        }
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()

    // --- Buttons & input ---

    private fun setupButtons() {
        val b = binding
        b.btnAdd.setOnClickListener { submitItem() }
        b.etItemText.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE ||
                actionId == EditorInfo.IME_ACTION_GO ||
                actionId == EditorInfo.IME_ACTION_SEND ||
                event?.keyCode == KeyEvent.KEYCODE_ENTER) {
                submitItem(); true
            } else false
        }
        b.etItemText.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_ENTER) {
                submitItem(); true
            } else false
        }
    }

    private fun submitItem() {
        val itemText = binding.etItemText.text.toString().trim()
        if (itemText.isEmpty()) {
            Toast.makeText(applicationContext, "Please enter an item", Toast.LENGTH_SHORT).show()
            return
        }
        val selectedList = lists.getOrNull(selectedListIndex)
        if (selectedList == null) { finish(); return }
        settings.lastSelectedListName = selectedList

        // Keep the request alive independently of this Activity, but never update a
        // view after the Activity has been destroyed.
        requestScope.launch {
            val result = withContext(Dispatchers.IO) { repository.addItem(selectedList, itemText) }
            if (isDestroyed) return@launch
            if (result.isSuccess) showConfirmation("✓ Added to \"$selectedList\"")
            else showError("✗ ${result.exceptionOrNull()?.let { KeepFailureMapper.messageFor(it) }
                ?: KeepFailureMapper.messageFor(KeepFailureCategory.SERVER)}")
        }
    }

    private fun showConfirmation(message: String) {
        if (isDestroyed) return
        binding.layoutInput.visibility = View.GONE
        binding.tvConfirmation.apply {
            text = message
            setTextColor(ContextCompat.getColor(this@QuickAddActivity, R.color.primary))
            visibility = View.VISIBLE
        }
        binding.root.postDelayed({ finish() }, 850)
    }

    private fun showError(message: String) {
        if (isDestroyed) return
        binding.layoutInput.visibility = View.GONE
        binding.tvConfirmation.apply {
            text = message
            setTextColor(Color.parseColor("#D32F2F"))
            visibility = View.VISIBLE
        }
        binding.root.postDelayed({ finish() }, 3400)
    }

    private inner class ListDropDownAdapter(items: List<String>) :
        ArrayAdapter<String>(this, R.layout.item_list_dropdown, items) {

        override fun getDropDownView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
            val view = super.getDropDownView(position, convertView, parent)
            val tv = view as? android.widget.TextView
            // Highlight selected row with primary yellow, plain background for others
            if (position == selectedListIndex) {
                tv?.setBackgroundColor(android.graphics.Color.parseColor("#FBBC04"))
                tv?.setTextColor(android.graphics.Color.parseColor("#1F1F1F"))
            } else {
                tv?.setBackgroundColor(android.graphics.Color.parseColor("#1F1F1F"))
                tv?.setTextColor(android.graphics.Color.parseColor("#EFEFEF"))
            }
            return view
        }

        override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
            // Collapsed spinner view hidden behind our icon button — make text invisible
            val view = super.getView(position, convertView, parent)
            val tv = view as? android.widget.TextView
            tv?.setTextColor(android.graphics.Color.TRANSPARENT)
            return view
        }
    }
}
