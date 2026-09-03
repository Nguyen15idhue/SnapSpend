# UI/UX SnapSpend — Kết quả đã đạt theo từng bước / từng giai đoạn

> Phạm vi: fix lỗi + UI mock đủ demo, không dữ liệu thật.
> Cách dùng: làm xong bước nào thì điền kết quả ngay dưới bước đó. Không dồn cuối kế hoạch mới ghi.
> Đối chiếu bước với `UIUX_Cacbuoccanlam.md`.
> Trạng thái hiện tại: **Giai đoạn 1-2 đã xong 2026-09-03 (build xanh, khung Mock chạy). Giai đoạn 3 chưa làm.**

Quy ước trạng thái: `⬜ Chưa làm | 🔄 Đang làm | ✅ Đạt | ❌ Fail (ghi rõ lỗi)`

---

## Giai đoạn 1 — Fix lỗi chặn

### Bước 1.1: Fix lỗi compile MainActivity
- Trạng thái: ✅ Đạt
- File tạo/sửa:
  - [x] `android/.../MainActivity.kt` — xóa 1 dòng `var confirmDelete`, thêm import `rememberScrollState`/`verticalScroll`, thêm import `PickVisualMediaRequest` + `LocalLifecycleOwner` (lifecycle), thêm import `formatVnd`
  - [x] `ui/format/Format.kt` (mới) — tách hàm `formatVnd()`, dùng `Locale.forLanguageTag("vi-VN")`
- Lỗi đã fix:
  - [x] E1 trùng `confirmDelete` (MainActivity.kt:310-311) — còn 1 khai báo duy nhất
  - [x] E2 FQN inline — `verticalScroll(androidx…)` về 0, chỉ còn import
- Kết quả test:
  - [x] Mở file không báo `Redeclaration` — KQ: PASS (grep còn đúng 1 dòng khai báo)
  - [x] Không còn warning FQN — KQ: PASS (`compileDebugKotlin` không còn warning)
- Ghi chú: tách Format.kt xong xóa luôn import `NumberFormat/Locale/DateTimeFormatter` thừa.

### Bước 1.2: Fix docs vận hành bị corrupt
- Trạng thái: ✅ Đạt
- File tạo/sửa:
  - [x] `docs/HUONG_DAN_VAN_HANH.md` — viết lại full (build debug, tài khoản mock, kịch bản demo 5 phút, toggle Demo/Real, xử lý sự cố)
- Lỗi đã fix:
  - [x] E3 docs lẫn manifest (dòng 1-18)
- Kết quả test:
  - [x] Preview markdown không thấy `<manifest>` — KQ: PASS (grep chỉ còn nhắc tên thẻ trong 2 file checklist)
  - [x] Có mục “Kịch bản demo” — KQ: PASS (mục 4, 7 bước)
- Ghi chú: giữ thêm mục 8 ghi chú kỹ thuật Room/Mock cho dev.

### Bước 1.3: Chốt cấu hình build chạy được
- Trạng thái: ✅ Đạt (build xanh, không hạ SDK như dự phòng)
- File tạo/sửa:
  - [x] `android/app/build.gradle.kts` — xóa plugin `org.jetbrains.kotlin.android` (AGP 9 tích hợp sẵn), `kotlinOptions` → `kotlin { jvmToolchain(17) }`, nâng `compileSdk/targetSdk` 36 → 37 (do compose-bom 2026.08.00 đòi SDK 37)
  - [ ] `android/build.gradle.kts` — giữ nguyên (không cần hạ Kotlin/AGP)
- Lỗi đã fix:
  - [x] E4 SDK/AGP quá mới — xử lý bằng nâng compileSdk 37 thay vì hạ, SDK tự tải
  - [x] E9 (mới, phát hiện khi build): `PickVisualMedia.ImageOnly` truyền trực tiếp — sửa sang `PickVisualMediaRequest(ImageOnly)` + import `androidx.activity.result.PickVisualMediaRequest`
  - [x] Warning `LocalLifecycleOwner` deprecated — chuyển sang `androidx.lifecycle.compose.LocalLifecycleOwner`; warning `Locale()` deprecated — chuyển `Locale.forLanguageTag("vi-VN")`
- Kết quả test:
  - [x] `./gradlew :app:assembleDebug` SUCCESS — KQ: PASS (`BUILD SUCCESSFUL in 6s`, APK `app-debug.apk` 15.8MB tại `app/build/outputs/apk/debug/`)
  - [ ] Cài APK lên emulator API ___ mở được app — KQ: chưa test (máy build không mở emulator trong đợt này)
- Ghi chú: JDK Temurin 17.0.16, Gradle 9.5.0 (tự tải), SDK Build-Tools 36 + platform 37 (tự tải, accept license tự động). Lần build đầu tốn ~2 phút tải, các lần sau 6-20s.

### Bước 1.4: Thêm quyền demo
- Trạng thái: ✅ Đạt (mức code + static check; test trên emulator chưa làm)
- File tạo/sửa:
  - [x] `android/app/src/main/AndroidManifest.xml` — thêm `READ_MEDIA_IMAGES` + `READ_EXTERNAL_STORAGE maxSdkVersion=32`, giữ `CAMERA/INTERNET`
- Lỗi đã fix:
  - [x] E5 thiếu quyền Android 13+
- Kết quả test:
  - [ ] Emulator API 33+ pick ảnh được — KQ: chưa test (chờ GĐ4 khi có emulator)
  - [ ] Từ chối camera hiện màn xin quyền, không crash — KQ: chưa test (code đã có nhánh `if (!hasCamera)` hiện nút xin quyền)
- Ghi chú: build đã merge manifest thành công (qua chặng `processDebugResources`).

**Kết quả Giai đoạn 1 (nghiệm thu mới sang GĐ2):**
- [x] Build xanh (`BUILD SUCCESSFUL`, APK 15.8MB), hết lỗi compile, docs viết lại
- [ ] Cài được + mở app không crash trắng — chưa test trên emulator (còn nợ)
- Ngày nghiệm thu: 2026-09-03 (build) | Người test: AI + user (cần user cài APK lên emulator xác nhận) | Ghi chú: sang GĐ2 được vì code đã build xanh; mục cài emulator dồn vào GĐ4 demo.

---

## Giai đoạn 2 — Dựng khung Mock

### Bước 2.1: Tách interface Repository
- Trạng thái: ✅ Đạt
- File tạo/sửa:
  - [x] `data/repository/SnapSpendRepository.kt` (mới, 12 hàm + `val expenses: Flow`)
- Kết quả test:
  - [x] Interface compile được, không import Context — KQ: PASS (grep `android.content.Context` = 0, build xanh)
- Ghi chú: interface dùng `android.net.Uri` cho chữ ký createExpense (không phải Context, chấp nhận được).

### Bước 2.2: Đổi Repository thành RealRepository
- Trạng thái: ✅ Đạt
- File tạo/sửa:
  - [x] `data/repository/RealRepository.kt` (mới, copy nguyên logic `Repository.kt` + implements interface)
  - [x] Xóa `data/Repository.kt` cũ
- Kết quả test:
  - [x] Không đổi behavior, build vẫn xanh — KQ: PASS (logic copy nguyên; 1 lỗi override `ApiMessage` vs `Unit` ở `deleteAccount/shareExpense` đã fix bằng block body `: Unit`)
- Ghi chú: `updateExpense` giữ nguyên trả Unit, các hàm còn lại giữ chữ ký cũ.

### Bước 2.3: Tạo MockRepository + MockData
- Trạng thái: ✅ Đạt (mức code + static check; test runtime trên emulator còn nợ)
- File tạo/sửa:
  - [x] `data/mock/MockData.kt` (24 expense VNĐ, ngày tương đối từ hôm nay, đủ 9 category)
  - [x] `data/repository/MockRepository.kt` (2 StateFlow + delay 500ms + flag `failNext`)
  - [x] `data/AppConfig.kt` (`isDemo = true` mặc định)
- Kết quả test:
  - [x] `expenses` emit 24 món — KQ: PASS static (đếm `ExpenseDto(` = 24; sort date desc/id desc trong flow)
  - [ ] `stats()` total = sum list — KQ: chưa chạy runtime (logic tính live từ StateFlow, filter theo from/to)
  - [ ] `create` +1 / `delete` -1 — KQ: chưa chạy runtime (code mutate StateFlow tại chỗ, throw khi thiếu id)
  - [ ] Có loading, không freeze — KQ: chưa chạy runtime (delay 500ms trong suspend, không block UI)
- Ghi chú (3 expense mẫu): Phở Thìn 65k/food; Grab đi làm 87k/transport; Zara áo khoác 799k/shopping. `analyze()` trả mẫu TV cố định. `addFriend`: trùng → “Đã là bạn bè”, lạ → “Không tìm thấy”, pool thêm được `thu_ha`, `quang_huy`. `deleteAccount()` reset list rỗng.

### Bước 2.4: Nối MainActivity sang Demo
- Trạng thái: ✅ Đạt (mức code + build; test airplane/toggle trên emulator còn nợ)
- File tạo/sửa:
  - [x] `MainActivity.kt onCreate` — `if (AppConfig.isDemo) MockRepository() else RealRepository(...)`, demo không khởi tạo Room/Retrofit
  - [x] Đổi 7 chữ ký composable `repo: Repository` → `repo: SnapSpendRepository`, `localExpenses` → `expenses`
- Kết quả test:
  - [ ] Airplane mode vẫn full data — KQ: chưa test (chờ emulator, code đã không chạm mạng khi demo)
  - [ ] Toggle Demo/Real không crash — KQ: chưa test (toggle UI làm ở GĐ4-Bước 4.7)
- Ghi chú: grep case-sensitive không còn `data.Repository`/`localExpenses`/`repo: Repository` trong MainActivity.

**Kết quả Giai đoạn 2:**
- [x] Khung Mock xong, build xanh (`BUILD SUCCESSFUL in 8s`), UI gọi duy nhất interface
- [ ] Mở app không mạng vẫn thấy data mẫu — chưa test trên emulator (còn nợ, dồn GĐ4)
- Ngày nghiệm thu: 2026-09-03 (code + build) | Ghi chú: sang GĐ3 được; nợ runtime test sẽ trả khi có emulator ở GĐ4.

---

## Giai đoạn 3 — Design System tối thiểu

### Bước 3.1: Semantic colors + Theme
- Trạng thái: ⬜ Chưa làm
- File tạo/sửa:
  - [ ] `ui/theme/Color.kt`, `ui/theme/Theme.kt` (mới)
  - [ ] `MainActivity.kt` — bọc `SnapSpendTheme`, thay hard-code
- Lỗi đã fix:
  - [ ] Quét `Color.Black/Gray` trong screens về 0
- Kết quả test:
  - [ ] Search hard-code = 0 — KQ: ___ (còn ___ chỗ)
  - [ ] Card border outline + bo 16-18 — KQ: ___ (kèm screenshot)
- Ghi chú (chốt mã primary đã dùng): ___

### Bước 3.2: Components dùng chung
- Trạng thái: ⬜ Chưa làm
- File tạo/sửa:
  - [ ] `ui/components/AppComponents.kt` — `CategoryChip, AmountText, EmptyState, LoadingShimmer, ErrorRetry, ConfirmDialog, BarChartV2`
- Kết quả test:
  - [ ] Mọi màn đều dùng đúng component — KQ: ___
  - [ ] Chart 14 ngày không cắt chữ — KQ: ___
- Ghi chú: ___

### Bước 3.3: Đổi icon text sang Material Icons
- Trạng thái: ⬜ Chưa làm
- File tạo/sửa:
  - [ ] `MainActivity.kt:80-83` + các nút Add/Delete/Share/Back
- Lỗi đã fix:
  - [ ] E7 icon `▦ ● ▥ ○`
- Kết quả test:
  - [ ] Search `Text("▦")` = 0 — KQ: ___
- Ghi chú: ___

**Kết quả Giai đoạn 3:**
- [ ] UI đều màu/spacing/icon, đủ chụp slide
- Ngày nghiệm thu: ___ | Screenshot đính kèm: ___

---

## Giai đoạn 4 — Hoàn thiện từng màn

### Bước 4.1: Auth
- Trạng thái: ⬜ Chưa làm | File sửa: `ui/screens/AuthScreen.kt` (tách từ MainActivity)
- Kết quả test: login mock OK ___ / validate email ___ / show-hide pass ___ / error ___ 
- Ghi chú: ___

### Bước 4.2: Camera + ExpenseForm
- Trạng thái: ⬜ Chưa làm | File sửa: `CameraScreen.kt`, `ExpenseForm.kt`
- Lỗi đã fix: [ ] E6 overflow grid → FlowRow
- Kết quả test: máy 360dp không cắt ___ / lưu lên đầu Album ___ / bỏ ảnh vẫn lưu ___ / auto-AI đoán đúng ___
- Ghi chú: ___

### Bước 4.3: Album
- Trạng thái: ⬜ Chưa làm
- Kết quả test: search “phở” ___ / filter food ___ / xóa+Undo ___ / refresh ___
- Ghi chú: ___

### Bước 4.4: Detail / Edit (màn mới)
- Trạng thái: ⬜ Chưa làm | File mới: `ui/screens/DetailScreen.kt`
- Kết quả test: tap đúng id ___ / sửa cập nhật ___ / share Toast ___ / xóa 2 bước ___
- Ghi chú: ___

### Bước 4.5: Stats + AI
- Trạng thái: ⬜ Chưa làm
- Kết quả test: đổi 7D/30D ___ / total≈sum byCategory ___ (total: ___) / AI đủ 4 khối TV ___
- Ghi chú: ___

### Bước 4.6: Friends
- Trạng thái: ⬜ Chưa làm
- Kết quả test: thêm trùng ___ / not found ___ / list +1 ___
- Ghi chú: ___

### Bước 4.7: Profile
- Trạng thái: ⬜ Chưa làm
- Kết quả test: toggle Demo ___ / logout ___ / xóa TK về Auth ___
- Ghi chú: ___

**Kết quả Giai đoạn 4 + demo 5 phút:**
- [ ] Đi hết Auth→Camera→Album→Detail→Stats→AI→Friends/Profile không crash
- Ngày demo: ___ | Máy demo: ___ | Video/screenshot: ___
- Ghi chú: ___

---

## Tổng hợp backlog (ghi khi phát sinh lỗi mới ngoài E1-E7)

| Ngày | Bước | Lỗi mới | Cách xử lý | Trạng thái |
|------|------|---------|------------|------------|
| ___ | ___ | ___ | ___ | ⬜ |
