package nl.freshlytyped.keepquickadd

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import nl.freshlytyped.keepquickadd.databinding.LayoutFloatingWidgetBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class FloatingWidgetService : Service() {

    companion object {
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "floating_widget_channel"
        private const val CLOSE_DELAY_MS = 850L

        var isRunning = false
            private set
    }

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var binding: LayoutFloatingWidgetBinding? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private lateinit var repository: KeepRepository
    private lateinit var settings: AppSettings
    private var lists: List<String> = emptyList()
    private var selectedListIndex: Int = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        repository = KeepRepository(this)
        settings = AppSettings(this)
        lists = settings.getLists()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        createFloatingWidget()
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        serviceScope.cancel()
        removeFloatingWidget()
    }

    // --- Notification ---

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_description)
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(android.R.drawable.ic_input_add)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    // --- Widget creation ---

    private fun createFloatingWidget() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        binding = LayoutFloatingWidgetBinding.inflate(LayoutInflater.from(this))
        floatingView = binding?.root

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.START
            @Suppress("DEPRECATION")
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
        }

        windowManager?.addView(floatingView, params)
        setupSpinner()
        setupButtons()
        setupOutsideTouchDismiss(params)
        setupBackGesture()
        setupKeyboardTracking(params)
        binding?.etItemText?.requestFocus()
    }

    /**
     * For TYPE_APPLICATION_OVERLAY, system insets are not dispatched and
     * SOFT_INPUT_ADJUST_RESIZE does not work. We add an invisible 1x1 helper
     * window that DOES receive insets, and use it to measure the keyboard
     * height, then shift the overlay accordingly.
     */
    private var keyboardHelperView: View? = null
    private fun setupKeyboardTracking(params: WindowManager.LayoutParams) {
        val view = floatingView ?: return

        // Poll the root window insets for IME height. On Android 11+ this works
        // for TYPE_APPLICATION_OVERLAY windows when the soft keyboard is shown.
        val pollRunnable = object : Runnable {
            var tickCount = 0
            override fun run() {
                val v = floatingView ?: return
                tickCount++

                // Use the real screen height from display metrics, not v.rootView.height
                // (which for an overlay window equals the popup's own height!)
                val screenHeight = resources.displayMetrics.heightPixels

                val rect = android.graphics.Rect()
                v.getWindowVisibleDisplayFrame(rect)
                val frameBased = (screenHeight - rect.bottom).coerceAtLeast(0)

                val imeHeight = frameBased
                // Heuristic: ignore status/nav bar (< 15% of screen)
                val finalY = if (imeHeight > screenHeight * 0.15) imeHeight else 0

                if (tickCount <= 5 || tickCount % 10 == 0) {
                    android.util.Log.d("FloatingWidget",
                        "tick=$tickCount  frameBased=$frameBased  screen=$screenHeight  rectBottom=${rect.bottom}  finalY=$finalY  paramsY=${params.y}")
                }

                if (params.y != finalY) {
                    params.y = finalY
                    try { windowManager?.updateViewLayout(v, params) } catch (_: Exception) {}
                }
                v.postDelayed(this, 100)
            }
        }
        view.post(pollRunnable)
        // Store the runnable for cleanup
        view.setTag(R.id.action_settings, pollRunnable)
    }

    // --- Spinner ---

    private fun setupSpinner() {
        val b = binding ?: return

        val adapter = object : ArrayAdapter<String>(
            this,
            android.R.layout.simple_spinner_item,
            lists
        ) {
            // Selected view is invisible — icon button is shown instead
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View =
                super.getView(position, convertView, parent).also { it.visibility = View.INVISIBLE }

            override fun getDropDownView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent)
                (view as? TextView)?.apply {
                    val isSelected = position == b.spinnerLists.selectedItemPosition
                    setTextColor(ContextCompat.getColor(context, R.color.widget_dropdown_text))
                    setBackgroundColor(ContextCompat.getColor(context, R.color.widget_dropdown_bg))
                    textSize = 14f
                    setPadding(48, 24, 48, 24)
                    val check = if (isSelected)
                        ContextCompat.getDrawable(context, R.drawable.ic_check)
                            ?.also { it.setBounds(0, 0, it.intrinsicWidth, it.intrinsicHeight) }
                    else null
                    setCompoundDrawablesRelative(check, null, null, null)
                    compoundDrawablePadding = 12
                }
                return view
            }
        }.apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        b.spinnerLists.adapter = adapter

        // Restore last selected list by name; fall back to first item
        val lastName = settings.lastSelectedListName
        val restoredIndex = lastName
            ?.let { name -> lists.indexOfFirst { it == name }.takeIf { it >= 0 } }
            ?: 0
        b.spinnerLists.setSelection(restoredIndex)
        selectedListIndex = restoredIndex

        // Track selection changes explicitly since spinner view is invisible
        b.spinnerLists.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedListIndex = position
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        // Hide list picker button when there is only one list
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
            textSize = 14f * resources.displayMetrics.scaledDensity
        }
        val padding = (48 + 48 + 12).dpToPx()
        val widest = lists.maxOfOrNull { paint.measureText(it).toInt() } ?: 0
        val maxScreen = (resources.displayMetrics.widthPixels * 0.9).toInt()
        return (widest + padding).coerceAtMost(maxScreen)
    }

    private fun Int.dpToPx(): Int =
        (this * resources.displayMetrics.density).toInt()

    // --- Buttons & input ---

    private fun setupButtons() {
        val b = binding ?: return
        b.btnAdd.setOnClickListener { submitItem() }
        b.etItemText.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE ||
                event?.keyCode == KeyEvent.KEYCODE_ENTER) {
                submitItem(); true
            } else false
        }
    }

    private fun setupOutsideTouchDismiss(params: WindowManager.LayoutParams) {
        params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        windowManager?.updateViewLayout(floatingView, params)
        floatingView?.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_OUTSIDE) { stopSelf(); true } else false
        }
    }

    private fun setupBackGesture() {
        val backListener = View.OnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_UP && keyCode == KeyEvent.KEYCODE_BACK) {
                stopSelf(); true
            } else false
        }
        floatingView?.isFocusableInTouchMode = true
        floatingView?.requestFocus()
        floatingView?.setOnKeyListener(backListener)
        binding?.etItemText?.setOnKeyListener(backListener)
    }

    // --- Submit ---

    private fun submitItem() {
        val b = binding ?: return
        val itemText = b.etItemText.text.toString().trim()

        if (itemText.isEmpty()) {
            showToast("Please enter an item")
            return
        }

        val selectedList = lists.getOrNull(selectedListIndex)

        if (selectedList == null) {
            floatingView?.visibility = View.GONE
            closeAfterDelay()
            return
        }

        settings.lastSelectedListName = selectedList

        // Use GlobalScope so coroutine survives stopSelf() → serviceScope.cancel()
        @Suppress("OPT_IN_USAGE")
        GlobalScope.launch(Dispatchers.Main) {
            val result = repository.addItem(selectedList, itemText)
            if (result.isSuccess) {
                showConfirmation("✓ Added to \"$selectedList\"")
            } else {
                val error = result.exceptionOrNull()?.message ?: "Unknown error"
                android.util.Log.e("FloatingWidget", "addItem failed: $error")
                showError("✗ $error")
            }
        }
    }

    // --- Helpers ---

    private fun showToast(message: String) =
        Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()

    private fun showConfirmation(message: String) {
        binding?.layoutInput?.visibility = View.GONE
        binding?.tvConfirmation?.apply {
            text = message
            setTextColor(ContextCompat.getColor(this@FloatingWidgetService, R.color.primary))
            visibility = View.VISIBLE
        }
        Handler(mainLooper).postDelayed({ stopSelf() }, CLOSE_DELAY_MS)
    }

    private fun showError(message: String) {
        binding?.layoutInput?.visibility = View.GONE
        binding?.tvConfirmation?.apply {
            text = message
            setTextColor(android.graphics.Color.parseColor("#D32F2F"))
            visibility = View.VISIBLE
        }
        // Keep error visible longer, then close
        Handler(mainLooper).postDelayed({ stopSelf() }, CLOSE_DELAY_MS * 4)
    }

    private fun closeAfterDelay() =
        Handler(mainLooper).postDelayed({ stopSelf() }, CLOSE_DELAY_MS)

    private fun removeFloatingWidget() {
        floatingView?.let { v ->
            (v.getTag(R.id.action_settings) as? Runnable)?.let { v.removeCallbacks(it) }
            try { windowManager?.removeView(v) } catch (_: Exception) {}
        }
        floatingView = null
        keyboardHelperView = null
        binding = null
    }
}
