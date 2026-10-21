package com.pori.assistant.brain

import com.pori.assistant.data.Settings
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

/** Direct client for the Claude Messages API (with tool use). */
class LlmClient(private val settings: Settings) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun send(system: String, messages: JSONArray, tools: JSONArray): JSONObject =
        withContext(Dispatchers.IO) {
            val key = settings.apiKeyNow()
            if (key.isBlank()) throw IOException("No API key set. Add it in settings.")

            val body = JSONObject()
                .put("model", settings.modelNow())
                .put("max_tokens", 1024)
                .put("system", system)
                .put("messages", messages)
            if (tools.length() > 0) body.put("tools", tools)

            val req = Request.Builder()
                .url("https://api.anthropic.com/v1/messages")
                .header("x-api-key", key)
                .header("anthropic-version", "2023-06-01")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()

            http.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw IOException("API error ${resp.code}: ${text.take(300)}")
                JSONObject(text)
            }
        }
}
