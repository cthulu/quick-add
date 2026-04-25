package nl.freshlytyped.keepquickadd

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

    /**
     * Publishes an "add item to list" message to PubNub.
     *
     * Endpoint: POST https://ps.pndsn.com/publish/{pub_key}/{sub_key}/0/{channel}/0
     * Body: {"message": "add {item} to the list {list}"}
     */
    suspend fun     addItem(listName: String, text: String): Result<Unit> = withContext(Dispatchers.IO) {
        val publishKey = settings.publishKey
        val subscribeKey = settings.subscribeKey
        val apiKey = settings.apiKey
        val channel = settings.channel

        if (publishKey.isBlank() || subscribeKey.isBlank()) {
            return@withContext Result.failure(
                IOException("PubNub keys not configured — open Settings")
            )
        }

        try {
            val payload = JSONObject().put(
                "message", "add $text to the list $listName"
            ).toString()

            val url = "${AppSettings.PUBNUB_BASE_URL}/publish" +
                "/$publishKey/$subscribeKey/0/$channel/0"

            val body = payload.toRequestBody("application/json".toMediaType())
            val requestBuilder = Request.Builder()
                .url(url)
                .post(body)
            if (apiKey.isNotBlank()) requestBuilder.header("x-api-key", apiKey)
            val request = requestBuilder.build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val errorMsg = when (response.code) {
                    400 -> "Bad request — check PubNub keys"
                    401, 403 -> "Authentication error — check API key"
                    else -> "PubNub error: ${response.code}"
                }
                return@withContext Result.failure(IOException(errorMsg))
            }
            Result.success(Unit)
        } catch (e: java.net.UnknownHostException) {
            Result.failure(IOException("No internet — cannot reach ${e.message ?: "host"}"))
        } catch (e: java.net.SocketTimeoutException) {
            Result.failure(IOException("Network timeout — check connection"))
        } catch (e: Exception) {
            Result.failure(IOException("${e.javaClass.simpleName}: ${e.message ?: "unknown"}"))
        }
    }

    /**
     * Test PubNub connectivity by doing a lightweight publish to verify keys.
     */
    suspend fun testConnection(publishKey: String, subscribeKey: String, apiKey: String, channel: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val payload = JSONObject().put("message", "test").toString()
                val url = "${AppSettings.PUBNUB_BASE_URL}/publish" +
                    "/$publishKey/$subscribeKey/0/$channel/0"

                val body = payload.toRequestBody("application/json".toMediaType())
                val requestBuilder = Request.Builder()
                    .url(url)
                    .post(body)
                if (apiKey.isNotBlank()) requestBuilder.header("x-api-key", apiKey)
                val request = requestBuilder.build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) Result.success(Unit)
                else Result.failure(IOException("PubNub returned ${response.code}"))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}
