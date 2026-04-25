package nl.freshlytyped.keepquickadd

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
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
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun updateUI() {
        binding.tvStatus.text = "Tap to open the Quick Add popup"
        binding.tvStatus.setTextColor(getColor(R.color.on_surface_secondary))
        binding.btnGrantPermission.visibility = android.view.View.GONE
        binding.btnStartWidget.visibility = android.view.View.VISIBLE
        binding.btnStartWidget.text = "Open Quick Add"
        binding.btnStopWidget.visibility = android.view.View.GONE
    }

    private fun openQuickAdd() {
        startActivity(Intent(this, QuickAddActivity::class.java))
    }
}
