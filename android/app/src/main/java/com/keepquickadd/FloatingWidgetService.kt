package com.keepquickadd

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ActivityNotFoundException
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
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.keepquickadd.databinding.LayoutFloatingWidgetBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class FloatingWidgetService : Service() {

    companion object {
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "floating_widget_channel"
        private const val CLOSE_DELAY_MS = 500L
        // Colors resolved at runtime from theme (supports light/dark mode)
        // See res/values/widget_colors.xml and res/values-night/widget_colors.xml

        var isRunning = false
            private set
    }

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var binding: LayoutFloatingWidgetBinding? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private lateinit var repository: KeepRepository
    private lateinit var settings: AppSettings
    private var keepLists: List<KeepList> = emptyList()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        repository = KeepRepository(this)
        settings = AppSettings(this)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        createFloatingWidget()
        loadLists()
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        serviceScope.cancel()
        removeFloatingWidget()
    }

    // --- List loading ---

    private fun loadLists() {
        serviceScope.launch {
            // Show cached data immediately for instant display
            repository.getLists(forceRefresh = false).onSuccess { lists ->
                keepLists = lists
                setupSpinner()
            }

            // Fetch fresh data in background if cache is stale
            if (!repository.isCacheFresh()) {
                repository.getLists(forceRefresh = true).fold(
                    onSuccess = { lists ->
                        if (lists != keepLists) {
                            keepLists = lists
                            setupSpinner()
                        }
                    },
                    onFailure = {
                        if (keepLists.isEmpty()) {
                            showToast("Could not load lists. Check server connection.")
                        }
                    }
                )
            }
        }
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

        binding?.etItemText?.requestFocus()
    }

    // --- Spinner ---

    private fun setupSpinner() {
        val b = binding ?: return

        val adapter = object : android.widget.ArrayAdapter<String>(
            this,
            android.R.layout.simple_spinner_item,
            keepLists.map { it.title }
        ) {
            // Selected view is invisible — we display the icon button instead
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

        // Restore last selected list by ID; fall back to first item if not found
        val lastId = settings.lastSelectedListId
        val restoredIndex = lastId
            ?.let { keepLists.indexOfFirst { list -> list.id == it }.takeIf { idx -> idx >= 0 } }
            ?: 0
        b.spinnerLists.setSelection(restoredIndex)

        // List icon button triggers the hidden spinner dropdown
        b.btnListPicker.setOnClickListener { b.spinnerLists.performClick() }
    }

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

        val selectedList = keepLists.getOrNull(b.spinnerLists.selectedItemPosition)
        floatingView?.visibility = View.GONE

        if (selectedList == null) {
            showToast("\"$itemText\" added (no list selected)")
            closeAfterDelay()
            return
        }

        settings.lastSelectedListId = selectedList.id

        serviceScope.launch {
            repository.addItem(selectedList.id, itemText).fold(
                onSuccess = {
                    showToast("Added \"$itemText\" to \"${selectedList.title}\"")
                    closeAfterDelay()
                },
                onFailure = { openKeepFallback(itemText, selectedList.title) }
            )
        }
    }

    private fun openKeepFallback(itemText: String, listTitle: String) {
        try {
            startActivity(Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "$listTitle: $itemText")
                setPackage("com.google.android.keep")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            showToast("Backend offline — opening Keep directly")
        } catch (e: ActivityNotFoundException) {
            showToast("Backend offline and Google Keep is not installed")
        }
        closeAfterDelay()
    }

    // --- Helpers ---

    private fun showToast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    private fun closeAfterDelay() =
        Handler(mainLooper).postDelayed({ stopSelf() }, CLOSE_DELAY_MS)

    private fun removeFloatingWidget() {
        floatingView?.let { windowManager?.removeView(it) }
        floatingView = null
        binding = null
    }

}
