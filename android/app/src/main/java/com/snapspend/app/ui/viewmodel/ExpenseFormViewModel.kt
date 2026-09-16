package com.snapspend.app.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snapspend.app.data.repository.SnapSpendRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

class ExpenseFormViewModel(private val repo: SnapSpendRepository, private val handle: SavedStateHandle) : ViewModel() {
    val amount: StateFlow<String> = handle.getStateFlow("amount", "")
    // Rỗng = chưa chọn danh mục (chờ AI hoặc người dùng chọn).
    val category: StateFlow<String> = handle.getStateFlow("category", "")
    val note: StateFlow<String> = handle.getStateFlow("note", "")
    // Toàn bộ text OCR (dùng để phân loại), tách khỏi note hiển thị (đã tóm tắt).
    private val ocrText: StateFlow<String> = handle.getStateFlow("ocrText", "")
    private val _confidence = MutableStateFlow<Double?>(null)
    val confidence: StateFlow<Double?> = _confidence.asStateFlow()
    // Các danh mục AI phát hiện (hóa đơn có thể gồm nhiều loại).
    val candidates: StateFlow<List<String>> = handle.getStateFlow("candidates", emptyList<String>())
    private val _classifying = MutableStateFlow(false)
    val classifying: StateFlow<Boolean> = _classifying.asStateFlow()
    // Các món tách từ hóa đơn (tạm thời, không lưu SavedState vì không Parcelable).
    private val _items = MutableStateFlow<List<com.snapspend.app.data.ocr.ReceiptOcr.ReceiptItem>>(emptyList())
    val items: StateFlow<List<com.snapspend.app.data.ocr.ReceiptOcr.ReceiptItem>> = _items.asStateFlow()
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun onAmount(v: String) { handle["amount"] = v.filter(Char::isDigit); _error.value = null }
    // Người dùng tự chọn -> bỏ gợi ý AI.
    fun onCategory(v: String) { handle["category"] = v; _confidence.value = null }
    fun onNote(v: String) { handle["note"] = v }

    /** Lưu text OCR đầy đủ (để phân loại) + note tóm tắt (để hiển thị). */
    fun applyOcr(fullText: String, summary: String) {
        if (fullText.isNotBlank()) {
            handle["ocrText"] = fullText.take(600)
            if (summary.isNotBlank()) handle["note"] = summary
            _confidence.value = null
        }
    }

    fun setOcrText(fullText: String) {
        if (fullText.isNotBlank()) {
            handle["ocrText"] = fullText.take(600)
            _confidence.value = null
        }
    }

    /** Dùng mini model tách text OCR thành nội dung sạch + tổng; chỉ áp dụng khi hợp lệ. */
    suspend fun extractViaAi(text: String): Boolean {
        val res = runCatching { repo.extractReceipt(text) }.getOrNull() ?: return false
        if (res.fallback) return false
        val summary = buildString {
            res.merchant?.takeIf { it.isNotBlank() }?.let { append(it) }
            val names = res.items.mapNotNull { it.name.takeIf { n -> n.isNotBlank() } }.take(3)
            if (names.isNotEmpty()) {
                if (isNotEmpty()) append(" - ")
                append(names.joinToString(", "))
            }
        }.take(140)
        val totalOk = res.total != null && res.total in 1..9_999_999_999
        if (summary.isBlank() && !totalOk) return false
        if (summary.isNotBlank()) handle["note"] = summary
        if (totalOk && amount.value.isBlank()) handle["amount"] = res.total.toString()
        return true
    }

    /** Điền số tiền tách từ hóa đơn nếu người dùng chưa nhập. */
    fun prefillAmount(value: Long) {
        if (amount.value.isBlank()) handle["amount"] = value.toString()
    }

    fun amountOk(): Boolean = (amount.value.toLongOrNull() ?: 0L) > 0

    fun canSave(): Boolean = !_loading.value && amountOk() && category.value.isNotBlank()

    /** Phân tích hóa đơn thành danh sách món + danh mục từng món. */
    fun analyzeReceipt() {
        val ocr = ocrText.value.ifBlank { return }
        val parsed = com.snapspend.app.data.ocr.ReceiptOcr.parseItems(ocr)
        _items.value = parsed
        if (parsed.isEmpty()) return
        viewModelScope.launch {
            val cats = runCatching { repo.classifyItems(parsed.map { it.name }) }.getOrDefault(emptyList())
            // Lưu danh mục từng món vào map tạm để UI hiển thị; tách khi bấm nút.
            _itemCats.value = cats.associate { it.name to it.category }
        }
    }

    private val _itemCats = MutableStateFlow<Map<String, String>>(emptyMap())
    val itemCats: StateFlow<Map<String, String>> = _itemCats.asStateFlow()
    fun classify(uri: Uri?) {
        viewModelScope.launch {
            _classifying.value = true
            _error.value = null
            runCatching { repo.classify(uri, ocrText.value.ifBlank { note.value }.ifBlank { null }) }
                .onSuccess { handle["category"] = it.category; _confidence.value = it.confidence; handle["candidates"] = it.candidates }
                .onFailure { _error.value = it.message ?: "Phân loại thất bại" }
            _classifying.value = false
        }
    }

    fun submit(uri: Uri?, onDone: () -> Unit) {
        val a = amount.value
        val c = category.value
        val n = note.value
        if (!canSave()) return
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            runCatching { repo.createExpense(uri, a.toLong(), c, n.ifBlank { null }, LocalDate.now().toString()) }
                .onSuccess { onDone() }
                .onFailure { _error.value = it.message ?: "Không lưu được" }
            _loading.value = false
        }
    }

    /** Tách hóa đơn thành N khoản (mỗi món 1 khoản, giữ số tiền + danh mục từng món). */
    fun splitExpenses(onDone: () -> Unit) {
        val list = _items.value
        if (list.isEmpty()) return
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            runCatching {
                val date = LocalDate.now().toString()
                list.forEach { item ->
                    val cat = _itemCats.value[item.name] ?: "other"
                    repo.createExpense(null, item.amount, cat, item.name, date)
                }
            }.onSuccess { onDone() }
                .onFailure { _error.value = it.message ?: "Tách khoản thất bại" }
            _loading.value = false
        }
    }

    fun reset() { handle["amount"] = ""; handle["category"] = ""; handle["note"] = ""; handle["ocrText"] = ""; handle["candidates"] = emptyList<String>(); _confidence.value = null; _items.value = emptyList(); _itemCats.value = emptyMap() }
}
