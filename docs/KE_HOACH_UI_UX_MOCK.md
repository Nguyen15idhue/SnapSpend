# Kế hoạch hoàn thiện UI/UX SnapSpend với Mock Data (Backend làm sau)

> Mục tiêu: fix hết lỗi hiện có, hoàn thiện UI/UX đủ để **demo / trình bày**, dùng **mock data**, không phụ thuộc backend. Backend thực hiện sau mà không phải sửa UI.
> Ngày lập: 2026-09-03 | Nguồn phân tích: `android/`, `server/`, `docs/API.md`

---

## 1. Kết luận khả thi

**Khả thi 100%.** Lý do:

1. App hiện tại đã có đủ 4 tab + luồng `Auth -> Camera -> Album -> Stats -> Profile`, chỉ thiếu polish và bị 1 lỗi chặn compile.
2. API contract (`docs/API.md`) đã ổn định -> có thể tách `Repository` thành interface, UI chỉ phụ thuộc interface. Sau này cắm backend thật vào là chạy.
3. Mock data chi tiêu VNĐ rất dễ tạo, không cần ảnh thật (dùng màu + emoji + Coil placeholder).
4. Không cần đụng tới `server/`, Docker, Postgres, JWT, OpenAI trong giai đoạn này.

Nguyên tắc: **UI không gọi Retrofit trực tiếp. UI chỉ gọi `SnapSpendRepository` (interface). Có 2 implement: `MockRepository` (demo) và `RealRepository` (backend sau này). Công tắc `BuildConfig.FLAVOR hoặc Setting Demo/Real`.**

---

## 2. Hiện trạng lỗi cần fix (đã rà soát code)

### P0 — Chặn compile / chạy

| # | File | Lỗi | Cách fix |
|---|------|-----|----------|
| 1 | `android/.../MainActivity.kt:310-311` | Khai báo `var confirmDelete` 2 lần | Xóa 1 dòng |
| 2 | `android/.../MainActivity.kt:195,267,314` | Dùng `verticalScroll(rememberScrollState())` thiếu import rõ ràng, dùng `androidx.compose.foundation.rememberScrollState` inline — vẫn compile được nhưng khó đọc | Import explicit `rememberScrollState`, `verticalScroll` |
| 3 | `android/app/src/main/AndroidManifest.xml` | File `docs/HUONG_DAN_VAN_HANH.md` hiện đang chứa lẫn nội dung manifest (dòng 1-18 là XML) — docs bị corrupt | Viết lại `HUONG_DAN_VAN_HANH.md` đúng nội dung hướng dẫn |
| 4 | `android/app/build.gradle.kts` | `compileSdk=36`, AGP `9.3.1`, Kotlin `2.3.21` — quá mới, máy demo chưa chắc có SDK 36 | Hạ về `compileSdk=34`, AGP `8.5.x`, Kotlin `2.0.x` nếu build lỗi; hoặc giữ nguyên nếu Android Studio Hedgehog+ build được |
| 5 | `MainActivity.kt:59-60` | Khởi tạo `TokenStore/DB/API/Repo` trực tiếp trong `onCreate`, khó chuyển Demo/Real | Đưa vào `AppContainer` / factory `rememberRepository(isDemo)` |

### P1 — Lỗi UX / logic (vẫn chạy nhưng demo xấu)

| # | Vấn đề | Ảnh hưởng demo |
|---|--------|----------------|
| 6 | `FilterChip` grid `height(250dp)+userScrollEnabled=false` dễ overflow máy nhỏ | Form nhập bị cắt |
| 7 | `BarChart` vẽ đen, không trục/label | Slide thống kê xấu |
| 8 | Album không có loading/empty/error, không pull-to-refresh, không search/filter | Trông như app chưa xong |
| 9 | Không có màn Detail/Edit dù API đã có `PUT /expenses/{id}` | Thiếu 1 flow trình bày |
| 10 | Share expense có API nhưng UI không có nút share | Mất 1 tính năng friends |
| 11 | Token lưu SharedPrefs plain, không auto-logout khi 401 | Không cần fix ngay cho demo, ghi nợ backlog backend |
| 12 | Ảnh upload không nén, không placeholder khi `imageUrl=null` | Demo lag, card xấu |

---

## 3. Kiến trúc mục tiêu (để backend sau không sửa UI)

```
ui/
  theme/      -> Design System (màu semantic, typo, spacing, shape)
  navigation/ -> NavHost (auth/home/detail/stats/friends/profile)
  screens/    -> Auth, Album, Camera, ExpenseForm, Detail, Stats, AiAnalysis, Friends, Profile
  components/ -> AppCard, AmountText, CategoryChip, EmptyState, LoadingShimmer, ErrorRetry, BarChart
data/
  repository/
    SnapSpendRepository.kt  (interface duy nhất UI được phép gọi)
    MockRepository.kt       (trả mock data + delay giả 400-800ms)
    RealRepository.kt       (đổi tên từ Repository.kt hiện tại, giữ nguyên logic Retrofit+Room)
  mock/
    MockData.kt             (20-30 expense VNĐ, stats, analysis, friends)
    MockImages.kt           (placeholder màu theo category, không cần ảnh thật)
```

Interface đề xuất (khớp `docs/API.md` để sau này không đổi):

```kotlin
interface SnapSpendRepository {
  val expenses: Flow<List<ExpenseDto>>
  suspend fun login(email, pass): AuthResponse
  suspend fun register(email, user, pass): AuthResponse
  suspend fun refreshExpenses()
  suspend fun createExpense(uri: Uri?, amount: Long, category: String, note: String?, date: String): ExpenseDto
  suspend fun updateExpense(id: Long, amount: Long, category: String, note: String?, date: String): ExpenseDto
  suspend fun deleteExpense(id: Long)
  suspend fun stats(from: String, to: String): StatsDto
  suspend fun analyze(from: String, to: String): AnalysisDto
  suspend fun friends(): List<FriendDto>
  suspend fun addFriend(username: String): FriendDto
  suspend fun shareExpense(id: Long, friendId: Long)
  suspend fun deleteAccount()
}
```

Công tắc Demo/Real:

- Cách 1 (nhanh nhất cho demo): `object AppConfig { var isDemo = true }` + toggle trong Profile -> `Remember: nếu isDemo dùng MockRepository else RealRepository`.
- Cách 2 (chuẩn): `buildConfigField("boolean","IS_DEMO",...)` theo buildType. Demo dùng `debug`, release dùng backend.

Khuyến nghị: làm Cách 1 trước (1 buổi), khi nối backend chuyển sang Cách 2.

---

## 4. Design System (theo yêu cầu của bạn)

Tuân thủ: Material3, semantic tokens, spacing 4/8, Inter/Roboto, shadow nhẹ, radius vừa, **không gradient, không glassmorphism**, icon thống nhất.

### 4.1 Màu semantic (light trước, dark sau nếu còn thời gian)

| Token | Giá trị đề xuất | Dùng cho |
|-------|----------------|----------|
| `primary` | `#1A73E8` hoặc `#16A34A` (chốt 1 màu fintech) | Button, active tab |
| `onPrimary` | `#FFFFFF` | Chữ trên button |
| `surface` | `#FFFFFF` | Nền màn hình |
| `surfaceVariant` | `#F5F6F8` | Nền card Album |
| `outline` | `#E5E7EB` | Border card/input |
| `onSurface / onSurfaceVariant` | `#111827 / #6B7280` | Tiêu đề / mô tả |
| `error` | `#DC2626` | Xóa, cảnh báo |
| `success` | `#16A34A` | AI confidence cao |
| `warning` | `#D97706` | AI confidence thấp |

Không hard-code `Color.Black/White/Gray` trong screens nữa. Gom vào `ui/theme/Color.kt`.

### 4.2 Typography (dùng default Roboto của Android, hoặc thêm Inter)

- `headlineSmall`: tiêu đề màn hình (Album, Thống kê...)
- `titleMedium/bold`: số tiền card
- `bodyMedium`: note, mô tả
- `labelSmall`: date, category
- Số tiền: `NumberFormat vi-VN + " ₫"` — giữ hàm `formatVnd()` hiện tại, tách ra `ui/format/Format.kt`.

### 4.3 Spacing & Shape

- Spacing scale: `4, 8, 12, 16, 20, 24, 32` — padding màn hình chuẩn `16`, card inner `12-16`, gap list `12`.
- Shape: card `RoundedCorner 16-18`, ảnh `12`, button `12`, chip `50` cho nút chụp.
- Elevation: card `0-1dp + border 1dp outline`, dialog `3dp`. Không đổ bóng nặng.

### 4.4 Icon

Hiện tại dùng text `▦ ● ▥ ○` — demo sẽ bị chê. Đổi sang `Icons.Filled/Outlined`: `PhotoAlbum, PhotoCamera, BarChart, Person, Add, Delete, Share, ArrowBack, Refresh`. Không thêm lib Lucide (cho Compose chưa ổn định), dùng Material Icons là đủ đồng nhất.

### 4.5 Components dùng chung (bắt buộc để UI đều)

- `AppCard`, `CategoryChip(key)`, `AmountText(vnd)`, `EmptyState(icon,title,action)`, `LoadingShimmer`, `ErrorRetry(msg,onRetry)`, `ConfirmDialog`, `BarChartV2 (có label ngày + giá trị)`, `DonutChart (byCategory)`.

---

## 5. Mock Data Spec (đủ đẹp để pitch)

`data/mock/MockData.kt`:

- 24 expenses trong 30 ngày gần nhất, VNĐ thực tế:
  - food: Phở Thìn 65k, Cơm tấm 45k, Highlands 79k, GrabFood 132k...
  - transport: Grab 87k, Xăng 120k, Gửi xe 10k...
  - shopping: Zara 799k, Shopee 249k...
  - housing: Điện 642k, Nước 180k, Net 220k...
  - entertainment: CGV 210k...
  - health: Pharmacity 185k...
  - bills, education, other mỗi loại 1-2 món.
- Mỗi expense: `id, amount, category, imageUrl=null (hiện placeholder màu), note tiếng Việt, expenseDate=yyyy-MM-dd, aiConfidence 0.55-0.95`.
- `stats(from,to)`: tính từ list mock (tổng, avg, byCategory, byDay) — không hard-code số.
- `analyze()`: trả `summary` 2-3 câu + `trends 3 dòng + anomalies 2 dòng + recommendations 3 dòng` tiếng Việt, khớp số liệu mock.
- `friends()`: 3 bạn `minh_tran, lan_anh, duc_minh`.
- Delay giả `400-800ms` để demo loading shimmer. Có flag `shouldFail=false` để demo error state khi cần.

Ảnh: không cần ảnh thật. `MockImages.kt` map `category -> màu nền + emoji`. Coil load `null` -> hiện placeholder này. Khi chụp ảnh thật ở màn Camera (demo), hiện `AsyncImage(uri)` như hiện tại là đủ.

---

## 6. Full flow UI cần hoàn thiện (để trình bày)

| Màn | Việc làm | Mock hỗ trợ |
|-----|----------|-------------|
| Auth | Polish layout, logo, validate email/pass, show/hide pass, error text, nút chuyển login/register | `login/register` trả token fake sau 600ms |
| Camera | Giữ CameraX, thêm nút flash/giả lập, nút Album, xử lý từ chối quyền đẹp hơn | Không cần mock, cho phép “dùng ảnh mẫu” để qua form nhanh khi demo trên emulator không có camera |
| ExpenseForm | Fix overflow (chuyển grid sang `FlowRow` wrap), chip AI + 9 category, preview ảnh, validate amount>0, nút Lưu hiện loading | `createExpense` thêm vào list mock, auto phân loại nếu `auto` (rule đơn giản theo note) |
| Album (Home) | Search + filter category + sort mới/cũ + pull-to-refresh + Empty/Error + swipe-to-delete (có undo) | List 24 món, filter/search chạy local |
| Detail/Edit | **Màn mới**: xem ảnh lớn, sửa amount/category/note/date, nút Share, nút Xóa (confirm) | `update/delete/share` thao tác trên list mock |
| Stats | Khoảng thời gian (7D/30D/tháng này/tùy chọn), tổng to, BarChart có label, Donut theo category, top 3 chi | `stats()` tính real-time từ mock |
| AI Analysis | Nút phân tích, shimmer khi chờ, card summary/trends/anomalies/recommendations, nút copy/share | `analyze()` trả mẫu tiếng Việt |
| Friends | List bạn, thêm bạn (validate not found), xem expense được share | `friends/addFriend/shareExpense` mock |
| Profile | Avatar, email, toggle Demo/Real, đăng xuất, xóa tài khoản (confirm 2 bước), privacy note | `deleteAccount()` clear mock |

Navigation: `NavHost(auth -> home{album,camera,stats,profile} -> detail/{id} -> expenseForm)`. BottomBar 4 tab giữ nguyên nhưng đổi icon Material.

---

## 7. Kế hoạch thực hiện (3-4 ngày, 1 dev)

### Phase 0 — Fix chặn (0.5 ngày)
- [ ] Fix duplicate `confirmDelete`, imports scroll, tách `formatVnd`.
- [ ] Viết lại `HUONG_DAN_VAN_HANH.md` (hiện bị lẫn XML manifest).
- [ ] Chạy `./gradlew :app:assembleDebug` đảm bảo build xanh trước khi refactor.

### Phase 1 — Khung Mock + Design System (1 ngày)
- [ ] Tạo `SnapSpendRepository.kt` (copy signature từ `Repository.kt`).
- [ ] Đổi `Repository.kt` -> `RealRepository : SnapSpendRepository`.
- [ ] Tạo `MockRepository.kt + MockData.kt + AppConfig.isDemo`.
- [ ] Tạo `ui/theme/Color.kt, Type.kt, Spacing.kt, Theme.kt` (semantic tokens).
- [ ] Tạo `ui/components/` base: `AppCard, EmptyState, LoadingShimmer, ErrorRetry, ConfirmDialog`.
- [ ] Gắn toggle Demo trong Profile, mặc định `isDemo=true`.

### Phase 2 — Polish full flow (1.5 ngày)
- [ ] Auth polish.
- [ ] Album: search/filter/sort/refresh/delete-undo.
- [ ] Detail/Edit mới + Share.
- [ ] ExpenseForm fix overflow bằng FlowRow.
- [ ] Stats: BarChartV2 + Donut + range selector.
- [ ] AI Analysis card.
- [ ] Friends + Profile hoàn thiện.

### Phase 3 — Demo-ready (0.5-1 ngày)
- [ ] Icon Material thay text icon, placeholder ảnh theo category.
- [ ] Empty/loading/error cho mọi màn.
- [ ] Chạy thử emulator + máy thật, quay video demo.
- [ ] Ghi nợ backlog backend vào cuối file này (không fix backend lúc này).

**Không làm trong đợt này:** JWT refresh, rate-limit, S3, HTTPS, migration, push notification, dark mode full (để backlog).

---

## 8. Tiêu chí nghiệm thu demo

- [ ] Mở app không cần mạng/backend vẫn full data mẫu.
- [ ] Đi hết flow không crash: login fake -> chụp/chọn ảnh mẫu -> lưu -> album thấy món mới -> detail sửa/xóa/share -> stats đổi range số nhảy đúng -> AI phân tích ra tiếng Việt -> thêm bạn -> logout.
- [ ] Mọi màn có 3 trạng thái: loading / empty / error (có thể demo bằng flag).
- [ ] Không còn `Color.Black/Gray` hard-code, spacing đều 4/8, không gradient/glass.
- [ ] `./gradlew :app:assembleDebug` xanh.

---

## 9. Rủi ro & giảm thiểu

| Rủi ro | Giảm thiểu |
|--------|------------|
| AGP 9.3.1 + compileSdk 36 không build được trên máy cũ | Hạ xuống AGP 8.5 + compileSdk 34, giữ code Compose nguyên |
| Emulator không có camera | Thêm nút “Dùng ảnh mẫu” trong CameraScreen để vẫn qua form |
| Mock khác API thật sau này | Interface copy y chang `Api.kt + API.md`, Review 1 lần trước khi code màn hình |
| Scope phình (dark mode, animation) | Cắt sang backlog, ưu tiên flow chạy mượt trước, đẹp sau |

---

## 10. Backlog chuyển sang phase Backend (không làm giờ)

- Refresh token + Encrypted DataStore + auto logout 401.
- Rate-limit, CORS, validation `from<=to`, phân trang `GET /expenses`.
- Xóa file ảnh khi xóa expense, strip EXIF, scan virus, S3 + CDN.
- Friend request/accept/reject thật, `GET /shared-with-me`, `GET /categories`.
- Migration thay `EnsureCreated`, Swagger, ProblemDetails, logging/tracing.
- WorkManager offline queue, nén ảnh <2MB trước upload.
- Dark mode, instrumented test, Play Console checklist.

---

## 11. File sẽ tạo/sửa (để dev bám theo)

```
android/app/src/main/java/com/snapspend/app/
  MainActivity.kt (gọn lại, chỉ NavHost)
  data/repository/SnapSpendRepository.kt (mới)
  data/repository/MockRepository.kt (mới)
  data/repository/RealRepository.kt (đổi tên từ Repository.kt)
  data/mock/MockData.kt (mới)
  ui/theme/{Color,Type,Spacing,Theme}.kt (mới)
  ui/components/{AppCard,States,Charts,Chips}.kt (mới)
  ui/screens/{Auth,Album,Camera,ExpenseForm,Detail,Stats,AiAnalysis,Friends,Profile}.kt (tách từ MainActivity)
docs/HUONG_DAN_VAN_HANH.md (viết lại)
```
