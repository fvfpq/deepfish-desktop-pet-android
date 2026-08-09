package com.deepfish.pet

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.AttributeSet
import android.util.LruCache
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.deepfish.pet.model.Behaviors
import com.deepfish.pet.model.BodyClock
import com.deepfish.pet.model.SpinTracker
import kotlin.math.abs
import kotlin.random.Random

interface PetWindowHost {
    fun moveWindow(dx: Int, dy: Int)
    fun walkStep(dx: Int, dy: Int): Boolean
    fun openChat()
    fun onTap()
}

class PetView(context: Context, attrs: AttributeSet? = null) :
    FrameLayout(context, attrs) {

    private val bitmapCache = object : LruCache<String, Bitmap>(32 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int =
            value.byteCount
    }

    private val character: ImageView = ImageView(context)
    private val baseContainer: FrameLayout = FrameLayout(context)
    private val speech: TextView = TextView(context)
    private val effectLayer: FrameLayout = FrameLayout(context)
    private val speechTail: View = View(context)
    private var speechParams: LayoutParams? = null
    private var tailParams: LayoutParams? = null
    private val charTopInset = 76

    private var currentFrame = "neutral"
    private var walkDirection = -1
    private var walkPending = false
    private var lastClickAt = 0L
    private var tripCount = 0

    var host: PetWindowHost? = null
    var freeWalk = true
    var interactiveZones = true
    var spicyLines = true
    var behaviorIntensity = 1f
    var chatVisible = false
        set(value) {
            field = value
            if (value) stopWalk()
        }

    private val clickLines = listOf(
        "我有在认真营业。", "好的，用户又发癫了。", "你是不是想摸摸头？", "再点就要收费啦。"
    )

    private val signaturePool = listOf(
        "ciallo", "smug", "pressure", "shock", "fly", "price", "rival", "goAway", "stranded"
    )

    private val behaviorLabels = mapOf(
        "walk" to "散步中", "wash" to "洗碗中", "work" to "认真加班", "coffee" to "咖啡时间",
        "toy" to "摸鱼中", "sleep" to "睡觉中", "dream" to "做梦中", "hungry" to "肚子咕咕叫",
        "sit" to "坐好了", "pat" to "摸摸头", "feed" to "投喂成功", "shy" to "害羞",
        "trip" to "绊倒了", "cry" to "委屈巴巴", "think" to "深度思考", "smug" to "自信",
        "angry" to "用户怒了", "ciallo" to "随机卖萌", "fly" to "起飞", "price" to "准备涨价",
        "panic" to "慌乱", "rival" to "双枪模式", "shock" to "震惊", "pressure" to "压力测试",
        "stranded" to "搁浅了", "stretch" to "欢迎回来", "startle" to "惊醒", "dizzy" to "转晕了",
        "goAway" to "AGI 训练中"
    )

    init {
        setBackgroundColor(Color.TRANSPARENT)
        character.scaleType = ImageView.ScaleType.FIT_CENTER
        character.contentDescription = "蓝色大肥鱼桌宠"
        val characterParams = LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        )
        characterParams.topMargin = dp(charTopInset)
        baseContainer.layoutParams = characterParams
        baseContainer.addView(
            character,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
        addView(baseContainer)

        speech.setTextColor(Color.rgb(51, 65, 92))
        speech.setBackgroundResource(R.drawable.bubble_pet_bg)
        speech.textSize = 14f
        speech.setPadding(dp(14), dp(10), dp(14), dp(10))
        speech.setLineSpacing(0f, 1.3f)
        speech.alpha = 0f
        speech.setOnClickListener { dismissSpeech() }
        speechParams = LayoutParams(dp(250), LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
        addView(speech, speechParams)

        speechTail.background = drawableOf(R.drawable.bubble_pet_tail)
        speechTail.alpha = 0f
        tailParams = LayoutParams(dp(22), dp(12), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
        addView(speechTail, tailParams)

        addView(
            effectLayer,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
        setFrame("neutral")
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        applyClock()
        scheduleIdle()
        scheduleBlink()
        startBreathing()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val charHeight = h - dp(charTopInset)
        val tailH = dp(12)
        speechParams?.let {
            it.bottomMargin = charHeight + dp(6)
            speech.layoutParams = it
        }
        tailParams?.let {
            it.bottomMargin = charHeight - (tailH / 2) + dp(6)
            speechTail.layoutParams = it
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun drawableOf(res: Int) = androidx.core.content.ContextCompat.getDrawable(context, res)

    private fun dismissSpeech() {
        removeCallbacks(speechTimer)
        speech.animate().alpha(0f).setDuration(180).start()
        speechTail.animate().alpha(0f).setDuration(180).start()
    }

    private fun isSleepPhase(): Boolean = BodyClock.getPhase().id == "sleep"

    // ---- Frame control (ported from app.js) ----

    private fun restingFrame(): String =
        if (isSleepPhase() && !chatVisible) "sleep" else "neutral"

    private fun setFrame(name: String) {
        val resolved = Behaviors.frameName(name)
        currentFrame = resolved
        var bmp = bitmapCache.get(resolved)
        if (bmp == null) {
            try {
                context.assets.open("frames/frame-$resolved.png").use {
                    BitmapFactory.decodeStream(it)
                }?.also { bmp = it; bitmapCache.put(resolved, it) }
            } catch (_: Throwable) {
                bmp = null
            }
        }
        character.setImageBitmap(bmp)
        val poseScale = poseVisualScale(resolved)
        baseContainer.scaleX = poseScale
        baseContainer.scaleY = poseScale
        baseContainer.pivotX = baseContainer.width / 2f
        baseContainer.pivotY = baseContainer.height * 0.89f
    }

    private fun poseVisualScale(frame: String): Float = when (frame) {
        "curious" -> 1.05f
        "shy", "wave", "ciallo", "goAway" -> 0.89f
        "happy", "excited", "price", "toy", "pat", "feed", "smug" -> 0.92f
        "surprised", "shock", "panic", "trip", "stranded", "startle", "angry", "rival", "pressure" -> 0.91f
        "sleepy", "sleep", "stretch" -> 0.91f
        "jump", "fly" -> 0.84f
        "walk", "walk-b" -> 0.92f
        "work", "wash", "coffee", "hungry", "think" -> 0.91f
        else -> 1f
    }

    private fun stopFramePlayback(reset: Boolean = true) {
        removeCallbacks(frameTimer)
        removeCallbacks(frameLoopTimer)
        frameTimer = null
        frameLoopTimer = null
        if (reset) setFrame(restingFrame())
    }

    private fun stopActionAnimation() {
        removeCallbacks(shakeTimer)
        removeCallbacks(shakeEndTimer)
        shakeTimer = null
        shakeEndTimer = null
        character.animate().rotation(0f).translationX(0f).setDuration(120).start()
    }

    private fun playFrame(name: String, duration: Long) {
        stopFramePlayback(false)
        setFrame(name)
        frameTimer = Runnable { frameTimer = null; setFrame(restingFrame()) }
        postDelayed(frameTimer, duration)
    }

    private fun startFrameLoop(names: Array<String>, intervals: IntArray? = null) {
        stopFramePlayback(false)
        var index = 0
        frameLoopTimer = object : Runnable {
            override fun run() {
                setFrame(names[index])
                val duration = intervals?.get(index % intervals.size) ?: 220
                index = (index + 1) % names.size
                if (frameLoopTimer === this) postDelayed(this, duration.toLong())
            }
        }
        frameLoopTimer!!.run()
    }

    private fun playBehaviorFrames(name: String, duration: Long) {
        if (name != "ciallo") {
            playFrame(name, duration)
            return
        }
        startFrameLoop(arrayOf("ciallo", "ciallo-b"), intArrayOf(420, 420))
        frameTimer = Runnable { stopFramePlayback() }
        postDelayed(frameTimer, duration)
    }

    // ---- Speech bubble ----

    fun say(text: String, label: String? = null, duration: Long = 4200) {
        val phase = BodyClock.getPhase()
        speech.text = "${label ?: phase.label}\n${Behaviors.stripHints(text)}"
        speech.alpha = 0f
        speechTail.alpha = 0f
        speech.animate().alpha(1f).setDuration(180).start()
        speechTail.animate().alpha(1f).setDuration(180).start()
        removeCallbacks(speechTimer)
        speechTimer = Runnable { dismissSpeech() }
        postDelayed(speechTimer, duration)
    }

    // ---- Actions & animation ----

    private fun playActionAnimation(action: String, duration: Long) {
        when (action) {
            "walk" -> startWalkBob(duration)
            "pat" -> playSequence(listOf(-4f, 5f, 0f), listOf(0f, 0f, 0f), listOf(200L, 220L, 200L))
            "feed" -> playSequence(listOf(2f, -2f, 0f), listOf(-3f, 3f, 0f), listOf(225L, 225L, 225L))
            "stretch" -> playSequence(listOf(12f, -14f, 0f), listOf(0f, 0f, 0f), listOf(duration / 3, duration / 3, duration / 3))
            "startle" -> playSequence(listOf(-22f, 0f, 0f), listOf(-4f, 4f, 0f), listOf(duration / 3, duration / 3, duration / 3))
            "think" -> playSequence(listOf(0f, 0f, 0f), listOf(-4f, 3f, 0f), listOf(duration / 3, duration / 3, duration / 3))
            "wash", "work", "coffee", "toy" -> startSwayLoop(duration, when (action) {
                "wash" -> 3f; "coffee" -> 4f; "toy" -> 2f; else -> 2f
            }, when (action) {
                "wash" -> 500L; "work" -> 850L; "coffee" -> 700L; else -> 650L
            })
            "sit" -> playSequence(listOf(28f, 28f, 28f), listOf(0f, 0f, 0f), listOf(duration / 3, duration / 3, duration / 3))
            "trip", "stranded" -> playSequence(listOf(0f, 8f, 38f), listOf(0f, 14f, 82f), listOf(duration / 3, duration / 3, duration / 3))
            "cry" -> playSequence(listOf(10f, 12f, 10f), listOf(0f, 0f, 0f), listOf(300L, 300L, 300L))
            "shock", "panic" -> startShakeLoop(duration, 180L, 4f)
            "angry", "rival" -> startShakeLoop(duration, 300L, 3f)
            "dizzy" -> startShakeLoop(duration, 460L, 7f)
            "fly" -> playSequence(listOf(-45f, 0f), listOf(8f, 0f), listOf(duration / 2, duration / 2))
            "ciallo" -> startSwayLoop(duration, 8f, 450L)
            "sleep" -> playSequence(listOf(7f, 7f, 7f), listOf(5f, 5f, 5f), listOf(duration / 3, duration / 3, duration / 3))
            else -> Unit
        }
    }

    private fun playSequence(ys: List<Float>, rots: List<Float>, durations: List<Long>) {
        character.animate().cancel()
        var elapsed = 0L
        for (i in ys.indices) {
            val y = ys[i]
            val rot = rots.getOrElse(i) { 0f }
            character.animate().translationY(y).rotation(rot).setDuration(durations[i])
                .setStartDelay(elapsed).start()
            elapsed += durations[i]
        }
    }

    private fun startSwayLoop(duration: Long, deg: Float, cycle: Long) {
        character.animate().cancel()
        removeCallbacks(shakeTimer)
        removeCallbacks(shakeEndTimer)
        var toggle = true
        shakeTimer = object : Runnable {
            override fun run() {
                toggle = !toggle
                character.animate().rotation(if (toggle) deg else -deg).setDuration(cycle).start()
                if (shakeTimer === this) postDelayed(this, cycle)
            }
        }
        shakeTimer!!.run()
        shakeEndTimer = Runnable { shakeTimer = null; character.animate().rotation(0f).setDuration(150).start() }
        postDelayed(shakeEndTimer, duration)
    }

    private fun startShakeLoop(duration: Long, cycle: Long, depth: Float) {
        character.animate().cancel()
        removeCallbacks(shakeTimer)
        removeCallbacks(shakeEndTimer)
        var toggle = true
        shakeTimer = object : Runnable {
            override fun run() {
                toggle = !toggle
                character.animate().translationX(if (toggle) depth else -depth).setDuration(cycle).start()
                if (shakeTimer === this) postDelayed(this, cycle)
            }
        }
        shakeTimer!!.run()
        shakeEndTimer = Runnable { shakeTimer = null; character.animate().translationX(0f).setDuration(150).start() }
        postDelayed(shakeEndTimer, duration)
    }

    private fun startWalkBob(@Suppress("UNUSED_PARAMETER") duration: Long) {
        character.animate().cancel()
        var toggle = true
        frameTimer = object : Runnable {
            override fun run() {
                toggle = !toggle
                character.animate().translationY(if (toggle) -3f else 0f).rotation(if (toggle) 0.7f else -0.6f)
                    .setDuration(340).start()
                if (frameTimer === this) postDelayed(this, 340)
            }
        }
        frameTimer!!.run()
    }

    private fun startBreathing() {
        character.animate().cancel()
        breatheTimer = object : Runnable {
            override fun run() {
                val phase = BodyClock.getPhase()
                val scale = if (phase.id == "sleep" && !chatVisible) 0.88f else 1f
                character.animate().translationY(-3f).scaleX(scale * 1.008f).scaleY(scale * 1.008f)
                    .setDuration(1800).start()
                character.animate().translationY(0f).scaleX(scale).scaleY(scale)
                    .setDuration(1800).setStartDelay(1800).withEndAction {
                        if (breatheTimer !== null) post(breatheTimer!!)
                    }.start()
            }
        }
        breatheTimer!!.run()
    }

    // ---- Walk (ported from app.js startWalk) ----

    fun startWalk(duration: Long = 7200, announce: Boolean = true) {
        stopWalk(false)
        walkDirection = if (Random.nextBoolean()) 1 else -1
        if (announce) say(Behaviors.formatLine("walk"), "散步中", 3400)
        startFrameLoop(arrayOf("walk", "walk-b", "walk-b", "walk"), intArrayOf(165, 235, 305, 190))
        startWalkBob(duration)

        walkTimer = object : Runnable {
            override fun run() {
                if (walkPending || chatVisible || dragState != null) {
                    if (walkTimer === this) postDelayed(this, 48)
                    return
                }
                walkPending = true
                val boundary = host?.walkStep(walkDirection * 3, 0) ?: true
                if (boundary) walkDirection *= -1
                walkPending = false
                if (walkTimer === this) postDelayed(this, 48)
            }
        }
        postDelayed(walkTimer, 48)
        walkStopTimer = Runnable { stopWalk() }
        postDelayed(walkStopTimer, duration)
    }

    fun stopWalk(clearScene: Boolean = true) {
        removeCallbacks(walkTimer)
        removeCallbacks(walkStopTimer)
        walkTimer = null
        walkStopTimer = null
        stopFramePlayback()
        stopActionAnimation()
        character.animate().translationY(0f).rotation(0f).scaleX(1f).scaleY(1f).setDuration(120).start()
        if (clearScene) setFrame(restingFrame())
    }

    // ---- Behavior runner (ported from app.js runBehavior) ----

    data class SayOptions(val say: Boolean = true, val label: String? = null)

    fun runBehavior(id: String, options: SayOptions = SayOptions()) {
        val behavior = Behaviors.get(id)
        if (id == "walk") {
            if (freeWalk) startWalk(behavior.duration, options.say)
            return
        }
        stopWalk(false)
        playActionAnimation(id, behavior.duration)
        playBehaviorFrames(id, behavior.duration)
        if (id == "dizzy") dizzyStars(behavior.duration)
        particles(behavior.icon, if (id in listOf("cry", "shock", "rival")) 6 else 3)
        if (options.say) {
            say(Behaviors.formatLine(id), options.label ?: behaviorLabels[id], minOf(5200L, behavior.duration + 1800))
        }
    }

    // ---- Particles ----

    fun particles(icon: String, count: Int = 5) {
        for (i in 0 until count) {
            val node = TextView(context)
            node.text = icon
            node.textSize = 21f
            node.alpha = 0f
            val params = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
            params.bottomMargin = dp(74)
            effectLayer.addView(node, params)
            val driftX = Random.nextFloat() * 160f - 80f
            val turn = Random.nextFloat() * 160f - 80f
            node.translationX = Random.nextFloat() * 24f - 12f
            node.animate().translationY(-90f).translationX(node.translationX + driftX)
                .rotation(turn).alpha(1f).setStartDelay((i * 60).toLong())
                .setDuration(900).withEndAction {
                    try {
                        node.animate().alpha(0f).setDuration(100).withEndAction {
                            removeIfAttached(node)
                        }.start()
                    } catch (_: Exception) {
                        removeIfAttached(node)
                    }
                }.start()
        }
    }

    private fun removeIfAttached(child: View) {
        if (child.parent === effectLayer) effectLayer.removeView(child)
    }

    fun dizzyStars(duration: Long) {
        try {
            val container = FrameLayout(context)
            container.layoutParams = LayoutParams(dp(150), dp(42), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
            container.translationY = -dp(260).toFloat()
            effectLayer.addView(container)
            val stars = arrayOf("★", "✦", "★", "✦", "★")
            for (i in stars.indices) {
                val star = TextView(context)
                star.text = stars[i]
                star.textSize = 20f
                star.setTextColor(Color.rgb(242, 187, 56))
                val params = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER)
                container.addView(star, params)
                val delay = (i * -180L).coerceAtLeast(0L)
                star.animate().translationX(-72f).setDuration(225).setStartDelay(delay).start()
                star.animate().translationX(72f).setDuration(225).setStartDelay(delay + 225).start()
                star.animate().translationX(0f).setDuration(225).setStartDelay(delay + 450).start()
            }
            container.postDelayed({ removeIfAttached(container) }, duration)
        } catch (_: Exception) {
            // 动画叠加极端场景下静默兜底，避免闪退
        }
    }

    // ---- Idle scheduling (ported from app.js) ----

    private fun nextIdleBehavior(): String {
        val phase = BodyClock.getPhase()
        val pool = Behaviors.getPlan(phase.id).toMutableList()
        if (phase.id != "sleep" && Math.random() < 0.38) pool.addAll(signaturePool)
        if (!freeWalk) pool.removeAll { it == "walk" }
        if (!spicyLines) pool.removeAll { it in listOf("angry", "rival", "goAway") }
        return pool.ifEmpty { listOf("think") }.random()
    }

    private fun scheduleIdle() {
        removeCallbacks(idleTimer)
        val phase = BodyClock.getPhase()
        val intensity = behaviorIntensity.coerceIn(0.5f, 1.8f)
        val delay = ((15 + Math.random() * 25) * 1000 / kotlin.math.max(phase.energy * intensity, 0.3)).toLong()
        idleTimer = Runnable {
            if (!chatVisible && dragState == null) runBehavior(nextIdleBehavior())
            scheduleIdle()
        }
        postDelayed(idleTimer, delay)
    }

    fun applyClock() {
        val phase = BodyClock.getPhase()
        if (phase.id == "sleep" && walkTimer == null && !chatVisible) setFrame("sleep")
        if (frameTimer == null && frameLoopTimer == null && dragState == null && !chatVisible) {
            setFrame(restingFrame())
        }
    }

    // ---- Blink (ported from app.js scheduleBlink) ----

    private fun scheduleBlink() {
        removeCallbacks(blinkTimer)
        val delay = 2800L + (Math.random() * 4200).toLong()
        blinkTimer = Runnable {
            val busy = dragState != null || chatVisible || frameTimer != null ||
                    frameLoopTimer != null || walkTimer != null
            if (!busy) {
                val previous = currentFrame
                setFrame("blink")
                postDelayed({
                    if (currentFrame == "blink") setFrame(previous)
                }, 135)
            }
            scheduleBlink()
        }
        postDelayed(blinkTimer, delay)
    }

    // ---- Touch handling (ported from app.js gesture handlers) ----

    private var dragState: DragState? = null

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                stopWalk()
                stopFramePlayback()
                dragState = DragState(
                    event.rawX, event.rawY,
                    SpinTracker(event.rawX, event.rawY)
                )
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val d = dragState ?: return true
                val dx = event.rawX - d.x
                val dy = event.rawY - d.y
                if (dx != 0f || dy != 0f) {
                    d.spin.track(event.rawX, event.rawY)
                    host?.moveWindow(dx.toInt(), dy.toInt())
                    d.x = event.rawX
                    d.y = event.rawY
                    d.total += abs(dx) + abs(dy)
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val completed = dragState
                dragState = null
                if (completed == null) return true
                if (completed.spin.isDizzy()) {
                    runBehavior("dizzy")
                    return true
                }
                if (completed.total > 10) {
                    runBehavior("startle", SayOptions(label = "拖拽反馈"))
                    return true
                }
                val now = System.currentTimeMillis()
                if (now - lastClickAt < 320) {
                    lastClickAt = 0
                    host?.openChat()
                } else {
                    lastClickAt = now
                    host?.onTap()
                    say(clickLines.random(), "点击反馈", 2400)
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private class DragState(
        var x: Float,
        var y: Float,
        val spin: SpinTracker
    ) {
        var total = 0f
    }

    fun onWakeEvent(action: String, awayMs: Long = 0) {
        runBehavior(action)
        if (action == "startle" && awayMs > 3600000) say("诶？！你终于回来了！", "惊醒", 5000)
    }

    fun onCommand(command: String) {
        if (command == "open-chat") host?.openChat()
        else runBehavior(command)
    }

    // timers
    private var frameTimer: Runnable? = null
    private var frameLoopTimer: Runnable? = null
    private var shakeTimer: Runnable? = null
    private var shakeEndTimer: Runnable? = null
    private var walkTimer: Runnable? = null
    private var walkStopTimer: Runnable? = null
    private var idleTimer: Runnable? = null
    private var blinkTimer: Runnable? = null
    private var speechTimer: Runnable? = null
    private var breatheTimer: Runnable? = null
}
