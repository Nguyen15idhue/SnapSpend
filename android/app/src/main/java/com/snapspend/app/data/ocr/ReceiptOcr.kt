package com.snapspend.app.data.ocr

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * OCR hóa đơn chạy trên máy bằng ML Kit (offline, không quota).
 * Đọc ảnh -> text; tách số tiền tổng nếu nhận diện được.
 */
object ReceiptOcr {

    suspend fun readText(context: Context, uri: Uri): String = suspendCancellableCoroutine { cont ->
        val image = InputImage.fromFilePath(context, uri)
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            .process(image)
            .addOnSuccessListener { cont.resume(it.text) }
            .addOnFailureListener { cont.resume("") }
    }

    /** Tách số tiền tổng từ text hóa đơn (ưu tiên dòng "tổng/total"). */
    fun extractAmount(text: String): Long? {
        if (text.isBlank()) return null
        // Khớp số có phân cách nghìn (50.000 / 1,234,567) hoặc số thường.
        val numberRegex = Regex("""\d{1,3}(?:[.,]\d{3})+|\d+""")
        fun numbers(s: String): List<Long> = numberRegex.findAll(s)
            .mapNotNull { m -> m.value.replace(".", "").replace(",", "").toLongOrNull() }
            .filter { it in 1000..99_999_999_999 }
            .toList()

        val lines = text.lowercase().lines()
        // Ưu tiên dòng "tổng/total" trước, rồi mới tới "thanh toán/phải trả".
        var pool = emptyList<Long>()
        for (kw in listOf("tổng", "total", "thanh toán", "phải trả")) {
            val hit = lines.filter { it.contains(kw) }.flatMap { numbers(it) }
            if (hit.isNotEmpty()) { pool = hit; break }
        }
        if (pool.isEmpty()) pool = lines.flatMap { numbers(it) }
        return pool.maxOrNull()
    }

    // Dòng tiêu đề/boilerplate (đã bỏ dấu) — bỏ khi tóm tắt. So khớp không phụ thuộc dấu vì OCR hay sai dấu.
    private val BoilerplateNorm = listOf(
        "ten don vi", "ma so thu", "mst", "dia chi", "address", "so dien thoai", "dien thoai", "tel",
        "can cuoc", "cccd", "hinh thuc thanh toan", "nguoi mua", "buyer", "nguoi ban", "seller",
        "kinh gui", "ma tra cuu", "ky hieu", "cong tien", "thue suat", "tong tien", "viet bang chu",
        "tra cuu", "signature", "ngay", "date", "hoa don", "invoice", "stt", "don gia", "so luong",
        "ma cqt", "ten hang hoa", "description", "unit", "quantity", "code", "(no)", "(tar", "(pay", "(buyer"
    )

    private fun normalize(s: String): String =
        java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()

    /** Tóm tắt nội dung chi tiêu (chỉ giữ tên hàng/món), bỏ tiêu đề và thông tin hành chính. */
    fun summarize(text: String, maxLen: Int = 120): String {
        if (text.isBlank()) return ""
        val items = text.lines().map { it.trim() }
            .filter { it.length >= 4 }
            .filter { l -> val n = normalize(l); BoilerplateNorm.none { n.contains(it) } }
            .filter { l -> l.count { it.isLetter() } >= 3 }
        val joined = items.joinToString(", ").replace(Regex("\\s+"), " ").trim()
        return (if (joined.isBlank()) text else joined).take(maxLen)
    }
}
