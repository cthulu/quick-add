package nl.freshlytyped.keepquickadd

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

internal enum class KeepFailureCategory {
    OFFLINE,
    TIMEOUT,
    CONFIGURATION,
    SERVER
}

internal class KeepFailureException(val category: KeepFailureCategory) :
    IOException(KeepFailureMapper.messageFor(category))

internal object KeepFailureMapper {
    fun categoryFor(statusCode: Int): KeepFailureCategory = when {
        statusCode == 408 -> KeepFailureCategory.TIMEOUT
        statusCode in 400..499 -> KeepFailureCategory.CONFIGURATION
        else -> KeepFailureCategory.SERVER
    }

    fun categoryFor(error: Throwable): KeepFailureCategory = when (error) {
        is KeepFailureException -> error.category
        is SocketTimeoutException -> KeepFailureCategory.TIMEOUT
        is UnknownHostException, is ConnectException, is NoRouteToHostException ->
            KeepFailureCategory.OFFLINE
        else -> KeepFailureCategory.SERVER
    }

    fun messageFor(category: KeepFailureCategory): String = when (category) {
        KeepFailureCategory.OFFLINE -> "No internet connection. Check your connection and try again."
        KeepFailureCategory.TIMEOUT -> "The connection timed out. Check your connection and try again."
        KeepFailureCategory.CONFIGURATION -> "Keep connection settings are invalid. Check Settings and try again."
        KeepFailureCategory.SERVER -> "Keep is unavailable right now. Try again later."
    }

    fun messageFor(error: Throwable): String = messageFor(categoryFor(error))
}

class KeepRepository(context: Context) {

    private val settings = AppSettings(context)

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private fun publishUrl(publishKey: String, subscribeKey: String, channel: String): String =
        "${AppSettings.PUBNUB_BASE_URL}/publish".toHttpUrl().newBuilder()
            .addPathSegment(publishKey)
            .addPathSegment(subscribeKey)
            .addPathSegment("0")
            .addPathSegment(channel)
            .addPathSegment("0")
            .build()
            .toString()

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
                KeepFailureException(KeepFailureCategory.CONFIGURATION)
            )
        }

        try {
            val payload = JSONObject().put(
                "message", "add $text to the list $listName"
            ).toString()

            val url = publishUrl(publishKey, subscribeKey, channel)

            val body = payload.toRequestBody("application/json".toMediaType())
            val requestBuilder = Request.Builder()
                .url(url)
                .post(body)
            if (apiKey.isNotBlank()) requestBuilder.header("x-api-key", apiKey)
            val request = requestBuilder.build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        KeepFailureException(KeepFailureMapper.categoryFor(response.code))
                    )
                }
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(KeepFailureException(KeepFailureMapper.categoryFor(e)))
        }
    }

    /**
     * Test PubNub connectivity by doing a lightweight publish to verify keys.
     */
    suspend fun testConnection(publishKey: String, subscribeKey: String, apiKey: String, channel: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            if (publishKey.isBlank() || subscribeKey.isBlank()) {
                return@withContext Result.failure(
                    KeepFailureException(KeepFailureCategory.CONFIGURATION)
                )
            }

            try {
                val payload = JSONObject().put("message", "test").toString()
                val url = publishUrl(publishKey, subscribeKey, channel)

                val body = payload.toRequestBody("application/json".toMediaType())
                val requestBuilder = Request.Builder()
                    .url(url)
                    .post(body)
                if (apiKey.isNotBlank()) requestBuilder.header("x-api-key", apiKey)
                val request = requestBuilder.build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) Result.success(Unit)
                    else Result.failure(
                        KeepFailureException(KeepFailureMapper.categoryFor(response.code))
                    )
                }
            } catch (e: Exception) {
                Result.failure(KeepFailureException(KeepFailureMapper.categoryFor(e)))
            }
        }
}
