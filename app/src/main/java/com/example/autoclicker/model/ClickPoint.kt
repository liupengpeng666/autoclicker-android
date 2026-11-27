package com.example.autoclicker.model

import java.util.UUID

/** 单个点位：可以是单击，也可以是一段滑动 */
data class ClickPoint(
    val id: String = UUID.randomUUID().toString(),
    var x: Int = 0,
    var y: Int = 0,
    /** 滑动终点，仅 type=SWIPE 时使用 */
    var endX: Int = 0,
    var endY: Int = 0,
    var type: PointType = PointType.CLICK,
    /** 该点重复点击/滑动次数，0 表示无限（会阻塞后续点位，谨慎使用） */
    var repeatCount: Int = 1,
    /** 该点每次动作之间的间隔(ms) */
    var intervalMs: Long = 200L,
    /** 按下持续时间(ms) */
    var pressDurationMs: Long = 60L,
    /** 坐标随机偏移半径(px)，0 表示固定坐标 */
    var jitterRadius: Int = 0
)

enum class PointType {
    CLICK, SWIPE
}
