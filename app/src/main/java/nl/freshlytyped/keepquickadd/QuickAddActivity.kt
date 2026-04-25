package nl.freshlytyped.keepquickadd

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.freshlytyped.keepquickadd.databinding.LayoutFloatingWidgetBinding

/**
 * Transparent bottom-sheet style Activity that hosts the quick-add popup.
 * Activities (unlike overlay services) get correct soft-keyboard handling
 * for free via SOFT_INPUT_ADJUST_RESIZE.
 */
class QuickAddActivity : AppCompatActivity() {

    private lateinit var binding: LayoutFloatingWidgetBinding
    private lateinit var settings: AppSettings
    private lateinit var repository: KeepRepository
    private var lists: List<String> = emptyList()
    private var selectedListIndex: Int = 0

    private val DROP_DOWN_TEXT_COLOR = Color.parseColor("#EFEFEF")
    private val DROP_DOWN_BG_COLOR = Color.parseColor("#1F1F1F")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure window: transparent background, bottom gravity, resize on keyboard
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setGravity(Gravity.BOTTOM)
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT
            )
            setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
            )
            // Tap outside to dismiss
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            attributes = attributes.apply { dimAmount = 0.4f }
        }

        binding = LayoutFloatingWidgetBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = KeepRepository(this)
        settings = AppSettings(this)
        lists = settings.getLists()

        setupSpinner()
        setupButtons()

        binding.etItemText.requestFocus()
        // Show keyboard on launch
        binding.etItemText.post {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(binding.etItemText, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        // Tap outside the popup (the dim area) → dismiss.
        // ACTION_OUTSIDE only fires for windowIsFloating but we use match_parent
        // so we approximate by checking if the touch is outside the popup bounds.
        if (event.action == android.view.MotionEvent.ACTION_DOWN) {
            val popup = binding.root
            val loc = IntArray(2)
            popup.getLocationOnScreen(loc)
            val x = event.rawX
            val y = event.rawY
            val outside = x < loc[0] || x > loc[0] + popup.width ||
                          y < loc[1] || y > loc[1] + popup.height
            if (outside) { finish(); return true }
        }
        return super.onTouchEvent(event)
    }


    // --- Spinner / list picker ---

    private fun setupSpinner() {
        val b = binding
        val adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, lists) {
            override fun getDropDownView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val tv = (super.getDropDownView(position, convertView, parent) as TextView).apply {
                    setTextColor(DROP_DOWN_TEXT_COLOR)
                    setBackgroundColor(DROP_DOWN_BG_COLOR)
                    setPadding(48.dp(), 24.dp(), 48.dp(), 24.dp())
                    val tick = if (position == selectedListIndex) {
                        ContextCompat.getDrawable(this@QuickAddActivity, R.drawable.ic_check)
                    } else null
                    setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, tick, null)
                    compoundDrawablePadding = 12.dp()
                    layoutParams = android.widget.AbsListView.LayoutParams(
                        android.widget.AbsListView.LayoutParams.MATCH_PARENT,
                        android.widget.AbsListView.LayoutParams.WRAP_CONTENT
                    )
                }
                return tv
            }
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                // Selected view is invisible — just hidden behind the icon button
                val tv = (super.getView(position, convertView, parent) as TextView).apply { setTextColor(Color.TRANSPARENT) }
                return tv
            }
        }
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
            b.spinnerLists.dropDownWidth = measureDropDownWidth()
            b.btnListPicker.setOnClickListener { b.spinnerLists.performClick() }
        }
    }

    private fun measureDropDownWidth(): Int {
        val paint = android.graphics.Paint().apply {
            textSize = 14f * resources.displayMetrics.density
        }
        val padding = (48 + 48 + 12).dp()
        val widest = lists.maxOfOrNull { paint.measureText(it).toInt() } ?: 0
        val maxScreen = (resources.displayMetrics.widthPixels * 0.9).toInt()
        return (widest + padding).coerceAtMost(maxScreen)
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

        // GlobalScope so the coroutine survives Activity destruction
        @Suppress("OPT_IN_USAGE")
        GlobalScope.launch(Dispatchers.Main) {
            val result = withContext(Dispatchers.IO) { repository.addItem(selectedList, itemText) }
            if (result.isSuccess) showConfirmation("✓ Added to \"$selectedList\"")
            else showError("✗ ${result.exceptionOrNull()?.message ?: "Unknown error"}")
        }
    }

    private fun showConfirmation(message: String) {
        binding.layoutInput.visibility = View.GONE
        binding.tvConfirmation.apply {
            text = message
            setTextColor(ContextCompat.getColor(this@QuickAddActivity, R.color.primary))
            visibility = View.VISIBLE
        }
        binding.root.postDelayed({ finish() }, 850)
    }

    private fun showError(message: String) {
        binding.layoutInput.visibility = View.GONE
        binding.tvConfirmation.apply {
            text = message
            setTextColor(Color.parseColor("#D32F2F"))
            visibility = View.VISIBLE
        }
        binding.root.postDelayed({ finish() }, 3400)
    }
}
