package nl.freshlytyped.keepquickadd

import android.content.Context
import android.content.SharedPreferences

class AppSettings(context: Context) {

    companion object {
        private const val PREFS_NAME = "app_settings"
        private const val KEY_PUBNUB_PUBLISH_KEY = "pubnub_publish_key"
        private const val KEY_PUBNUB_SUBSCRIBE_KEY = "pubnub_subscribe_key"
        private const val KEY_PUBNUB_API_KEY = "pubnub_api_key"
        private const val KEY_PUBNUB_CHANNEL = "pubnub_channel"
        private const val KEY_LAST_LIST_NAME = "last_selected_list_name"
        private const val KEY_LIST_COUNT = "list_count"
        private const val KEY_LIST_PREFIX = "list_name_"
        private const val KEY_HIDDEN_CALENDAR_IDS = "hidden_calendar_ids"
        const val DEFAULT_LIST_NAME = "Shopping"
        const val DEFAULT_CHANNEL = "channel-ha"
        // Not const so tests can override via reflection (compiler would inline a const).
        @JvmField var PUBNUB_BASE_URL = "https://ps.pndsn.com"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var publishKey: String
        get() = prefs.getString(KEY_PUBNUB_PUBLISH_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PUBNUB_PUBLISH_KEY, value.trim()).apply()

    var subscribeKey: String
        get() = prefs.getString(KEY_PUBNUB_SUBSCRIBE_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PUBNUB_SUBSCRIBE_KEY, value.trim()).apply()

    var apiKey: String
        get() = prefs.getString(KEY_PUBNUB_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PUBNUB_API_KEY, value.trim()).apply()

    var channel: String
        get() = prefs.getString(KEY_PUBNUB_CHANNEL, DEFAULT_CHANNEL) ?: DEFAULT_CHANNEL
        set(value) = prefs.edit().putString(KEY_PUBNUB_CHANNEL, value.trim()).apply()

    fun isConfigured(): Boolean =
        publishKey.isNotBlank() && subscribeKey.isNotBlank()

    /** Last selected list name (persisted by name). */
    var lastSelectedListName: String?
        get() = prefs.getString(KEY_LAST_LIST_NAME, null)
        set(value) = prefs.edit().putString(KEY_LAST_LIST_NAME, value).apply()

    /**
     * Returns the configured list names. Always has at least one entry.
     */
    fun getLists(): List<String> {
        val count = prefs.getInt(KEY_LIST_COUNT, 0)
        if (count == 0) return listOf(DEFAULT_LIST_NAME)
        return (0 until count).map { i ->
            prefs.getString("$KEY_LIST_PREFIX$i", "") ?: ""
        }.filter { it.isNotBlank() }.ifEmpty { listOf(DEFAULT_LIST_NAME) }
    }

    /**
     * Saves the list names. The first nonblank entry keeps its whitespace; later entries are trimmed.
     */
    fun saveLists(names: List<String>) {
        val cleaned = names.mapIndexed { i, name ->
            if (i == 0) name.ifBlank { DEFAULT_LIST_NAME } else name.trim()
        }.filterIndexed { i, name -> i == 0 || name.isNotBlank() }

        prefs.edit().apply {
            putInt(KEY_LIST_COUNT, cleaned.size)
            cleaned.forEachIndexed { i, name -> putString("$KEY_LIST_PREFIX$i", name) }
            apply()
        }
    }

    var hiddenCalendarIds: Set<Long>
        get() = prefs.getString(KEY_HIDDEN_CALENDAR_IDS, "")
            ?.split(',')
            ?.mapNotNull { it.trim().toLongOrNull() }
            ?.filter { it >= 0 }
            ?.toSet()
            ?: emptySet()
        set(value) = prefs.edit()
            .putString(KEY_HIDDEN_CALENDAR_IDS, value.filter { it >= 0 }.distinct().joinToString(","))
            .apply()
}
