package com.snapspend.app.model

import java.time.LocalDate

data class User(val id: Long, val username: String, val email: String)
data class Expense(
    val id: Long,
    val amount: Long,
    val category: String,
    val imageUrl: String?,
    val note: String?,
    val expenseDate: String,
    val aiConfidence: Double? = null
)

data class Category(val key: String, val label: String, val emoji: String)

data class Stats(
    val total: Long,
    val averageDaily: Double,
    val byCategory: Map<String, Long>,
    val byDay: Map<String, Long>
)

data class BehaviorAnalysis(
    val summary: String,
    val trends: List<String>,
    val anomalies: List<String>,
    val recommendations: List<String>
)

val categories = listOf(
    Category("food", "Ăn uống", "🍜"),
    Category("shopping", "Mua sắm", "🛍️"),
    Category("transport", "Đi lại", "🛵"),
    Category("entertainment", "Giải trí", "🎬"),
    Category("housing", "Nhà ở", "🏠"),
    Category("health", "Sức khỏe", "💊"),
    Category("education", "Giáo dục", "📚"),
    Category("bills", "Hóa đơn", "🧾"),
    Category("other", "Khác", "•")
)
