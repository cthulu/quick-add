package nl.freshlytyped.keepquickadd.calendar

import android.content.Context
import android.content.SharedPreferences

class CalendarPreferences(context: Context) {

    fun filterHiddenCalendars(
        calendars: List<CalendarRepository.CalendarInfo>,
        hiddenCalendarIds: Set<Long>
    ): List<CalendarRepository.CalendarInfo> = calendars.filterNot { it.id in hiddenCalendarIds }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveSelectedCalendar(calendarId: Long) {
        prefs.edit().putLong(KEY_LAST_SELECTED_CALENDAR_ID, calendarId).apply()
    }

    fun selectCalendar(
        availableCalendars: List<CalendarRepository.CalendarInfo>
    ): CalendarRepository.CalendarInfo? {
        val rememberedId = prefs.getLong(KEY_LAST_SELECTED_CALENDAR_ID, NO_CALENDAR_ID)
        val selectedCalendar = availableCalendars.find { it.id == rememberedId }
            ?: availableCalendars.firstOrNull()

        if (selectedCalendar != null && selectedCalendar.id != rememberedId) {
            saveSelectedCalendar(selectedCalendar.id)
        }

        return selectedCalendar
    }

    fun getSelectedCalendarId(): Long? {
        return prefs.getLong(KEY_LAST_SELECTED_CALENDAR_ID, NO_CALENDAR_ID)
            .takeUnless { it == NO_CALENDAR_ID }
    }

    private companion object {
        const val PREFS_NAME = "calendar_quick_add"
        const val KEY_LAST_SELECTED_CALENDAR_ID = "last_selected_calendar_id"
        const val NO_CALENDAR_ID = -1L
    }
}
