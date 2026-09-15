package com.snapspend.app.data.repository

import android.net.Uri
import com.snapspend.app.data.remote.AnalysisDto
import com.snapspend.app.data.remote.AuthResponse
import com.snapspend.app.data.remote.CategoryDto
import com.snapspend.app.data.remote.ClassificationDto
import com.snapspend.app.data.remote.ExpenseDto
import com.snapspend.app.data.remote.FriendDto
import com.snapspend.app.data.remote.SharedExpenseDto
import com.snapspend.app.data.remote.StatsDto
import kotlinx.coroutines.flow.Flow

/**
 * Giao diện duy nhất mà UI được phép gọi.
 * Chỉ có [RealRepository] (Retrofit + Room, cần backend).
 */
interface SnapSpendRepository {
    val expenses: Flow<List<ExpenseDto>>

    suspend fun login(email: String, password: String): AuthResponse
    suspend fun register(email: String, username: String, password: String): AuthResponse

    suspend fun refreshExpenses()

    suspend fun createExpense(uri: Uri?, amount: Long, category: String, note: String?, date: String): ExpenseDto

    suspend fun updateExpense(id: Long, amount: Long, category: String, note: String?, date: String)

    suspend fun deleteExpense(id: Long)

    /** Khôi phục bản ghi đã xóa (Undo), giữ nguyên amount/category/note/date/ảnh/aiConfidence. */
    suspend fun restoreExpense(expense: ExpenseDto): ExpenseDto

    suspend fun stats(from: String, to: String): StatsDto
    suspend fun analyze(from: String, to: String): AnalysisDto

    suspend fun friends(): List<FriendDto>
    suspend fun addFriend(username: String): FriendDto
    suspend fun shareExpense(id: Long, friendId: Long)

    /** Danh mục dùng chung (Real lấy từ server, Mock trả list local). */
    suspend fun categories(): List<CategoryDto>

    /** Chi tiêu người khác chia sẻ cho tôi (chỉ xem). */
    suspend fun sharedWithMe(): List<SharedExpenseDto>

    /** Phân loại ảnh + ghi chú thành category (dùng trước khi lưu). */
    suspend fun classify(uri: Uri?, note: String?): ClassificationDto

    suspend fun deleteAccount()
}
