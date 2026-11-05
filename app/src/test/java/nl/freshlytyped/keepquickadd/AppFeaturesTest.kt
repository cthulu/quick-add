package nl.freshlytyped.keepquickadd

import org.junit.Assert.assertEquals
import org.junit.Test

class AppFeaturesTest {

    @Test
    fun `keep availability matches product flavor`() {
        assertEquals(BuildConfig.FLAVOR == "personal", AppFeatures.keepEnabled)
    }
}
