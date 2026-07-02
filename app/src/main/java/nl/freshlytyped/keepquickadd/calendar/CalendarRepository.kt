package nl.freshlytyped.keepquickadd.calendar

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import android.util.Log
import java.time.ZonedDateTime

class CalendarRepository(private val context: Context) {

    private val contentResolver: ContentResolver = context.contentResolver

    fun findWritableCalendar(): CalendarInfo? {
        return try {
            val calendarsUri = CalendarContract.Calendars.CONTENT_URI
            val projection = arrayOf(
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                CalendarContract.Calendars.VISIBLE,
                CalendarContract.Calendars.OWNER_ACCOUNT,
                CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL
            )
            
            val selection = "${CalendarContract.Calendars.VISIBLE} = 1"

            contentResolver.query(
                calendarsUri,
                projection,
                selection,
                null,
                null
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndex(CalendarContract.Calendars._ID)
                val nameIndex = cursor.getColumnIndex(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
                val accessIndex = cursor.getColumnIndex(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL)
                val ownerIndex = cursor.getColumnIndex(CalendarContract.Calendars.OWNER_ACCOUNT)

                var primaryCalendar: CalendarInfo? = null
                var firstWritableCalendar: CalendarInfo? = null

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIndex)
                    val name = cursor.getString(nameIndex) ?: "Unnamed Calendar"
                    val accessLevel = cursor.getInt(accessIndex)
                    val ownerAccount = cursor.getString(ownerIndex) ?: ""

                    val isWritable = accessLevel >= CalendarContract.Calendars.CAL_ACCESS_EDITOR

                    if (isWritable) {
                        val calendarInfo = CalendarInfo(
                            id = id,
                            displayName = name,
                            ownerAccount = ownerAccount
                        )

                        if (firstWritableCalendar == null) {
                            firstWritableCalendar = calendarInfo
                        }

                        // Prefer primary calendar (usually the one owned by the user)
                        if (primaryCalendar == null && ownerAccount.isNotEmpty()) {
                            primaryCalendar = calendarInfo
                        }
                    }
                }

                primaryCalendar ?: firstWritableCalendar
            }
        } catch (e: Exception) {
            Log.e("CalendarRepository", "Error querying calendars", e)
            null
        }
    }

    fun insertEvent(
        calendarId: Long,
        title: String,
        startTime: ZonedDateTime,
        endTime: ZonedDateTime?,
        timezone: String
    ): Long? {
        return try {
            val values = ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                put(CalendarContract.Events.TITLE, title)
                put(CalendarContract.Events.DTSTART, startTime.toInstant().toEpochMilli())
                put(
                    CalendarContract.Events.DTEND,
                    (endTime ?: startTime.plusHours(1)).toInstant().toEpochMilli()
                )
                put(CalendarContract.Events.EVENT_TIMEZONE, timezone)
                put(CalendarContract.Events.HAS_ALARM, 1)
            }

            val eventUri = contentResolver.insert(
                CalendarContract.Events.CONTENT_URI,
                values
            )

            if (eventUri != null) {
                val eventId = eventUri.lastPathSegment?.toLongOrNull()
                Log.d("CalendarRepository", "Event inserted with ID: $eventId")
                eventId
            } else {
                Log.w("CalendarRepository", "Event insertion returned null URI")
                null
            }
        } catch (e: Exception) {
            Log.e("CalendarRepository", "Error inserting event", e)
            null
        }
    }

    data class CalendarInfo(
        val id: Long,
        val displayName: String,
        val ownerAccount: String
    )
}
