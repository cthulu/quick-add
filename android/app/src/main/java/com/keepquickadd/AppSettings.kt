package com.keepquickadd

import android.content.Context
import android.content.SharedPreferences

class AppSettings(context: Context) {

    companion object {
        private const val PREFS_NAME = "app_settings"
        private const val KEY_BACKEND_URL = "backend_url"
        private const val KEY_LAST_LIST_NAME = "last_selected_list_name"
        private const val KEY_LIST_COUNT = "list_count"
        private const val KEY_LIST_PREFIX = "list_name_"
        const val DEFAULT_BACKEND_URL = "http://10.0.2.2:8000"
        const val DEFAULT_LIST_NAME = "Inbox"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var backendUrl: String
        get() = prefs.getString(KEY_BACKEND_URL, DEFAULT_BACKEND_URL) ?: DEFAULT_BACKEND_URL
        set(value) = prefs.edit().putString(KEY_BACKEND_URL, value.trimEnd('/')).apply()

    fun isConfigured(): Boolean = prefs.contains(KEY_BACKEND_URL)

    /** Last selected list name (persisted by name, not ID). */
    var lastSelectedListName: String?
        get() = prefs.getString(KEY_LAST_LIST_NAME, null)
        set(value) = prefs.edit().putString(KEY_LAST_LIST_NAME, value).apply()

    /**
     * Returns the configured list names. Always has at least one entry (the first
     * entry defaults to DEFAULT_LIST_NAME and cannot be removed by the user).
     */
    fun getLists(): List<String> {
        val count = prefs.getInt(KEY_LIST_COUNT, 0)
        if (count == 0) return listOf(DEFAULT_LIST_NAME)
        return (0 until count).map { i ->
            prefs.getString("$KEY_LIST_PREFIX$i", "") ?: ""
        }.filter { it.isNotBlank() }.ifEmpty { listOf(DEFAULT_LIST_NAME) }
    }

    /**
     * Saves the list names. The first entry is always kept even if empty.
     * Blank entries (except the first) are discarded.
     */
    fun saveLists(names: List<String>) {
        val cleaned = names.mapIndexed { i, name ->
            if (i == 0) name.ifBlank { DEFAULT_LIST_NAME } else name.trim()
        }.filterIndexed { i, name -> i == 0 || name.isNotBlank() }

        prefs.edit().apply {
            putInt(KEY_LIST_COUNT, cleaned.size)
            cleaned.forEachIndexed { i, name -> putString("$KEY_LIST_PREFIX$i", name) }
            // Remove any old extra entries
            apply()
        }
    }
}
