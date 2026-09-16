package com.snapspend.app.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snapspend.app.data.remote.ExpenseDto
import com.snapspend.app.data.repository.SnapSpendRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
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
    // Snapshot hàng loạt vừa xóa để hoàn tác (giữ đủ ảnh + aiConfidence).
    private val _lastBulkDeleted = MutableStateFlow<List<ExpenseDto>>(emptyList())

    companion object {
        const val PAGE_SIZE = 10
        /** Debounce tìm kiếm để mỗi lần gõ nhanh chỉ gọi 1 request. */
        const val SEARCH_DEBOUNCE_MS = 400L
    }

    init {
        // Gõ tìm kiếm: debounce 400ms rồi mới gọi server (tránh spam request mỗi ký tự).
        viewModelScope.launch {
            @OptIn(FlowPreview::class)
            query.debounce(SEARCH_DEBOUNCE_MS).drop(1).collect {
                handle["page"] = 1
                clearSelection()
                loadCurrent()
            }
        }
    }

    // Danh sách hiển thị = trang hiện tại do server lọc/sắp xếp (không lọc client để đủ dữ liệu).
    val visible: StateFlow<List<ExpenseDto>> =
        combine(expenses, sortDesc) { list, desc ->
            if (desc) list.sortedWith(compareByDescending<ExpenseDto> { it.expenseDate }.thenByDescending { it.id })
            else list.sortedWith(compareBy<ExpenseDto> { it.expenseDate }.thenBy { it.id })
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onQuery(v: String) { handle["query"] = v }
    fun onFilter(cat: String?) {
        handle["filterCat"] = cat
        handle["page"] = 1
        clearSelection()
        loadCurrent()
    }
    fun toggleSort() {
        handle["sortDesc"] = !sortDesc.value
        loadCurrent()
    }

    /** Tổng số trang từ tổng bản ghi (ít nhất 1). */
    fun totalPages(): Int = maxOf(1, (_totalCount.value + PAGE_SIZE - 1) / PAGE_SIZE)

    private fun sortParam(): String? = if (sortDesc.value) null else "oldest"

    private fun loadCurrent() {
        viewModelScope.launch {
            _refreshing.value = true
            _loadError.value = null
            runCatching {
                repo.loadPage(
                    page = page.value,
                    pageSize = PAGE_SIZE,
                    search = query.value.takeIf { it.isNotBlank() },
                    category = filterCat.value,
                    sort = sortParam()
                )
            }
                .onSuccess { total -> _totalCount.value = total }
                .onFailure { _loadError.value = it.message ?: "Tải thất bại" }
            _refreshing.value = false
        }
    }

    fun refresh() {
        loadCurrent()
    }

    /** Nhảy tới trang N (kể cả khi đang tìm/lọc — server phân trang). */
    fun goToPage(p: Int) {
        val target = p.coerceIn(1, totalPages())
        if (target == page.value) {
            loadCurrent()
            return
        }
        handle["page"] = target
        clearSelection()
        loadCurrent()
    }

    fun nextPage() = goToPage(page.value + 1)
    fun prevPage() = goToPage(page.value - 1)

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
        // Giữ snapshot để hoàn tác (đủ amount, category, note, date, ảnh, aiConfidence).
        val snapshot = visible.value.filter { ids.contains(it.id) }
        viewModelScope.launch {
            runCatching { repo.deleteExpenses(ids) }
                .onSuccess { n ->
                    _bulkDeleted.value = n
                    _lastBulkDeleted.value = snapshot.take(n)
                    clearSelection()
                    // Xóa hết trang cuối → lùi về trang trước để không trắng trang.
                    val newTotal = _totalCount.value - n
                    if (page.value > 1 && newTotal <= (page.value - 1) * PAGE_SIZE) {
                        handle["page"] = page.value - 1
                    }
                    loadCurrent()
                }
                .onFailure { _loadError.value = it.message ?: "Xóa thất bại" }
        }
    }

    fun clearBulkDeleted() { _bulkDeleted.value = 0; _lastBulkDeleted.value = emptyList() }

    /** Hoàn tác xóa hàng loạt: khôi phục từng bản ghi, báo số khôi phục được khi lỗi giữa chừng. */
    fun undoBulkDelete() {
        val items = _lastBulkDeleted.value
        _bulkDeleted.value = 0
        if (items.isEmpty()) return
        _lastBulkDeleted.value = emptyList()
        viewModelScope.launch {
            var restored = 0
            var lastErr: String? = null
            for (e in items) {
                runCatching { repo.restoreExpense(e) }
                    .onSuccess { restored++ }
                    .onFailure { lastErr = it.message ?: "Hoàn tác thất bại" }
            }
            if (restored < items.size) _loadError.value = "Chỉ khôi phục được $restored/${items.size} khoản${lastErr?.let { ": $it" } ?: ""}"
            else _loadError.value = null
            loadCurrent()
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
