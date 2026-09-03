package com.snapspend.app.data

/**
 * Công tắc Demo/Real.
 * - true (mặc định): dùng MockRepository, mở app là có data mẫu, không cần mạng.
 * - false: dùng RealRepository, cần chạy backend.
 * Toggle nằm trong màn Profile (làm ở Giai đoạn 4).
 */
object AppConfig {
    var isDemo: Boolean = true
}
