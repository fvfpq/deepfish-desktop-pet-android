package com.deepfish.pet.gateway

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * openclaw Gateway node 客户端：通过 WebSocket 连接本机/远程 Gateway，
 * 以 node 角色完成 connect 握手，并向 Gateway 暴露 mobile-ui 手机操作能力。
 */
class GatewayNode(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
) {

    sealed interface ConnectionState {
        data object Disconnected : ConnectionState
        data object Connecting : ConnectionState
        data class Connected(val serverVersion: String?) : ConnectionState
        data class Error(val message: String) : ConnectionState
    }

    data class InvokeRequest(
        val id: String,
        val nodeId: String,
        val command: String,
        val paramsJson: String?,
        val timeoutMs: Long?,
    )

    data class InvokeResult(
        val ok: Boolean,
        val payloadJson: String?,
        val code: String?,
        val message: String?,
    ) {
        companion object {
            fun ok(payloadJson: String?) = InvokeResult(true, payloadJson, null, null)
            fun error(code: String, message: String) = InvokeResult(false, null, code, message)
        }
    }

    var onInvoke: (suspend (InvokeRequest) -> InvokeResult)? = null
    var onChatEvent: ((JSONObject) -> Unit)? = null

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    private val frameMutex = Mutex()
    private val pendingRequests = mutableMapOf<String, CompletableDeferred<JSONObject>>()

    private var webSocket: WebSocket? = null
    private var connectJob: Job? = null
    private val disconnected = AtomicBoolean(true)
    private var deviceToken: String? = null
    private var reconnectAttempt = 0

    private var host: String = ""
    private var port: Int = 18789
    private var gatewayToken: String? = null
    private var shouldReconnect = false

    // 每次连接尝试的生命周期信号
    private var handshakeDone: CompletableDeferred<Unit>? = null
    private var transportDown: CompletableDeferred<Unit>? = null

    val isConnected: Boolean get() = _state.value is ConnectionState.Connected

    fun connect(host: String, port: Int, token: String?) {
        this.host = host
        this.port = port
        this.gatewayToken = token?.trim()?.takeIf { it.isNotEmpty() }
        shouldReconnect = true
        reconnectAttempt = 0
        startConnectLoop()
    }

    fun disconnect() {
        shouldReconnect = false
        disconnected.set(true)
        connectJob?.cancel()
        connectJob = null
        transportDown?.complete(Unit)
        handshakeDone?.complete(Unit)
        webSocket?.close(1000, "client disconnect")
        webSocket = null
        failAllPending("disconnected")
        _state.value = ConnectionState.Disconnected
    }

    private fun startConnectLoop() {
        connectJob?.cancel()
        connectJob = scope.launch {
            while (isActive && shouldReconnect) {
                val handshake = CompletableDeferred<Unit>()
                val down = CompletableDeferred<Unit>()
                handshakeDone = handshake
                transportDown = down
                disconnected.set(false)
                _state.value = ConnectionState.Connecting
                val attempt = reconnectAttempt
                var established = false
                try {
                    establishConnection(handshake, down)
                    established = true
                    reconnectAttempt = 0
                } catch (e: Exception) {
                    Log.w(TAG, "connect attempt $attempt failed: ${e.message}")
                    if (!shouldReconnect || !isActive) break
                    _state.value = ConnectionState.Error(e.message ?: "连接失败")
                    reconnectAttempt++
                }
                if (established) {
                    // 连接成功后等待连接断开或主动断开
                    down.await()
                    if (!shouldReconnect || !isActive) break
                } else {
                    if (!shouldReconnect || !isActive) break
                    delay(backoffMs(reconnectAttempt - 1))
                }
            }
            if (!shouldReconnect || !isActive) {
                _state.value = ConnectionState.Disconnected
            }
        }
    }

    private fun backoffMs(attempt: Int): Long {
        val exp = 1000L shl attempt.coerceAtMost(5)
        return exp.coerceAtMost(30_000L)
    }

    private suspend fun establishConnection(
        handshake: CompletableDeferred<Unit>,
        down: CompletableDeferred<Unit>,
    ) {
        val url = "ws://$host:$port"
        val request = Request.Builder().url(url).build()
        val ws = withContext(Dispatchers.IO) {
            httpClient.newWebSocket(request, listener(down))
        }
        webSocket = ws
        kotlinx.coroutines.withTimeoutOrNull(20_000) { handshake.await() }
            ?: throw IllegalStateException("连接握手超时")
    }

    private fun listener(down: CompletableDeferred<Unit>) = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.i(TAG, "ws open to $host:$port")
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            scope.launch { handleFrame(text) }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.w(TAG, "ws failure: ${t.message}")
            scope.launch { onTransportDown(down, "连接断开：${t.message}") }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            scope.launch { onTransportDown(down, "连接已关闭：$reason") }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(code, reason)
        }
    }

    private suspend fun onTransportDown(down: CompletableDeferred<Unit>, reason: String) {
        if (!shouldReconnect) {
            _state.value = ConnectionState.Disconnected
        }
        if (disconnected.compareAndSet(false, true)) {
            failAllPending(reason)
            down.complete(Unit)
        }
    }

    private suspend fun handleFrame(text: String) {
        val frame = runCatching { JSONObject(text) }.getOrNull() ?: return
        when (frame.optString("type")) {
            "event" -> handleEvent(frame)
            "res" -> handleResponse(frame)
        }
    }

    private suspend fun handleEvent(frame: JSONObject) {
        when (val event = frame.optString("event")) {
            "connect.challenge" -> {
                val payload = frame.optJSONObject("payload")
                val nonce = payload?.optString("nonce") ?: frame.optString("nonce")
                val ts = payload?.optLong("ts")
                if (nonce.isNullOrBlank()) return
                scope.launch { sendConnect(nonce, ts) }
            }
            "node.invoke.request" -> {
                val payload = frame.optJSONObject("payload")
                val invokeId = payload?.optString("id")
                val nodeId = payload?.optString("nodeId")
                val command = payload?.optString("command")
                if (invokeId == null || nodeId == null || command == null) return
                val paramsJson = payload.optString("paramsJSON").takeIf { it.isNotBlank() }
                val timeoutMs = payload.optLong("timeoutMs").takeIf { it > 0 }
                val handler = onInvoke
                if (handler == null) {
                    scope.launch {
                        sendInvokeResult(invokeId, nodeId, InvokeResult.error("UNAVAILABLE", "invoke handler missing"))
                    }
                    return
                }
                scope.launch {
                    val result = handler(
                        InvokeRequest(invokeId, nodeId, command, paramsJson, timeoutMs)
                    )
                    sendInvokeResult(invokeId, nodeId, result)
                }
            }
            "chat" -> {
                val payload = frame.optJSONObject("payload")
                onChatEvent?.invoke(payload ?: frame)
            }
        }
    }

    private suspend fun handleResponse(frame: JSONObject) {
        val id = frame.optString("id")
        if (id.isEmpty()) return
        pendingRequests.remove(id)?.complete(frame)
    }

    private suspend fun sendConnect(nonce: String, challengeTs: Long?) {
        val identity = NodeIdentityStore.loadOrCreate(context)
        val authToken = gatewayToken ?: deviceToken
        val signedAtMs = challengeTs ?: System.currentTimeMillis()

        val scopes = listOf("node.device")
        val clientId = "deepfish-node"
        val clientMode = "node"
        val role = "node"

        val authPayload = NodeIdentityStore.buildV3AuthPayload(
            deviceId = identity.deviceId,
            clientId = clientId,
            clientMode = clientMode,
            role = role,
            scopes = scopes,
            signedAtMs = signedAtMs,
            token = authToken,
            nonce = nonce,
            platform = "android",
            deviceFamily = "Android",
        )
        val signature = NodeIdentityStore.signPayload(authPayload, identity)
        val publicKey = NodeIdentityStore.publicKeyBase64Url(identity)

        val params = JSONObject().apply {
            put("minProtocol", 3)
            put("maxProtocol", 4)
            put("client", JSONObject().apply {
                put("id", clientId)
                put("displayName", "DeepFish Pet Node")
                put("version", "1.3.0")
                put("platform", "android")
                put("deviceFamily", "Android")
                put("mode", clientMode)
            })
            put("role", role)
            put("scopes", JSONArray().apply { scopes.forEach { put(it) } })
            put("caps", JSONArray().apply { put("mobileUI") })
            put("commands", JSONArray().apply {
                put("mobile-ui.observe")
                put("mobile-ui.act")
            })
            put("permissions", JSONObject())
            if (authToken != null) {
                put("auth", JSONObject().apply { put("token", authToken) })
            }
            if (signature != null && publicKey != null) {
                put("device", JSONObject().apply {
                    put("id", identity.deviceId)
                    put("publicKey", publicKey)
                    put("signature", signature)
                    put("signedAt", signedAtMs)
                    put("nonce", nonce)
                })
            }
        }

        val res = requestInternal("connect", params, timeoutMs = 20_000)
        if (res.optBoolean("ok", false)) {
            val payload = res.optJSONObject("payload")
            val auth = payload?.optJSONObject("auth")
            val token = auth?.optString("deviceToken")
            if (!token.isNullOrBlank()) {
                deviceToken = token
            }
            reconnectAttempt = 0
            _state.value = ConnectionState.Connected(payload?.optJSONObject("server")?.optString("version"))
            handshakeDone?.complete(Unit)
        } else {
            val err = res.optJSONObject("error") ?: res.optJSONObject("payload")
            val code = err?.optString("code") ?: "CONNECT_FAILED"
            val message = err?.optString("message") ?: "connect failed"
            Log.w(TAG, "connect rejected: $code - $message")
            _state.value = ConnectionState.Error("$code：$message")
            handshakeDone?.complete(Unit)
        }
    }

    private suspend fun sendInvokeResult(id: String, nodeId: String, result: InvokeResult) {
        try {
            val parsed = result.payloadJson?.let { runCatching { JSONObject(it) }.getOrNull() }
            val params = JSONObject().apply {
                put("id", id)
                put("nodeId", nodeId)
                put("ok", result.ok)
                if (parsed != null) put("payload", parsed)
                else if (result.payloadJson != null) put("payloadJson", result.payloadJson)
                if (result.code != null) {
                    put("error", JSONObject().apply {
                        put("code", result.code)
                        put("message", result.message)
                    })
                }
            }
            requestInternal("node.invoke.result", params, timeoutMs = 10_000)
        } catch (e: Exception) {
            Log.w(TAG, "sendInvokeResult failed: ${e.message}")
        }
    }

    /** 发送聊天消息；回复通过 onChatEvent 事件流到达。 */
    suspend fun sendChat(sessionKey: String, message: String, idempotencyKey: String? = null) {
        val params = JSONObject().apply {
            put("sessionKey", sessionKey)
            put("message", message)
            put("idempotencyKey", idempotencyKey ?: UUID.randomUUID().toString())
        }
        requestInternal("chat.send", params, timeoutMs = 20_000)
    }

    /** 发送一个 JSON-RPC 请求并等待响应。 */
    suspend fun request(method: String, params: JSONObject, timeoutMs: Long = 15_000): JSONObject =
        requestInternal(method, params, timeoutMs)

    private suspend fun requestInternal(
        method: String,
        params: JSONObject,
        timeoutMs: Long,
    ): JSONObject {
        val id = UUID.randomUUID().toString()
        val frame = JSONObject().apply {
            put("type", "req")
            put("id", id)
            put("method", method)
            put("params", params)
        }
        val deferred = CompletableDeferred<JSONObject>()
        frameMutex.withLock {
            pendingRequests[id] = deferred
        }
        val ws = webSocket
        if (ws == null) {
            pendingRequests.remove(id)
            throw IllegalStateException("Gateway 未连接")
        }
        val sent = runCatching { ws.send(frame.toString()) }.getOrDefault(false)
        if (!sent) {
            pendingRequests.remove(id)
            throw IllegalStateException("发送失败")
        }
        return kotlinx.coroutines.withTimeoutOrNull(timeoutMs) { deferred.await() }
            ?: run {
                pendingRequests.remove(id)
                throw IllegalStateException("请求超时：$method")
            }
    }

    private fun failAllPending(reason: String) {
        val err = JSONObject().apply {
            put("type", "res")
            put("ok", false)
            put("error", JSONObject().apply {
                put("code", "DISCONNECTED")
                put("message", reason)
            })
        }
        synchronized(pendingRequests) {
            pendingRequests.values.forEach { it.complete(err) }
            pendingRequests.clear()
        }
    }
}

private const val TAG = "GatewayNode"
