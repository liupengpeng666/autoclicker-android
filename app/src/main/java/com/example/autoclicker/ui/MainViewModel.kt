package com.example.autoclicker.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.autoclicker.core.AppContainer
import com.example.autoclicker.model.ClickConfig
import com.example.autoclicker.model.ClickPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo get() = AppContainer.repository

    /** 内存中的唯一配置对象。UI 直接就地修改它，避免 DataStore 往返造成对象重建 */
    private val _config = MutableStateFlow(ClickConfig())
    val config: StateFlow<ClickConfig> = _config

    val profiles = repo.profilesFlow

    init {
        viewModelScope.launch {
            _config.value = repo.configFlow.first()
        }
    }

    /** 就地改动后调用，只负责持久化 */
    fun persist() {
        viewModelScope.launch { repo.saveConfig(_config.value) }
    }

    fun addPoint(point: ClickPoint) {
        _config.value.points.add(point)
        persist()
    }

    fun loadProfile(config: ClickConfig) {
        _config.value = config
        persist()
    }

    fun saveProfile(name: String) {
        val config = _config.value
        config.name = name
        persist()
        viewModelScope.launch { repo.saveProfile(config) }
    }

    fun deleteProfile(name: String) {
        viewModelScope.launch { repo.deleteProfile(name) }
    }
}
