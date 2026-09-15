package com.snapspend.app.ui.viewmodel

import androidx.compose.runtime.compositionLocalOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import com.snapspend.app.data.repository.SnapSpendRepository

// Factory duy nhất: cấp repo + SavedStateHandle lấy từ owner thực tế
// (NavBackStackEntry) nên không đụng SavedStateRegistry của Activity.
class SnapSpendViewModelFactory(private val repo: SnapSpendRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        val handle: SavedStateHandle = extras.createSavedStateHandle()
        return when {
            modelClass.isAssignableFrom(AuthViewModel::class.java) -> AuthViewModel(repo, handle)
            modelClass.isAssignableFrom(AlbumViewModel::class.java) -> AlbumViewModel(repo, handle)
            modelClass.isAssignableFrom(ExpenseFormViewModel::class.java) -> ExpenseFormViewModel(repo, handle)
            modelClass.isAssignableFrom(StatsViewModel::class.java) -> StatsViewModel(repo, handle)
            modelClass.isAssignableFrom(ProfileViewModel::class.java) -> ProfileViewModel(repo, handle)
            modelClass.isAssignableFrom(DetailViewModel::class.java) -> DetailViewModel(repo, handle)
            modelClass.isAssignableFrom(CategoryViewModel::class.java) -> CategoryViewModel(repo)
            else -> throw IllegalArgumentException("Không hỗ trợ ${modelClass.simpleName}")
        } as T
    }
}

val LocalVmFactory = compositionLocalOf<SnapSpendViewModelFactory> { error("Chưa cung cấp VmFactory") }
