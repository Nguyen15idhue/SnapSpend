package com.snapspend.app.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snapspend.app.data.remote.AiAnalysisDto
import com.snapspend.app.data.remote.BasicAnalysisDto
import com.snapspend.app.data.remote.StatsDto
import com.snapspend.app.data.repository.SnapSpendRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class StatsViewModel(private val repo: SnapSpendRepository, private val handle: SavedStateHandle) : ViewModel() {
    val range: StateFlow<String> = handle.getStateFlow("range", "30D")
    private val _stats = MutableStateFlow<StatsDto?>(null)
    val stats: StateFlow<StatsDto?> = _stats.asStateFlow()
    // Phân tích cơ bản (chỉ số liệu) và AI phân tích (cơ bản + đánh giá AI).
    private val _basic = MutableStateFlow<BasicAnalysisDto?>(null)
    val basic: StateFlow<BasicAnalysisDto?> = _basic.asStateFlow()
    private val _full = MutableStateFlow<AiAnalysisDto?>(null)
    val full: StateFlow<AiAnalysisDto?> = _full.asStateFlow()
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()
    private val _analyzing = MutableStateFlow(false)
    val analyzing: StateFlow<Boolean> = _analyzing.asStateFlow()

    // Luồng chi tiêu để lọc theo ngày ngay trên biểu đồ.
    val expenses = repo.expenses.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val from: String get() = when (range.value) {
        "7D" -> LocalDate.now().minusDays(6).toString()
        "Tháng này" -> LocalDate.now().withDayOfMonth(1).toString()
        else -> LocalDate.now().minusDays(29).toString()
    }
    val to: String get() = LocalDate.now().toString()

    init { load() }

    fun onRange(r: String) {
        if (range.value == r) return
        handle["range"] = r
        load()
    }

    fun load() {
        viewModelScope.launch {
            _loading.value = true
            _basic.value = null
            _full.value = null
            _stats.value = runCatching { repo.stats(from, to) }.getOrNull()
            _loading.value = false
        }
    }

    fun analyzeBasic() {
        viewModelScope.launch {
            _analyzing.value = true
            _basic.value = runCatching { repo.analyzeBasic(from, to) }.getOrNull()
            _analyzing.value = false
        }
    }

    fun analyzeFull() {
        viewModelScope.launch {
            _analyzing.value = true
            _full.value = runCatching { repo.analyzeFull(from, to) }.getOrNull()
            _analyzing.value = false
        }
    }
}
