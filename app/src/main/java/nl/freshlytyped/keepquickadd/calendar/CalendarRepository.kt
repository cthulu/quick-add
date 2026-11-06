package nl.freshlytyped.keepquickadd.calendar

import android.content.ContentResolver
import android.content.Context
import android.graphics.Color
import android.provider.CalendarContract
import android.util.Log
import java.time.Instant
import java.time.ZoneId

class CalendarRepository(private val context: Context) {

    private val contentResolver: ContentResolver = context.contentResolver

    fun findAllCalendars(): List<CalendarInfo> {
        return try {
            val calendarsUri = CalendarContract.Calendars.CONTENT_URI
            val projection = arrayOf(
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                CalendarContract.Calendars.OWNER_ACCOUNT,
                CalendarContract.Calendars.CALENDAR_COLOR
            )

            // All visible calendars on the device, writable or read-only:
            // the list drives the event preview, not direct event creation.
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
                val ownerIndex = cursor.getColumnIndex(CalendarContract.Calendars.OWNER_ACCOUNT)
                val colorIndex = cursor.getColumnIndex(CalendarContract.Calendars.CALENDAR_COLOR)

                val calendars = mutableListOf<CalendarInfo>()

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIndex)
                    val name = cursor.getString(nameIndex) ?: "Unnamed Calendar"
                    val ownerAccount = cursor.getString(ownerIndex) ?: ""
                    val color = if (cursor.isNull(colorIndex)) Color.GRAY else cursor.getInt(colorIndex)

                    calendars.add(
                        CalendarInfo(
                            id = id,
                            displayName = name,
                            ownerAccount = ownerAccount,
                            color = color
                        )
                    )
                }

                calendars
            } ?: emptyList()
        } catch (e: Exception) {
            Log.e("CalendarRepository", "Error querying calendars", e)
            emptyList()
        }
    }

    fun findEventsOnDay(
        dayStartMillis: Long,
        dayEndMillis: Long,
        calendars: List<CalendarInfo>
    ): List<CalendarEventItem> {
        if (calendars.isEmpty()) return emptyList()

        return try {
            // Use the provider's documented Instances query so recurring series are
            // expanded for this range. Filter calendars in-process rather than passing
            // a SQL selection to the special Instances URI.
            val projection = arrayOf(
                CalendarContract.Instances.EVENT_ID,
                CalendarContract.Events.TITLE,
                CalendarContract.Events.ALL_DAY,
                CalendarContract.Events.ORIGINAL_ID,
                CalendarContract.Events.ORIGINAL_INSTANCE_TIME,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Events.CALENDAR_ID
            )

            val calendarIds = calendars.map { it.id }
            val calendarIdSet = calendarIds.toSet()

            CalendarContract.Instances.query(
                contentResolver,
                projection,
                dayStartMillis,
                dayEndMillis
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndex(CalendarContract.Instances.EVENT_ID)
                val titleIndex = cursor.getColumnIndex(CalendarContract.Events.TITLE)
                val allDayIndex = cursor.getColumnIndex(CalendarContract.Events.ALL_DAY)
                val originalIdIndex = cursor.getColumnIndex(CalendarContract.Events.ORIGINAL_ID)
                val originalTimeIndex = cursor.getColumnIndex(CalendarContract.Events.ORIGINAL_INSTANCE_TIME)
                val beginIndex = cursor.getColumnIndex(CalendarContract.Instances.BEGIN)
                val endIndex = cursor.getColumnIndex(CalendarContract.Instances.END)
                val calendarIdIndex = cursor.getColumnIndex(CalendarContract.Events.CALENDAR_ID)

                val zone = ZoneId.systemDefault()
                data class Row(
                    val id: Long,
                    val title: String,
                    val originalId: String?,
                    val originalTime: Long?,
                    val begin: Long,
                    val end: Long,
                    val calendarId: Long
                )

                val rows = mutableListOf<Row>()
                while (cursor.moveToNext()) {
                    val calendarId = cursor.getLong(calendarIdIndex)
                    if (calendarId !in calendarIdSet) continue
                    if (cursor.getInt(allDayIndex) == 1) continue
                    rows.add(
                        Row(
                            id = cursor.getLong(idIndex),
                            title = cursor.getString(titleIndex)?.takeIf { it.isNotBlank() } ?: "Untitled event",
                            originalId = cursor.getString(originalIdIndex),
                            originalTime = if (cursor.isNull(originalTimeIndex)) null else cursor.getLong(originalTimeIndex),
                            begin = cursor.getLong(beginIndex),
                            end = if (cursor.isNull(endIndex)) cursor.getLong(beginIndex) else cursor.getLong(endIndex),
                            calendarId = calendarId
                        )
                    )
                }

                // An occurrence the user moved shows as an exception row; drop the master's
                // instance at the original time if the provider still returns it.
                val suppressed = rows
                    .filter { it.originalId != null && it.originalTime != null }
                    .map { "${it.originalId}:${it.originalTime}" }
                    .toSet()

                val calendarById = calendars.associateBy { it.id }

                val events = rows
                    .filterNot { row -> row.originalId == null && "${row.id}:${row.begin}" in suppressed }
                    .sortedBy { it.begin }
                    .map {
                        val calendar = calendarById[it.calendarId]
                        CalendarEventItem(
                            id = it.id,
                            title = it.title,
                            startTime = Instant.ofEpochMilli(it.begin).atZone(zone),
                            endTime = Instant.ofEpochMilli(it.end).atZone(zone),
                            calendarColor = calendar?.color ?: Color.GRAY,
                            calendarName = calendar?.displayName ?: "Unknown calendar"
                        )
                    }

                events
            } ?: emptyList()
        } catch (e: Exception) {
            Log.e("CalendarRepository", "Error querying events", e)
            emptyList()
        }
    }

    data class CalendarInfo(
        val id: Long,
        val displayName: String,
        val ownerAccount: String,
        val color: Int
    )
}
