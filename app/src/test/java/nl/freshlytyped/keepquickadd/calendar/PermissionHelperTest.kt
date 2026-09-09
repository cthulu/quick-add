package nl.freshlytyped.keepquickadd.calendar

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PermissionHelperTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = mock()
        whenever(context.checkPermission(any(), any(), any()))
            .thenReturn(PackageManager.PERMISSION_GRANTED)
    }

    @Test
    fun `calendar permissions are granted only when read and write are granted`() {
        assertTrue(PermissionHelper.hasCalendarPermissions(context))

        whenever(context.checkPermission(eq(Manifest.permission.WRITE_CALENDAR), any(), any()))
            .thenReturn(PackageManager.PERMISSION_DENIED)

        assertFalse(PermissionHelper.hasCalendarPermissions(context))
    }

    @Test
    fun `temporary denial is identified when rationale is available`() {
        whenever(context.checkPermission(eq(Manifest.permission.READ_CALENDAR), any(), any()))
            .thenReturn(PackageManager.PERMISSION_DENIED)

        assertTrue(
            PermissionHelper.hasTemporaryDenial(context) { permission ->
                permission == Manifest.permission.READ_CALENDAR
            }
        )
    }

    @Test
    fun `permanent denial is identified when no rationale is available`() {
        whenever(context.checkPermission(eq(Manifest.permission.READ_CALENDAR), any(), any()))
            .thenReturn(PackageManager.PERMISSION_DENIED)

        assertFalse(PermissionHelper.hasTemporaryDenial(context) { false })
    }
}
