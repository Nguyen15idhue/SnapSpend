package com.snapspend.app.data.mock

import com.snapspend.app.data.remote.AnalysisDto
import com.snapspend.app.data.remote.ExpenseDto
import com.snapspend.app.data.remote.FriendDto
import java.time.LocalDate

/**
 * Data mẫu VNĐ để demo, không cần backend.
 * Ngày tính tương đối từ hôm nay để Stats theo tháng luôn có số liệu.
 */
object MockData {
    private fun d(daysAgo: Long): String = LocalDate.now().minusDays(daysAgo).toString()

    val expenses: List<ExpenseDto> = listOf(
        ExpenseDto(1, 65000, "food", null, "Phở Thìn", d(1), 0.92),
        ExpenseDto(2, 87000, "transport", null, "Grab đi làm", d(1), 0.88),
        ExpenseDto(3, 45000, "food", null, "Cơm tấm sườn", d(2), 0.9),
        ExpenseDto(4, 68000, "entertainment", null, "Trà sữa với team", d(2), 0.71),
        ExpenseDto(5, 10000, "transport", null, "Gửi xe", d(3), 0.66),
        ExpenseDto(6, 79000, "food", null, "Highlands trung nguyên", d(4), 0.85),
        ExpenseDto(7, 120000, "transport", null, "Đổ xăng", d(5), 0.78),
        ExpenseDto(8, 799000, "shopping", null, "Zara áo khoác", d(6), 0.95),
        ExpenseDto(9, 132000, "food", null, "GrabFood tối", d(7), 0.83),
        ExpenseDto(10, 642000, "housing", null, "Tiền điện tháng này", d(8), 0.9),
        ExpenseDto(11, 180000, "housing", null, "Tiền nước", d(8), 0.87),
        ExpenseDto(12, 45000, "transport", null, "Be đi học", d(9), 0.8),
        ExpenseDto(13, 249000, "shopping", null, "Shopee đồ gia dụng", d(10), 0.86),
        ExpenseDto(14, 220000, "housing", null, "Internet", d(11), 0.84),
        ExpenseDto(15, 55000, "food", null, "Bún bò Huế", d(12), 0.89),
        ExpenseDto(16, 210000, "entertainment", null, "CGV cuối tuần", d(13), 0.91),
        ExpenseDto(17, 185000, "health", null, "Pharmacity vitamin", d(14), 0.82),
        ExpenseDto(18, 199000, "shopping", null, "Áo thun", d(15), 0.77),
        ExpenseDto(19, 150000, "bills", null, "Nạp điện thoại", d(16), 0.75),
        ExpenseDto(20, 260000, "bills", null, "Netflix", d(18), 0.88),
        ExpenseDto(21, 500000, "health", null, "Khám răng", d(20), 0.81),
        ExpenseDto(22, 320000, "education", null, "Sách Clean Code", d(21), 0.87),
        ExpenseDto(23, 100000, "other", null, "Cắt tóc", d(22), 0.62),
        ExpenseDto(24, 899000, "education", null, "Khóa học Android", d(25), 0.85)
    )

    val initialFriends: List<FriendDto> = listOf(
        FriendDto(2, "minh_tran", "minh@snapspend.vn"),
        FriendDto(3, "lan_anh", "lananh@snapspend.vn"),
        FriendDto(4, "duc_minh", "ducminh@snapspend.vn")
    )

    /** Username tồn tại trên "server giả lập" — dùng để demo thêm bạn thành công. */
    val knownUsernames: Map<String, FriendDto> = mapOf(
        "minh_tran" to FriendDto(2, "minh_tran", "minh@snapspend.vn"),
        "lan_anh" to FriendDto(3, "lan_anh", "lananh@snapspend.vn"),
        "duc_minh" to FriendDto(4, "duc_minh", "ducminh@snapspend.vn"),
        "thu_ha" to FriendDto(5, "thu_ha", "thuha@snapspend.vn"),
        "quang_huy" to FriendDto(6, "quang_huy", "quanghuy@snapspend.vn")
    )

    val analysis = AnalysisDto(
        summary = "Tháng này bạn chi tiêu nhiều nhất cho nhóm Nhà ở và Shopping. Chi food ổn định, không có giao dịch bất thường lớn.",
        trends = listOf(
            "Nhóm housing chiếm tỷ trọng cao nhất do tiền điện, nước, internet dồn đầu tháng.",
            "Shopping tăng so với tuần trước (Zara, Shopee).",
            "Food đều 45k-130k/ngày, phù hợp nhịp sinh hoạt."
        ),
        anomalies = listOf(
            "Khóa học Android 899k là khoản education lớn nhất 30 ngày qua.",
            "Ngày chi nhiều nhất gấp 3 lần trung bình ngày (do đóng tiền điện + nước)."
        ),
        recommendations = listOf(
            "Đặt hạn mức shopping 1 triệu/tháng để tránh vượt.",
            "Gom hóa đơn điện/nước/internet vào 1 ngày để dễ theo dõi.",
            "Duy trì food dưới 150k/ngày như hiện tại."
        )
    )
}
