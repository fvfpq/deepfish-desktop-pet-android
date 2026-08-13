package com.deepfish.pet.chat

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.deepfish.pet.ApiKeyStore
import com.deepfish.pet.Prefs
import com.deepfish.pet.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 聊天悬浮窗：透明背景、可拖动，不跳转 Activity。
 * 由 PetService 管理，双击桌宠或点击通知切换显示。
 */
class ChatOverlay(private val context: Context) {

    private var windowManager: WindowManager? = null
    private var root: View? = null
    private var params: WindowManager.LayoutParams? = null
    private var visible = false

    private lateinit var chatLog: LinearLayout
    private lateinit var input: EditText
    private lateinit var chatScroll: ScrollView
    private val messages = mutableListOf<ChatMessage>()

    val isVisible: Boolean get() = visible

    fun show() {
        if (visible) return
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.chat_overlay, null)

        chatLog = view.findViewById(R.id.chat_log)
        input = view.findViewById(R.id.chat_input)
        chatScroll = view.findViewById(R.id.chat_scroll)
        view.findViewById<TextView>(R.id.provider_label).text = providerLabel()
        view.findViewById<TextView>(R.id.chat_close).setOnClickListener { hide() }
        view.findViewById<Button>(R.id.chat_send).setOnClickListener { send() }
        setupDrag(view.findViewById(R.id.chat_drag_handle), view)
        setupResize(view.findViewById(R.id.chat_resize_handle))
        setupInputFocus(view)

        if (chatLog.childCount == 0) {
            appendMessage("assistant", "我在呢。想聊什么？")
        }

        val p = WindowManager.LayoutParams(
            dp(320),
            dp(440),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(40)
            y = dp(120)
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }

        wm.addView(view, p)
        windowManager = wm
        root = view
        params = p
        visible = true
    }

    fun hide() {
        if (!visible) return
        root?.let { windowManager?.removeView(it) }
        root = null
        windowManager = null
        params = null
        visible = false
    }

    fun toggle() = if (visible) hide() else show()

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDrag(handle: View, view: View) {
        var downX = 0f
        var downY = 0f
        var startX = 0f
        var startY = 0f
        handle.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = params?.x?.toFloat() ?: 0f
                    startY = params?.y?.toFloat() ?: 0f
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    val p = params
                    val v = root
                    if (p != null && v != null) {
                        p.x = (startX + dx).toInt()
                        p.y = (startY + dy).toInt()
                        windowManager?.updateViewLayout(v, p)
                    }
                    true
                }
                else -> false
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupResize(handle: View) {
        var downX = 0f
        var downY = 0f
        var startW = 0
        var startH = 0
        handle.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startW = params?.width ?: 0
                    startH = params?.height ?: 0
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    val p = params
                    val v = root
                    if (p != null && v != null) {
                        p.width = (startW + dx).toInt().coerceAtLeast(dp(220))
                        p.height = (startH + dy).toInt().coerceAtLeast(dp(300))
                        windowManager?.updateViewLayout(v, p)
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun setupInputFocus(view: View) {
        input.setOnFocusChangeListener { _, hasFocus ->
            val p = params ?: return@setOnFocusChangeListener
            if (hasFocus) {
                p.flags = p.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            } else {
                p.flags = p.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            }
            windowManager?.updateViewLayout(view, p)
        }
        input.setOnClickListener {
            val p = params ?: return@setOnClickListener
            p.flags = p.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            windowManager?.updateViewLayout(view, p)
            input.requestFocus()
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(input, 0)
        }
    }

    private fun providerLabel(): String {
        return when (Prefs.settings(context).provider) {
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
        val sendBtn = root?.findViewById<Button>(R.id.chat_send)
        sendBtn?.isEnabled = false

        kotlinx.coroutines.CoroutineScope(Dispatchers.Main).launch {
            try {
                val settings = Prefs.settings(context)
                val key = ApiKeyStore.decrypt(context)
                val reply = ChatManager(settings, key).send(messages.toList())
                pending.text = reply
                messages += ChatMessage("assistant", reply)
            } catch (e: Exception) {
                pending.text = "没连上模型：${e.message}"
                pending.setBackgroundColor(0xFFFFF0F0.toInt())
                pending.setTextColor(0xFFA42D35.toInt())
            } finally {
                sendBtn?.isEnabled = true
            }
        }
    }

    private fun appendMessage(role: String, content: String): TextView {
        val node = TextView(context)
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
            width = dp(250)
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
        chatLog.post { chatScroll.fullScroll(View.FOCUS_DOWN) }
        return node
    }

    private fun dp(v: Int): Int = (v * context.resources.displayMetrics.density).toInt()
}
