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
data class AnalysisDto(
    val summary: String,
    val trends: List<String>,
    val anomalies: List<String>,
    val recommendations: List<String>
)

data class FriendDto(val id: Long, val username: String, val email: String)
data class AddFriendRequest(val username: String)

data class CategoryDto(val key: String, val name: String, val emoji: String)
data class ClassificationDto(val category: String, val confidence: Double, val candidates: List<String> = emptyList())
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
    @GET("expenses") suspend fun expenses(): List<ExpenseDto>
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
    @POST("expenses/restore") suspend fun restoreExpense(@Body body: ExpenseRestoreDto): ExpenseDto

    @GET("stats") suspend fun stats(@Query("from") from: String, @Query("to") to: String): StatsDto
    @POST("ai/analyze") suspend fun analyze(@Query("from") from: String, @Query("to") to: String): AnalysisDto

    @DELETE("account") suspend fun deleteAccount(): ApiMessage

    @GET("friends") suspend fun friends(): List<FriendDto>
    @POST("friends") suspend fun addFriend(@Body body: AddFriendRequest): FriendDto
    @POST("expenses/{id}/share/{friendId}") suspend fun shareExpense(@Path("id") id: Long, @Path("friendId") friendId: Long): ApiMessage

    @GET("categories") suspend fun categories(): List<CategoryDto>
    @GET("shared-with-me") suspend fun sharedWithMe(): List<SharedExpenseDto>

    @Multipart
    @POST("ai/classify")
    suspend fun classify(@Part("note") note: RequestBody?, @Part image: MultipartBody.Part?): ClassificationDto
}
