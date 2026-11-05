package nl.freshlytyped.keepquickadd

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import nl.freshlytyped.keepquickadd.calendar.CalendarQuickAddActivity
import nl.freshlytyped.keepquickadd.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        setupClickListeners()
        updateUI()
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupClickListeners() {
        binding.btnStartWidget.setOnClickListener { openQuickAdd() }
        binding.btnCalendarQuickAdd.setOnClickListener { openCalendarQuickAdd() }
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun updateUI() {
        val keepEnabled = AppFeatures.keepEnabled
        binding.tvDescription.text = if (keepEnabled) {
            "Quickly add items to your Google Keep lists\nfrom anywhere on your device."
        } else {
            "Quickly add calendar events from anywhere on your device."
        }
        binding.btnStartWidget.visibility = if (keepEnabled) {
            android.view.View.VISIBLE
        } else {
            android.view.View.GONE
        }
        binding.btnSettings.text = if (keepEnabled) {
            "⚙ Configure PubNub & Lists"
        } else {
            "⚙ Calendar Settings"
        }
        binding.tvStatus.text = if (keepEnabled) {
            "Tap to open the Quick Add popup"
        } else {
            "Tap to add a calendar event"
        }
        binding.tvStatus.setTextColor(getColor(R.color.on_surface_secondary))
        binding.btnGrantPermission.visibility = android.view.View.GONE
        binding.btnStartWidget.text = "Open Quick Add"
        binding.btnStopWidget.visibility = android.view.View.GONE
    }

    private fun openQuickAdd() {
        if (!AppFeatures.keepEnabled) return
        startActivity(Intent(this, QuickAddActivity::class.java))
    }

    private fun openCalendarQuickAdd() {
        startActivity(Intent(this, CalendarQuickAddActivity::class.java))
    }
}
