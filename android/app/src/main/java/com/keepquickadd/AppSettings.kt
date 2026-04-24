package com.keepquickadd

import android.content.Context
import android.content.SharedPreferences

class AppSettings(context: Context) {

    companion object {
        private const val PREFS_NAME = "app_settings"
        private const val KEY_BACKEND_URL = "backend_url"
        private const val KEY_LAST_LIST_ID = "last_selected_list_id"
        const val DEFAULT_BACKEND_URL = "http://10.0.2.2:8000"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var backendUrl: String
        get() = prefs.getString(KEY_BACKEND_URL, DEFAULT_BACKEND_URL) ?: DEFAULT_BACKEND_URL
        set(value) = prefs.edit().putString(KEY_BACKEND_URL, value.trimEnd('/')).apply()

    fun isConfigured(): Boolean = prefs.contains(KEY_BACKEND_URL)

    var lastSelectedListId: String?
        get() = prefs.getString(KEY_LAST_LIST_ID, null)
        set(value) = prefs.edit().putString(KEY_LAST_LIST_ID, value).apply()
}
