package com.sednium.localspaces.mcp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.io.IOException
import java.util.concurrent.TimeUnit

class StreamableHttpTransport(
    private val endpoint: String,
    private val authToken: String? = null,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private var sessionId: String? = null
    private var legacySseMode = false
    private var legacyPostEndpoint: String? = null

    private val incoming = Channel<JsonRpcMessage>(Channel.UNLIMITED)
    private var legacyEventSource: EventSource? = null

    val incomingMessages: Flow<JsonRpcMessage> get() = incoming.receiveAsFlow()

    sealed class JsonRpcMessage {
        data class Response(val response: JsonRpcResponse) : JsonRpcMessage()
        data class Notification(val notification: JsonRpcNotification) : JsonRpcMessage()
    }

    @Throws(McpTransportException::class)
    suspend fun request(req: JsonRpcRequest): JsonRpcResponse = withContext(Dispatchers.IO) {
        val isInitialize = req.method == "initialize"

        if (legacySseMode) return@withContext requestViaLegacySse(req)

        val bodyJson = json.encodeToString(JsonRpcRequest.serializer(), req)
        val requestBuilder = Request.Builder()
            .url(endpoint)
            .post(bodyJson.toRequestBody("application/json".toMediaType()))
            .header("Accept", "application/json, text/event-stream")

        sessionId?.let { requestBuilder.header("Mcp-Session-Id", it) }
        if (!isInitialize) requestBuilder.header("MCP-Protocol-Version", MCP_PROTOCOL_VERSION)
        authToken?.let { requestBuilder.header("Authorization", "Bearer $it") }

        val httpResponse = try {
            client.newCall(requestBuilder.build()).execute()
        } catch (e: IOException) {
            throw McpTransportException("Network error contacting $endpoint: ${e.message}", e)
        }

        if (isInitialize && httpResponse.code in intArrayOf(400, 404, 405)) {
            httpResponse.close()
            legacySseMode = true
            connectLegacySseAndDiscoverEndpoint()
            return@withContext requestViaLegacySse(req)
        }

        if (!httpResponse.isSuccessful) {
            val errBody = httpResponse.body?.string()
            httpResponse.close()
            throw McpTransportException("HTTP ${httpResponse.code} from $endpoint: $errBody")
        }

        if (isInitialize) {
            httpResponse.header("Mcp-Session-Id")?.let { sessionId = it }
        }

        val contentType = httpResponse.header("Content-Type") ?: ""
        when {
            contentType.contains("text/event-stream") -> readSseUntilMatchingResponse(httpResponse, req.id)
            else -> {
                val text = httpResponse.body?.string().orEmpty()
                httpResponse.close()
                parseSingleResponse(text)
            }
        }
    }

    fun sendNotification(notification: JsonRpcNotification) {
        val bodyJson = json.encodeToString(JsonRpcNotification.serializer(), notification)
        val requestBuilder = Request.Builder()
            .url(if (legacySseMode) (legacyPostEndpoint ?: endpoint) else endpoint)
            .post(bodyJson.toRequestBody("application/json".toMediaType()))
        sessionId?.let { requestBuilder.header("Mcp-Session-Id", it) }
        requestBuilder.header("MCP-Protocol-Version", MCP_PROTOCOL_VERSION)
        authToken?.let { requestBuilder.header("Authorization", "Bearer $it") }
        client.newCall(requestBuilder.build()).enqueue(NoopCallback)
    }

    private fun readSseUntilMatchingResponse(response: Response, expectedId: JsonRpcId): JsonRpcResponse {
        val body = response.body ?: throw McpTransportException("Empty SSE body from $endpoint")
        body.source().use { source ->
            while (true) {
                val line = source.readUtf8Line() ?: break
                if (line.startsWith("data:")) {
                    val payload = line.removePrefix("data:").trim()
                    val parsedResponse = runCatching { parseSingleResponse(payload) }.getOrNull()
                    if (parsedResponse != null) {
                        if (parsedResponse.id == expectedId) return parsedResponse
                        incoming.trySend(JsonRpcMessage.Response(parsedResponse))
                        continue
                    }
                    val parsedNotification = runCatching {
                        json.decodeFromString(JsonRpcNotification.serializer(), payload)
                    }.getOrNull()
                    if (parsedNotification != null) {
                        incoming.trySend(JsonRpcMessage.Notification(parsedNotification))
                    }
                }
            }
        }
        throw McpTransportException("SSE stream closed before a matching response arrived")
    }

    private fun parseSingleResponse(text: String): JsonRpcResponse =
        json.decodeFromString(JsonRpcResponse.serializer(), text)

    private fun connectLegacySseAndDiscoverEndpoint() {
        val latch = java.util.concurrent.CountDownLatch(1)
        legacyEventSource = EventSources.createFactory(client).newEventSource(
            Request.Builder().url(endpoint).header("Accept", "text/event-stream").build(),
            object : EventSourceListener() {
                override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                    if (type == "endpoint") {
                        legacyPostEndpoint = if (data.startsWith("http")) data
                        else endpoint.substringBeforeLast('/') + "/" + data.removePrefix("/")
                        latch.countDown()
                    } else {
                        val parsed = runCatching { json.decodeFromString(JsonRpcResponse.serializer(), data) }.getOrNull()
                        if (parsed != null) incoming.trySend(JsonRpcMessage.Response(parsed))
                    }
                }
            }
        )
        latch.await(10, TimeUnit.SECONDS)
    }

    private fun requestViaLegacySse(req: JsonRpcRequest): JsonRpcResponse {
        val target = legacyPostEndpoint ?: throw McpTransportException("Legacy SSE endpoint not yet discovered")
        val bodyJson = json.encodeToString(JsonRpcRequest.serializer(), req)
        val httpResponse = client.newCall(
            Request.Builder().url(target).post(bodyJson.toRequestBody("application/json".toMediaType())).build()
        ).execute()
        httpResponse.close()
        throw McpTransportException("Legacy HTTP+SSE servers should be awaited via `incomingMessages`, not this call path")
    }

    fun close() {
        legacyEventSource?.cancel()
        incoming.close()
    }

    private object NoopCallback : okhttp3.Callback {
        override fun onFailure(call: okhttp3.Call, e: IOException) { }
        override fun onResponse(call: okhttp3.Call, response: Response) { response.close() }
    }
}

class McpTransportException(message: String, cause: Throwable? = null) : Exception(message, cause)
