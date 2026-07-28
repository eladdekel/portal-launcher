package com.iblu01.portallauncher

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class HaApiClient(private val baseUrl: String, private val token: String) {

    companion object {
        private const val TAG = "HaApiClient"
    }

    fun testConnection(): HaApiResult {
        return get("/api/")
    }

    fun getStates(): HaApiResult {
        return get("/api/states")
    }

    fun getState(entityId: String): HaApiResult {
        return get("/api/states/$entityId")
    }

    fun callService(domain: String, service: String, entityId: String? = null, data: Map<String, Any>? = null): HaApiResult {
        val path = "/api/services/$domain/$service"
        val body = JSONObject()
        entityId?.let { id ->
            body.put("entity_id", if (id.contains(",")) JSONArray(id.split(",").map { it.trim() }) else id)
        }
        data?.forEach { (k, v) -> body.put(k, v) }
        val payload = if (body.length() > 0) body.toString() else null
        return post(path, payload)
    }

    private fun get(path: String): HaApiResult {
        return try {
            val conn = openConnection("$baseUrl$path")
            conn.requestMethod = "GET"
            readResponse(conn)
        } catch (e: Exception) {
            Log.w(TAG, "GET $path failed: ${e.message}")
            HaApiResult(ok = false, body = e.message ?: "unknown error")
        }
    }

    private fun post(path: String, body: String?): HaApiResult {
        return try {
            val conn = openConnection("$baseUrl$path")
            conn.requestMethod = "POST"
            conn.doOutput = body != null
            conn.setRequestProperty("Content-Type", "application/json")
            if (body != null) {
                OutputStreamWriter(conn.outputStream).use { it.write(body) }
            }
            readResponse(conn)
        } catch (e: Exception) {
            Log.w(TAG, "POST $path failed: ${e.message}")
            HaApiResult(ok = false, body = e.message ?: "unknown error")
        }
    }

    private fun openConnection(url: String): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.setRequestProperty("Accept", "application/json")
        return conn
    }

    private fun readResponse(conn: HttpURLConnection): HaApiResult {
        val code = conn.responseCode
        val body = if (code in 200..299) {
            conn.inputStream.bufferedReader().use { it.readText() }
        } else {
            runCatching { conn.errorStream?.bufferedReader()?.use { it.readText() } }.getOrNull()
        }
        return HaApiResult(ok = code in 200..299, body = body, statusCode = code)
    }
}

data class HaApiResult(
    val ok: Boolean,
    val body: String?,
    val statusCode: Int = -1,
)
