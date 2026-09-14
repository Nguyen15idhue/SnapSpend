# SnapSpend — Hướng dẫn chạy trên Android Studio + Mô tả chức năng & Workflow

> Tài liệu tổng hợp từ source thực tế (`android/`, `server/`, `docs/`).
> App: ghi chi tiêu bằng ảnh, phân loại category, album, thống kê, AI phân tích, bạn bè.

---

## 1. Tổng quan kiến trúc

```
SnapSpend/
├── android/  # App Android: Kotlin + Jetpack Compose + CameraX + Room + Retrofit + Coil
│   ├── app/src/main/java/com/snapspend/app/
│   │   ├── MainActivity.kt          # Toàn bộ UI: Auth, Album, Camera, Stats, Profile
│   │   ├── data/AppConfig.kt        # Công tắc Demo/Real (mặc định true)
│   │   ├── data/repository/         # SnapSpendRepository interface + Mock/Real
│   │   ├── data/remote/Api.kt       # Retrofit interface + DTO
│   │   ├── data/remote/Network.kt   # TokenStore (SharedPrefs) + OkHttp + JWT header
│   │   ├── data/local/LocalDb.kt    # Room: DB snapspend.db, bảng expenses
│   │   ├── data/mock/MockData.kt    # 24 expense mẫu, 3 bạn mẫu, analysis mẫu
│   │   ├── model/Models.kt          # 9 category cố định
│   │   ├── ui/screens/DetailScreen.kt
│   │   ├── ui/components/AppComponents.kt
│   ├── app/build.gradle.kts         # compileSdk 37, minSdk 26, target 37, JDK 17
│   └── app/src/main/AndroidManifest.xml # CAMERA, INTERNET, READ_MEDIA_IMAGES
├── server/   # Backend: ASP.NET Core 10 + EF Core + PostgreSQL + JWT + OpenAI
│   ├── docker-compose.yml           # postgres:17 + api:5080
│   ├── src/SnapSpend.Api/Program.cs # Map Auth/Expenses/Stats/Friends/Account
│   └── db/schema.sql
└── docs/     # API.md, HUONG_DAN_VAN_HANH.md, ...
```

Nguyên tắc quan trọng:

* UI chỉ gọi `SnapSpendRepository` (`data/repository/SnapSpendRepository.kt:17`).
* `AppConfig.isDemo = true` → `MockRepository` (không cần mạng/server).
* `AppConfig.isDemo = false` → `RealRepository` (Retrofit + Room + backend thật).

---

## 2. Yêu cầu máy

| Thứ | Yêu cầu |
|-----|---------|
| Android Studio | Ladybug trở lên |
| JDK | 17 (khai báo `kotlin { jvmToolchain(17) }` trong `android/app/build.gradle.kts:30`) |
| Gradle Plugin | AGP 9.3.1, Kotlin 2.3.21 (`android/build.gradle.kts`) |
| SDK | compileSdk 37, minSdk 26, targetSdk 37 |
| Emulator | API 30+ (khuyên API 33 để test quyền ảnh `READ_MEDIA_IMAGES`), có hoặc không có camera đều được |
| Backend (chỉ khi chạy Real) | Docker + Docker Compose, hoặc .NET 10 SDK + PostgreSQL 17 |

---

## 3. Cách chạy nhanh nhất trên Android Studio (Demo Mode — khuyên dùng thử đầu tiên)

Đây là chế độ mặc định, không cần backend, không cần mạng.

1. Mở Android Studio → **Open** → chọn thư mục `SnapSpend/android/` (chọn đúng thư mục `android`, không phải thư mục gốc `SnapSpend`, để Gradle nhận `settings.gradle.kts`).
2. Đợi **Gradle Sync** xong. Nếu lỗi SDK: `File → Settings → Appearance → System Settings → Android SDK` → cài **Android API 37** + Build-Tools.
3. Chọn cấu hình `app` + device (emulator hoặc máy thật bật USB Debugging).
4. Bấm **Run ▶ (Shift+F10)** hoặc build tay:

```bash
cd android
gradlew.bat :app:assembleDebug
# APK ra tại: app/build/outputs/apk/debug/app-debug.apk
```

5. Mở app → màn hình **Đăng nhập**:
   * Nhập bất kỳ email hợp lệ (có `@`) + mật khẩu ≥ 6 ký tự. Ví dụ: `demo@snapspend.vn / 123456`.
   * Ở Demo, `MockRepository.login()` luôn trả `mock-token-login`, không kiểm tra mật khẩu thật.
6. Vào app với tab mặc định **Camera**. Nếu emulator không có camera/ màn đen → bấm **“Ảnh mẫu”** hoặc **Album** để chọn ảnh gallery.
7. Chuyển qua lại 4 tab dưới `NavigationBar` (`MainActivity.kt:177-182`): **Album / Camera / Stats / Profile**.

> Reset data mẫu: Logout → login lại, hoặc `xóa app cài lại` (data mock nằm trong `MutableStateFlow`, reload là reset).

---

## 4. Cách chạy với Backend thật (Real Mode)

Dùng khi muốn test luồng end-to-end: JWT, upload ảnh, Postgres, AI OpenAI.

### 4.1. Chạy backend bằng Docker (khuyên dùng)

```bash
cd server
copy .env.example .env
# Mở .env sửa:
# JWT_KEY=chuoi-ngau-nhien-dai-hon-32-ky-tu
# OPENAI_API_KEY=sk-... (để trống vẫn chạy, chỉ AI analysis fallback)
# OPENAI_MODEL=gpt-5.6-luna
# STORAGE_BASE_URL=http://localhost:5080/uploads

docker compose up --build -d
# Kiểm tra: mở http://localhost:5080/ → {"app":"SnapSpend API","status":"ok"}
```

* Postgres: `localhost:5432`, db/user/pass `snapspend/snapspend/snapspend`, tự seed 9 category trong `Program.cs:37-47`.
* API base local: `http://localhost:5080/api` (xem `docs/API.md`).
* Ảnh upload lưu local: `server/src/SnapSpend.Api/wwwroot/uploads/`.

### 4.2. Trỏ app Android tới backend

Trong `android/app/build.gradle.kts:34-39`:

```kotlin
debug { buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:5080/api/\"") }
release { buildConfigField("String", "API_BASE_URL", "\"https://YOUR_DOMAIN/api/\"") }
```

* **Emulator:** giữ nguyên `http://10.0.2.2:5080/api/` (`10.0.2.2` = localhost của máy host).
* **Máy thật + backend ở cùng WiFi:** đổi thành `http://<IP-LAN-của-PC>:5080/api/`, ví dụ `http://192.168.1.10:5080/api/`, rồi Sync + Run lại.
* Cần `INTERNET` permission (đã có trong `AndroidManifest.xml:3`).

### 4.3. Tắt Demo trong app

Cách 1 (nhanh, không sửa code): vào tab **Profile → Chế độ Demo (Mock)** → gạt **OFF** → app `recreate()` và dùng `RealRepository`.

Cách 2 (cố định): sửa `data/AppConfig.kt:10` thành `var isDemo: Boolean = false`, Run lại.

Sau đó Register/Login thật (`POST /auth/register`, `/auth/login`), token JWT lưu trong `SharedPreferences "snapspend_auth"` (`data/remote/Network.kt:11-18`), mọi request sau tự gắn `Authorization: Bearer <token>`.

---

## 5. Chức năng hiện đang có

### 5.1. Auth (Đăng ký / Đăng nhập / Đăng xuất / Xóa tài khoản)

* File: `MainActivity.kt:195-246 (AuthScreen)`, `data/remote/Api.kt:52-53`, `server/.../Endpoints/AuthEndpoints.cs`, `AccountEndpoints.cs`.
* Demo: email bất kỳ có `@` + pass ≥ 6 là vào. `register/login` delay ~500ms giả lập mạng.
* Real: JWT (`Jwt__ExpiresMinutes: 10080`), `TokenStore.token`. Profile có **Đăng xuất** (xóa token) và **Xóa tài khoản** (`DELETE /account`, có `AlertDialog` xác nhận).

### 5.2. Camera + Tạo chi tiêu (core workflow)

* File: `MainActivity.kt:248-355`, `Api.kt:56-64`.
* CameraX: `Preview + ImageCapture` (`PreviewView`, `CAPTURE_MODE_MINIMIZE_LATENCY`), xin quyền `CAMERA` runtime. Nút chụp tròn giữa, nút **Album** (Photo Picker `PickVisualMedia.ImageOnly`), nút **Ảnh mẫu** (tạo bitmap `createSampleImage()` để demo trên emulator không camera).
* Form `ExpenseForm`: preview ảnh 260dp, nhập **Số tiền VNĐ** (chỉ số, `>0` mới cho Lưu), chọn category qua `FilterChip` + `FlowRow` (có ô `✨ AI tự phân loại` = `auto`), **Ghi chú** optional, ngày mặc định `LocalDate.now()`.
* Demo: `guessCategory(note)` đoán theo từ khóa (phở/cơm→food, grab/xe→transport, zara/shopee→shopping…). Real: upload multipart (`amount, category, note, expenseDate, image`) → server lưu + gán `aiConfidence`.

### 5.3. Album (danh sách chi tiêu)

* File: `MainActivity.kt:357-480`.
* Lấy từ `repo.expenses: Flow<List<ExpenseDto>>` (Demo: `StateFlow`, Real: Room `observeAll()` + `refreshExpenses()` gọi `GET /expenses` rồi `upsertAll`).
* Tính năng: đếm `N khoản chi`, **tìm kiếm theo note**, **lọc chip category** (Tất cả + 9 category), **sắp xếp mới/cũ**, **PullToRefresh**, **EmptyState**, **ErrorRetry**, **Card** hiện ảnh (Coil `AsyncImage`), `CategoryLabel`, ngày, note, `ConfidenceBadge (AI xx%)`, `AmountText (formatVnd)`.
* **Xóa vuốt (SwipeToDismiss EndToStart)** + **Snackbar Undo** (`deleteWithUndo()`): xóa rồi hiện “Đã xóa …” + nút Hoàn tác (tạo lại expense).
* Chạm card → mở `DetailScreen`.

### 5.4. Chi tiết + Sửa / Xóa / Chia sẻ

* File: `ui/screens/DetailScreen.kt:59-190`.
* Xem ảnh lớn, số tiền, category, ngày, note, badge AI. TopBar có Sửa (pencil), Share, Xóa.
* **Sửa:** bật `editing`, sửa amount/category/note/date (`YYYY-MM-DD`) → `PUT /expenses/{id}`.
* **Xóa:** `AlertDialog` → `DELETE /expenses/{id}`.
* **Chia sẻ:** `DropdownMenu` list bạn bè → `POST /expenses/{id}/share/{friendId}` + Toast kết quả.

### 5.5. Category cố định (9 nhóm)

`model/Models.kt:32-42` (server seed giống hệt trong `Program.cs`):

`food 🍜 Ăn uống, shopping 🛍️, transport 🛵, entertainment 🎬, housing 🏠, health 💊, education 📚, bills 🧾, other •`

### 5.6. Thống kê (Stats)

* File: `MainActivity.kt:483-537`, `Api.kt:69`.
* Chọn range `7D / 30D / Tháng này` → tính `from/to` → `GET /stats?from=&to=` → hiện **tổng**, **trung bình/ngày**, **BarChartV2** (Canvas, 14 cột gần nhất, bo góc), **Theo danh mục** (`CategoryShareRow`: số tiền + % + `LinearProgressIndicator`).
* Có `LoadingBox`, `ErrorRetry`.

### 5.7. AI phân tích hành vi

* File: `MainActivity.kt:523-535`, `Services/AiService.cs`, `Api.kt:70`.
* Bấm **“AI phân tích hành vi”** → `POST /ai/analyze?from=&to=` → hiện `SectionCard "AI phân tích"`: `summary` + `trends` + `anomalies` + `recommendations` (dạng `BulletList`).
* Demo: trả `MockData.analysis` tiếng Việt mẫu. Real: server gom tổng hợp rồi gọi **OpenAI Responses API** (`OPENAI_API_KEY/MODEL`), không có key thì fallback.

### 5.8. Bạn bè (Friends) + Share

* File: `MainActivity.kt:540-598`, `Api.kt:74-76`, `Endpoints/FriendEndpoints.cs`.
* Profile hiện `Bạn bè (N)`, avatar chữ cái đầu, nút Share khoản mới nhất cho từng bạn. Thêm bạn bằng username (`POST /friends {"username":"..."}`).
* Demo: chỉ chấp nhận username trong `MockData.knownUsernames` (`minh_tran, lan_anh, duc_minh, thu_ha, quang_huy`), trùng thì báo “Đã là bạn bè”, không thấy thì “Không tìm thấy”.
* MVP: add là `accepted` ngay, chưa có request/accept/reject (ghi rõ trong `API.md:83`).

### 5.9. Profile + Cấu hình + Privacy

* File: `MainActivity.kt:540-617`.
* Toggle **Demo/Real**, danh sách bạn bè, thêm bạn, đăng xuất, xóa tài khoản, text Privacy (“Ảnh và chi tiêu riêng tư mặc định…”).

### 5.10. Cache offline (Room)

* `data/local/LocalDb.kt`: DB `snapspend.db`, entity `expenses(id, amount, category, imageUrl, note, expenseDate)`, DAO `observeAll/upsert/upsertAll/delete`. Chỉ dùng ở Real Mode làm cache sau `refreshExpenses()`.

---

## 6. Workflow tổng thể (user journey)

```
[Auth] Login/Register → TokenStore.token
   ↓
[CAMERA tab - mặc định] Cho phép Camera → Chụp / Album / Ảnh mẫu
   → ExpenseForm (amount + category/auto + note) → repo.createExpense()
   → onSaved(): refreshTick++ + chuyển qua ALBUM
   ↓
[ALBUM tab] refreshExpenses() → Flow<List> → search/filter/sort
   → Chạm card → DetailScreen → Sửa / Xóa / Share cho bạn
   → Vuốt xóa → Snackbar Undo
   ↓
[STATS tab] Chọn 7D/30D/Tháng này → stats(from,to) → BarChart + byCategory
   → Bấm AI phân tích → analyze(from,to) → summary/trends/anomalies/recommendations
   ↓
[PROFILE tab] Xem friends → addFriend(username) → shareExpense(newest.id, friend.id)
   → Toggle Demo/Real → Logout / Delete account
```

Workflow kỹ thuật Real Mode:

```
App (Retrofit, Bearer JWT) ⇄ API ASP.NET (/api/auth, /api/expenses, /api/stats, /api/ai/analyze, /api/friends, /api/account)
API ⇄ Postgres (EF Core, EnsureCreated + seed categories)
API ⇄ wwwroot/uploads (StorageService, trả imageUrl)
API ⇄ OpenAI Responses API (AiService, timeout 45s)
App ⇄ Room (cache expenses, observe Flow)
```

Kịch bản demo 5 phút (từ `docs/HUONG_DAN_VAN_HANH.md`):

1. Login `demo@snapspend.vn / 123456` (30s).
2. Camera → Ảnh mẫu → `65000` → `✨ AI` → Lưu (60s).
3. Album: search `phở`, filter `food`, swipe xóa → Undo (60s).
4. Detail `Zara 799k` → sửa `749k` → Share `minh_tran` (45s).
5. Stats đổi range, xem chart (60s).
6. Bấm AI phân tích, đọc insight (45s).
7. Profile thêm `thu_ha`, toggle Demo (30s).

---

## 7. API tóm tắt (Real Mode)

Base emulator: `http://10.0.2.2:5080/api/` | Base local: `http://localhost:5080/api`. Auth: `Bearer JWT`.

`POST /auth/register, POST /auth/login, GET /expenses, POST /expenses (multipart: amount, category/auto, note?, expenseDate YYYY-MM-DD, image?), PUT /expenses/{id}, DELETE /expenses/{id}, POST /expenses/{id}/share/{friendId}, GET /stats?from=&to=, POST /ai/analyze?from=&to=, GET /friends, POST /friends, DELETE /account`. Chi tiết xem `docs/API.md`.

---

## 8. Xử lý sự cố thường gặp

| Hiện tượng | Nguyên nhân / Cách fix |
|------------|------------------------|
| Mở `SnapSpend/` gốc → không Sync được | Phải Open thư mục `SnapSpend/android/` |
| `SDK not found / compileSdk 37` | Cài Android API 37 trong SDK Manager, Sync lại |
| `KSP / Room schema` lỗi | Build → Clean, Rebuild; JDK để 17 |
| Emulator màn camera đen | Bấm **Ảnh mẫu**, không cần fix camera |
| Gallery trống | Kéo-thả ảnh vào emulator, hoặc dùng Ảnh mẫu |
| Bật Real mà báo lỗi mạng | Backend chưa chạy, hoặc sai IP (máy thật phải dùng IP LAN, không dùng `10.0.2.2`/`localhost`) |
| Muốn đổi URL backend | Sửa `app/build.gradle.kts` `API_BASE_URL` debug/release, Sync + Run lại |
| Muốn reset demo | Logout → login lại |

Giới hạn MVP (không phải bug): ảnh mock là placeholder emoji, AI mock là text mẫu, add/share mock reset khi restart, Real thiếu rate-limit/refresh-token/cloud-storage/HTTPS/moderation (ghi trong `README.md:12`).

