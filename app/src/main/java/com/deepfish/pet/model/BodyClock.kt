package com.deepfish.pet.model

import java.util.Calendar

enum class BodyPhase(val id: String, val label: String, val energy: Double, val line: String) {
    SLEEP("sleep", "呼呼大睡", 0.16, "Zzz... 模型也要休息。"),
    MORNING("morning", "刚刚醒来", 0.58, "早上好，先伸个懒腰。"),
    FOCUS("focus", "专注营业", 0.92, "今天也要认真回答问题。"),
    LUNCH("lunch", "寻找白饭", 0.64, "蓝色大肥鱼也要吃午饭。"),
    AFTERNOON("afternoon", "下午摸鱼", 0.76, "让我先假装思考三秒。"),
    EVENING("evening", "陪你加班", 0.72, "夜色很好，适合聊点什么。"),
    SLEEPY("sleep", "开始犯困", 0.22, "很晚啦，答案明天也不会跑。")
}

object BodyClock {

    private val phases = listOf(
        BodyPhase.SLEEP to 0..6,
        BodyPhase.MORNING to 6..9,
        BodyPhase.FOCUS to 9..12,
        BodyPhase.LUNCH to 12..14,
        BodyPhase.AFTERNOON to 14..18,
        BodyPhase.EVENING to 18..23,
        BodyPhase.SLEEPY to 23..24
    )

    private fun currentHour(): Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

    fun getPhase(): BodyPhase {
        val hour = currentHour()
        return phases.firstOrNull { hour >= it.second.first && hour < it.second.last }?.first ?: BodyPhase.SLEEP
    }

    fun msUntilNextMinute(): Long {
        val now = Calendar.getInstance()
        return 60000L - (now.get(Calendar.SECOND) * 1000L + now.get(Calendar.MILLISECOND))
    }
}
