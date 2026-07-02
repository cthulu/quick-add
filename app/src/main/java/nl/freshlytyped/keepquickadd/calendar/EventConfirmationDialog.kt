package nl.freshlytyped.keepquickadd.calendar

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class EventConfirmationDialog(
    context: Context,
    private val title: String,
    private val startTime: ZonedDateTime,
    private val endTime: ZonedDateTime?,
    private val calendarName: String,
    private val onConfirm: () -> Unit,
    private val onCancel: () -> Unit
) : Dialog(context) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val formatter = DateTimeFormatter.ofPattern("EEE, MMM d 'at' h:mm a")
        val startStr = startTime.format(formatter)
        
        val timeStr = if (endTime != null && endTime.isAfter(startTime.plusHours(1))) {
            val endFormatter = DateTimeFormatter.ofPattern("h:mm a")
            val endStr = endTime.format(endFormatter)
            "$startStr - $endStr"
        } else {
            startStr
        }

        val message = buildString {
            append("Event: $title\n")
            append("Time: $timeStr\n")
            append("Calendar: $calendarName")
        }

        val alertDialog = AlertDialog.Builder(context)
            .setTitle("Confirm Event")
            .setMessage(message)
            .setPositiveButton("Create") { _, _ ->
                onConfirm()
                dismiss()
            }
            .setNegativeButton("Cancel") { _, _ ->
                onCancel()
                dismiss()
            }
            .create()

        alertDialog.show()
    }
}
