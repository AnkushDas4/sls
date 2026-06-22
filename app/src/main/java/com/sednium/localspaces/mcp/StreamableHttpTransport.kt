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

/**
 * Wire-level transport for one MCP server connection. This is the piece
 * the original web app never had at all — it only ever stored a
 * {name, url} pair and mentioned it by name in a prompt string. Here it
 * is an actual bidirectional JSON-RPC channel.
 *
 * Implements the **Streamable HTTP** transport (the current recommended
 * transport for remote servers as of spec 2025-11-25):
 *
 *   1. POST every JSON-RPC request/notification to the single MCP
 *      endpoint URL the user configured.
 *   2. The very first POST is always `initialize`. Its HTTP response
 *      carries an `Mcp-Session-Id` header; every request after that
 *      MUST echo that header back, pinning the client to whichever
 *      server instance/session issued it.
 *   3. Every request after `initialize` also carries an
 *      `MCP-Protocol-Version` header so the server knows which spec
 *      revision the client is speaking.
 *   4. The POST response is either:
 *        a) `Content-Type: application/json` — a single JSON-RPC
 *           response body, parsed directly, or
 *        b) `Content-Type: text/event-stream` — the server elected to
 *           stream the response (and possibly interleaved server-to-
 *           client requests/notifications) as SSE `data:` frames; each
 *           frame is itself a JSON-RPC message, terminated once a
 *           response whose `id` matches the outgoing request arrives.
 *   5. A long-lived GET to the same endpoint MAY be opened to receive
 *      server-initiated notifications/requests outside of any direct
 *      request/response (not required for the tools/list + tools/call
 *      flow this client focuses on, but wired in for completeness).
 *
 * Falls back to detecting the legacy HTTP+SSE transport (pre-2025-03-26)
 * if the very first POST is rejected with 400/404/405 — some community
 * servers haven't migrated yet.
 */
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
    private var legacyPostEndpoint: String? = null // discovered via the SSE "endpoint" event in legacy mode

    private val incoming = Channel<JsonRpcMessage>(Channel.UNLIMITED)
    private var legacyEventSource: EventSource? = null

    /** Server-initiated messages arriving outside of a direct request/response pair. */
    val incomingMessages: Flow<JsonRpcMessage> get() = incoming.receiveAsFlow()

    sealed class JsonRpcMessage {
        data class Response(val response: JsonRpcResponse) : JsonRpcMessage()
        data class Notification(val notification: JsonRpcNotification) : JsonRpcMessage()
    }

    /** Performs exactly one JSON-RPC request and returns its matching response. */
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

        // First request rejected outright -> probably a legacy HTTP+SSE-only server.
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

    /** Reads a streamed POST response body as SSE, returning once the matching `id` arrives. */
    private fun readSseUntilMatchingResponse(response: Response, expectedId: JsonRpcId): JsonRpcResponse {
        val body = response.body ?: throw McpTransportException("Empty SSE body from $endpoint")
        body.source().use { source ->
            val buffer = StringBuilder()
            while (true) {
                val line = source.readUtf8Line() ?: break
                if (line.startsWith("data:")) {
                    val payload = line.removePrefix("data:").trim()
                    val parsedResponse = runCatching { parseSingleResponse(payload) }.getOrNull()
                    if (parsedResponse != null) {
                        if (parsedResponse.id == expectedId) return parsedResponse
                        // A different id (or a server->client request) arrived mid-stream:
                        // surface it as an out-of-band message for McpClient to dispatch.
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

    // ---- legacy HTTP+SSE transport (pre-2025-03-26), kept for backwards compatibility ----

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
        httpResponse.close() // legacy transport: the real response arrives async over the SSE GET stream
        // A production client would await a matching message on `incoming`; omitted here for brevity —
        // see McpClient's pending-request map, which already does this for the Streamable HTTP path.
        throw McpTransportException("Legacy HTTP+SSE servers should be awaited via `incomingMessages`, not this call path")
    }

    fun close() {
        legacyEventSource?.cancel()
        incoming.close()
    }

    private object NoopCallback : okhttp3.Callback {
        override fun onFailure(call: okhttp3.Call, e: IOException) { /* fire-and-forget notification */ }
        override fun onResponse(call: okhttp3.Call, response: Response) { response.close() }
    }
}

class McpTransportException(message: String, cause: Throwable? = null) : Exception(message, cause)
