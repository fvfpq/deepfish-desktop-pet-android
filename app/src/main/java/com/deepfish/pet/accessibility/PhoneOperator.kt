package com.deepfish.pet.accessibility

import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.deepfish.pet.Prefs
import com.deepfish.pet.ApiKeyStore
import com.deepfish.pet.chat.ChatManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * 根据自然语言指令操作手机的闭环：
 * 1. observe() 抓取当前屏幕无障碍快照
 * 2. 把快照压缩成文本喂给 LLM，让模型返回动作列表（JSON）
 * 3. 逐条通过 AccessibilityActionExecutor 执行
 *
 * 执行层代码来自 openclaw（MIT License，见同目录 LICENSE.openclaw）。
 */
class PhoneOperator(private val context: Context) {

    private val executor by lazy { AccessibilityActionExecutor() }

    companion object {
        /** 每轮最多连续执行多少个动作，防止失控 */
        private const val MAX_ACTIONS_PER_TURN = 10
        /** 单条指令最多轮次（observe→act→再 observe），防止死循环 */
        private const val MAX_TURNS = 4
    }

    val isServiceConnected: Boolean
        get() = OpenClawAccessibilityService.isConnected.value

    fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    /** 返回操作过程中的用户可读日志行。必须从协程中调用。 */
    suspend fun run(instruction: String, onLog: (String) -> Unit): List<String> =
        withContext(Dispatchers.Default) { runInner(instruction, onLog) }

    private suspend fun runInner(instruction: String, onLog: (String) -> Unit): List<String> {
        val logs = mutableListOf<String>()
        if (!isServiceConnected) {
            logs += "无障碍服务未开启，无法操作手机"
            return logs
        }

            val settings = Prefs.settings(context)
            val apiKey = ApiKeyStore.decrypt(context)
            val llm = ChatManager(settings, apiKey)

            var turns = 0
            var finished = false
            while (turns < MAX_TURNS && !finished) {
                turns++
                val snapshot = observeOrNull(logs) ?: break

                onLog("—— 第 ${turns} 轮：已读取当前屏幕 ——")
                logs += "—— 第 ${turns} 轮：已读取当前屏幕 ——"
                val uiText = snapshotToPrompt(snapshot)
                if (uiText.isBlank()) {
                    logs += "没有检测到可操作的内容"
                    break
                }

                val actionsJson = try {
                    llm.sendWithPrompt(SYSTEM_PROMPT, "用户指令：$instruction\n\n当前屏幕：\n$uiText")
                } catch (e: Exception) {
                    logs += "模型调用失败：${e.message}"
                    break
                }

                val actions = parseActions(actionsJson, logs)
                if (actions.isEmpty()) {
                    logs += "模型没有给出可执行的动作，已结束"
                    finished = true
                    break
                }

                val snapshotId = snapshot.id
                for (action in actions) {
                    when (action) {
                        is MobileUiAction.Wait -> {
                            if (action.ms > 0) {
                                kotlinx.coroutines.delay(action.ms)
                                logs += "等待 ${action.ms}ms"
                            }
                        }
                        is MobileUiAction.GlobalAction -> {
                            val ok = execute(snapshotId, action, logs)
                            if (ok) logs += "已完成：${describe(action)}"
                            if (!ok || action.name == GlobalActionName.Home) {
                                finished = true
                            }
                            break
                        }
                        else -> {
                            val ok = execute(snapshotId, action, logs)
                            logs += if (ok) "已完成：${describe(action)}" else "执行失败：${describe(action)}"
                            if (!ok) {
                                finished = true
                                break
                            }
                        }
                    }
                }

                if (!finished) {
                    logs += "重新观察屏幕以确认结果…"
                }
            }
            if (!finished) logs += "已完成（到达最大轮次）"
            return logs
    }

    private fun observeOrNull(logs: MutableList<String>): MobileUiSnapshot? = try {
        executor.observe()
    } catch (e: Exception) {
        logs += "读取屏幕失败：${e.message}"
        null
    }

    private suspend fun execute(snapshotId: String, action: MobileUiAction, logs: MutableList<String>): Boolean {
        val result = executor.act(snapshotId, action)
        val ok = result.code == ActionOutcomeCode.Completed ||
            result.code == ActionOutcomeCode.AcceptedButUnverified
        if (!ok) logs += "执行返回 ${result.code.value}：${result.message ?: ""}"
        return ok
    }

    private fun describe(action: MobileUiAction): String = when (action) {
        is MobileUiAction.Activate -> "点击节点 ${action.ref}"
        is MobileUiAction.SetText -> "在 ${action.ref} 输入文字"
        is MobileUiAction.Scroll -> "滚动 ${action.direction}"
        is MobileUiAction.Tap -> "点击坐标 (${action.x}, ${action.y})"
        is MobileUiAction.Swipe -> "滑动 ${action.x1},${action.y1} → ${action.x2},${action.y2}"
        is MobileUiAction.GlobalAction -> "全局操作 ${action.name}"
        is MobileUiAction.Wait -> "等待 ${action.ms}ms"
    }

    private fun snapshotToPrompt(snapshot: MobileUiSnapshot): String = buildString {
        append("包名：${snapshot.packageName ?: "未知"}\n")
        append("标题：${snapshot.windowTitle ?: "未知"}\n")
        for (n in snapshot.nodes.take(120)) {
            append(
                "[${n.ref}] ${n.role} 文本=\"${n.text ?: ""}\" 描述=\"${n.contentDescription ?: ""}\" " +
                    "区域=(${n.boundsInScreen.left},${n.boundsInScreen.top},${n.boundsInScreen.right},${n.boundsInScreen.bottom}) " +
                    "可点击=${n.clickable} 可编辑=${n.editable} 可滚动=${n.scrollable}\n"
            )
        }
    }

    private fun parseActions(json: String, logs: MutableList<String>): List<MobileUiAction> {
        val text = json.trim()
        // 模型可能返回 ```json 代码块
        val cleaned = text.substringAfter("```json", text)
            .substringAfter("```", text)
            .substringBefore("```")
            .trim()
        return try {
            val root = if (cleaned.startsWith("[")) JSONArray(cleaned) else {
                val obj = JSONObject(cleaned)
                obj.optJSONArray("actions") ?: JSONArray().apply { put(obj) }
            }
            buildList {
                for (i in 0 until minOf(root.length(), MAX_ACTIONS_PER_TURN)) {
                    val a = root.getJSONObject(i)
                    parseAction(a)?.let(::add)
                }
            }
        } catch (e: Exception) {
            logs += "解析模型动作失败：${e.message}"
            emptyList()
        }
    }

    private fun parseAction(obj: JSONObject): MobileUiAction? {
        val type = obj.optString("type", "").lowercase()
        return when (type) {
            "tap" -> MobileUiAction.Tap(obj.optInt("x"), obj.optInt("y"))
            "activate", "click" -> MobileUiAction.Activate(obj.optString("ref"))
            "set_text", "input" -> MobileUiAction.SetText(obj.optString("ref"), obj.optString("text"))
            "scroll" -> MobileUiAction.Scroll(
                ref = obj.optString("ref"),
                direction = if (obj.optString("direction", "forward").lowercase() == "backward") {
                    ScrollDirection.Backward
                } else {
                    ScrollDirection.Forward
                }
            )
            "swipe" -> MobileUiAction.Swipe(
                obj.optInt("x1"), obj.optInt("y1"),
                obj.optInt("x2"), obj.optInt("y2"),
                obj.optLong("durationMs", 300L)
            )
            "global_action" -> MobileUiAction.GlobalAction(
                when (obj.optString("name", "").lowercase()) {
                    "home" -> GlobalActionName.Home
                    "back" -> GlobalActionName.Back
                    "recents" -> GlobalActionName.Recents
                    "notifications" -> GlobalActionName.Notifications
                    else -> GlobalActionName.Home
                }
            )
            "wait" -> MobileUiAction.Wait(obj.optLong("ms", 500L))
            "done", "finish", "observe" -> MobileUiAction.Wait(0)
            else -> null
        }
    }

    private val SYSTEM_PROMPT = """
        你是手机自动操作助手。用户给出自然语言指令，你需要根据当前屏幕的无障碍节点快照，输出一串 JSON 动作来完成任务。
        节点格式：[ref] role 文本= 描述= 区域=(left,top,right,bottom) 可点击= 可编辑= 可滚动=

        可用动作（输出 JSON 数组）：
        - {"type":"activate","ref":"n12"}          点击某个节点
        - {"type":"set_text","ref":"n5","text":"hello"}  在输入框输入文字
        - {"type":"scroll","ref":"n3","direction":"forward|backward"}  滚动
        - {"type":"tap","x":100,"y":200}           按坐标点击（节点不便定位时用）
        - {"type":"swipe","x1":0,"y1":500,"x2":0,"y2":200,"durationMs":300}  滑动
        - {"type":"global_action","name":"home|back|recents|notifications"}  系统操作
        - {"type":"wait","ms":800}                 等待界面变化
        - {"type":"done","message":"完成说明"}      任务完成

        规则：
        1. 优先用 ref 定位节点，只有无法定位时才用坐标。
        2. 输入框设文本用 set_text，不要尝试点击键盘。
        3. 密码框等敏感内容要拒绝，直接返回 done 并说明无法操作。
        4. 一次只输出一个 JSON 数组，不要输出其他文字。
        5. 如果界面没有可执行动作或目标不存在，返回 {"type":"done","message":"说明原因"}。
    """.trimIndent()
}
