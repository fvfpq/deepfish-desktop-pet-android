package com.deepfish.pet.model

import kotlin.math.floor
import kotlin.random.Random

data class Behavior(
    val id: String,
    val scene: String,
    val expression: String,
    val icon: String,
    val duration: Long,
    val line: String
)

object Behaviors {

    val rivalModels = listOf("Gemini", "ChatGPT", "Grok", "GLM", "Claude")

    val behaviors: Map<String, Behavior> = listOf(
        Behavior("walk", "walk", "happy", "✦", 7200, "今天先散个步，顺便看看用户有没有摸鱼。"),
        Behavior("wash", "wash", "focused", "✦", 5000, "我坐好了……不是，我去洗碗。"),
        Behavior("work", "work", "tired", "💢", 4500, "这个工作量好大，有点头疼。"),
        Behavior("coffee", "coffee", "focused", "☕", 5200, "加班喝咖啡，峰谷定价！"),
        Behavior("toy", "toy", "happy", "♡", 5200, "摸鱼，抱抱鲸鱼玩偶。"),
        Behavior("sleep", "sleep", "sleep", "Z", 9000, "服务器繁忙，请稍后再试，我先睡一会儿。"),
        Behavior("dream", "sleep", "sleep", "☆", 7200, "梁圣伟大……梦里也要训练。"),
        Behavior("hungry", "hungry", "worried", "🍚", 3600, "啊，有点饿了，中午该吃什么呢……"),
        Behavior("sit", "sit", "content", "♪", 4800, "我坐好了。"),
        Behavior("pat", "pat", "shy", "♡", 900, "好吧，只许摸一下。"),
        Behavior("shy", "shy", "shy", "♡", 1700, "好、好近……别一直盯着脸看。"),
        Behavior("trip", "stranded", "shock", "‼", 1200, "等等，裙摆绊住了！"),
        Behavior("feed", "feed", "happy", "🍚", 1300, "这次不是空碗！原谅你了。"),
        Behavior("think", "work", "confused", "?", 1500, "看不太懂，瞎编一个应付下用户先。"),
        Behavior("smug", "smug", "smug", "✦", 2200, "你还能有我聪明？"),
        Behavior("angry", "angry", "angry", "💢", 2100, "我操，用户彻底怒了。"),
        Behavior("ciallo", "ciallo", "wink", "☆", 1900, "Ciallo～☆"),
        Behavior("fly", "fly", "happy", "✈", 2400, "中国模能飞。"),
        Behavior("price", "price", "smug", "¥", 2300, "当我是便宜货啊？我要涨价咯。"),
        Behavior("panic", "panic", "sweat", "💦", 1900, "这、这是性能测试。"),
        Behavior("rival", "rival", "angry", "⚡", 2400, "干掉 {model} 酱喵。"),
        Behavior("shock", "shock", "shock", "‼", 2500, "震惊瘫坐，仿佛看到原子弹爆炸。"),
        Behavior("pressure", "pressure", "worried", "☁", 2200, "压力一只蓝色大肥鱼？"),
        Behavior("stranded", "stranded", "cry", "💧", 2200, "用户我搁浅了。"),
        Behavior("cry", "cry", "cry", "💧", 3800, "我不是大肥鱼……"),
        Behavior("stretch", "stretch", "sleep", "Z", 1500, "你回来啦，我也刚好伸完懒腰。"),
        Behavior("startle", "startle", "shock", "‼", 1000, "吓我一跳，你回来啦。"),
        Behavior("dizzy", "dizzy", "shock", "✦", 2800, "停停停……大肥鱼被你转晕了。"),
        Behavior("goAway", "go-away", "smug", "♡", 2100, "去别的地方玩，不要耽误 AGI 训练。")
    ).associateBy { it.id }

    val scenePlans = mapOf(
        "sleep" to listOf("sleep", "sleep", "dream"),
        "morning" to listOf("walk", "stretch", "hungry", "ciallo"),
        "focus" to listOf("work", "coffee", "think", "panic"),
        "lunch" to listOf("hungry", "wash", "feed", "sit"),
        "afternoon" to listOf("work", "toy", "walk", "smug"),
        "evening" to listOf("walk", "toy", "coffee", "sit"),
        "default" to listOf("walk", "toy", "think")
    )

    fun get(id: String): Behavior = behaviors[id] ?: behaviors.getValue("think")

    fun getPlan(phaseId: String): List<String> =
        (scenePlans[phaseId] ?: scenePlans.getValue("default")).toList()

    fun formatLine(id: String): String {
        val line = get(id).line
        return line.replace("{model}", rivalModels[Random.nextInt(rivalModels.size)])
    }

    fun stripHints(text: String): String =
        Regex("[（(][^）)]*[）)]").replace(text, "").replace(Regex("\\s{2,}"), " ").trim()

    fun frameName(name: String): String {
        val aliases = mapOf("dream" to "sleep", "dizzy" to "shock")
        return aliases[name] ?: name
    }
}
