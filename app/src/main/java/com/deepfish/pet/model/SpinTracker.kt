package com.deepfish.pet.model

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.PI

class SpinTracker(x: Float, y: Float) {
    private var x = x
    private var y = y
    private var startedAt = System.currentTimeMillis()
    private var path = 0f
    private var turn = 0f
    private var absoluteTurn = 0f
    private var lastAngle: Double? = null
    private var dizzy = false

    fun track(x: Float, y: Float): Boolean {
        if (dizzy) return true
        val now = System.currentTimeMillis()
        if (now - startedAt > MAX_WINDOW_MS) reset(x, y, now)

        val dx = x - this.x
        val dy = y - this.y
        val distance = hypot(dx, dy)
        if (distance < MIN_SEGMENT_PX) return false

        val angle = atan2(dy.toDouble(), dx.toDouble())
        if (lastAngle != null) {
            val delta = normalizeAngle(angle - lastAngle!!)
            turn += delta.toFloat()
            absoluteTurn += abs(delta).toFloat()
        }
        lastAngle = angle
        path += distance
        this.x = x
        this.y = y

        val elapsed = max(1L, now - startedAt)
        val speed = path / elapsed * 1000f
        val consistency = abs(turn) / max(absoluteTurn, 0.001f)
        dizzy = path >= MIN_PATH_PX &&
                abs(turn) >= MIN_TURN_RADIANS &&
                speed >= MIN_SPEED_PX_PER_SECOND &&
                consistency >= MIN_TURN_CONSISTENCY
        return dizzy
    }

    fun isDizzy(): Boolean = dizzy

    private fun reset(x: Float, y: Float, now: Long) {
        this.x = x
        this.y = y
        startedAt = now
        path = 0f
        turn = 0f
        absoluteTurn = 0f
        lastAngle = null
        dizzy = false
    }

    private fun normalizeAngle(angle: Double): Double {
        var a = angle
        while (a > PI) a -= PI * 2
        while (a < -PI) a += PI * 2
        return a
    }

    companion object {
        private const val MAX_WINDOW_MS = 1800L
        private const val MIN_SEGMENT_PX = 4f
        private const val MIN_PATH_PX = 360f
        private const val MIN_SPEED_PX_PER_SECOND = 260f
        private const val MIN_TURN_RADIANS = (PI * 2.4).toFloat()
        private const val MIN_TURN_CONSISTENCY = 0.68f
    }
}
