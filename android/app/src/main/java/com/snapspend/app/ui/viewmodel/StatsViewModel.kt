package com.snapspend.app.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snapspend.app.data.remote.AnalysisDto
import com.snapspend.app.data.remote.StatsDto
import com.snapspend.app.data.repository.SnapSpendRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

class StatsViewModel(private val repo: SnapSpendRepository, private val handle: SavedStateHandle) : ViewModel() {
    val range: StateFlow<String> = handle.getStateFlow("range", "30D")
    private val _stats = MutableStateFlow<StatsDto?>(null)
    val stats: StateFlow<StatsDto?> = _stats.asStateFlow()
    private val _analysis = MutableStateFlow<AnalysisDto?>(null)
    val analysis: StateFlow<AnalysisDto?> = _analysis.asStateFlow()
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()
    private val _analyzing = MutableStateFlow(false)
    val analyzing: StateFlow<Boolean> = _analyzing.asStateFlow()

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
            _analysis.value = null
            _stats.value = runCatching { repo.stats(from, to) }.getOrNull()
            _loading.value = false
        }
    }

    fun analyze() {
        viewModelScope.launch {
            _analyzing.value = true
            _analysis.value = runCatching { repo.analyze(from, to) }.getOrNull()
            _analyzing.value = false
        }
    }
}
