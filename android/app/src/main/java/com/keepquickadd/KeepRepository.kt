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
        private const val CACHE_TTL_MS = 30 * 60 * 1000L // 30 minutes

        // Change this to your backend URL (e.g. http://192.168.x.x:8000 or Tailscale IP)
        private const val BASE_URL = "http://10.0.2.2:8000" // 10.0.2.2 = host machine from emulator
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * Returns cached lists if fresh (< 30 min old), otherwise fetches from server.
     */
    suspend fun getLists(forceRefresh: Boolean = false): Result<List<KeepList>> {
        if (!forceRefresh && isCacheFresh()) {
            val cached = loadFromCache()
            if (cached != null) return Result.success(cached)
        }
        return fetchFromServer()
    }

    private fun isCacheFresh(): Boolean {
        val timestamp = prefs.getLong(KEY_CACHE_TIMESTAMP, 0L)
        return System.currentTimeMillis() - timestamp < CACHE_TTL_MS
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

    fun getCacheAge(): Long {
        val timestamp = prefs.getLong(KEY_CACHE_TIMESTAMP, 0L)
        if (timestamp == 0L) return -1L
        return System.currentTimeMillis() - timestamp
    }

    private suspend fun fetchFromServer(): Result<List<KeepList>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$BASE_URL/lists")
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    IOException("Server error: ${response.code}")
                )
            }

            val body = response.body?.string() ?: return@withContext Result.failure(
                IOException("Empty response body")
            )

            val arr = JSONArray(body)
            val lists = (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                KeepList(id = obj.getString("id"), title = obj.getString("title"))
            }

            saveToCache(lists)
            Result.success(lists)
        } catch (e: Exception) {
            // If network fails, fall back to cache even if stale
            val cached = loadFromCache()
            if (cached != null) {
                Result.success(cached)
            } else {
                Result.failure(e)
            }
        }
    }

    suspend fun addItem(listId: String, text: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().put("text", text).toString()
            val body = json.toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("$BASE_URL/lists/$listId/items")
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
}
