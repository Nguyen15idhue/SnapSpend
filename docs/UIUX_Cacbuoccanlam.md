# UI/UX SnapSpend — Các bước cần làm (Mock Data, đủ Demo)

> Phạm vi: **Fix hết lỗi chặn + hoàn thiện UI/UX chạy bằng mock data, đủ trình bày demo app. Không cần dữ liệu thật, không đụng backend.**
> Base phân tích: `android/` hiện tại + `docs/KE_HOACH_UI_UX_MOCK.md`

---

## 0. Tiêu chí DONE tổng

- [ ] `./gradlew :app:assembleDebug` build xanh, cài được lên emulator/máy thật.
- [ ] Bật app **không cần mạng, không cần server** vẫn đi hết flow demo.
- [ ] Hết lỗi compile/runtime đã liệt kê ở Mục 1.
- [ ] UI đồng nhất: semantic color, spacing 4/8, không hard-code, không gradient/glass.
- [ ] Mọi màn có mock data VNĐ nhìn được, đủ để pitch.

---

## Giai đoạn 1 — Fix lỗi chặn (bắt buộc trước)

### Bước 1.1: Fix lỗi compile MainActivity
**File:** `android/app/src/main/java/com/snapspend/app/MainActivity.kt`
**Việc cần làm:**
1. Xóa 1 trong 2 dòng `var confirmDelete` (dòng 310-311).
2. Thêm import explicit:
   ```kotlin
   import androidx.compose.foundation.rememberScrollState
   import androidx.compose.foundation.verticalScroll
   ```
   Thay các chỗ `androidx.compose.foundation.rememberScrollState()` inline bằng `rememberScrollState()`.
3. Tách hàm `formatVnd()` ra file riêng `ui/format/Format.kt` để dùng chung.
**Yêu cầu đạt:** File không còn lỗi đỏ trong Android Studio, không còn biến trùng.
**Checklist test:**
- [ ] Open file không báo lỗi `Redeclaration`.
- [ ] Không còn warning `fully qualified name`.
**Ghi chú:** Lỗi này là P0, fix 5 phút nhưng phải xong trước mọi việc khác.

### Bước 1.2: Fix docs vận hành bị corrupt
**File:** `docs/HUONG_DAN_VAN_HANH.md`
**Việc cần làm:** File hiện chứa XML manifest ở 18 dòng đầu + 1 đoạn Room ở cuối. Viết lại thành hướng dẫn demo mock:
- Cách build debug, cách bật Demo mode, kịch bản demo 5 phút, danh sách tài khoản mock.
**Yêu cầu đạt:** File đọc được, người ngoài vẫn demo được khi bạn vắng mặt.
**Checklist test:**
- [ ] Mở file preview markdown không thấy thẻ `<manifest>`.
- [ ] Có mục “Kịch bản demo”.

### Bước 1.3: Chốt cấu hình build chạy được
**File:** `android/app/build.gradle.kts`, `android/build.gradle.kts`, `gradle/wrapper/gradle-wrapper.properties`
**Việc cần làm:**
1. Thử build giữ nguyên (`compileSdk 36`, AGP `9.3.1`, Kotlin `2.3.21`).
2. Nếu fail (thiếu SDK 36 / JDK 17): hạ về `compileSdk 34`, AGP `8.5.2`, Kotlin `2.0.20`, `composeBom 2024.06.00`.
3. Giữ `minSdk 26`, `buildConfig=true`, `API_BASE_URL` debug giữ nguyên (không dùng tới khi mock nhưng giữ để sau này).
**Yêu cầu đạt:** `./gradlew :app:assembleDebug` ra được APK.
**Checklist test:**
- [ ] `assembleDebug` SUCCESS.
- [ ] APK cài được lên emulator API 30+.
**Ghi chú:** Không nâng cấp lib trong đợt này trừ khi build fail.

### Bước 1.4: Thêm quyền + khai báo cần cho demo
**File:** `android/app/src/main/AndroidManifest.xml`
**Việc cần làm:**
1. Thêm `READ_MEDIA_IMAGES` (Android 13+) để pick ảnh gallery không crash máy mới.
2. Giữ `CAMERA`, `INTERNET`.
**Yêu cầu đạt:** Pick ảnh trên Android 13/14 không bị từ chối ngầm.
**Checklist test:**
- [ ] Trên emulator API 33+: pick ảnh mẫu được.
- [ ] Từ chối quyền camera hiện màn xin quyền đẹp, không crash.

---

## Giai đoạn 2 — Dựng khung Mock (xương sống demo)

### Bước 2.1: Tách interface Repository
**File mới:** `data/repository/SnapSpendRepository.kt`
**Việc cần làm:** Copy đúng 12 hàm từ `Repository.kt` hiện tại thành interface:
`login, register, refreshExpenses, createExpense, updateExpense, deleteExpense, stats, analyze, friends, addFriend, shareExpense, deleteAccount` + `val expenses: Flow<List<ExpenseDto>>`.
**Yêu cầu đạt:** UI sau này chỉ gọi interface này, không gọi Retrofit/Room trực tiếp.
**Checklist test:**
- [ ] Interface compile được, không import Android Context.

### Bước 2.2: Đổi Repository hiện tại thành RealRepository
**File sửa:** `data/Repository.kt` → chuyển thành `data/repository/RealRepository.kt`, `class RealRepository(...) : SnapSpendRepository`.
**Việc cần làm:** Chỉ đổi tên + implements, giữ nguyên logic.
**Yêu cầu đạt:** Không đổi behavior, để dành khi nối backend.
**Checklist test:**
- [ ] Không xóa logic nào, build vẫn xanh.

### Bước 2.3: Tạo MockRepository + MockData
**File mới:** `data/mock/MockData.kt`, `data/repository/MockRepository.kt`
**Việc cần làm:**
1. `MockData`: 24 expense VNĐ 30 ngày gần nhất (food/transport/shopping/housing/entertainment/health/bills/education/other), mỗi món có `amount, category, note tiếng Việt, date yyyy-MM-dd, aiConfidence 0.55-0.95, imageUrl=null`.
2. `MockRepository`: implement interface, dùng `MutableStateFlow` giữ list, mọi hàm `delay(400-800ms)` rồi trả data. `stats()` tính live từ list. `analyze()` trả mẫu tiếng Việt. `create/update/delete/share` sửa list tại chỗ. Có flag `failNext=true` để test error khi cần.
3. `AppConfig.isDemo = true` mặc định, toggle trong Profile.
**Yêu cầu đạt:** Đảo `isDemo` là đổi nguồn data, UI không đổi code.
**Checklist test:**
- [ ] `expenses` emit đủ 24 món.
- [ ] `stats(đầu tháng-nay)` total = sum list.
- [ ] `createExpense` xong list tăng 1, `delete` giảm 1.
- [ ] Delay có loading, không freeze UI.

### Bước 2.4: Nối MainActivity sang chế độ Demo
**File sửa:** `MainActivity.kt onCreate`
**Việc cần làm:**
```kotlin
val repo: SnapSpendRepository = if (AppConfig.isDemo) MockRepository() else RealRepository(...)
```
Mặc định demo = Mock, không khởi tạo Retrofit/Room khi demo để mở app nhanh.
**Yêu cầu đạt:** Tắt mạng vẫn mở app full data.
**Checklist test:**
- [ ] Bật airplane mode vẫn vào Album/Stats được.
- [ ] Chuyển toggle Demo/Real không crash (Real có thể báo lỗi mạng là OK).

---

## Giai đoạn 3 — Design System tối thiểu (để UI đều, pitch được)

### Bước 3.1: Semantic colors + Theme
**File mới:** `ui/theme/Color.kt`, `ui/theme/Theme.kt`
**Việc cần làm:**
- Định nghĩa: `primary #16A34A (hoặc #1A73E8 - chốt 1), surface #FFFFFF, surfaceVariant #F5F6F8, outline #E5E7EB, onSurface #111827, onSurfaceVariant #6B7280, error #DC2626`.
- `SnapSpendTheme { MaterialTheme(colorScheme, ...) }`, bọc `setContent`.
- Quét `MainActivity` thay `Color.White/Black/Gray/LightGray` bằng token.
**Yêu cầu đạt:** Không còn hard-code màu trong screens, đổi 1 chỗ đổi cả app.
**Checklist test:**
- [ ] Search `Color.Black`, `Color.Gray` trong `ui/screens` = 0 kết quả.
- [ ] Card có border `outline`, nền `surfaceVariant`, radius 16-18.

### Bước 3.2: Components dùng chung
**File mới:** `ui/components/AppComponents.kt` (hoặc tách 3 file: `Cards.kt`, `States.kt`, `Charts.kt`)
**Việc cần làm:**
1. `CategoryChip(key)`: emoji + label tiếng Việt, map từ `model/categories`.
2. `AmountText(vnd)`: bold, format vi-VN + ₫.
3. `EmptyState(icon,title,desc,action)`, `LoadingShimmer`, `ErrorRetry(msg,onRetry)`, `ConfirmDialog`.
4. `BarChartV2`: có label ngày + cột bo, màu `primary`; `DonutRow`: list byCategory + % (vẽ bằng `Canvas` đơn giản hoặc Row progress).
**Yêu cầu đạt:** Mọi màn dùng chung 1 bộ, không tự vẽ lẻ.
**Checklist test:**
- [ ] Mỗi màn đều có empty/loading/error dùng đúng component.
- [ ] Chart hiện đủ 14 ngày cuối, không cắt chữ.

### Bước 3.3: Đổi icon text sang Material Icons
**Việc cần làm:** BottomBar `▦ ● ▥ ○` → `PhotoAlbum, PhotoCamera, BarChart, Person`. Nút thêm/xóa/share/back/refresh dùng `Icons.Filled`.
**Yêu cầu đạt:** Nhìn như app thật trên slide.
**Checklist test:**
- [ ] Không còn `Text("▦")`, `Text("●")` trong code.

---

## Giai đoạn 4 — Hoàn thiện từng màn (full flow present)

### Bước 4.1: Auth
**Việc cần làm:** Polish layout (logo, subtitle), validate email có `@`, pass >=6, show/hide pass, nút chuyển login/register, error text đỏ, loading disable nút. Mock: bất kỳ email/pass hợp lệ đều login được sau 600ms.
**Checklist test:**
- [ ] Email trống/sai báo lỗi, không gọi repo.
- [ ] Login thành công vào Home, reload app vẫn login (lưu flag mock trong memory là đủ).
- [ ] Sai mock (nếu set fail) hiện `ErrorRetry`.

### Bước 4.2: Camera + ExpenseForm
**Việc cần làm:**
1. Giữ CameraX, thêm nút “Dùng ảnh mẫu” (cho emulator không camera).
2. Form: chuyển grid category sang `FlowRow` wrap (fix overflow), chip `✨ AI tự phân loại`, validate `amount>0`, preview ảnh 260dp bo 18, nút Lưu loading.
3. Mock `createExpense`: nếu `category==auto` tự đoán theo note (chứa “grab/xe” → transport, “phở/cơm/ăn” → food...), gán `aiConfidence` tương ứng.
**Checklist test:**
- [ ] Máy nhỏ (360dp) form không cắt, scroll được hết nút Lưu.
- [ ] Lưu xong tự về Album và thấy món mới lên đầu.
- [ ] Bỏ ảnh vẫn lưu được (imageUrl=null → placeholder).

### Bước 4.3: Album (Home)
**Việc cần làm:** Search theo note, filter chip category, sort mới/cũ, pull-to-refresh (`PullToRefreshBox`), swipe-to-delete có Undo (Snackbar), Empty (“Chưa có chi tiêu”), Error + Retry.
**Checklist test:**
- [ ] Search “phở” ra đúng món.
- [ ] Filter `food` chỉ hiện food.
- [ ] Xóa hiện Snackbar Undo, Undo khôi phục được.
- [ ] Refresh quay 1s rồi hết.

### Bước 4.4: Detail / Edit (màn mới, bắt buộc để demo sâu)
**File mới:** `ui/screens/DetailScreen.kt`
**Việc cần làm:** Xem ảnh lớn, amount, category chip, note, date, aiConfidence badge (Xanh nếu >0.7, Vàng nếu thấp hơn), nút Sửa (mở form pre-fill), Share (chọn bạn), Xóa (ConfirmDialog). Mock `update/delete/share` chạy local.
**Checklist test:**
- [ ] Từ Album tap card vào Detail đúng id.
- [ ] Sửa amount lưu xong Album cập nhật.
- [ ] Share xong Toast “Đã chia sẻ với X”.
- [ ] Xóa confirm 2 bước, xóa xong back về Album.

### Bước 4.5: Stats + AI Analysis
**Việc cần làm:**
1. Selector range: `7D / 30D / Tháng này`. Đổi range gọi lại `stats()` mock.
2. Hiện tổng to, BarChartV2, Donut/list byCategory kèm %, top 3 chi.
3. Nút “AI phân tích”: hiện shimmer 1s, rồi card `summary/trends/anomalies/recommendations` tiếng Việt, nút Copy.
**Checklist test:**
- [ ] Đổi 7D→30D total tăng (hoặc bằng), chart đổi theo.
- [ ] ByCategory cộng lại ≈ total.
- [ ] AI luôn trả đủ 4 khối, không trống.

### Bước 4.6: Friends
**Việc cần làm:** List 3 bạn mock, avatar chữ cái đầu, thêm bạn (nếu username không tồn tại báo “Không tìm thấy”), mỗi bạn có nút Share nhanh 1 expense gần nhất (để demo).
**Checklist test:**
- [ ] Thêm `minh_tran` trùng báo “Đã là bạn”.
- [ ] Thêm `nguoi_la_123` báo not found.
- [ ] Thêm bạn mới list tăng 1.

### Bước 4.7: Profile
**Việc cần làm:** Avatar + email mock, toggle `Chế độ Demo (Mock)`, nút Đăng xuất, Xóa tài khoản (confirm 2 bước), mục Privacy. Toggle Demo để reviewer thấy app có 2 mode.
**Checklist test:**
- [ ] Tắt Demo → app báo lỗi mạng (chấp nhận được, vì chưa nối backend).
- [ ] Bật lại Demo → data về ngay.
- [ ] Xóa tài khoản xong về Auth, list mock reset.

---

## Phụ lục: Kịch bản demo 5 phút (dùng khi trình bày)

1. (30s) Auth: login mock `demo@snapspend.vn / 123456`.
2. (60s) Camera: “Dùng ảnh mẫu” → nhập 65k → auto AI → Lưu → Album lên đầu.
3. (60s) Album: search “grab”, filter food, swipe xóa + Undo.
4. (45s) Detail: tap món Zara 799k → Sửa thành 749k → Share cho Minh.
5. (60s) Stats: đổi 7D/30D, chỉ BarChart + Donut.
6. (45s) AI: bấm phân tích, đọc to 1 insight + 1 gợi ý.
7. (30s) Friends/Profile: thêm bạn, toggle Demo/Real, kết luận “nối backend không sửa UI”.

---

## Ghi chú giới hạn

- Không làm: backend, JWT thật, OpenAI thật, upload S3, dark mode full, animation phức tạp, test tự động.
- Ảnh mock: dùng màu + emoji theo category, không cần ảnh thật.
- Nếu thiếu giờ: cắt Detail/Share trước, giữ Album + Stats + AI là vẫn pitch được.
