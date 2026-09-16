# 09 — Kết quả fix lỗi + test toàn diện

> Đối chiếu 1-1 với `08_KE_HOACH_FIX_LOI_TEST_TOAN_DIEN.md` (cùng folder `docs/2`).
> Quy ước: `⬜ Chưa làm | 🔄 Đang làm | ✅ Đạt | ❌ Fail (ghi rõ lỗi)`
> Base code: commit `d3360c1` (08 sửa đổi lần 2: cascade nhẹ).
> Đáp án nhận diện: `../3/expected.json` (13 bill).

---

## F0 — Chuẩn bị

### Baseline test (ngày chạy: 2026-09-16)
- Trạng thái: ✅ Đạt
- `dotnet test`: **32 passed / 0 failed** (13 ApiTests + 19 ClassifyTests)
- `gradlew :app:testDebugUnitTest`: **11 passed / 0 failed** (ReceiptOcrTest 7 + FormatTest 4)
- `gradlew :app:assembleDebug`: ✅ xanh (BUILD SUCCESSFUL)
- Ghi chú: lúc chạy baseline API dev đang giữ DLL (`dotnet SnapSpend.Api.dll`, PID 20696) gây lỗi MSB3027 — đã dừng process rồi chạy lại xanh. Khi chạy test nhớ tắt API dev trước.

### Đáp án `expected.json`
- Trạng thái: ✅ Đạt (người dùng chốt 2026-09-16, giữ nguyên tên món trong `needsReview`)
- Đủ 13 bill (file/merchant/total/items/category): ✅ (tổng khớp đáp án chat 2026-09-16; tổng các món đã cộng tay kiểm tra: bill1.jpg 326k, bill1.png 1.053k, bill2.jpg 1.795k, bill3.jpg 54k, bill4.webp 324.555, bill5.webp 7.751k, bill6.png 3.584k, bill7.jpg 1.284k, bill8.jpg 136k, bill9.jpeg 260k, bill10.webp 1.550k, bill12.png 6.407.500, bill13.jpg 225k)
- Tên món chưa chắc (cần duyệt): bill2 dòng 3, bill4 dòng 3, bill5 (dòng 5,7,8,10,17), bill7 toàn bộ viết tay (ghi trong `needsReview` của file JSON).
- Ghi chú:

**Kết quả F0:** ✅ | Ngày nghiệm thu: 2026-09-16 | Người duyệt: user

---

## F1 — SỐ TIỀN (tầng 0 + tầng 1)

- Trạng thái: ⬜
- File đã tạo/sửa:
- Kết quả test theo checklist 08-F1:
  - [ ] Unit ≥ 12 case xanh
  - [ ] `gradlew test` xanh toàn bộ
  - [ ] Appium bill7/bill13/bill5: đúng HOẶC trống + hint
- Kết quả đã đạt:
- Ghi chú:

**Kết quả F1:** ⬜

---

## F2 — DANH MỤC + NỘI DUNG + TÁCH KHOẢN

- Trạng thái: ⬜
- File đã tạo/sửa:
- Kết quả test theo checklist 08-F2:
  - [ ] `dotnet test` thêm ≥ 6 case bulk + 13/13 classify
  - [ ] `gradlew test` parseItems/validateTotal/summarize xanh
  - [ ] Appium tách N khoản đúng tổng
  - [ ] `/ai/extract`: lab JSON đúng; mặc định tắt → fallback + reason
- Kết quả đã đạt:
- Ghi chú:

**Kết quả F2:** ⬜

---

## F3 — Fix bug nền (B1, B2, B3, B4, B5, B6, B8)

- Trạng thái: ⬜
- File đã tạo/sửa:
- Kết quả test theo checklist 08-F3:
  - [ ] Server test search/filter + page
  - [ ] Debounce: gõ nhanh → 1 request
  - [ ] Stats lọc ngày đủ khoản
  - [ ] Bulk xóa → Hoàn tác đủ ảnh + aiConfidence
  - [ ] Build + test xanh 2 phía
  - [ ] Ollama tắt mặc định vẫn 200 fallback
  - [ ] Ảnh đúng `STORAGE_BASE_URL` (ghi rõ máy thật nếu chưa test được)
- Kết quả đã đạt:
- Ghi chú:

**Kết quả F3:** ⬜

---

## F4 — Test toàn diện

### F4.1 Server (`dotnet test`)
- Trạng thái: ⬜ | __ passed / __ failed
- Ghi chú:

### F4.2 Android (`gradlew test`)
- Trạng thái: ⬜ | __ passed / __ failed
- `kotlinx-coroutines-test` version đã pin: ____
- Ghi chú:

### F4.3 Smoke API thật
- Trạng thái: ⬜
- Ghi chú (thời gian analyze-full lần 2: ____ms):

### F4.4 E2E Appium
- Trạng thái: ⬜
- e2e_real_flow.py: __/__
- Luồng hóa đơn 3 ảnh: __/__
- Phân trang/debounce/darkmode/offline: __/__
- Ghi chú:

### F4.5 Gate nghiệm thu nhận diện (quan trọng nhất)
- Trạng thái: ⬜
- **Số tiền**: __/13 đúng (bill7: ⬜ trống + hint); tự điền sai im lặng: __ case
- **Danh mục**: __/13 đúng
- **Nội dung**: __/13 sạch MST/mã + có tên món thật
- Bảng chi tiết từng ảnh:

| Bill | Tổng (đúng/sai/trống) | Danh mục | Nội dung | Ghi chú |
|------|------------------------|----------|----------|---------|
| bill1.jpg | | | | |
| bill1.png | | | | |
| bill2.jpg | | | | |
| bill3.jpg | | | | |
| bill4.webp | | | | |
| bill5.webp | | | | |
| bill6.png | | | | |
| bill7.jpg | | | | |
| bill8.jpg | | | | |
| bill9.jpeg | | | | |
| bill10.webp | | | | |
| bill12.png | | | | |
| bill13.jpg | | | | |

**Kết quả F4:** ⬜

---

## F5 — Tài liệu & chốt

- Trạng thái: ⬜
- [ ] README (Ollama profile, STORAGE_BASE_URL, endpoint mới)
- [ ] Ghi chú bổ sung vào 04/07 (không sửa kết quả cũ)
- [ ] AGENTS.md (nếu đổi quy ước)
- [ ] Bảng mã A/B → PASS/FAIL + bằng chứng (dưới đây)

| Mã | Kết quả | Bằng chứng |
|----|---------|------------|
| A1 | | |
| A2 | | |
| A3 | | |
| A4 | | |
| A5 | | |
| A6 | | |
| A7 | | |
| B1 | | |
| B2 | | |
| B3 | | |
| B4 | | |
| B5 | | |
| B6 | | |
| B8 | | |

**Kết quả F5:** ⬜

---

## Tổng hợp

| Bước | Trạng thái |
|------|------------|
| F0 | |
| F1 | |
| F2 | |
| F3 | |
| F4 | |
| F5 | |
