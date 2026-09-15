package com.snapspend.app.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
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
import com.snapspend.app.model.categories as localCategories
import java.io.File
import java.io.IOException

/**
 * Implement thật: Retrofit + Room, cần backend chạy.
 * Logic giữ nguyên từ Repository cũ, chỉ đổi tên + implements [SnapSpendRepository].
 */
class RealRepository(private val context: Context, private val db: AppDatabase, private val api: SnapSpendApi) : SnapSpendRepository {
    override val expenses: Flow<List<ExpenseDto>> = db.expenseDao().observeAll().map { list ->
        list.map { ExpenseDto(it.id, it.amount, it.category, it.imageUrl, it.note, it.expenseDate, it.aiConfidence) }
    }

    override suspend fun login(email: String, password: String) = net { api.login(AuthRequest(email, password)) }
    override suspend fun register(email: String, username: String, password: String) = net { api.register(RegisterRequest(email, username, password)) }

    override suspend fun refreshExpenses() {
        val remote = net { api.expenses() }
        db.expenseDao().replaceAll(remote.map { it.toEntity() })
    }

    override suspend fun createExpense(uri: Uri?, amount: Long, category: String, note: String?, date: String): ExpenseDto {
        val (imagePart, tempFile) = uri?.let { createImagePart(it) } ?: (null to null)
        val result = try {
            net {
                api.createExpense(
                    amount.toString().toRequestBody("text/plain".toMediaType()),
                    category.toRequestBody("text/plain".toMediaType()),
                    note?.toRequestBody("text/plain".toMediaType()),
                    date.toRequestBody("text/plain".toMediaType()),
                    imagePart
                )
            }
        } finally {
            tempFile?.delete()
        }
        db.expenseDao().upsert(result.toEntity())
        return result
    }

    override suspend fun updateExpense(id: Long, amount: Long, category: String, note: String?, date: String) {
        val result = net { api.updateExpense(id, ExpenseUpsertDto(amount, category, note, date)) }
        db.expenseDao().upsert(result.toEntity())
    }

    override suspend fun deleteExpense(id: Long) {
        net { api.deleteExpense(id) }
        db.expenseDao().delete(id)
    }

    override suspend fun restoreExpense(expense: ExpenseDto): ExpenseDto {
        val result = net {
            api.restoreExpense(
                ExpenseRestoreDto(expense.amount, expense.category, expense.note, expense.expenseDate, expense.imageUrl, expense.aiConfidence)
            )
        }
        db.expenseDao().upsert(result.toEntity())
        return result
    }

    override suspend fun stats(from: String, to: String) = net { api.stats(from, to) }
    override suspend fun analyze(from: String, to: String) = net { api.analyze(from, to) }
    override suspend fun deleteAccount(): Unit { net { api.deleteAccount() } }
    override suspend fun friends() = net { api.friends() }
    override suspend fun addFriend(username: String) = net { api.addFriend(AddFriendRequest(username)) }
    override suspend fun shareExpense(id: Long, friendId: Long): Unit { net { api.shareExpense(id, friendId) } }

    // Real lấy category từ server; lỗi mạng thì fallback về list local để UI không trống.
    override suspend fun categories(): List<CategoryDto> =
        runCatching { net { api.categories() } }
            .getOrElse { localCategories.map { CategoryDto(it.key, it.label, it.emoji) } }

    override suspend fun sharedWithMe(): List<SharedExpenseDto> = net { api.sharedWithMe() }

    override suspend fun classify(uri: Uri?, note: String?): ClassificationDto {
        val (imagePart, tempFile) = uri?.let { createImagePart(it) } ?: (null to null)
        return try {
            net { api.classify(note?.toRequestBody("text/plain".toMediaType()), imagePart) }
        } finally {
            tempFile?.delete()
        }
    }

    /** Bọc mọi lỗi mạng/HTTP thành thông báo tiếng Việt thống nhất cho UI. */
    private suspend fun <T> net(block: suspend () -> T): T =
        try { block() } catch (e: IOException) { throw e }
        catch (e: Exception) { throw IOException(e.toUserMessage(), e) }

    /**
     * Nén ảnh trước khi upload: scale cạnh dài ≤ 1600px, xoay theo EXIF, nén JPEG ~80.
     * Trả về part + file tạm để caller xóa sau khi upload xong.
     */
    private fun createImagePart(uri: Uri): Pair<MultipartBody.Part, File> {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }

        var sample = 1
        val maxEdge = maxOf(bounds.outWidth, bounds.outHeight)
        while (maxEdge / sample > MAX_EDGE) sample *= 2

        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: throw IOException("Không đọc được ảnh đã chọn.")

        // Xoay theo EXIF để ảnh không bị nằm ngang.
        val orientation = resolver.openInputStream(uri)?.use {
            runCatching { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
                .getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
        val rotated = rotate(decoded, orientation)

        val file = File.createTempFile("expense_", ".jpg", context.cacheDir)
        file.outputStream().use { rotated.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        if (rotated !== decoded) decoded.recycle()
        rotated.recycle()
        return MultipartBody.Part.createFormData("image", file.name, file.asRequestBody("image/jpeg".toMediaType())) to file
    }

    private fun rotate(src: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            else -> return src
        }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
    }

    private companion object {
        const val MAX_EDGE = 1600
        const val JPEG_QUALITY = 80
    }
}

private fun ExpenseDto.toEntity() = ExpenseEntity(id, amount, category, imageUrl, note, expenseDate, aiConfidence)
