package nl.freshlytyped.keepquickadd.calendar

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.ZoneId
import java.time.ZonedDateTime

@RunWith(RobolectricTestRunner::class)
class CalendarIntentUrisTest {

    @Test
    fun `date URI focuses the calendar on the event date`() {
        val start = ZonedDateTime.of(2026, 9, 9, 10, 0, 0, 0, ZoneId.of("UTC"))

        assertEquals(
            Uri.parse("content://com.android.calendar/time/1788948000000"),
            CalendarIntentUris.dateUri(start)
        )
    }

    @Test
    fun `date URI falls back to the calendar time view without a start`() {
        assertEquals(
            Uri.parse("content://com.android.calendar/time"),
            CalendarIntentUris.dateUri(null)
        )
    }
}
