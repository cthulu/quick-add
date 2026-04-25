package com.keepquickadd

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class KeepRepository(context: Context) {

    private val settings = AppSettings(context)

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val baseUrl: String get() = settings.backendUrl

    /** Add an item to a named Keep list. */
    suspend fun addItem(listName: String, text: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().put("text", text).put("list_name", listName).toString()
            val body = json.toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("$baseUrl/items")
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val errorMsg = when (response.code) {
                    404 -> "List \"$listName\" not found on server"
                    500 -> "Server error — check backend logs"
                    401, 403 -> "Authentication error — check credentials"
                    else -> "Server error: ${response.code}"
                }
                return@withContext Result.failure(IOException(errorMsg))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Test connectivity to a given URL (hits /health). */
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
