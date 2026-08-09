package com.deepfish.pet.chat

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.deepfish.pet.ApiKeyStore
import com.deepfish.pet.Prefs
import com.deepfish.pet.R
import kotlinx.coroutines.launch

class ChatActivity : AppCompatActivity() {

    private lateinit var chatLog: LinearLayout
    private lateinit var input: EditText
    private val messages = mutableListOf<ChatMessage>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        chatLog = findViewById(R.id.chat_log)
        input = findViewById(R.id.chat_input)
        findViewById<Button>(R.id.chat_close).setOnClickListener { finish() }
        findViewById<Button>(R.id.chat_send).setOnClickListener { send() }

        findViewById<TextView>(R.id.provider_label).text = providerLabel()

        appendMessage("assistant", "我在呢。想聊什么？")
    }

    private fun providerLabel(): String {
        return when (Prefs.settings(this).provider) {
            "deepseek" -> "DeepSeek"
            "custom" -> "自定义模型"
            else -> "免费模型"
        }
    }

    private fun send() {
        val content = input.text.toString().trim()
        if (content.isEmpty()) return
        input.text.clear()
        messages += ChatMessage("user", content)
        appendMessage("user", content)

        val pending = appendMessage("assistant", "正在想...")
        findViewById<Button>(R.id.chat_send).isEnabled = false

        lifecycleScope.launch {
            try {
                val settings = Prefs.settings(this@ChatActivity)
                val key = ApiKeyStore.decrypt(this@ChatActivity)
                val reply = ChatManager(settings, key).send(messages.toList())
                pending.text = reply
                messages += ChatMessage("assistant", reply)
            } catch (e: Exception) {
                pending.text = "没连上模型：${e.message}"
                pending.setBackgroundColor(0xFFFFF0F0.toInt())
                pending.setTextColor(0xFFA42D35.toInt())
            } finally {
                findViewById<Button>(R.id.chat_send).isEnabled = true
            }
        }
    }

    private fun appendMessage(role: String, content: String): TextView {
        val node = TextView(this)
        node.text = content
        node.setTextIsSelectable(true)
        node.textSize = 14f
        node.setPadding(dp(12), dp(10), dp(12), dp(10))
        node.setLineSpacing(0f, 1.2f)

        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = dp(9)
            gravity = android.view.Gravity.START
            width = dp(260)
            if (role == "user") {
                gravity = android.view.Gravity.END
                node.setBackgroundResource(R.drawable.bubble_user)
                node.setTextColor(0xFFFFFFFF.toInt())
            } else {
                gravity = android.view.Gravity.START
                node.setBackgroundResource(R.drawable.bubble_assistant)
                node.setTextColor(0xFF142350.toInt())
            }
        }
        chatLog.addView(node, params)
        chatLog.post { (chatLog.parent as? android.widget.ScrollView)?.fullScroll(View.FOCUS_DOWN) }
        return node
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
