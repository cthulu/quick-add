package nl.freshlytyped.keepquickadd

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import nl.freshlytyped.keepquickadd.calendar.CalendarQuickAddActivity

/**
 * Trampoline activity used as a launcher shortcut.
 * Immediately starts the CalendarQuickAddActivity and finishes itself.
 */
class CalendarQuickAddShortcutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, CalendarQuickAddActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        })
        finish()
    }
}
