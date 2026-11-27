package com.example.autoclicker.core

import com.example.autoclicker.model.ClickConfig
import com.example.autoclicker.model.PointType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * 调度器：按「点位顺序 -> 每点重复次数 -> 整轮循环」三层结构执行。
 * 支持间隔抖动与坐标偏移（拟人化基础）。
 */
class ClickScheduler {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running

    private val _progress = MutableStateFlow("待机")
    val progress: StateFlow<String> = _progress

    fun start(config: ClickConfig) {
        if (job?.isActive == true) return
        _running.value = true
        job = scope.launch {
            try {
                var loop = 0
                while (isActive && (config.loopCount == 0 || loop < config.loopCount)) {
                    loop++
                    for ((index, point) in config.points.withIndex()) {
                        if (!isActive) break
                        _progress.value = "第 ${if (config.loopCount == 0) loop else "$loop/${config.loopCount}"} 轮 · 点位 ${index + 1}/${config.points.size}"

                        val repeats = if (point.repeatCount == 0) Int.MAX_VALUE else point.repeatCount
                        for (r in 0 until repeats) {
                            if (!isActive) break
                            val service = ClickerAccessibilityService.instance
                            if (service == null) {
                                _progress.value = "无障碍服务未连接"
                                break
                            }

                            if (point.type == PointType.CLICK) {
                                val (px, py) = jitter(point.x, point.y, point.jitterRadius)
                                service.click(px, py, point.pressDurationMs)
                            } else {
                                val (sx, sy) = jitter(point.x, point.y, point.jitterRadius)
                                val (ex, ey) = jitter(point.endX, point.endY, point.jitterRadius)
                                service.swipe(sx, sy, ex, ey, point.pressDurationMs)
                            }

                            if (r < repeats - 1) {
                                delay(jittered(point.intervalMs, config.jitterPercent))
                            }
                        }
                        if (index < config.points.size - 1) {
                            delay(config.loopDelayMs)
                        }
                    }
                }
                _progress.value = "已完成"
            } finally {
                _running.value = false
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        _running.value = false
        _progress.value = "已停止"
    }

    /** 间隔抖动：在 base 上下浮动 ±percent% */
    private fun jittered(base: Long, percent: Int): Long {
        if (percent <= 0 || base <= 0) return base
        val delta = base * percent / 100.0
        val value = base + Random.nextDouble(-delta, delta)
        return value.roundToInt().coerceAtLeast(1L)
    }

    /** 坐标偏移：在半径内随机（高斯倾向中心） */
    private fun jitter(x: Int, y: Int, radius: Int): Pair<Int, Int> {
        if (radius <= 0) return x to y
        val dx = (Random.nextGaussian() * radius / 2).roundToInt().coerceIn(-radius, radius)
        val dy = (Random.nextGaussian() * radius / 2).roundToInt().coerceIn(-radius, radius)
        return (x + dx) to (y + dy)
    }
}
