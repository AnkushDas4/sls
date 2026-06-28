package com.sednium.localspaces.mcp

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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
import java.util.concurrent.ConcurrentHashMap
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

    // Legacy HTTP+SSE responses arrive asynchronously on the SSE stream,
    // not in the POST's own HTTP response body — there's no way to just
    // return them from the function that sent the request. Every in-flight
    // request gets a Deferred here, keyed by its JSON-RPC id; the SSE
    // listener below completes the matching one the moment a response with
    // that id comes in. Anything that arrives with no matching pending
    // request (truly unsolicited notifications) still goes to `incoming`.
    private val pendingLegacyRequests = ConcurrentHashMap<JsonRpcId, CompletableDeferred<JsonRpcResponse>>()

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
            // Some MCP servers sit behind a CDN/WAF that blocks generic
            // HTTP-client default user agents outright (commonly returned
            // as a blanket 403/404/405 before the request ever reaches the
            // server's actual MCP routing) — identify honestly rather than
            // looking like an anonymous bot.
            .header("User-Agent", "Sednium-Oorty-MCP-Client/1.0")

        sessionId?.let { requestBuilder.header("Mcp-Session-Id", it) }
        if (!isInitialize) requestBuilder.header("MCP-Protocol-Version", MCP_PROTOCOL_VERSION)
        authToken?.let { requestBuilder.header("Authorization", "Bearer $it") }

        val httpResponse = try {
            client.newCall(requestBuilder.build()).execute()
        } catch (e: IOException) {
            throw McpTransportException("Network error contacting $endpoint: ${e.message}", e)
        }

        if (isInitialize && httpResponse.code in intArrayOf(400, 404, 405)) {
            // A 405 here is ambiguous: it could genuinely mean "this server
            // only speaks the legacy HTTP+SSE transport", or it could mean
            // something entirely unrelated rejected the request before it
            // ever reached real MCP routing (auth, a WAF, a strict
            // protocol-version check) on a server that's actually modern
            // Streamable HTTP. Capture the real reason now, so if the
            // legacy attempt below ALSO fails — which it will, for a
            // server that was never legacy-only — the error that surfaces
            // is the original, actionable one instead of a confusing
            // second-order failure about SSE discovery.
            val originalBody = httpResponse.body?.string()
            val originalCode = httpResponse.code
            httpResponse.close()

            val legacyResult = runCatching {
                legacySseMode = true
                connectLegacySseAndDiscoverEndpoint()
                requestViaLegacySse(req)
            }

            if (legacyResult.isSuccess) {
                return@withContext legacyResult.getOrThrow()
            }

            // Legacy didn't pan out either — this almost certainly wasn't
            // a legacy-only server to begin with. Don't leave this
            // transport permanently stuck assuming legacy for every future
            // call; surface the original failure, which is the one most
            // likely to actually explain what's wrong.
            legacySseMode = false
            legacyPostEndpoint = null
            throw McpTransportException(
                "HTTP $originalCode from $endpoint: ${originalBody ?: "(empty body)"} " +
                    "[legacy HTTP+SSE fallback also failed: ${legacyResult.exceptionOrNull()?.message}]"
            )
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
            .header("User-Agent", "Sednium-Oorty-MCP-Client/1.0")
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

    private suspend fun connectLegacySseAndDiscoverEndpoint() {
        val ready = CompletableDeferred<Unit>()
        var sawAnyEvent = false

        legacyEventSource = EventSources.createFactory(client).newEventSource(
            Request.Builder().url(endpoint)
                .header("Accept", "text/event-stream")
                .header("User-Agent", "Sednium-Oorty-MCP-Client/1.0")
                .build(),
            object : EventSourceListener() {
                override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                    // The legacy MCP transport's first frame is meant to be
                    // `event: endpoint` with the POST URL as data. Some
                    // servers don't set the event name precisely, so as a
                    // fallback: if this is the very first frame we've seen
                    // and it clearly isn't JSON-RPC (doesn't parse, and
                    // looks like a path/URL), treat it as the endpoint too
                    // rather than silently dropping it and timing out.
                    val looksLikeEndpoint = type == "endpoint" ||
                        (!sawAnyEvent && (data.startsWith("/") || data.startsWith("http")) && runCatching {
                            json.decodeFromString(JsonRpcResponse.serializer(), data)
                        }.isFailure)
                    sawAnyEvent = true

                    if (looksLikeEndpoint && legacyPostEndpoint == null) {
                        legacyPostEndpoint = if (data.startsWith("http")) data
                        else endpoint.substringBeforeLast('/') + "/" + data.removePrefix("/")
                        ready.complete(Unit)
                        return
                    }

                    val parsedResponse = runCatching {
                        json.decodeFromString(JsonRpcResponse.serializer(), data)
                    }.getOrNull()
                    if (parsedResponse != null) {
                        val pending = pendingLegacyRequests.remove(parsedResponse.id)
                        if (pending != null) {
                            pending.complete(parsedResponse)
                        } else {
                            incoming.trySend(JsonRpcMessage.Response(parsedResponse))
                        }
                        return
                    }
                    val parsedNotification = runCatching {
                        json.decodeFromString(JsonRpcNotification.serializer(), data)
                    }.getOrNull()
                    if (parsedNotification != null) {
                        incoming.trySend(JsonRpcMessage.Notification(parsedNotification))
                    }
                }

                override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                    if (!ready.isCompleted) {
                        ready.completeExceptionally(
                            McpTransportException("Legacy SSE connection to $endpoint failed: ${t?.message ?: response?.code}")
                        )
                    }
                }

                override fun onClosed(eventSource: EventSource) {
                    if (!ready.isCompleted) {
                        ready.completeExceptionally(McpTransportException("Legacy SSE stream closed before sending an endpoint event"))
                    }
                }
            }
        )

        withTimeoutOrNull(15_000) { ready.await() }
        // No explicit failure handling needed here: if `ready` never
        // completed, legacyPostEndpoint stays null and the caller
        // (requestViaLegacySse) reports that clearly instead of timing out
        // silently the way the old CountDownLatch version did.
    }

    private suspend fun requestViaLegacySse(req: JsonRpcRequest): JsonRpcResponse {
        if (legacyPostEndpoint == null) {
            // Either this is the very first legacy request and discovery
            // hasn't run yet, or a prior discovery attempt failed/timed
            // out — retry rather than permanently failing every request
            // for the rest of this transport's lifetime.
            connectLegacySseAndDiscoverEndpoint()
        }
        val target = legacyPostEndpoint
            ?: throw McpTransportException(
                "Legacy SSE endpoint not yet discovered: the server never sent an 'endpoint' event within 15s of connecting to $endpoint"
            )

        val deferred = CompletableDeferred<JsonRpcResponse>()
        pendingLegacyRequests[req.id] = deferred

        try {
            val bodyJson = json.encodeToString(JsonRpcRequest.serializer(), req)
            val legacyPostBuilder = Request.Builder()
                .url(target)
                .post(bodyJson.toRequestBody("application/json".toMediaType()))
                .header("User-Agent", "Sednium-Oorty-MCP-Client/1.0")
            authToken?.let { legacyPostBuilder.header("Authorization", "Bearer $it") }
            val httpResponse = try {
                client.newCall(legacyPostBuilder.build()).execute()
            } catch (e: IOException) {
                throw McpTransportException("Network error posting to legacy endpoint $target: ${e.message}", e)
            }
            if (!httpResponse.isSuccessful) {
                val errBody = httpResponse.body?.string()
                httpResponse.close()
                throw McpTransportException("HTTP ${httpResponse.code} posting to legacy endpoint $target: $errBody")
            }
            httpResponse.close()

            // The actual JSON-RPC result for this specific request doesn't
            // come back in the POST's own response — it arrives
            // asynchronously as a `data:` event on the SSE stream already
            // being read above, which completes `deferred` by matching id.
            return withTimeoutOrNull(30_000) { deferred.await() }
                ?: throw McpTransportException("Timed out waiting for a legacy SSE response to '${req.method}'")
        } finally {
            pendingLegacyRequests.remove(req.id)
        }
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
