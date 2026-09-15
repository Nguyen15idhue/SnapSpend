package com.snapspend.app.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snapspend.app.data.remote.ExpenseDto
import com.snapspend.app.data.remote.FriendDto
import com.snapspend.app.data.repository.SnapSpendRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class DetailViewModel(private val repo: SnapSpendRepository, private val handle: SavedStateHandle) : ViewModel() {
    private val _expense = MutableStateFlow<ExpenseDto?>(null)
    val expense: StateFlow<ExpenseDto?> = _expense.asStateFlow()
    private val _notFound = MutableStateFlow(false)
    val notFound: StateFlow<Boolean> = _notFound.asStateFlow()
    private val _friends = MutableStateFlow<List<FriendDto>>(emptyList())
    val friends: StateFlow<List<FriendDto>> = _friends.asStateFlow()
    val editing: StateFlow<Boolean> = handle.getStateFlow("editing", false)
    val amount: StateFlow<String> = handle.getStateFlow("amount", "")
    val category: StateFlow<String> = handle.getStateFlow("category", "")
    val note: StateFlow<String> = handle.getStateFlow("note", "")
    val date: StateFlow<String> = handle.getStateFlow("date", "")
    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private val _msg = MutableStateFlow<String?>(null)
    val msg: StateFlow<String?> = _msg.asStateFlow()

    init {
        viewModelScope.launch {
            _friends.value = runCatching { repo.friends() }.getOrDefault(emptyList())
        }
    }

    // Nạp expense theo id từ Flow dùng chung; không tìm thấy thì báo để UI quay lại.
    fun open(id: Long) {
        viewModelScope.launch {
            val e = runCatching { repo.expenses.first().find { it.id == id } }.getOrNull()
            if (e == null) { _notFound.value = true; return@launch }
            _expense.value = e
            _notFound.value = false
            handle["editing"] = false
            _error.value = null
            handle["amount"] = e.amount.toString()
            handle["category"] = e.category
            handle["note"] = e.note ?: ""
            handle["date"] = e.expenseDate
        }
    }

    fun toggleEditing() { handle["editing"] = !editing.value; _error.value = null }
    fun cancelEditing() { handle["editing"] = false; _error.value = null; open(_expense.value?.id ?: return) }
    fun onAmount(v: String) { handle["amount"] = v.filter(Char::isDigit) }
    fun onCategory(v: String) { handle["category"] = v }
    fun onNote(v: String) { handle["note"] = v }
    fun onDate(v: String) { handle["date"] = v }

    fun save(onDone: () -> Unit) {
        val e = _expense.value ?: return
        val amountLong = amount.value.toLongOrNull() ?: 0L
        if (amountLong <= 0) { _error.value = "Số tiền phải lớn hơn 0"; return }
        viewModelScope.launch {
            _saving.value = true
            _error.value = null
            runCatching { repo.updateExpense(e.id, amountLong, category.value, note.value.ifBlank { null }, date.value) }
                .onSuccess { onDone() }
                .onFailure { _error.value = it.message }
            _saving.value = false
        }
    }

    fun delete(onDone: () -> Unit) {
        val e = _expense.value ?: return
        viewModelScope.launch {
            runCatching { repo.deleteExpense(e.id) }
                .onSuccess { onDone() }
                .onFailure { _error.value = it.message }
        }
    }

    fun share(friend: FriendDto) {
        val e = _expense.value ?: return
        viewModelScope.launch {
            runCatching { repo.shareExpense(e.id, friend.id) }
                .onSuccess { _msg.value = "Đã chia sẻ với @${friend.username}" }
                .onFailure { _msg.value = it.message ?: "Chia sẻ thất bại" }
        }
    }

    fun clearMsg() { _msg.value = null }
}
