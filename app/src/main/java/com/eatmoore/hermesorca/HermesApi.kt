package com.eatmoore.hermesorca

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

class HermesApi {

    suspend fun health(baseUrl: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val c = open("${baseUrl.trimEnd('/')}/health", null, "GET", 2500, 3500)
            val ok = c.responseCode in 200..299
            c.disconnect()
            ok
        }.getOrDefault(false)
    }

    suspend fun detailedHealth(baseUrl: String, key: String): Result<String> =
        get("${baseUrl.trimEnd('/')}/health/detailed", key)

    suspend fun capabilities(baseUrl: String, key: String): Result<String> =
        get("${baseUrl.trimEnd('/')}/v1/capabilities", key)

    suspend fun skills(baseUrl: String, key: String): Result<String> =
        get("${baseUrl.trimEnd('/')}/v1/skills", key)

    suspend fun toolsets(baseUrl: String, key: String): Result<String> =
        get("${baseUrl.trimEnd('/')}/v1/toolsets", key)

    suspend fun responses(
        baseUrl: String,
        key: String,
        input: String,
        conversation: String
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject()
                .put("model", "hermes-agent")
                .put("input", input)
                .put("conversation", conversation)
                .put("store", true)

            val json = post("${baseUrl.trimEnd('/')}/v1/responses", key, body)
            extractResponseText(JSONObject(json))
        }
    }

    suspend fun responseWithImage(
        baseUrl: String,
        key: String,
        prompt: String,
        mime: String,
        bytes: ByteArray
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val dataUrl = "data:$mime;base64," + Base64.getEncoder().encodeToString(bytes)
            val content = JSONArray()
                .put(JSONObject().put("type", "input_text").put("text", prompt))
                .put(JSONObject().put("type", "input_image").put("image_url", dataUrl))

            val input = JSONArray()
                .put(JSONObject().put("role", "user").put("content", content))

            val body = JSONObject()
                .put("model", "hermes-agent")
                .put("input", input)
                .put("conversation", "orca-native")
                .put("store", true)

            val json = post("${baseUrl.trimEnd('/')}/v1/responses", key, body)
            extractResponseText(JSONObject(json))
        }
    }

    suspend fun getJobs(baseUrl: String, key: String): Result<String> =
        get("${baseUrl.trimEnd('/')}/api/jobs", key)

    suspend fun runJob(baseUrl: String, key: String, jobId: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                post("${baseUrl.trimEnd('/')}/api/jobs/$jobId/run", key, JSONObject())
            }
        }

    private suspend fun get(url: String, key: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val c = open(url, key, "GET", 4000, 12000)
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val text = stream.bufferedReader().use { it.readText() }
            c.disconnect()
            if (code !in 200..299) error("HTTP $code: $text")
            prettyJson(text)
        }
    }

    private fun post(url: String, key: String, body: JSONObject): String {
        val c = open(url, key, "POST", 5000, 180000)
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json")
        c.outputStream.bufferedWriter().use { it.write(body.toString()) }

        val code = c.responseCode
        val stream = if (code in 200..299) c.inputStream else c.errorStream
        val text = stream.bufferedReader().use { it.readText() }
        c.disconnect()
        if (code !in 200..299) error("HTTP $code: $text")
        return text
    }

    private fun open(
        url: String,
        key: String?,
        method: String,
        connectTimeout: Int,
        readTimeout: Int
    ): HttpURLConnection {
        return (URL(url).openConnection() as HttpURLConnection).apply {
            this.connectTimeout = connectTimeout
            this.readTimeout = readTimeout
            requestMethod = method
            if (!key.isNullOrBlank()) setRequestProperty("Authorization", "Bearer $key")
        }
    }

    private fun prettyJson(text: String): String {
        return runCatching { JSONObject(text).toString(2) }
            .recoverCatching { JSONArray(text).toString(2) }
            .getOrDefault(text)
    }

    private fun extractResponseText(obj: JSONObject): String {
        val output = obj.optJSONArray("output") ?: return obj.toString(2)
        val chunks = mutableListOf<String>()
        for (i in 0 until output.length()) {
            val item = output.optJSONObject(i) ?: continue
            if (item.optString("type") != "message") continue
            val content = item.optJSONArray("content") ?: continue
            for (j in 0 until content.length()) {
                val part = content.optJSONObject(j) ?: continue
                if (part.optString("type") == "output_text") {
                    val text = part.optString("text")
                    if (text.isNotBlank()) chunks += text
                }
            }
        }
        return if (chunks.isNotEmpty()) chunks.joinToString("\n") else obj.toString(2)
    }
}
