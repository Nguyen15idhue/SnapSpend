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

- Trạng thái: 🔄 (còn mục Appium chưa kiểm chứng — xem dưới)
- File đã tạo/sửa:
  - `data/ocr/ReceiptOcr.kt` — thêm `AmountLevel`/`AmountGuess`/`guessAmount`/`validateTotal`; `extractTotal` lấy số dòng Tổng CUỐI + nhận `cộng` (T.Cộng); `parseItems` lấy số CUỐI dòng + giữ dòng trùng tên; `readText` 2 lượt (gốc → tiền xử lý upscale×2/grayscale/contrast + xoay EXIF, chọn bản tốt hơn); `extractAmount` trả null khi LOW.
  - `ui/viewmodel/ExpenseFormViewModel.kt` — thêm `amountHint`/`flagAmountHint`; xóa hint khi sửa/tự điền/reset/OCR mới.
  - `ui/screens/FormScreen.kt` — nối `guessAmount`: HIGH điền ngay, MEDIUM điền + hint, LOW/null để trống + hint; hint hiện dưới ô số tiền (màu tertiary).
  - `app/src/test/.../data/ocr/ReceiptOcrTest.kt` — thêm 13 test.
- Kết quả test theo checklist 08-F1:
  - [x] Unit 13 case mới xanh (số cuối dòng, giữ dòng trùng bill13, validate khớp/lệch/ngưỡng 5%, dòng Tổng cuối, T.Cộng, 4 case thang HIGH/MEDIUM/LOW/null).
  - [x] `gradlew :app:testDebugUnitTest` xanh: **24/24** (ReceiptOcrTest 20 + FormatTest 4).
  - [x] `gradlew :app:assembleDebug` xanh (BUILD SUCCESSFUL).
  - [ ] Appium bill7/bill13/bill5: **chưa kiểm chứng được** — không có emulator chạy (`adb devices` trống), API chưa chạy, RAM trống ~1.6GB không đủ boot emulator ổn định. Bù tạm: 13 unit test mã hóa đúng scenario 3 bill này ở tầng parser.
- Kết quả đã đạt: parser 2 quy tắc + đối chiếu tổng + thang tin cậy hoạt động, có unit test bao phủ; UI nhắc kiểm tra khi tin cậy thấp.
- Ghi chú (điều chỉnh có lý do so với 08-F1): 08 ghi "lệch → KHÔNG tự điền", nhưng bill4 (VAT +13%), bill6 (phụ thu +10%), bill8 (tiền giờ +9%) có tổng đúng mà tổng món lệch — từ chối sẽ rớt gate F4.5 (cần 12/13). Nên MEDIUM = **vẫn tự điền + hint bắt kiểm tra**; chỉ LOW (không dòng Tổng/món) mới để trống. Không tự điền sai im lặng trong mọi case.
- Ghi chú: `extractAmount` cũ có fallback số lớn nhất nhưng UI không dùng (FormScreen chỉ dùng `extractTotal`) — nay `extractAmount` trả null khi LOW, 7 test cũ vẫn xanh.
- **Bug thật từ test tay user (bill7 viết tay, 2026-09-16):** ô tiền tự điền **90000** (sai, đúng phải trống) + note bị đè thành "Mì, Bún, Tôm", dù hint LOW hiện đúng. Nguyên nhân: luật nội bộ đã từ chối (LOW) nhưng `extractViaAi` tin luôn số Ollama 0.5B bịa ra. **Đã fix:** số tiền AI chỉ dùng khi khớp `guessAmount` luật ±5% (`ExpenseFormViewModel.extractViaAi`). Test tay lại bill7 đang chờ user. Note AI bịa ("Mì, Bún, Tôm") chuyển sang F2 (cổng kiểm note).

**Kết quả F1:** 🔄 | Còn lại: Appium 3 bill (chạy khi có emulator + API).

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

- Trạng thái: 🔄 (code + test xong; còn 4 mục UI/emulator chưa kiểm chứng — xem dưới)
- File đã tạo/sửa:
  - `server/.../Endpoints/ExpenseEndpoints.cs` — `GET /api/expenses` thêm `search/category/from/to/sort` (giữ `PagedExpensesDto`); search bỏ dấu trong bộ nhớ (khớp cả "phở" lẫn "pho", chạy được Postgres lẫn InMemory); category lạ/ngày sai/`from > to` → 400 `{message}`.
  - `server/.../Endpoints/StatsEndpoints.cs` — gỡ `POST /ai/analyze` cũ (B4; client chỉ dùng analyze-basic/full).
  - `server/.../appsettings.json` — `Storage:BaseUrl` về `http://localhost:5080/uploads` (B5).
  - `server/.env.example` + `README.md` — ghi rõ `STORAGE_BASE_URL` cho emulator (`10.0.2.2`) / máy thật (IP LAN).
  - `android/.../data/remote/Api.kt` — `expenses()` thêm query `search/category/from/to/sort` (optional, tương thích lùi).
  - `android/.../data/repository/{SnapSpendRepository,RealRepository}.kt` — `loadPage()` chuyển search/filter/sort lên server; thêm `dayExpenses(date)`; xóa `refreshAllExpenses` (hết giới hạn ngầm 1000 — B6).
  - `android/.../ui/viewmodel/AlbumViewModel.kt` — xóa `ensureFullForSearch`/`isSearching`; query debounce 400ms (`Flow.debounce`, không thêm lib); đổi query/filter/sort/page đều reload trang server; giữ lùi trang khi xóa hết trang cuối.
  - `android/.../ui/viewmodel/StatsViewModel.kt` + `ui/screens/StatsScreen.kt` — bấm cột ngày gọi `dayExpenses()` qua server (đủ khoản cả trang khác) + trạng thái loading/error/Thử lại (B2).
  - `android/.../ui/screens/AlbumScreen.kt` — snackbar bulk delete có "Hoàn tác"; dialog sửa thành "Có thể hoàn tác ngay sau khi xóa"; thanh trang hiện cả khi tìm/lọc (B3).
  - `android/.../ui/viewmodel/AlbumViewModel.kt` — `undoBulkDelete()` khôi phục từng snapshot qua `restoreExpense`, lỗi giữa chừng báo số khôi phục được (B3).
  - `server/tests/.../ApiTests.cs` — thêm 2 test F3 (search có/bỏ dấu; filter category + ngày + trang + case 400).
- Kết quả test theo checklist 08-F3:
  - [x] Server test search/filter + page: `ApiTests` **19/19 xanh** (17 cũ + 2 mới F3).
  - [x] Smoke API thật (Postgres docker, API chạy local tạm port 5090, xong đã tắt + xóa dữ liệu test): search `pho` → 1, search `phở` → 1, `category=food` → 2, `from=to=2026-09-15` → total 1 + page đúng 1 item; `category=zzz`/ngày sai/`from > to` → 400; `POST /ai/analyze` cũ → 404; bulk-delete 3 → `deleted=3`, restore từng cái → 3/3, total về 3.
  - [ ] Debounce: gõ nhanh → 1 request — **chưa kiểm chứng được** (cần đếm request trên log API + emulator; logic `debounce(400)` + `drop(1)` đã vào code).
  - [ ] Stats lọc ngày đủ khoản trên UI — **chưa kiểm chứng được** (cần emulator bấm cột ngày; server `from/to` đã smoke OK).
  - [ ] Bulk xóa 3 → Hoàn tác đủ ảnh + aiConfidence trên UI — **chưa kiểm chứng được** (cần emulator bấm snackbar; API restore đã smoke 3/3).
  - [x] Build + test xanh 2 phía: `dotnet build` API 0 warning/0 error; `gradlew :app:assembleDebug` BUILD SUCCESSFUL; `testDebugUnitTest` **24/24** (ReceiptOcr 20 + Format 4).
  - [ ] Ollama tắt mặc định vẫn 200 fallback — N/A (B8 đã gỡ Ollama hẳn ở nhánh khác; `/ai/extract` server không còn, client `extractReceipt` trả null an toàn, ngoài phạm vi F3).
  - [ ] Ảnh đúng `STORAGE_BASE_URL` — **chưa kiểm chứng được** (cần emulator/máy thật tải ảnh; config + tài liệu đã sửa).
- Ghi chú: full `dotnet test` còn 25 fail `BillFixtureTests` + 1 fail `RecognitionTests.Debug_noise` — thuộc engine F1/F2 đang làm song song (file `RecognitionService`, không chạm trong F3); `ApiTests` của F3 xanh 19/19.
- Kết quả đã đạt: tìm/lọc/phân trang qua server (hết spam request + hết giới hạn 1000); Stats ngày đủ dữ liệu; bulk delete hoàn tác được; hết dead code `/ai/analyze`; config ảnh đúng quy ước.

**Kết quả F3:** 🔄

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
