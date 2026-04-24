package com.keepquickadd

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class KeepList(val id: String, val title: String)

class KeepRepository(context: Context) {

    companion object {
        private const val PREFS_NAME = "keep_cache"
        private const val KEY_LISTS_JSON = "cached_lists"
        private const val KEY_CACHE_TIMESTAMP = "cache_timestamp"
        const val CACHE_TTL_MS = 30 * 60 * 1000L // 30 minutes
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val settings = AppSettings(context)

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val baseUrl: String get() = settings.backendUrl

    /** Returns cached lists if fresh (< 30 min), otherwise fetches from server. */
    suspend fun getLists(forceRefresh: Boolean = false): Result<List<KeepList>> {
        if (!forceRefresh && isCacheFresh()) {
            val cached = loadFromCache()
            if (cached != null) return Result.success(cached)
        }
        return fetchFromServer()
    }

    fun isCacheFresh(): Boolean {
        val timestamp = prefs.getLong(KEY_CACHE_TIMESTAMP, 0L)
        return System.currentTimeMillis() - timestamp < CACHE_TTL_MS
    }

    fun getCacheAge(): Long {
        val timestamp = prefs.getLong(KEY_CACHE_TIMESTAMP, 0L)
        if (timestamp == 0L) return -1L
        return System.currentTimeMillis() - timestamp
    }

    fun clearCache() {
        prefs.edit()
            .remove(KEY_LISTS_JSON)
            .remove(KEY_CACHE_TIMESTAMP)
            .apply()
    }

    private fun loadFromCache(): List<KeepList>? {
        val json = prefs.getString(KEY_LISTS_JSON, null) ?: return null
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                KeepList(id = obj.getString("id"), title = obj.getString("title"))
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun saveToCache(lists: List<KeepList>) {
        val arr = JSONArray()
        lists.forEach { list ->
            arr.put(JSONObject().apply {
                put("id", list.id)
                put("title", list.title)
            })
        }
        prefs.edit()
            .putString(KEY_LISTS_JSON, arr.toString())
            .putLong(KEY_CACHE_TIMESTAMP, System.currentTimeMillis())
            .apply()
    }

    private suspend fun fetchFromServer(): Result<List<KeepList>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/lists")
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException("Server error: ${response.code}"))
            }

            val body = response.body?.string()
                ?: return@withContext Result.failure(IOException("Empty response body"))

            val arr = JSONArray(body)
            val lists = (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                KeepList(id = obj.getString("id"), title = obj.getString("title"))
            }

            saveToCache(lists)
            Result.success(lists)
        } catch (e: Exception) {
            // Network failed - fall back to stale cache if available
            val cached = loadFromCache()
            if (cached != null) Result.success(cached)
            else Result.failure(e)
        }
    }

    suspend fun addItem(listId: String, text: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().put("text", text).toString()
            val body = json.toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("$baseUrl/lists/$listId/items")
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException("Server error: ${response.code}"))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Test connectivity to a given URL (before saving it). */
    suspend fun testConnection(url: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("${url.trimEnd('/')}/health")
                .get()
                .build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(IOException("Server returned ${response.code}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
