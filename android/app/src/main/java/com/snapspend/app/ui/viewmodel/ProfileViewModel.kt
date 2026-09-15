package com.snapspend.app.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snapspend.app.data.remote.ExpenseDto
import com.snapspend.app.data.remote.FriendDto
import com.snapspend.app.data.remote.SharedExpenseDto
import com.snapspend.app.data.repository.SnapSpendRepository
import com.snapspend.app.ui.format.formatVnd
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProfileViewModel(private val repo: SnapSpendRepository, private val handle: SavedStateHandle) : ViewModel() {
    private val _friends = MutableStateFlow<List<FriendDto>>(emptyList())
    val friends: StateFlow<List<FriendDto>> = _friends.asStateFlow()
    val username: StateFlow<String> = handle.getStateFlow("username", "")
    private val _msg = MutableStateFlow<String?>(null)
    val msg: StateFlow<String?> = _msg.asStateFlow()
    private val _shared = MutableStateFlow<List<SharedExpenseDto>>(emptyList())
    val shared: StateFlow<List<SharedExpenseDto>> = _shared.asStateFlow()

    val expenses = repo.expenses.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init { loadFriends(); loadShared() }

    fun onUsername(v: String) { handle["username"] = v }

    fun loadShared() {
        viewModelScope.launch {
            _shared.value = runCatching { repo.sharedWithMe() }.getOrDefault(emptyList())
        }
    }

    fun loadFriends() {
        viewModelScope.launch {
            _friends.value = runCatching { repo.friends() }.getOrDefault(emptyList())
        }
    }

    fun addFriend() {
        val name = username.value
        viewModelScope.launch {
            runCatching { repo.addFriend(name) }
                .onSuccess { _friends.value = _friends.value + it; handle["username"] = ""; _msg.value = "Đã thêm @${it.username}" }
                .onFailure { _msg.value = it.message }
        }
    }

    fun shareNewest(newest: ExpenseDto?, friend: FriendDto) {
        if (newest == null) return
        viewModelScope.launch {
            runCatching { repo.shareExpense(newest.id, friend.id) }
                .onSuccess { _msg.value = "Đã chia sẻ ${formatVnd(newest.amount)} với @${friend.username}" }
                .onFailure { _msg.value = it.message }
        }
    }

    fun deleteAccount(onDone: () -> Unit) {
        viewModelScope.launch {
            runCatching { repo.deleteAccount() }
                .onSuccess { onDone() }
                .onFailure { _msg.value = it.message }
        }
    }

    fun clearMsg() { _msg.value = null }
}
