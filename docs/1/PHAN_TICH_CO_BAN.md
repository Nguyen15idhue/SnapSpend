# SnapSpend — Phân tích cơ bản: Chức năng, Database, Luồng

Ngày tạo: 2026-09-04. Nguồn đối chiếu: `android/`, `server/`, `docs/`.

## 1. Giới thiệu

SnapSpend là app Android ghi chi tiêu bằng ảnh. Người dùng chụp ảnh món chi tiêu, nhập số tiền, chọn category (hoặc để AI tự phân loại), app lưu ảnh + thống kê + AI phân tích hành vi + chia sẻ cho bạn bè.

Kiến trúc 2 phần:

- `android/`: Kotlin + Jetpack Compose + CameraX + Room + Retrofit + Coil. UI chỉ gọi `SnapSpendRepository`.
- `server/`: ASP.NET Core + EF Core + PostgreSQL + JWT + lưu ảnh local + OpenAI Responses API.
- Công tắc `data/AppConfig.kt:isDemo` (mặc định `true`): `true` = `MockRepository` (demo offline), `false` = `RealRepository` (backend thật).

## 2. Chức năng của app

| # | Chức năng | Mô tả | File chính |
|---|-----------|-------|------------|
| 1 | Đăng ký / Đăng nhập / Đăng xuất | Demo: email có `@` + pass ≥ 6 là vào. Real: JWT lưu `SharedPreferences`, gắn `Bearer` mọi request | `MainActivity.kt:AuthScreen`, `Api.kt`, `Network.kt:TokenStore` |
| 2 | Chụp ảnh / chọn ảnh | CameraX Preview + ImageCapture, xin quyền CAMERA runtime; thêm nút Album (Photo Picker) và Ảnh mẫu cho emulator không camera | `MainActivity.kt:CameraScreen` |
| 3 | Tạo chi tiêu | Nhập số tiền VNĐ (>0), chọn 1 trong 9 category hoặc `auto` (AI), ghi chú optional, ngày mặc định hôm nay | `MainActivity.kt:ExpenseForm`, `POST /expenses` multipart |
| 4 | Album chi tiêu | List card ảnh + category + ngày + note + badge `AI xx%` + số tiền; tìm kiếm theo note, lọc chip category, sort mới/cũ, pull-refresh, empty/error state | `MainActivity.kt:AlbumScreen` |
| 5 | Xóa + Undo | Vuốt card sang trái để xóa, Snackbar hiện nút Hoàn tác | `MainActivity.kt:deleteWithUndo` |
| 6 | Chi tiết + Sửa / Xóa / Share | Xem ảnh lớn, sửa amount/category/note/date, xóa có xác nhận, share cho bạn qua dropdown | `ui/screens/DetailScreen.kt` |
| 7 | Category cố định | 9 nhóm: food 🍜, shopping 🛍️, transport 🛵, entertainment 🎬, housing 🏠, health 💊, education 📚, bills 🧾, other • | `model/Models.kt`, server seed trong `Program.cs` |
| 8 | Thống kê | Chọn 7D/30D/Tháng này → tổng, trung bình/ngày, biểu đồ cột theo ngày, % theo category | `MainActivity.kt:StatsScreen`, `GET /stats` |
| 9 | AI phân tích | Trả summary + trends + anomalies + recommendations tiếng Việt. Demo dùng text mẫu, Real gọi OpenAI | `POST /ai/analyze`, `Services/AiService.cs` |
| 10 | Bạn bè | Xem list, thêm bằng username, share khoản mới nhất cho bạn | `MainActivity.kt:ProfileScreen`, `GET/POST /friends` |
| 11 | Profile + Xóa tài khoản | Toggle Demo/Real, đăng xuất (xóa token), xóa tài khoản (`DELETE /account`) kèm dialog xác nhận | `MainActivity.kt:ProfileScreen` |
| 12 | Cache offline | Room lưu expenses sau mỗi lần đồng bộ server | `data/local/LocalDb.kt` |

## 3. Database

### 3.1. PostgreSQL — database chính (server)

File: `server/db/schema.sql`. EF Core map trong `server/src/SnapSpend.Api/Data/AppDbContext.cs`.

- `users(id PK, email UNIQUE, username UNIQUE, password_hash, created_at)` — gốc, xóa user cascade hết dữ liệu liên quan.
- `categories(key PK, name, emoji)` — 9 dòng seed, không cho user tự thêm.
- `expenses(id PK, user_id FK→users ON DELETE CASCADE, amount BIGINT CHECK >0, category DEFAULT 'other', image_url, note, expense_date DATE, ai_confidence, category_source user/ai, created_at, updated_at)` + index `(user_id, expense_date)` phục vụ thống kê.
- `friendships(id PK, user_id FK, friend_id FK, status pending/accepted, created_at, UNIQUE(user_id,friend_id), CHECK user_id <> friend_id)` — MVP thêm là `accepted` ngay.
- `expense_shares(id PK, expense_id FK→expenses CASCADE, owner_id FK, receiver_id FK, created_at, UNIQUE(expense_id, receiver_id))` — 1 expense share cho 1 bạn 1 lần.

Quan hệ: User 1-N Expenses, User N-N qua Friendships, Expense 1-N ExpenseShares.

### 3.2. Room — cache local (Android, chỉ Real Mode)

File: `android/.../data/local/LocalDb.kt`. DB `snapspend.db`, 1 bảng `expenses(id, amount, category, imageUrl, note, expenseDate)`. Luồng: `refreshExpenses()` gọi `GET /expenses` → `upsertAll` → UI observe `Flow` từ Room.

### 3.3. Dữ liệu phi-DB

- Demo: `data/mock/MockData.kt` — 24 expense, 3 bạn, 1 analysis mẫu trong `MutableStateFlow`; restart app là reset.
- Token: `TokenStore` lưu JWT trong `SharedPreferences "snapspend_auth"`.

## 4. Các luồng cơ bản

### F1. Auth

```
Nhập email/pass → repo.login/register → (Demo: delay 500ms trả mock-token | Real: POST /auth/* trả JWT → TokenStore.token) → vào 4 tab
Logout: xóa token. Xóa TK: DELETE /account → xóa token → về màn login.
```

### F2. Tạo chi tiêu (luồng lõi)

```
Tab Camera → cấp quyền → Chụp / Album / Ảnh mẫu → ExpenseForm(amount, category/auto, note) → repo.createExpense(uri,...)
→ Demo: guessCategory(note) theo từ khóa + thêm vào StateFlow
→ Real: multipart POST /expenses → server lưu file + insert DB → upsert Room → về Album
```

### F3. Album → Detail

```
Album: refreshExpenses → Flow list → search/filter/sort → chạm card → DetailScreen → Sửa (PUT /expenses/{id}) / Xóa (DELETE) / Share (POST /expenses/{id}/share/{friendId})
Vuốt xóa ở Album → Snackbar Undo → tạo lại expense.
```

### F4. Stats + AI

```
Chọn range → tính from/to → GET /stats → tổng + trung bình + byDay (vẽ BarChartV2) + byCategory (% + progress bar)
→ Bấm AI phân tích → POST /ai/analyze → summary/trends/anomalies/recommendations (BulletList)
```

### F5. Bạn bè

```
Profile → GET /friends → list + nút share khoản mới nhất → nhập username → POST /friends → thêm vào list (Demo chỉ nhận 5 username mẫu, trùng/không thấy thì báo lỗi)
```

### F6. Kỹ thuật Real Mode

```
App (Retrofit + Bearer JWT) ⇄ API (/api/auth, /expenses, /stats, /ai/analyze, /friends, /account) ⇄ Postgres (EF Core) + wwwroot/uploads + OpenAI API
```

## 5. Giới hạn MVP đã biết

Ảnh mock là placeholder emoji, AI mock là text mẫu, add/share mock reset khi restart; bản Real thiếu rate-limit, refresh-token, cloud storage, HTTPS, moderation — xem `README.md`.
