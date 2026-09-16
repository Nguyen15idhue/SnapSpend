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
    private val _totalCount = MutableStateFlow(0)
    val totalCount: StateFlow<Int> = _totalCount.asStateFlow()
    // Trang hiện tại (rememberSaveable qua SavedStateHandle để xoay màn hình không mất).
    val page: StateFlow<Int> = handle.getStateFlow("page", 1)
    private val _loadError = MutableStateFlow<String?>(null)
    val loadError: StateFlow<String?> = _loadError.asStateFlow()
    private val _lastDeleted = MutableStateFlow<ExpenseDto?>(null)
    val lastDeleted: StateFlow<ExpenseDto?> = _lastDeleted.asStateFlow()

    // Chế độ chọn nhiều (bulk action): tap/long-press để chọn, action đầu tiên là Xóa.
    private val _selectionMode = MutableStateFlow(false)
    val selectionMode: StateFlow<Boolean> = _selectionMode.asStateFlow()
    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedIds: StateFlow<Set<Long>> = _selectedIds.asStateFlow()
    private val _bulkDeleted = MutableStateFlow(0)
    val bulkDeleted: StateFlow<Int> = _bulkDeleted.asStateFlow()

    companion object {
        const val PAGE_SIZE = 10
        /** Kích thước chunk khi tải toàn bộ lúc tìm kiếm/lọc. */
        const val REFRESH_CHUNK = 50
    }

    // Danh sách hiển thị = lọc + sắp xếp, tính lại tự động khi state đổi.
    val visible: StateFlow<List<ExpenseDto>> =
        combine(expenses, query, filterCat, sortDesc) { list, q, cat, desc ->
            list.filter { (q.isBlank() || (it.note ?: "").contains(q, ignoreCase = true)) && (cat == null || it.category == cat) }
                .let { if (desc) it.sortedWith(compareByDescending<ExpenseDto> { it.expenseDate }.thenByDescending { it.id })
                      else it.sortedWith(compareBy<ExpenseDto> { it.expenseDate }.thenBy { it.id }) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onQuery(v: String) { handle["query"] = v; ensureFullForSearch() }
    fun onFilter(cat: String?) { handle["filterCat"] = cat; ensureFullForSearch() }
    fun toggleSort() { handle["sortDesc"] = !sortDesc.value }

    /** Tổng số trang từ tổng bản ghi (ít nhất 1). */
    fun totalPages(): Int = maxOf(1, (_totalCount.value + PAGE_SIZE - 1) / PAGE_SIZE)

    /** Có đang tìm/lọc không — khi đó cache giữ TOÀN BỘ để lọc đúng, ẩn thanh trang. */
    fun isSearching(): Boolean = query.value.isNotBlank() || filterCat.value != null

    fun refresh() {
        viewModelScope.launch {
            _refreshing.value = true
            _loadError.value = null
            runCatching {
                if (isSearching()) repo.refreshAllExpenses(REFRESH_CHUNK)
                else repo.loadPage(page.value, PAGE_SIZE)
            }
                .onSuccess { total -> _totalCount.value = total }
                .onFailure { _loadError.value = it.message ?: "Tải thất bại" }
            _refreshing.value = false
        }
    }

    /** Nhảy tới trang N (chỉ khi không tìm/lọc). */
    fun goToPage(p: Int) {
        val target = p.coerceIn(1, totalPages())
        if (target == page.value || isSearching()) return
        handle["page"] = target
        clearSelection()
        viewModelScope.launch {
            _refreshing.value = true
            _loadError.value = null
            runCatching { repo.loadPage(target, PAGE_SIZE) }
                .onSuccess { total -> _totalCount.value = total }
                .onFailure { _loadError.value = it.message ?: "Tải trang thất bại" }
            _refreshing.value = false
        }
    }

    fun nextPage() = goToPage(page.value + 1)
    fun prevPage() = goToPage(page.value - 1)

    /** Khi bắt đầu tìm/lọc: tải toàn bộ về cache một lần để kết quả đúng. */
    private fun ensureFullForSearch() {
        if (!isSearching()) return
        viewModelScope.launch {
            runCatching { repo.refreshAllExpenses(REFRESH_CHUNK) }
                .onSuccess { total -> _totalCount.value = total }
                .onFailure { _loadError.value = it.message ?: "Tải thất bại" }
        }
    }

    // --- Chọn nhiều ---
    /** Vào chế độ chọn (không chọn sẵn gì) — cho nút "Chọn" trên header. */
    fun enterSelection() { _selectionMode.value = true }

    /** Nhấn giữ card: vào chế độ chọn + chọn sẵn card đó. */
    fun startSelection(id: Long) {
        _selectionMode.value = true
        _selectedIds.value = _selectedIds.value + id
    }

    fun toggleSelect(id: Long) {
        _selectedIds.value = _selectedIds.value.toMutableSet().also { s -> if (!s.add(id)) s.remove(id) }
    }

    fun selectAll(ids: List<Long>) { _selectedIds.value = ids.toSet() }
    fun clearSelection() {
        _selectedIds.value = emptySet()
        _selectionMode.value = false
    }

    fun deleteSelected() {
        val ids = _selectedIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            runCatching { repo.deleteExpenses(ids) }
                .onSuccess { n ->
                    _bulkDeleted.value = n
                    clearSelection()
                    // Xóa hết trang cuối → lùi về trang trước để không trắng trang.
                    val newTotal = _totalCount.value - n
                    if (!isSearching() && page.value > 1 && newTotal <= (page.value - 1) * PAGE_SIZE) {
                        goToPage(page.value - 1)
                    } else refresh()
                }
                .onFailure { _loadError.value = it.message ?: "Xóa thất bại" }
        }
    }

    fun clearBulkDeleted() { _bulkDeleted.value = 0 }

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
