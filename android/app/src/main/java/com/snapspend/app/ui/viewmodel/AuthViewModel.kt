package com.snapspend.app.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snapspend.app.data.repository.SnapSpendRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel(private val repo: SnapSpendRepository, private val handle: SavedStateHandle) : ViewModel() {
    val register: StateFlow<Boolean> = handle.getStateFlow("register", false)
    val email: StateFlow<String> = handle.getStateFlow("email", "")
    val username: StateFlow<String> = handle.getStateFlow("username", "")
    val password: StateFlow<String> = handle.getStateFlow("password", "")
    val showPass: StateFlow<Boolean> = handle.getStateFlow("showPass", false)
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun onEmail(v: String) { handle["email"] = v; _error.value = null }
    fun onUsername(v: String) { handle["username"] = v }
    fun onPassword(v: String) { handle["password"] = v; _error.value = null }
    fun toggleMode() { handle["register"] = !register.value; _error.value = null }
    fun toggleShowPass() { handle["showPass"] = !showPass.value }

    fun emailOk(): Boolean = email.value.contains("@")

    fun canSubmit(): Boolean =
        !loading.value && emailOk() && password.value.length >= 6 && (!register.value || username.value.isNotBlank())

    fun submit(onLoggedIn: (String) -> Unit) {
        val mail = email.value
        val pass = password.value
        val reg = register.value
        val user = username.value
        if (!canSubmit()) return
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            runCatching {
                if (reg) repo.register(mail, user.ifBlank { mail.substringBefore('@') }, pass)
                else repo.login(mail, pass)
            }.onSuccess { onLoggedIn(it.token) }
                .onFailure { _error.value = it.message ?: "Đăng nhập thất bại" }
            _loading.value = false
        }
    }
}
