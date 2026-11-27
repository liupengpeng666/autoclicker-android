package com.example.autoclicker.model

/** 一整套连点配置（方案） */
data class ClickConfig(
    var name: String = "默认方案",
    val points: MutableList<ClickPoint> = mutableListOf(),
    /** 整轮点位循环次数，0 表示无限循环 */
    var loopCount: Int = 0,
    /** 从最后一个点位回到第一个点位的间隔(ms) */
    var loopDelayMs: Long = 500L,
    /** 间隔随机抖动百分比，例如 30 表示 ±30% */
    var jitterPercent: Int = 0
)
