package com.snapspend.app.data.repository

import android.net.Uri
import com.snapspend.app.data.remote.AnalysisDto
import com.snapspend.app.data.remote.AuthResponse
import com.snapspend.app.data.remote.ExpenseDto
import com.snapspend.app.data.remote.FriendDto
import com.snapspend.app.data.remote.StatsDto
import kotlinx.coroutines.flow.Flow

/**
 * Giao diện duy nhất mà UI được phép gọi.
 * - Demo: [MockRepository] (mock data, không cần mạng/server).
 * - Thật: [RealRepository] (Retrofit + Room, cần backend).
 * Đảo [com.snapspend.app.data.AppConfig.isDemo] là đổi nguồn data, UI giữ nguyên.
 */
interface SnapSpendRepository {
    val expenses: Flow<List<ExpenseDto>>

    suspend fun login(email: String, password: String): AuthResponse
    suspend fun register(email: String, username: String, password: String): AuthResponse

    suspend fun refreshExpenses()

    suspend fun createExpense(uri: Uri?, amount: Long, category: String, note: String?, date: String): ExpenseDto

    suspend fun updateExpense(id: Long, amount: Long, category: String, note: String?, date: String)

    suspend fun deleteExpense(id: Long)

    suspend fun stats(from: String, to: String): StatsDto
    suspend fun analyze(from: String, to: String): AnalysisDto

    suspend fun friends(): List<FriendDto>
    suspend fun addFriend(username: String): FriendDto
    suspend fun shareExpense(id: Long, friendId: Long)

    suspend fun deleteAccount()
}
