package com.example.autoclicker.core

import android.content.Context
import com.example.autoclicker.data.ConfigRepository
import com.example.autoclicker.overlay.FloatingWindowManager

/** 全局单例容器，让 Activity / Service / 悬浮窗共享同一份状态 */
object AppContainer {

    val scheduler = ClickScheduler()

    lateinit var repository: ConfigRepository
        private set

    var floatingWindow: FloatingWindowManager? = null

    fun init(context: Context) {
        if (!::repository.isInitialized) {
            repository = ConfigRepository(context.applicationContext)
        }
    }
}
