package com.snapspend.app.data.repository

import android.net.Uri
import com.snapspend.app.data.mock.MockData
import com.snapspend.app.data.remote.AnalysisDto
import com.snapspend.app.data.remote.AuthResponse
import com.snapspend.app.data.remote.ExpenseDto
import com.snapspend.app.data.remote.FriendDto
import com.snapspend.app.data.remote.StatsDto
import com.snapspend.app.data.remote.UserDto
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.time.LocalDate

/**
 * Implement giả lập cho demo: không mạng, không server.
 * Mọi hàm delay ~500ms để hiện loading, sửa list tại chỗ.
 * Đặt [failNext] = true để giả lập lỗi mạng cho màn error state.
 */
class MockRepository : SnapSpendRepository {

    var failNext: Boolean = false

    private val expenseState = MutableStateFlow(MockData.expenses)
    private val friendState = MutableStateFlow(MockData.initialFriends)
    private val sharedPairs = mutableSetOf<Pair<Long, Long>>()

    override val expenses: Flow<List<ExpenseDto>> = expenseState.map { list ->
        list.sortedWith(compareByDescending<ExpenseDto> { it.expenseDate }.thenByDescending { it.id })
    }

    private suspend fun gate() {
        delay(500)
        if (failNext) {
            failNext = false
            throw IOException("Lỗi mạng giả lập (mock).")
        }
    }

    override suspend fun login(email: String, password: String): AuthResponse {
        gate()
        val name = email.substringBefore('@').ifBlank { "demo" }
        return AuthResponse("mock-token-login", UserDto(1, name, email))
    }

    override suspend fun register(email: String, username: String, password: String): AuthResponse {
        gate()
        return AuthResponse("mock-token-register", UserDto(1, username, email))
    }

    override suspend fun refreshExpenses() {
        gate()
    }

    override suspend fun createExpense(uri: Uri?, amount: Long, category: String, note: String?, date: String): ExpenseDto {
        gate()
        require(amount > 0) { "Số tiền phải lớn hơn 0." }
        val guessed = if (category.isBlank() || category == "auto") guessCategory(note) else category to 1.0
        val nextId = (expenseState.value.maxOfOrNull { it.id } ?: 0) + 1
        val dto = ExpenseDto(nextId, amount, guessed.first, null, note, date, guessed.second)
        expenseState.value = listOf(dto) + expenseState.value
        return dto
    }

    override suspend fun updateExpense(id: Long, amount: Long, category: String, note: String?, date: String) {
        gate()
        val current = expenseState.value
        val old = current.find { it.id == id } ?: throw NoSuchElementException("Không tìm thấy chi tiêu.")
        expenseState.value = current.map {
            if (it.id == id) it.copy(amount = amount, category = category, note = note, expenseDate = date) else it
        }
        check(old.id == id)
    }

    override suspend fun deleteExpense(id: Long) {
        gate()
        val current = expenseState.value
        if (current.none { it.id == id }) throw NoSuchElementException("Không tìm thấy chi tiêu.")
        expenseState.value = current.filterNot { it.id == id }
    }

    override suspend fun stats(from: String, to: String): StatsDto {
        gate()
        val f = LocalDate.parse(from)
        val t = LocalDate.parse(to)
        val data = expenseState.value.filter {
            val d = LocalDate.parse(it.expenseDate)
            !d.isBefore(f) && !d.isAfter(t)
        }
        val total = data.sumOf { it.amount }
        val days = maxOf(1, t.toEpochDay() - f.toEpochDay() + 1).toDouble()
        return StatsDto(
            total = total,
            averageDaily = total / days,
            byCategory = data.groupBy { it.category }.mapValues { e -> e.value.sumOf { it.amount } },
            byDay = data.groupBy { it.expenseDate }.mapValues { e -> e.value.sumOf { it.amount } }
        )
    }

    override suspend fun analyze(from: String, to: String): AnalysisDto {
        gate()
        return MockData.analysis
    }

    override suspend fun friends(): List<FriendDto> {
        gate()
        return friendState.value
    }

    override suspend fun addFriend(username: String): FriendDto {
        gate()
        val key = username.trim()
        if (friendState.value.any { it.username.equals(key, ignoreCase = true) })
            throw IllegalStateException("Đã là bạn bè.")
        val found = MockData.knownUsernames.entries
            .find { it.key.equals(key, ignoreCase = true) }?.value
            ?: throw NoSuchElementException("Không tìm thấy người dùng.")
        friendState.value = friendState.value + found
        return found
    }

    override suspend fun shareExpense(id: Long, friendId: Long) {
        gate()
        if (expenseState.value.none { it.id == id }) throw NoSuchElementException("Không tìm thấy chi tiêu.")
        if (friendState.value.none { it.id == friendId }) throw NoSuchElementException("Không tìm thấy bạn bè.")
        sharedPairs.add(id to friendId)
    }

    override suspend fun deleteAccount() {
        gate()
        expenseState.value = emptyList()
        friendState.value = emptyList()
        sharedPairs.clear()
    }

    private fun guessCategory(note: String?): Pair<String, Double> {
        val s = (note ?: "").lowercase()
        if (s.contains("grab") || s.contains("be ") || s.contains("taxi") || s.contains("xe") || s.contains("xăng")) return "transport" to 0.8
        if (s.contains("phở") || s.contains("cơm") || s.contains("ăn") || s.contains("food") || s.contains("bún") || s.contains("trà sữa")) return "food" to 0.85
        if (s.contains("shop") || s.contains("mua") || s.contains("zara") || s.contains("quần áo") || s.contains("shopee")) return "shopping" to 0.82
        if (s.contains("điện") || s.contains("nước") || s.contains("internet") || s.contains("nhà")) return "housing" to 0.8
        if (s.contains("phim") || s.contains("cgv") || s.contains("game")) return "entertainment" to 0.78
        if (s.contains("thuốc") || s.contains("khám") || s.contains("bệnh")) return "health" to 0.8
        if (s.contains("học") || s.contains("sách") || s.contains("khóa")) return "education" to 0.8
        if (s.contains("netflix") || s.contains("điện thoại") || s.contains("hóa đơn")) return "bills" to 0.78
        return "other" to 0.4
    }
}
