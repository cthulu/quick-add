package com.keepquickadd

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Color
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.keepquickadd.databinding.LayoutFloatingWidgetBinding

class FloatingWidgetService : Service() {

    companion object {
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "floating_widget_channel"
        var isRunning = false
            private set
    }

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var binding: LayoutFloatingWidgetBinding? = null

    // Sample Keep lists - in a real app these come from Google Keep API
    private val keepLists = listOf(
        "Inbox",
        "Groceries",
        "Shopping List",
        "Todo",
        "Work Tasks",
        "Ideas",
        "Books to Read"
    )

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        createFloatingWidget()
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        removeFloatingWidget()
    }

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
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
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

    private fun createFloatingWidget() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val inflater = LayoutInflater.from(this)
        binding = LayoutFloatingWidgetBinding.inflate(inflater)
        floatingView = binding?.root

        // Full-width, anchored to bottom of screen, sits just above the keyboard
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.START
            // Use softInputMode to push layout above keyboard
            @Suppress("DEPRECATION")
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
            x = 0
            y = 0
        }

        windowManager?.addView(floatingView, params)

        setupSpinner()
        setupButtons()
        setupOutsideTouchDismiss(params)
        setupBackGesture()

        // Auto-focus the task name field and show keyboard
        binding?.etItemText?.requestFocus()
    }

    private fun setupSpinner() {
        binding?.let { b ->
            // Custom dark-themed adapter
            val adapter = object : ArrayAdapter<String>(
                this,
                android.R.layout.simple_spinner_item,
                keepLists
            ) {
                override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                    val view = super.getView(position, convertView, parent)
                    (view as? TextView)?.apply {
                        setTextColor(Color.parseColor("#EFEFEF"))
                        textSize = 13f
                        setPadding(8, 4, 8, 4)
                        gravity = android.view.Gravity.CENTER_VERTICAL
                        // Fill the width so chevron is pushed to the right
                        layoutParams?.width = android.view.ViewGroup.LayoutParams.MATCH_PARENT
                        // Add chevron as compound drawable on the right
                        val chevron = androidx.core.content.ContextCompat.getDrawable(
                            context, R.drawable.ic_chevron_down
                        )
                        chevron?.setBounds(0, 0, chevron.intrinsicWidth, chevron.intrinsicHeight)
                        setCompoundDrawablesRelative(null, null, chevron, null)
                        compoundDrawablePadding = 4
                    }
                    return view
                }

                override fun getDropDownView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                    val view = super.getDropDownView(position, convertView, parent)
                    (view as? TextView)?.apply {
                        setTextColor(Color.parseColor("#EFEFEF"))
                        setBackgroundColor(Color.parseColor("#2D2D2D"))
                        textSize = 14f
                        setPadding(32, 24, 32, 24)
                        setCompoundDrawables(null, null, null, null)
                    }
                    return view
                }
            }.apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }

            b.spinnerLists.adapter = adapter
        }
    }

    private fun setupButtons() {
        binding?.let { b ->
            // Send / Add button
            b.btnAdd.setOnClickListener {
                submitItem()
            }

            // Handle Done key on keyboard
            b.etItemText.setOnEditorActionListener { _, actionId, event ->
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE ||
                    (event != null && event.keyCode == android.view.KeyEvent.KEYCODE_ENTER)) {
                    submitItem()
                    true
                } else {
                    false
                }
            }
        }
    }

    private fun setupOutsideTouchDismiss(params: WindowManager.LayoutParams) {
        // FLAG_NOT_TOUCH_MODAL allows touches outside the window to be detected
        params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        windowManager?.updateViewLayout(floatingView, params)

        floatingView?.setOnTouchListener { _, event ->
            if (event.action == android.view.MotionEvent.ACTION_OUTSIDE) {
                stopSelf()
                true
            } else {
                false
            }
        }
    }

    private fun setupBackGesture() {
        // Intercept back key press to dismiss the widget
        floatingView?.isFocusableInTouchMode = true
        floatingView?.requestFocus()
        floatingView?.setOnKeyListener { _, keyCode, event ->
            if (event.action == android.view.KeyEvent.ACTION_UP &&
                keyCode == android.view.KeyEvent.KEYCODE_BACK) {
                stopSelf()
                true
            } else {
                false
            }
        }

        // Also intercept back on the EditText
        binding?.etItemText?.setOnKeyListener { _, keyCode, event ->
            if (event.action == android.view.KeyEvent.ACTION_UP &&
                keyCode == android.view.KeyEvent.KEYCODE_BACK) {
                stopSelf()
                true
            } else {
                false
            }
        }
    }

    private fun submitItem() {
        binding?.let { b ->
            val itemText = b.etItemText.text.toString().trim()
            val selectedList = b.spinnerLists.selectedItem?.toString() ?: "Unknown"

            if (itemText.isEmpty()) {
                Toast.makeText(this, "Please enter a task name", Toast.LENGTH_SHORT).show()
                return
            }

            // Hide widget so toast is visible
            floatingView?.visibility = View.GONE

            // Show toast
            Toast.makeText(
                this,
                "Added \"$itemText\" to \"$selectedList\"",
                Toast.LENGTH_LONG
            ).show()

            // Close after brief delay
            android.os.Handler(mainLooper).postDelayed({
                stopSelf()
            }, 500)
        }
    }

    private fun removeFloatingWidget() {
        floatingView?.let {
            windowManager?.removeView(it)
        }
        floatingView = null
        binding = null
    }
}
