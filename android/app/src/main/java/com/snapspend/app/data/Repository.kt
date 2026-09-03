package com.snapspend.app.data

import android.content.Context
import android.net.Uri
import com.snapspend.app.data.local.AppDatabase
import com.snapspend.app.data.local.ExpenseEntity
import com.snapspend.app.data.remote.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.time.LocalDate

class Repository(private val context: Context, private val db: AppDatabase, private val api: SnapSpendApi) {
    val localExpenses: Flow<List<ExpenseDto>> = db.expenseDao().observeAll().map { list ->
        list.map { ExpenseDto(it.id, it.amount, it.category, it.imageUrl, it.note, it.expenseDate, null) }
    }

    suspend fun login(email: String, password: String) = api.login(AuthRequest(email, password))
    suspend fun register(email: String, username: String, password: String) = api.register(RegisterRequest(email, username, password))

    suspend fun refreshExpenses() {
        val remote = api.expenses()
        db.expenseDao().upsertAll(remote.map { it.toEntity() })
    }

    suspend fun createExpense(uri: Uri?, amount: Long, category: String, note: String?, date: String): ExpenseDto {
        val imagePart = uri?.let { createImagePart(it) }
        val result = api.createExpense(
            amount.toString().toRequestBody("text/plain".toMediaType()),
            category.toRequestBody("text/plain".toMediaType()),
            note?.toRequestBody("text/plain".toMediaType()),
            date.toRequestBody("text/plain".toMediaType()),
            imagePart
        )
        db.expenseDao().upsert(result.toEntity())
        return result
    }

    suspend fun updateExpense(id: Long, amount: Long, category: String, note: String?, date: String) {
        val result = api.updateExpense(id, ExpenseUpsertDto(amount, category, note, date))
        db.expenseDao().upsert(result.toEntity())
    }

    suspend fun deleteExpense(id: Long) {
        api.deleteExpense(id)
        db.expenseDao().delete(id)
    }

    suspend fun stats(from: String, to: String) = api.stats(from, to)
    suspend fun analyze(from: String, to: String) = api.analyze(from, to)
    suspend fun deleteAccount() = api.deleteAccount()
    suspend fun friends() = api.friends()
    suspend fun addFriend(username: String) = api.addFriend(AddFriendRequest(username))
    suspend fun shareExpense(id: Long, friendId: Long) = api.shareExpense(id, friendId)

    private fun createImagePart(uri: Uri): MultipartBody.Part {
        val resolver = context.contentResolver
        val ext = resolver.getType(uri)?.substringAfterLast('/') ?: "jpeg"
        val file = File.createTempFile("expense_", ".${ext}", context.cacheDir)
        resolver.openInputStream(uri).use { input -> requireNotNull(input); file.outputStream().use { output -> input.copyTo(output) } }
        return MultipartBody.Part.createFormData("image", file.name, file.asRequestBody((resolver.getType(uri) ?: "image/jpeg").toMediaType()))
    }
}

private fun ExpenseDto.toEntity() = ExpenseEntity(id, amount, category, imageUrl, note, expenseDate)
