package com.snapspend.app.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snapspend.app.data.remote.ExpenseDto
import com.snapspend.app.data.repository.SnapSpendRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AlbumViewModel(private val repo: SnapSpendRepository, private val handle: SavedStateHandle) : ViewModel() {
    private val expenses = repo.expenses.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val query: StateFlow<String> = handle.getStateFlow("query", "")
    val filterCat: StateFlow<String?> = handle.getStateFlow("filterCat", null)
    val sortDesc: StateFlow<Boolean> = handle.getStateFlow("sortDesc", true)
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()
    private val _loadError = MutableStateFlow<String?>(null)
    val loadError: StateFlow<String?> = _loadError.asStateFlow()
    private val _lastDeleted = MutableStateFlow<ExpenseDto?>(null)
    val lastDeleted: StateFlow<ExpenseDto?> = _lastDeleted.asStateFlow()

    // Danh sách hiển thị = lọc + sắp xếp, tính lại tự động khi state đổi.
    val visible: StateFlow<List<ExpenseDto>> =
        combine(expenses, query, filterCat, sortDesc) { list, q, cat, desc ->
            list.filter { (q.isBlank() || (it.note ?: "").contains(q, ignoreCase = true)) && (cat == null || it.category == cat) }
                .let { if (desc) it.sortedWith(compareByDescending<ExpenseDto> { it.expenseDate }.thenByDescending { it.id })
                      else it.sortedWith(compareBy<ExpenseDto> { it.expenseDate }.thenBy { it.id }) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onQuery(v: String) { handle["query"] = v }
    fun onFilter(cat: String?) { handle["filterCat"] = cat }
    fun toggleSort() { handle["sortDesc"] = !sortDesc.value }

    fun refresh() {
        viewModelScope.launch {
            _refreshing.value = true
            _loadError.value = null
            runCatching { repo.refreshExpenses() }.onFailure { _loadError.value = it.message ?: "Tải thất bại" }
            _refreshing.value = false
        }
    }

    private val deletingIds = mutableSetOf<Long>()

    fun delete(e: ExpenseDto) {
        // Guard: swipe có thể kích hoạt nhiều lần cho cùng item.
        if (!deletingIds.add(e.id)) return
        viewModelScope.launch {
            runCatching { repo.deleteExpense(e.id) }
                .onSuccess { _lastDeleted.value = e }
                .onFailure { _loadError.value = it.message ?: "Xóa thất bại" }
            deletingIds.remove(e.id)
        }
    }

    // Undo khôi phục đầy đủ snapshot cũ (amount, category, note, date, ảnh, aiConfidence).
    fun undoDelete() {
        val d = _lastDeleted.value ?: return
        _lastDeleted.value = null
        viewModelScope.launch {
            runCatching { repo.restoreExpense(d) }
                .onSuccess { _loadError.value = null }
                .onFailure { _loadError.value = it.message ?: "Hoàn tác thất bại" }
        }
    }

    fun clearUndo() { _lastDeleted.value = null }
}
