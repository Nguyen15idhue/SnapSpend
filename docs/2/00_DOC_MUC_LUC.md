# docs/2 — Mục lục tài liệu (đánh số thứ tự)

> Đặt tên: `SS_TEN.md` — số nhỏ là thứ tự đọc. Tài liệu mới luôn thêm số tiếp theo.
> Cập nhật lần cuối: 2026-09-16.

| STT | File | Loại | Nội dung | Trạng thái |
|-----|------|------|----------|------------|
| 00 | `00_DOC_MUC_LUC.md` | Index | File này — danh mục + quy ước số | Luôn cập nhật khi thêm tài liệu |
| 01 | `01_PHAN_TICH_HIEN_TRANG.md` | Phân tích | Hiện trạng mã nguồn, mã vấn đề (FND/AND/SRV/DB/DOC/TST) | Đã chốt (trước 2026-09-15) |
| 02 | `02_KE_HOACH_HOAN_THIEN.md` | Kế hoạch | Kế hoạch GĐ1–GĐ6 gốc (từ mã vấn đề 01) | Đã chốt |
| 03 | `03_CAC_BUOC_CAN_LAM.md` | Kế hoạch | Checklist bước làm chi tiết GĐ1–GĐ6 | Đã chốt (phạm vi rút gọn) |
| 04 | `04_KET_QUA_DAT_DUOC.md` | Kết quả | Nghiệm thu 1-1 với 03 (GĐ1–GĐ6, 23 bước ✅) | Hoàn thành 2026-09-16 |
| 05 | `05_RECEIPT_AI_KE_HOACH_TONG_QUAN.md` | Kế hoạch | Receipt Intelligence: OCR → trích xuất → tách khoản → phân tích | Đã chốt |
| 06 | `06_RECEIPT_AI_CAC_BUOC.md` | Kế hoạch | Checklist bước R1–R3 của 05 | Đã chốt |
| 07 | `07_RECEIPT_AI_KET_QUA.md` | Kết quả | Nghiệm thu 1-1 với 06 (R1–R3 ✅, 13/13 ảnh mẫu) | Hoàn thành 2026-09-16 |
| 08 | `08_KE_HOACH_FIX_LOI_TEST_TOAN_DIEN.md` | **Kế hoạch** | **Fix lỗi + test toàn diện; sửa đổi lần 3: engine DB duy nhất ở server, xóa Ollama; có bản đồ song song (bulk / note-gate / F3)** | 🔄 Đang làm (F1-backend ✅, F2/F3/F4 còn lại) |
| 09 | `09_KET_QUA_FIX_TEST_TOAN_DIEN.md` | Kết quả | Nghiệm thu 1-1 với 08 (tạo khi bắt đầu làm) | 🔄 Đang làm — F0 ✅, F1 🔄 (còn Appium) |

## Quy ước ghi kết quả

- Cứ 1 tài liệu "Kế hoạch/bước" (03, 06, 08) có đúng 1 tài liệu "Kết quả" đối chiếu 1-1 theo số bước (04, 07, 09).
- Trạng thái bước: `⬜ Chưa làm | 🔄 Đang làm | ✅ Đạt | ❌ Fail (ghi rõ lỗi) | ⛔ Ngoài phạm vi`.
- Không sửa nội dung tài liệu 01–07 đã nghiệm thu; bổ sung/Breaking change ghi vào 08.
