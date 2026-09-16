package com.snapspend.app.data.repository

import android.net.Uri
import com.snapspend.app.data.remote.AiAnalysisDto
import com.snapspend.app.data.remote.AuthResponse
import com.snapspend.app.data.remote.BasicAnalysisDto
import com.snapspend.app.data.remote.CategoryDto
import com.snapspend.app.data.remote.ClassificationDto
import com.snapspend.app.data.remote.ExtractReceiptRequest
import com.snapspend.app.data.remote.ItemCategoryDto
import com.snapspend.app.data.remote.ReceiptExtractDto
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

    /** Tải trang đầu (thay toàn bộ cache), trả về tổng số bản ghi server. */
    suspend fun refreshExpenses(pageSize: Int = 10): Int

    /** Nhảy tới trang N (thay toàn bộ cache bằng trang đó), trả về tổng số bản ghi. */
    suspend fun loadPage(
        page: Int,
        pageSize: Int = 10,
        search: String? = null,
        category: String? = null,
        from: String? = null,
        to: String? = null,
        sort: String? = null
    ): Int

    /** Khoản chi trong một ngày (dùng cho Stats lọc ngày qua server, tối đa 100). */
    suspend fun dayExpenses(date: String): List<ExpenseDto>

    suspend fun createExpense(uri: Uri?, amount: Long, category: String, note: String?, date: String): ExpenseDto

    suspend fun updateExpense(id: Long, amount: Long, category: String, note: String?, date: String)

    suspend fun deleteExpense(id: Long)

    /** Xóa hàng loạt (bulk action), trả về số bản ghi đã xóa. */
    suspend fun deleteExpenses(ids: List<Long>): Int

    /** Khôi phục bản ghi đã xóa (Undo), giữ nguyên amount/category/note/date/ảnh/aiConfidence. */
    suspend fun restoreExpense(expense: ExpenseDto): ExpenseDto

    suspend fun stats(from: String, to: String): StatsDto
    suspend fun analyzeBasic(from: String, to: String): BasicAnalysisDto
    suspend fun analyzeFull(from: String, to: String): AiAnalysisDto

    suspend fun friends(): List<FriendDto>
    suspend fun addFriend(username: String): FriendDto
    suspend fun shareExpense(id: Long, friendId: Long)

    /** Danh mục dùng chung (Real lấy từ server, Mock trả list local). */
    suspend fun categories(): List<CategoryDto>

    /** Chi tiêu người khác chia sẻ cho tôi (chỉ xem). */
    suspend fun sharedWithMe(): List<SharedExpenseDto>

    /** Phân loại ảnh + ghi chú thành category (dùng trước khi lưu). */
    suspend fun classify(uri: Uri?, note: String?): ClassificationDto

    /** Phân loại từng món (hóa đơn nhiều loại) thành category. */
    suspend fun classifyItems(names: List<String>): List<ItemCategoryDto>

    /** Tách text OCR thành {merchant, items, total} bằng mini model (null khi lỗi). */
    suspend fun extractReceipt(text: String): ReceiptExtractDto?

    suspend fun deleteAccount()
}
