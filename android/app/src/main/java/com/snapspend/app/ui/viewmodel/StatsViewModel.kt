package com.snapspend.app.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snapspend.app.data.remote.AiAnalysisDto
import com.snapspend.app.data.remote.BasicAnalysisDto
import com.snapspend.app.data.remote.ExpenseDto
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
    // Phân tích cơ bản (chỉ số liệu) và AI phân tích (cơ bản + đánh giá AI).
    private val _basic = MutableStateFlow<BasicAnalysisDto?>(null)
    val basic: StateFlow<BasicAnalysisDto?> = _basic.asStateFlow()
    private val _full = MutableStateFlow<AiAnalysisDto?>(null)
    val full: StateFlow<AiAnalysisDto?> = _full.asStateFlow()
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()
    private val _analyzing = MutableStateFlow(false)
    val analyzing: StateFlow<Boolean> = _analyzing.asStateFlow()

    // Khoản chi của ngày đang chọn trên biểu đồ — tải qua server (đủ dữ liệu, không dùng cache 1 trang).
    private val _dayExpenses = MutableStateFlow<List<ExpenseDto>>(emptyList())
    val dayExpenses: StateFlow<List<ExpenseDto>> = _dayExpenses.asStateFlow()
    private val _dayLoading = MutableStateFlow(false)
    val dayLoading: StateFlow<Boolean> = _dayLoading.asStateFlow()
    private val _dayError = MutableStateFlow<String?>(null)
    val dayError: StateFlow<String?> = _dayError.asStateFlow()

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

    /** Tải khoản chi của một ngày qua server để lọc đúng (kể cả khoản ở trang khác của Album). */
    fun loadDay(day: String) {
        viewModelScope.launch {
            _dayLoading.value = true
            _dayError.value = null
            runCatching { repo.dayExpenses(day) }
                .onSuccess { _dayExpenses.value = it }
                .onFailure { _dayError.value = it.message ?: "Tải thất bại"; _dayExpenses.value = emptyList() }
            _dayLoading.value = false
        }
    }

    fun clearDay() {
        _dayExpenses.value = emptyList()
        _dayError.value = null
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
