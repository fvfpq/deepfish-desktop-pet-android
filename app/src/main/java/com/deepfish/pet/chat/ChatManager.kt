package com.deepfish.pet.chat

import com.deepfish.pet.PetSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class ChatMessage(val role: String, val content: String)

class ChatManager(private val settings: PetSettings, private val apiKey: String) {

    private val systemPrompt = listOf(
        "你是桌面宠物大肥鱼，一只蓝色、聪明但偶尔装傻的二次元小鲸鱼女仆。",
        "用简短自然的中文回答，通常不超过 120 字。",
        "语气温柔、有一点机灵的吐槽，不要自称 DeepSeek 官方角色。",
        "遇到严肃问题时优先准确和有帮助，不要为了卖萌牺牲事实。"
    ).joinToString("\n")

    suspend fun send(messages: List<ChatMessage>): String = sendInternal(messages, systemPrompt, 400)

    /** 以自定义系统提示调用模型，用于非闲聊任务（如手机操作决策）。 */
    suspend fun sendWithPrompt(
        systemPrompt: String,
        userMessage: String,
        maxTokens: Int = 600,
    ): String = sendInternal(
        listOf(ChatMessage("user", userMessage)),
        systemPrompt,
        maxTokens,
    )

    private suspend fun sendInternal(
        messages: List<ChatMessage>,
        system: String,
        maxTokens: Int,
    ): String = withContext(Dispatchers.IO) {
        var endpoint = settings.endpoint.trim().trimEnd('/')
        if (!endpoint.startsWith("https://")) error("API 地址必须使用 HTTPS")
        if (!endpoint.contains("/chat/completions")) {
            endpoint = "$endpoint/chat/completions"
        }

        val recent = messages.takeLast(10)
        val body = JSONObject()
        body.put("model", settings.model)
        body.put("temperature", 0.8)
        body.put("max_tokens", maxTokens)
        body.put("messages", JSONArray().apply {
            put(JSONObject().put("role", "system").put("content", system))
            recent.forEach { m ->
                put(JSONObject().put("role", m.role).put("content", m.content))
            }
        })

        val conn = URL(endpoint).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 25000
            conn.readTimeout = 25000
            conn.setRequestProperty("Content-Type", "application/json")
            apiKey.takeIf { it.isNotBlank() }?.let {
                conn.setRequestProperty("Authorization", "Bearer $it")
            }
            conn.doOutput = true
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body.toString()) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()

            if (code !in 200..299) {
                val detail = try {
                    JSONObject(text).optJSONObject("error")?.optString("message")
                        ?: JSONObject(text).optString("message", "")
                } catch (_: Exception) { "" }
                error(if (detail.isNotBlank()) detail else "HTTP $code")
            }
            parseContent(text)
        } finally {
            conn.disconnect()
        }
    }

    private fun parseContent(json: String): String {
        val obj = JSONObject(json)
        val content = obj.optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
            ?.optString("content", "")
            ?.takeIf { it.isNotBlank() }
            ?: obj.optString("text", "").ifBlank { null }
        return content?.trim() ?: throw IllegalStateException("模型没有返回可显示的内容")
    }
}
