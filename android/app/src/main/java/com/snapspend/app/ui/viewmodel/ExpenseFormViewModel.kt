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

    /** Điền số tiền tách từ hóa đơn nếu người dùng chưa nhập. */
    fun prefillAmount(value: Long) {
        if (amount.value.isBlank()) handle["amount"] = value.toString()
    }

    fun amountOk(): Boolean = (amount.value.toLongOrNull() ?: 0L) > 0

    fun canSave(): Boolean = !_loading.value && amountOk() && category.value.isNotBlank()

    /** Gọi AI phân loại ảnh + ghi chú; kết quả điền sẵn vào category để người dùng xem/sửa. */
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

    fun reset() { handle["amount"] = ""; handle["category"] = ""; handle["note"] = ""; handle["ocrText"] = ""; handle["candidates"] = emptyList<String>(); _confidence.value = null }
}
