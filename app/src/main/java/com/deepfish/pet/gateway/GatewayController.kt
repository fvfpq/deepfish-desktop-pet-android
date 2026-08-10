package com.deepfish.pet.gateway

import android.content.Context
import com.deepfish.pet.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * 全局单例：管理到 openclaw Gateway 的 node 连接生命周期，
 * 并把 mobile-ui 命令桥接到无障碍执行层。
 */
object GatewayController {

    private var node: GatewayNode? = null
    private var mobileUi: MobileUiInvokeHandler? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow<GatewayNode.ConnectionState>(GatewayNode.ConnectionState.Disconnected)
    val state: StateFlow<GatewayNode.ConnectionState> = _state.asStateFlow()

    var onChatEvent: ((JSONObject) -> Unit)? = null

    private val pendingReplies = mutableMapOf<String, kotlinx.coroutines.CompletableDeferred<String>>()
    private val pendingText = mutableMapOf<String, StringBuilder>()

    val isConnected: Boolean get() = node?.isConnected == true

    fun ensureStarted(context: Context) {
        val current = node
        if (current != null) return
        val handler = MobileUiInvokeHandler()
        val client = GatewayNode(context.applicationContext, scope).apply {
            onInvoke = { req ->
                when (req.command) {
                    "mobile-ui.observe", "mobile-ui.act" ->
                        handler.handle(req.command, req.paramsJson)
                    else ->
                        GatewayNode.InvokeResult.error("UNKNOWN_COMMAND", "unknown command: ${req.command}")
                }
            }
            onChatEvent = { payload -> handleChatEvent(payload) }
        }
        scope.launch {
            client.state.collect { _state.value = it }
        }
        mobileUi = handler
        node = client
    }

    private fun handleChatEvent(payload: JSONObject) {
        onChatEvent?.invoke(payload)
        val sessionKey = payload.optString("sessionKey").ifBlank { "main" }
        val deferred = pendingReplies[sessionKey] ?: return
        val state = payload.optString("state")
        val message = payload.optJSONObject("message")
        val role = message?.optString("role")
        if (role == "assistant") {
            val text = extractAssistantText(message)
            if (state == "final") {
                deferred.complete(text ?: "")
                pendingReplies.remove(sessionKey)
                pendingText.remove(sessionKey)
            } else if (!text.isNullOrEmpty()) {
                val builder = pendingText.getOrPut(sessionKey) { StringBuilder() }
                builder.append(text)
            }
        }
    }

    private fun extractAssistantText(message: JSONObject?): String? {
        message ?: return null
        val content = message.optJSONArray("content") ?: return null
        for (i in 0 until content.length()) {
            val item = content.optJSONObject(i) ?: continue
            if (item.optString("type") == "text") {
                val text = item.optString("text")
                if (text.isNotBlank()) return text
            }
        }
        return message.optString("text").takeIf { it.isNotBlank() }
    }

    /** 通过 Gateway 发送聊天并等待 assistant 最终回复。 */
    suspend fun sendChatAwait(text: String, sessionKey: String = "main", timeoutMs: Long = 120_000): String {
        val client = node ?: throw IllegalStateException("Gateway 未连接")
        val deferred = kotlinx.coroutines.CompletableDeferred<String>()
        pendingReplies[sessionKey] = deferred
        pendingText.remove(sessionKey)
        client.sendChat(sessionKey, text)
        return kotlinx.coroutines.withTimeoutOrNull(timeoutMs) { deferred.await() }
            ?: run {
                pendingReplies.remove(sessionKey)
                pendingText.remove(sessionKey)
                pendingText[sessionKey]?.toString()?.takeIf { it.isNotBlank() }
                    ?: throw IllegalStateException("等待回复超时")
            }
    }

    fun start(context: Context, host: String, port: Int, token: String?) {
        ensureStarted(context)
        node?.connect(host, port, token)
    }

    fun startFromPrefs(context: Context) {
        ensureStarted(context)
        node?.connect(
            Prefs.gatewayHost(context),
            Prefs.gatewayPort(context),
            Prefs.gatewayToken(context),
        )
    }

    fun stop() {
        node?.disconnect()
    }

    fun sendChat(text: String, sessionKey: String = "main") {
        val client = node ?: return
        scope.launch {
            runCatching { client.sendChat(sessionKey, text) }
        }
    }

    fun sendRequest(method: String, params: JSONObject, onResult: (String) -> Unit) {
        val client = node ?: return
        scope.launch {
            val result = runCatching { client.request(method, params) }
            result.onSuccess { onResult(it.toString()) }
        }
    }
}
