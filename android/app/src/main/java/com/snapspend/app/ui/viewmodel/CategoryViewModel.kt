package com.snapspend.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snapspend.app.data.remote.CategoryDto
import com.snapspend.app.data.repository.SnapSpendRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// Danh mục dùng chung: Real lấy từ server, lỗi thì repo đã fallback list local.
class CategoryViewModel(private val repo: SnapSpendRepository) : ViewModel() {
    private val _categories = MutableStateFlow(
        com.snapspend.app.model.categories.map { CategoryDto(it.key, it.label, it.emoji) }
    )
    val categories: StateFlow<List<CategoryDto>> = _categories.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { repo.categories() }.onSuccess { if (it.isNotEmpty()) _categories.value = it }
        }
    }
}
