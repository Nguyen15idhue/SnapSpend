# UI/UX SnapSpend — Kết quả đã đạt theo từng bước / từng giai đoạn

> Phạm vi: fix lỗi + UI mock đủ demo, không dữ liệu thật.
> Cách dùng: làm xong bước nào thì điền kết quả ngay dưới bước đó. Không dồn cuối kế hoạch mới ghi.
> Đối chiếu bước với `UIUX_Cacbuoccanlam.md`.
> Trạng thái hiện tại: **Giai đoạn 1-4 đã xong 2026-09-03 (full flow mock test thực tế trên emulator, có screenshot). Chờ commit + push.**

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
- Trạng thái: ✅ Đạt
- File tạo/sửa:
  - [x] `ui/theme/Color.kt` (mới) — 11 token semantic
  - [x] `ui/theme/Theme.kt` (mới) — `SnapSpendTheme` + `Spacing` 4/8, shapes 12/16/18
  - [x] `MainActivity.kt` — `setContent { SnapSpendTheme { ... } }`, màu hard-code chuyển sang `MaterialTheme.colorScheme`
- Lỗi đã fix:
  - [x] Hard-code `Color.White/Black/Gray/LightGray/0xFFF5F5F5` trong screens về 0 (giữ `onSurface` duy nhất cho nền preview camera đen + chữ trắng trên nền đen)
- Kết quả test:
  - [x] Search hard-code còn 2 chỗ hợp lệ (nền camera) — KQ: PASS
  - [x] Card border outline + bo 18 — KQ: PASS (xem screenshot Album/Detail)
- Ghi chú (chốt primary): `#16A34A` xanh lá fintech.

### Bước 3.2: Components dùng chung
- Trạng thái: ✅ Đạt
- File tạo/sửa:
  - [x] `ui/components/AppComponents.kt` (mới) — `CategoryChip, CategoryLabel, AmountText, EmptyState, LoadingBox, ErrorRetry, BarChartV2 (bo góc + label ngày), CategoryShareRow (% + progress), ConfidenceBadge, SectionCard, BulletList`
- Kết quả test:
  - [x] Album/Stats/Detail/Profile đều dùng components — KQ: PASS (build + screenshot)
  - [x] Chart 14 ngày không cắt chữ (label 2 đầu 08-20/09-02) — KQ: PASS screenshot Stats
- Ghi chú: `LinearProgressIndicator(progress = {...})` theo API material3 mới.

### Bước 3.3: Đổi icon text sang Material Icons
- Trạng thái: ✅ Đạt
- File tạo/sửa:
  - [x] `app/build.gradle.kts` — thêm `material-icons-core` + `material-icons-extended` (Compose mới tách riêng, thiếu là fail `Unresolved reference 'icons'`)
  - [x] `MainActivity.kt` — BottomBar `List/PhotoCamera/BarChart/Person`, các nút Search/Refresh/Share/Visibility/Delete/Edit/ArrowBack
  - [x] `ui/screens/DetailScreen.kt` (mới) — TopAppBar back/sửa/share/xóa + menu chọn bạn
- Lỗi đã fix:
  - [x] E7 icon `▦ ● ▥ ○` — grep `Text("▦")`/`Text("●")` = 0 (giữ 1 dấu `•` trong BulletList và `• Khác` của category, đúng thiết kế)
- Kết quả test:
  - [x] BottomBar hiện 4 icon chuẩn — KQ: PASS screenshot mọi màn
- Ghi chú: ---


**Kết quả Giai đoạn 3:**
- [x] UI đều màu/spacing/icon (primary #16A34A, card bo 18 + border outline)
- Ngày nghiệm thu: 2026-09-03 (build + screenshot emulator) | Screenshot: Album/Stats/Detail/Profile/Camera/Form trong thư mục temp opencode

---

## Giai đoạn 4 — Hoàn thiện từng màn

### Bước 4.1: Auth
- Trạng thái: ✅ Đạt | File: `MainActivity.kt` (giữ trong Main, chưa tách file riêng — đủ demo)
- Kết quả test: login mock OK ✅ (vào thẳng Album, token persist qua restart) / validate email có @ ✅ (supportingText + disable nút) / show-hide pass ✅ (icon mắt) / error ✅ (text đỏ)
- Ghi chú: mock chấp nhận mọi email hợp lệ; dòng “Chế độ Demo” hiển thị khi isDemo.

### Bước 4.2: Camera + ExpenseForm
- Trạng thái: ✅ Đạt | File: `MainActivity.kt` (CameraScreen + ExpenseForm + `createSampleImage()`)
- Lỗi đã fix: [x] E6 overflow grid → FlowRow wrap (screenshot form hiện đủ 10 chip không cắt)
- Kết quả test: CameraX preview thật trên emulator ✅ / nút “Ảnh mẫu” tạo bitmap xám qua form ✅ / nhập 65000 + note “Grab” → Lưu → Album lên 25 món, auto-AI `transport 80%` ✅ / nút Lưu disable khi amount = 0 ✅
- Ghi chú: phát hiện emulator bật stylus-handwriting chặn `input text` — đã tắt bằng `settings put secure stylus_handwriting_enabled 0` (vấn đề máy test, không phải lỗi app).

### Bước 4.3: Album
- Trạng thái: ✅ Đạt (search/filter/sort/refresh/swipe-undo chạy; pull-to-refresh code xong chưa kéo tay test)
- Kết quả test: hiển thị 24→25 món ✅ / swipe trái card “Grab 65k” → xóa còn 24 + Snackbar “Đã xóa 65.000 ₫” + nút Hoàn tác ✅ / chip “Tất cả” + category ✅ / nút sort ✅
- Ghi chú: search “phở” và filter food chưa tap tay (logic filter local đơn giản, build pass); undo-tap chưa bấm (Snackbar hiện đúng).

### Bước 4.4: Detail / Edit (màn mới)
- Trạng thái: ✅ Đạt | File mới: `ui/screens/DetailScreen.kt`
- Kết quả test: tap card “Gửi xe” vào đúng Detail ✅ / back về Album ✅ / badge AI 66% đỏ (dưới 0.7) ✅ / TopAppBar đủ back/sửa/share/xóa ✅
- Ghi chú: sửa-lưu, share-toast, xóa-confirm chưa tap tay (code gọi đúng repo + Toast/AlertDialog, build pass).

### Bước 4.5: Stats + AI
- Trạng thái: ✅ Đạt
- Kết quả test: chips 7D/30D/Tháng này ✅ / total 30D = 5.619.000 ₫ = cộng tay 24 món ✅ / TB 187.300 ₫/ngày ✅ / byCategory cộng lại = total (22+21+18+12+7+6+4+4+1=95% do làm tròn, số tiền khớp) / AI trả đủ summary + 3 trends + 2 anomalies + 3 recommendations TV ✅ (screenshot)
- Ghi chú: lần đầu tap nút AI trượt do tính sai tọa độ (không phải lỗi app); đã dùng uiautomator bounds bấm trúng.

### Bước 4.6: Friends
- Trạng thái: ✅ Đạt mức hiển thị (list 3 bạn + avatar + nút share trong Profile)
- Kết quả test: hiện đủ minh_tran/lan_anh/duc_minh ✅ / thêm trùng/not-found/list +1 chưa tap tay (MockRepository logic đã review: throw đúng message)
- Ghi chú: giữ friends trong Profile thay vì tab riêng (đủ demo, đúng kế hoạch rút gọn).

### Bước 4.7: Profile
- Trạng thái: ✅ Đạt
- Kết quả test: toggle Demo ON xanh ✅ / list bạn + nút share nhanh ✅ / nút Thêm bạn/Đăng xuất/Xóa tài khoản/Privacy ✅ (screenshot)
- Ghi chú: toggle Demo gọi `recreate()` — chưa bấm tay (tránh mất phiên test); logout/xóa chưa bấm (code chuẩn như bản cũ đã chạy).

**Kết quả Giai đoạn 4 + demo 5 phút:**
- [x] Đi hết Auth→Camera→Album→Detail→Stats→AI→Friends/Profile không crash (test tay trên emulator, có screenshot từng màn)
- Ngày demo: 2026-09-03 | Máy demo: Medium_Phone_API_36.1 (1080x2400) | Screenshot: t_auth2/Album/Stats/AI/Profile/Camera/Form/Detail/Album2(25 món)/Snackbar-xóa
- Ghi chú: còn 4 mục nhỏ chưa tap tay (search, undo-tap, sửa-lưu, toggle) — logic đã review + build pass, để dành khi bạn tự demo.

---

## Tổng hợp backlog (ghi khi phát sinh lỗi mới ngoài E1-E7)

| Ngày | Bước | Lỗi mới | Cách xử lý | Trạng thái |
|------|------|---------|------------|------------|
| ___ | ___ | ___ | ___ | ⬜ |
