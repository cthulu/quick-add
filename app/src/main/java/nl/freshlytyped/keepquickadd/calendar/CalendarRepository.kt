package nl.freshlytyped.keepquickadd.calendar

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Color
import android.provider.CalendarContract
import android.util.Log
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class CalendarRepository(private val context: Context) {

    private val contentResolver: ContentResolver = context.contentResolver

    fun findAllCalendars(): List<CalendarInfo> {
        return try {
            val calendarsUri = CalendarContract.Calendars.CONTENT_URI
            val projection = arrayOf(
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                CalendarContract.Calendars.VISIBLE,
                CalendarContract.Calendars.OWNER_ACCOUNT,
                CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
                CalendarContract.Calendars.CALENDAR_COLOR
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
                val colorIndex = cursor.getColumnIndex(CalendarContract.Calendars.CALENDAR_COLOR)

                val calendars = mutableListOf<CalendarInfo>()

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIndex)
                    val name = cursor.getString(nameIndex) ?: "Unnamed Calendar"
                    val accessLevel = cursor.getInt(accessIndex)
                    val ownerAccount = cursor.getString(ownerIndex) ?: ""
                    val color = if (cursor.isNull(colorIndex)) Color.GRAY else cursor.getInt(colorIndex)

                    val isWritable = accessLevel >= CalendarContract.Calendars.CAL_ACCESS_EDITOR

                    if (isWritable) {
                        calendars.add(
                            CalendarInfo(
                                id = id,
                                displayName = name,
                                ownerAccount = ownerAccount,
                                color = color
                            )
                        )
                    }
                }

                calendars
            } ?: emptyList()
        } catch (e: Exception) {
            Log.e("CalendarRepository", "Error querying calendars", e)
            emptyList()
        }
    }

    fun findWritableCalendar(): CalendarInfo? {
        return try {
            val calendarsUri = CalendarContract.Calendars.CONTENT_URI
            val projection = arrayOf(
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                CalendarContract.Calendars.VISIBLE,
                CalendarContract.Calendars.OWNER_ACCOUNT,
                CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
                CalendarContract.Calendars.IS_PRIMARY,
                CalendarContract.Calendars.CALENDAR_COLOR
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
                val primaryIndex = cursor.getColumnIndex(CalendarContract.Calendars.IS_PRIMARY)
                val colorIndex = cursor.getColumnIndex(CalendarContract.Calendars.CALENDAR_COLOR)

                var primaryCalendar: CalendarInfo? = null
                var firstWritableCalendar: CalendarInfo? = null

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIndex)
                    val name = cursor.getString(nameIndex) ?: "Unnamed Calendar"
                    val accessLevel = cursor.getInt(accessIndex)
                    val ownerAccount = cursor.getString(ownerIndex) ?: ""
                    val isPrimary = cursor.getInt(primaryIndex) == 1
                    val color = if (cursor.isNull(colorIndex)) Color.GRAY else cursor.getInt(colorIndex)

                    val isWritable = accessLevel >= CalendarContract.Calendars.CAL_ACCESS_EDITOR

                    if (isWritable) {
                        val calendarInfo = CalendarInfo(
                            id = id,
                            displayName = name,
                            ownerAccount = ownerAccount,
                            color = color
                        )

                        if (firstWritableCalendar == null) {
                            firstWritableCalendar = calendarInfo
                        }

                        if (primaryCalendar == null && isPrimary) {
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

    fun findEventsOnDay(
        dayStartMillis: Long,
        dayEndMillis: Long,
        calendars: List<CalendarInfo>
    ): List<CalendarEventItem> {
        if (calendars.isEmpty()) return emptyList()

        return try {
            // The time-range Instances query expands recurring events to one row per
            // occurrence; the Events table only holds one row per series.
            val instancesUri = CalendarContract.Instances.CONTENT_URI.buildUpon()
                .apply {
                    ContentUris.appendId(this, dayStartMillis)
                    ContentUris.appendId(this, dayEndMillis)
                }
                .build()
            val projection = arrayOf(
                CalendarContract.Events._ID,
                CalendarContract.Events.TITLE,
                CalendarContract.Events.ALL_DAY,
                CalendarContract.Events.ORIGINAL_ID,
                CalendarContract.Events.ORIGINAL_INSTANCE_TIME,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Events.CALENDAR_ID
            )

            val calendarIds = calendars.map { it.id }
            val idPlaceholders = calendarIds.joinToString(",") { "?" }
            val selection = "${CalendarContract.Events.CALENDAR_ID} IN ($idPlaceholders)"
            val selectionArgs = calendarIds.map { it.toString() }.toTypedArray()

            contentResolver.query(
                instancesUri,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndex(CalendarContract.Events._ID)
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
                    if (cursor.getInt(allDayIndex) == 1) continue
                    rows.add(
                        Row(
                            id = cursor.getLong(idIndex),
                            title = cursor.getString(titleIndex)?.takeIf { it.isNotBlank() } ?: "Untitled event",
                            originalId = cursor.getString(originalIdIndex),
                            originalTime = if (cursor.isNull(originalTimeIndex)) null else cursor.getLong(originalTimeIndex),
                            begin = cursor.getLong(beginIndex),
                            end = if (cursor.isNull(endIndex)) cursor.getLong(beginIndex) else cursor.getLong(endIndex),
                            calendarId = cursor.getLong(calendarIdIndex)
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

                rows
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
