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
            .addOnSuccessListener { first ->
                // Lượt 1 đủ tốt (có dòng Tổng / nhiều số) thì dùng ngay cho nhanh.
                if (isUsableOcr(first.text)) { cont.resume(first.text); return@addOnSuccessListener }
                // Lượt 2: tiền xử lý (phóng to + xám + tương phản) rồi chọn bản tốt hơn.
                val bmp = preprocess(context, uri)
                if (bmp == null) { cont.resume(first.text); return@addOnSuccessListener }
                TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                    .process(InputImage.fromBitmap(bmp, 0))
                    .addOnSuccessListener { second ->
                        cont.resume(if (ocrScore(second.text) > ocrScore(first.text)) second.text else first.text)
                    }
                    .addOnFailureListener { cont.resume(first.text) }
            }
            .addOnFailureListener { cont.resume("") }
    }

    private fun isUsableOcr(text: String): Boolean {
        if (text.isBlank()) return false
        val lower = text.lowercase()
        // Có dòng Tổng và số tiền hợp lệ là đủ tốt.
        return TotalKeys.any { lower.contains(it) } && extractTotal(text) != null
    }

    private fun ocrScore(text: String): Int {
        if (text.isBlank()) return -1
        val lower = text.lowercase()
        var score = text.count(Char::isDigit)
        if (TotalKeys.any { lower.contains(it) }) score += 100
        return score
    }

    /** Phóng to ảnh nhỏ (< 1000px) + xám + tăng tương phản để ML Kit đọc tốt hơn. */
    private fun preprocess(context: Context, uri: Uri): android.graphics.Bitmap? {
        return try {
            val resolver = context.contentResolver
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
            val w = bounds.outWidth
            val h = bounds.outHeight
            if (w <= 0 || h <= 0) return null
            var sample = 1
            while (w / sample > 2000 || h / sample > 2000) sample *= 2
            val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
            var bmp = resolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, opts) } ?: return null
            // Xoay đúng chiều theo EXIF (fromBitmap không tự xoay như fromFilePath).
            val orient = resolver.openInputStream(uri)?.use { android.media.ExifInterface(it).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL) }
                ?: android.media.ExifInterface.ORIENTATION_NORMAL
            val deg = when (orient) {
                android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            val m = android.graphics.Matrix()
            val longSide = maxOf(bmp.width, bmp.height)
            if (longSide < 1000) m.preScale(2f, 2f)
            if (deg != 0f) m.postRotate(deg)
            if (!m.isIdentity) bmp = android.graphics.Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            // Xám + tương phản 1.2 quanh mức 128.
            val gray = android.graphics.Bitmap.createBitmap(bmp.width, bmp.height, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(gray)
            val cm = android.graphics.ColorMatrix().apply { setSaturation(0f) }
            val contrast = android.graphics.ColorMatrix(floatArrayOf(
                1.2f, 0f, 0f, 0f, -25.6f,
                0f, 1.2f, 0f, 0f, -25.6f,
                0f, 0f, 1.2f, 0f, -25.6f,
                0f, 0f, 0f, 1f, 0f))
            contrast.preConcat(cm)
            canvas.drawBitmap(bmp, 0f, 0f, android.graphics.Paint().apply { colorFilter = android.graphics.ColorMatrixColorFilter(contrast) })
            gray
        } catch (_: Exception) { null }
    }

    /** Mức tin cậy của số tiền đoán được: HIGH = tự điền; MEDIUM = tự điền + nhắc kiểm tra; LOW = không tự điền. */
    enum class AmountLevel { HIGH, MEDIUM, LOW }
    data class AmountGuess(val amount: Long, val level: AmountLevel)

    /** Đoán số tiền kèm mức tin cậy: 1) dòng Tổng (đối chiếu món) → 2) tổng các món → 3) số lớn nhất (LOW). */
    fun guessAmount(text: String): AmountGuess? {
        if (text.isBlank()) return null
        val total = extractTotal(text)
        if (total != null) {
            val items = parseItems(text)
            // Không tách được món nào thì không có gì để đối chiếu — vẫn tin dòng Tổng.
            val level = if (items.isEmpty() || validateTotal(total, items)) AmountLevel.HIGH else AmountLevel.MEDIUM
            return AmountGuess(total, level)
        }
        val sum = parseItems(text).sumOf { it.amount }
        if (sum > 0) return AmountGuess(sum, AmountLevel.MEDIUM)
        return largestNumber(text)?.let { AmountGuess(it, AmountLevel.LOW) }
    }

    /** Tách số tiền tổng từ text hóa đơn (ưu tiên dòng "tổng/total"). */
    fun extractAmount(text: String): Long? {
        val g = guessAmount(text) ?: return null
        // Mức LOW (số lớn nhất trôi nổi, không dòng Tổng/món) không tự điền để tránh sai im lặng.
        return if (g.level == AmountLevel.LOW) null else g.amount
    }

    /** Đối chiếu số dòng Tổng với tổng các món (lệch ≤ 5% là khớp — VAT/phí nhỏ vẫn qua). */
    fun validateTotal(total: Long, items: List<ReceiptItem>): Boolean {
        if (total <= 0) return false
        val sum = items.sumOf { it.amount }
        if (sum <= 0) return false
        return kotlin.math.abs(sum - total) * 100.0 / total <= 5.0
    }

    // Từ khóa dòng tổng ("cộng" bắt T.Cộng/Tổng cộng — số quyết toán in sau subtotal/VAT/phụ thu).
    private val TotalKeys = listOf("tổng", "cộng", "total", "thanh toán", "phải trả")

    /** Số ở dòng Tổng CUỐI cùng (né subtotal/VAT/phụ thu in trước); không có thì null. */
    fun extractTotal(text: String): Long? {
        if (text.isBlank()) return null
        // Khớp số có phân cách nghìn (50.000 / 1,234,567) hoặc số thường.
        val numberRegex = Regex("""\d{1,3}(?:[.,]\d{3})+|\d+""")
        fun numbers(s: String): List<Long> = numberRegex.findAll(s)
            .mapNotNull { m -> m.value.replace(".", "").replace(",", "").toLongOrNull() }
            .filter { it in 1000..9_999_999_999 }
            .toList()

        var last = emptyList<Long>()
        for (line in text.lowercase().lines()) {
            if (isCodeLine(line)) continue
            if (TotalKeys.any { line.contains(it) }) {
                val hit = numbers(line)
                if (hit.isNotEmpty()) last = hit
            }
        }
        return last.maxOrNull()
    }

    private fun largestNumber(text: String): Long? {
        val numberRegex = Regex("""\d{1,3}(?:[.,]\d{3})+|\d+""")
        return text.lowercase().lines()
            .filter { !isCodeLine(it) }
            .flatMap { line ->
                numberRegex.findAll(line)
                    .mapNotNull { it.value.replace(".", "").replace(",", "").toLongOrNull() }
                    .filter { it in 1000..9_999_999_999 }
            }
            .maxOrNull()
    }

    data class ReceiptItem(val name: String, val amount: Long)

    /** Tách danh sách món (tên + thành tiền), bỏ dòng tổng/ngày/mã. */
    fun parseItems(text: String): List<ReceiptItem> {
        if (text.isBlank()) return emptyList()
        val numberRegex = Regex("""\d{1,3}(?:[.,]\d{3})+|\d+""")
        fun amounts(s: String): List<Long> = numberRegex.findAll(s)
            .mapNotNull { it.value.replace(".", "").replace(",", "").toLongOrNull() }
            .filter { it in 1000..9_999_999_999 }
            .toList()
        val skip = listOf("tổng", "cộng", "total", "thanh toán", "phải trả", "ngày", "date", "năm", "year", "giờ", "mã số thuế", "mst", "ký hiệu", "tra cứu")
        return text.lines().map { it.trim() }
            .filter { it.length >= 6 }
            .filter { l -> skip.none { l.lowercase().contains(it) } && !isCodeLine(l) }
            .mapNotNull { line ->
                val list = amounts(line)
                if (list.isEmpty()) return@mapNotNull null
                val name = line.replace(numberRegex, " ").replace(Regex("[^\\p{L}\\p{N}\\s]"), " ").replace(Regex("\\s+"), " ").trim()
                if (name.count { it.isLetter() } < 3) return@mapNotNull null
                // Bảng hóa đơn VN xếp SL, đơn giá, thành tiền — số CUỐI dòng mới là thành tiền.
                // Giữ nguyên dòng trùng tên (2 dòng Coca = 2 món) để đối chiếu tổng đúng.
                ReceiptItem(name.take(80), list.last())
            }
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
        // đ/Đ KHÔNG phân rã trong Unicode FormD nên map tay (nếu không "tiền điện" khớp hụt "tien dien").
        java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase().replace("đ", "d")

    // Dòng mã/số hiệu (số hóa đơn, mã tra cứu, ngày...) — KHÔNG lấy số tiền từ đây.
    private val CodeLineNorm = listOf(
        "so (", "(no)", "so:", "ma so", "ma cqt", "ma hang", "ma khach hang", "khach hang",
        "ky hieu", "seri", "ma tra", "ngay", "date", "nam ", "dien thoai", "tel", "dia chi", "address"
    )

    private fun isCodeLine(s: String): Boolean {
        val n = normalize(s)
        return CodeLineNorm.any { n.contains(it) }
    }

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
