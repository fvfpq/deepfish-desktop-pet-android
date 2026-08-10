package com.deepfish.pet.gateway

import com.deepfish.pet.accessibility.AccessibilityActionExecutor
import com.deepfish.pet.accessibility.ActionOutcomeCode
import com.deepfish.pet.accessibility.GlobalActionName
import com.deepfish.pet.accessibility.MobileUiAction
import com.deepfish.pet.accessibility.MobileUiSnapshot
import com.deepfish.pet.accessibility.OpenClawAccessibilityService
import com.deepfish.pet.accessibility.ScrollDirection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * 把 node.invoke 的 mobile-ui 命令桥接到无障碍执行层。
 * 与官方 Android MobileUiHandler 输出一致：
 * observe 返回 {snapshotId,capturedAtMs,package,windowTitle,nodes}；
 * act 接受 {snapshotId,action:{type,...}} 并返回 {code,message}。
 */
class MobileUiInvokeHandler(
    private val executor: AccessibilityActionExecutor = AccessibilityActionExecutor(),
) {

    val isConnected: Boolean
        get() = OpenClawAccessibilityService.isConnected.value

    suspend fun handle(command: String, paramsJson: String?): GatewayNode.InvokeResult =
        withContext(Dispatchers.Default) {
            when (command) {
                "mobile-ui.observe" -> handleObserve()
                "mobile-ui.act" -> handleAct(paramsJson)
                else -> GatewayNode.InvokeResult.error("UNKNOWN_COMMAND", "unknown command: $command")
            }
        }

    private fun handleObserve(): GatewayNode.InvokeResult = try {
        val snapshot = executor.observe()
        GatewayNode.InvokeResult.ok(snapshotJson(snapshot).toString())
    } catch (e: Throwable) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        GatewayNode.InvokeResult.error("MOBILE_UI_OBSERVE_FAILED", e.message ?: "snapshot failed")
    }

    private suspend fun handleAct(paramsJson: String?): GatewayNode.InvokeResult {
        val request = parseActRequest(paramsJson)
            ?: return GatewayNode.InvokeResult.error(
                "INVALID_REQUEST",
                "expected {snapshotId,action:{type,...}}",
            )
        return try {
            val result = executor.act(request.snapshotId, request.action)
            GatewayNode.InvokeResult.ok(
                JSONObject().apply {
                    put("code", result.code.value)
                    put("message", result.message)
                }.toString(),
            )
        } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            GatewayNode.InvokeResult.error("MOBILE_UI_ACT_FAILED", e.message ?: "action failed")
        }
    }

    private fun snapshotJson(snapshot: MobileUiSnapshot): JSONObject = JSONObject().apply {
        put("snapshotId", snapshot.id)
        put("capturedAtMs", snapshot.capturedAtMs)
        put("package", snapshot.packageName ?: JSONObject.NULL)
        put("windowTitle", snapshot.windowTitle ?: JSONObject.NULL)
        put("nodes", JSONArray().apply {
            snapshot.nodes.forEach { node ->
                put(JSONObject().apply {
                    put("ref", node.ref)
                    put("parentRef", node.parentRef ?: JSONObject.NULL)
                    put("role", node.role)
                    put("text", node.text ?: JSONObject.NULL)
                    put("contentDescription", node.contentDescription ?: JSONObject.NULL)
                    put("viewId", node.viewId ?: JSONObject.NULL)
                    put("bounds", JSONArray().apply {
                        put(node.boundsInScreen.left)
                        put(node.boundsInScreen.top)
                        put(node.boundsInScreen.right)
                        put(node.boundsInScreen.bottom)
                    })
                    put("flags", JSONObject().apply {
                        put("clickable", node.clickable)
                        put("editable", node.editable)
                        put("scrollable", node.scrollable)
                        put("enabled", node.enabled)
                        put("focused", node.focused)
                    })
                    put("actions", JSONArray().apply {
                        node.actions.forEach { put(it) }
                    })
                })
            }
        })
    }

    private fun parseActRequest(paramsJson: String?): ActRequest? {
        val params = runCatching { paramsJson?.let { JSONObject(it) } }.getOrNull() ?: return null
        val snapshotId = params.optString("snapshotId").takeIf { it.isNotBlank() } ?: return null
        val actionObj = params.optJSONObject("action") ?: return null
        val type = actionObj.optString("type").takeIf { it.isNotBlank() } ?: return null
        val action = when (type) {
            "activate" -> MobileUiAction.Activate(actionObj.optString("ref").takeIf { it.isNotBlank() } ?: return null)
            "set_text" -> MobileUiAction.SetText(
                ref = actionObj.optString("ref").takeIf { it.isNotBlank() } ?: return null,
                text = actionObj.optString("text"),
            )
            "scroll" -> MobileUiAction.Scroll(
                ref = actionObj.optString("ref").takeIf { it.isNotBlank() } ?: return null,
                direction = when (actionObj.optString("direction")) {
                    "backward" -> ScrollDirection.Backward
                    else -> ScrollDirection.Forward
                },
            )
            "tap" -> MobileUiAction.Tap(actionObj.optInt("x"), actionObj.optInt("y"))
            "swipe" -> MobileUiAction.Swipe(
                x1 = actionObj.optInt("x1"),
                y1 = actionObj.optInt("y1"),
                x2 = actionObj.optInt("x2"),
                y2 = actionObj.optInt("y2"),
                durationMs = actionObj.optLong("durationMs", 300L),
            )
            "global_action" -> MobileUiAction.GlobalAction(
                when (actionObj.optString("name")) {
                    "back" -> GlobalActionName.Back
                    "home" -> GlobalActionName.Home
                    "recents" -> GlobalActionName.Recents
                    "notifications" -> GlobalActionName.Notifications
                    else -> return null
                },
            )
            "wait" -> MobileUiAction.Wait(actionObj.optLong("ms", 500L))
            else -> return null
        }
        return ActRequest(snapshotId, action)
    }

    private data class ActRequest(val snapshotId: String, val action: MobileUiAction)
}
