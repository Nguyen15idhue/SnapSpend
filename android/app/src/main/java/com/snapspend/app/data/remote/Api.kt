package com.snapspend.app.data.remote

import com.google.gson.annotations.SerializedName
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Part


data class AuthRequest(val email: String, val password: String)
data class RegisterRequest(val email: String, val username: String, val password: String)
data class AuthResponse(val token: String, val user: UserDto)
data class UserDto(val id: Long, val username: String, val email: String)

data class ExpenseDto(
    val id: Long,
    val amount: Long,
    val category: String,
    @SerializedName("imageUrl") val imageUrl: String?,
    val note: String?,
    @SerializedName("expenseDate") val expenseDate: String,
    @SerializedName("aiConfidence") val aiConfidence: Double?
)

data class ExpenseUpsertDto(val amount: Long, val category: String, val note: String?, val expenseDate: String)
data class PagedExpensesDto(
    val items: List<ExpenseDto>,
    val total: Int,
    val page: Int,
    @SerializedName("pageSize") val pageSize: Int
)
data class BulkDeleteRequest(val ids: List<Long>)
data class BulkDeleteResponse(val deleted: Int)
data class ExpenseRestoreDto(
    val amount: Long,
    val category: String,
    val note: String?,
    val expenseDate: String,
    val imageUrl: String?,
    val aiConfidence: Double?
)
data class StatsDto(
    val total: Long,
    val averageDaily: Double,
    val byCategory: Map<String, Long>,
    val byDay: Map<String, Long>
)
data class CategoryShareDto(val key: String, val name: String, val amount: Long, val share: Int)
data class TopExpenseDto(val id: Long, val amount: Long, val category: String, val note: String?, val date: String)
data class RecurringDto(val note: String, val count: Int, val total: Long)
data class BasicAnalysisDto(
    val total: Long,
    val averageDaily: Double,
    val dayCount: Int,
    val previousTotal: Long,
    val topCategory: String,
    val topShare: Int,
    val level: String,
    val breakdown: List<CategoryShareDto>,
    val topExpenses: List<TopExpenseDto>,
    val biggestDay: String?,
    val biggestDayAmount: Long,
    val weekendTotal: Long,
    val weekdayTotal: Long,
    val recurring: List<RecurringDto>,
    val summary: String
)
data class AiAnalysisDto(
    val basic: BasicAnalysisDto,
    val summary: String,
    val evaluation: String?,
    val trends: List<String>,
    val anomalies: List<String>,
    val recommendations: List<String>
)

data class FriendDto(val id: Long, val username: String, val email: String)
data class AddFriendRequest(val username: String)

data class CategoryDto(val key: String, val name: String, val emoji: String)
data class ClassificationDto(val category: String, val confidence: Double, val candidates: List<String> = emptyList())
data class ClassifyItemsRequest(val items: List<String>)
data class ItemCategoryDto(val name: String, val category: String, val confidence: Double)
data class ExtractReceiptRequest(val text: String)
data class ReceiptExtractItemDto(val name: String, val amount: Long)
data class ReceiptExtractDto(
    val merchant: String?,
    val date: String?,
    val items: List<ReceiptExtractItemDto>,
    val total: Long?,
    val fallback: Boolean
)
data class SharedExpenseDto(
    val id: Long,
    val amount: Long,
    val category: String,
    val imageUrl: String?,
    val note: String?,
    val expenseDate: String,
    val aiConfidence: Double?,
    val ownerUsername: String
)

data class ApiMessage(val message: String)

interface SnapSpendApi {
    @POST("auth/register") suspend fun register(@Body body: RegisterRequest): AuthResponse
    @POST("auth/login") suspend fun login(@Body body: AuthRequest): AuthResponse
    @GET("expenses") suspend fun expenses(
        @Query("page") page: Int,
        @Query("pageSize") pageSize: Int,
        @Query("search") search: String? = null,
        @Query("category") category: String? = null,
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
        @Query("sort") sort: String? = null
    ): PagedExpensesDto
    @Multipart
    @POST("expenses")
    suspend fun createExpense(
        @Part("amount") amount: RequestBody,
        @Part("category") category: RequestBody,
        @Part("note") note: RequestBody?,
        @Part("expenseDate") expenseDate: RequestBody,
        @Part image: MultipartBody.Part?
    ): ExpenseDto

    @PUT("expenses/{id}") suspend fun updateExpense(@Path("id") id: Long, @Body body: ExpenseUpsertDto): ExpenseDto
    @DELETE("expenses/{id}") suspend fun deleteExpense(@Path("id") id: Long): ApiMessage
    @POST("expenses/bulk-delete") suspend fun bulkDelete(@Body body: BulkDeleteRequest): BulkDeleteResponse
    @POST("expenses/restore") suspend fun restoreExpense(@Body body: ExpenseRestoreDto): ExpenseDto

    @GET("stats") suspend fun stats(@Query("from") from: String, @Query("to") to: String): StatsDto
    @POST("ai/analyze-basic") suspend fun analyzeBasic(@Query("from") from: String, @Query("to") to: String): BasicAnalysisDto
    @POST("ai/analyze-full") suspend fun analyzeFull(@Query("from") from: String, @Query("to") to: String): AiAnalysisDto

    @DELETE("account") suspend fun deleteAccount(): ApiMessage

    @GET("friends") suspend fun friends(): List<FriendDto>
    @POST("friends") suspend fun addFriend(@Body body: AddFriendRequest): FriendDto
    @POST("expenses/{id}/share/{friendId}") suspend fun shareExpense(@Path("id") id: Long, @Path("friendId") friendId: Long): ApiMessage

    @GET("categories") suspend fun categories(): List<CategoryDto>
    @GET("shared-with-me") suspend fun sharedWithMe(): List<SharedExpenseDto>

    @Multipart
    @POST("ai/classify")
    suspend fun classify(@Part("note") note: RequestBody?, @Part image: MultipartBody.Part?): ClassificationDto

    @POST("ai/classify-items")
    suspend fun classifyItems(@Body body: ClassifyItemsRequest): List<ItemCategoryDto>

    @POST("ai/extract")
    suspend fun extractReceipt(@Body body: ExtractReceiptRequest): ReceiptExtractDto
}
