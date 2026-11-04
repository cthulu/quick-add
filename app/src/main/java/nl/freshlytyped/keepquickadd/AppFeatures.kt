package nl.freshlytyped.keepquickadd

internal object AppFeatures {
    val keepEnabled: Boolean
        get() = BuildConfig.KEEP_ENABLED
}
