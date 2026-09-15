# SnapSpend — Kết quả đã đạt (theo từng bước)

> Đối chiếu 1-1 với `CAC_BUOC_CAN_LAM.md` (cùng folder `docs/2`).
> Cách dùng: làm xong bước nào thì điền kết quả ngay dưới bước đó, không dồn cuối.
> Quy ước trạng thái: `⬜ Chưa làm | 🔄 Đang làm | ✅ Đạt | ❌ Fail (ghi rõ lỗi)`
> Mỗi bước gồm: **File đã tạo/sửa** · **Kết quả test theo checklist** · **Kết quả đã đạt** · **Ghi chú**.

---

## Trạng thái xuất phát (trước khi thực hiện kế hoạch này)

> **Điều chỉnh phạm vi (2026-09-15):** Dự án là bài tập lớn, không cần production hardening.
> Rút gọn còn **GĐ1–GĐ4 + 6.1, 6.4** (21 bước). **Bỏ:** 3.3 (phân trang), 3.5 (camera nâng cấp),
> 4.4 (CI GitHub Actions — không push repo), toàn bộ GĐ5 (5.1–5.10), và 6.2/6.3/6.5.
> Các bước bỏ được ghi rõ trong bảng tổng hợp với trạng thái "ngoài phạm vi".

> **Điều chỉnh kiến trúc (2026-09-15, lần 2):** Theo yêu cầu người dùng, **bỏ hẳn Demo/Mock — app chỉ dùng Real**.
> Đã xóa `MockRepository`, `MockData`, `AppConfig.isDemo`, `LocalIsDemo`, công tắc Demo ở Profile; `MainActivity` luôn dựng
> `RealRepository`. **Bước 2.4 trở thành không còn áp dụng** (đánh dấu ⛔). Phần "Mock" trong 2.5/3.1/3.2/6.4 không còn;
> backend **bắt buộc chạy**. Đã cập nhật `AGENTS.md` + `README.md` cho khớp.

Đã có sẵn (kế thừa từ docs/1 + các việc đã làm tới 2026-09-14):

- [x] Android MVP demo: Auth → Camera → Album → Detail → Stats → AI → Profile, Mock/Real parity cơ bản.
- [x] Server: auth JWT, expenses CRUD + upload, stats, ai/analyze, friends, account delete.
- [x] PostgreSQL local (docker compose) + EF Core Migrations (`InitialCreate`) + `DesignTimeDbFactory`.
- [x] Đã kiểm chứng: `dotnet build` 0 warning/0 error; `assembleDebug` xanh; smoke test API PASS; app chạy trên emulator API 36.1.
- [x] Tài liệu `docs/1`, `docs/2` (phân tích + kế hoạch), `AGENTS.md`.
- [ ] **Chưa làm**: toàn bộ các bước GĐ1–GĐ6 dưới đây.

Ngày bắt đầu kế hoạch: 2026-09-14.

---

## GĐ1 — Nền tảng

### Bước 1.1 — Chốt migration là nguồn schema duy nhất
- Trạng thái: ✅ Đạt
- File đã tạo/sửa:
  - `server/docker-compose.yml` — bỏ mount `./db/schema.sql` khỏi `postgres` (giữ file `db/schema.sql` làm tài liệu tham khảo).
  - `server/src/SnapSpend.Api/Data/AppDbContext.cs` — thêm `HasData` seed 9 category vào model.
  - `server/src/SnapSpend.Api/Migrations/20260914151322_SeedCategories.cs` (+ `.Designer.cs`, `AppDbContextModelSnapshot.cs`) — migration seed 9 category, idempotent.
  - `server/src/SnapSpend.Api/Program.cs` — bỏ block seed category lúc runtime, chỉ còn `MigrateAsync()`.
- Kết quả test theo checklist:
  - [x] `down -v` xóa volume; `up -d postgres` DB trống — volume `server_postgres_data` bị xóa, postgres tạo lại `healthy`; không đụng container project khác (station/travela/cgbas).
  - [x] Log apply `InitialCreate` — lần đầu log: `Applying migration '20260914140612_InitialCreate'` + `Applying migration '20260914151322_SeedCategories'`.
  - [x] Đủ 5 bảng + `__EFMigrationsHistory` — `\dt` thấy categories, expense_shares, expenses, friendships, users, `__EFMigrationsHistory`.
  - [x] `categories` = 9 — `SELECT count(*) FROM categories;` = 9.
  - [x] Chạy lần 2 không lỗi — lần 2/3 log `No migrations were applied. The database is already up to date.`; `GET /` = `{"app":"SnapSpend API","status":"ok"}`.
- Kết quả đã đạt: Từ máy sạch chỉ cần `docker compose up -d postgres` + chạy API là có đủ bảng + 9 category; không còn 2 nguồn schema song song.
- Ghi chú: `dotnet build` 0 warning/0 error. Lần chạy đầu có log `fail: ... SELECT ... __EFMigrationsHistory` là bình thường (bảng chưa tồn tại), không phải lỗi.

### Bước 1.2 — Dọn code server nhỏ
- Trạng thái: ✅ Đạt
- File đã tạo/sửa:
  - `server/src/SnapSpend.Api/Data/AppDbContext.cs` — xóa 5 dòng `ToTable(...)` bị lặp.
  - `server/src/SnapSpend.Api/Endpoints/StatsEndpoints.cs` — thêm validate `from <= to`; parse chặt `yyyy-MM-dd` bằng `DateOnly.TryParseExact` (InvariantCulture) cho cả `/stats` và `/ai/analyze`.
- Kết quả test theo checklist:
  - [x] `dotnet build` xanh, 0 warning/0 error.
  - [x] `GET /stats?from=2026-09-30&to=2026-09-01` → `400 {"message":"from must be on or before to."}`.
  - [x] `GET /stats?from=01/09/2026&to=2026-09-30` → `400 {"message":"Invalid date. Use yyyy-MM-dd."}` (không parse kiểu culture).
  - [x] `GET /stats?from=2026-09-01&to=2026-09-30` với dữ liệu hợp lệ → `200 {"total":50000,...,"byDay":{"2026-09-10":50000}}`.
  - [x] `POST /ai/analyze?from=2026-09-30&to=2026-09-01` → `400 {"message":"from must be on or before to."}`.
- Kết quả đã đạt: `AppDbContext` gọn, không trùng lệnh; Stats/analyze trả 400 rõ ràng cho `from > to` và ngày sai định dạng; shape response giữ nguyên.
- Ghi chú: Smoke test bằng `Invoke-WebRequest` trên API local (register → tạo expense → stats).

### Bước 1.3 — Health check + cấu hình khởi động an toàn
- Trạng thái: ✅ Đạt
- File đã tạo/sửa:
  - `server/src/SnapSpend.Api/Program.cs` — thêm `GET /health` (check `CanConnectAsync`, 200/503); validate `Jwt:Key` (thiếu/ngắn < 32 ký tự/placeholder `CHANGE_ME` → ném `InvalidOperationException` rõ ràng) trước khi khởi động.
- Kết quả test theo checklist:
  - [x] `GET /health` → `200 {"status":"healthy"}` khi DB chạy; `503 {"status":"unhealthy"}` khi tắt postgres.
  - [x] Chạy API với `Jwt__Key=short` → process thoát ngay, log `InvalidOperationException` nêu rõ cách đặt `Jwt__Key`.
  - [x] Chạy API với key hợp lệ → khởi động bình thường, listen `:5080`.
- Kết quả đã đạt: `/health` phản ánh đúng trạng thái DB; API từ chối chạy nếu JWT key yếu/placeholder.
- Ghi chú: Chạy local giờ bắt buộc đặt `Jwt__Key` (≥ 32 ký tự) qua biến môi trường.

### Bước 1.4 — Cập nhật README + FILE_LIST
- Trạng thái: ✅ Đạt
- File đã tạo/sửa:
  - `README.md` — viết lại: kiến trúc (UI chỉ gọi `SnapSpendRepository`, Mock/Real, mặc định Demo), yêu cầu môi trường, 3 lệnh chạy local, lưu ý `Jwt__Key` bắt buộc và `schema.sql` chỉ tham khảo, cấu trúc thư mục, smoke test nhanh.
  - `FILE_LIST.txt` — đã xóa (thay bằng mục cấu trúc thư mục trong README).
- Kết quả test theo checklist:
  - [x] Làm theo README → build xanh cả 2 phía: `dotnet build` 0 warning/0 error; `gradlew :app:assembleDebug` xanh (`app-debug.apk` ~23MB).
  - [x] README không còn thông tin lỗi thời (bỏ OpenAI Responses API, bỏ FILE_LIST, ghi đúng model Gemini hiện tại dùng qua cấu hình `Ai:*`).
- Kết quả đã đạt: Người mới đọc README chạy được app + server mà không cần hỏi; mặc định Demo mode được ghi rõ.
- Ghi chú: `assembleDebug` lần đầu mất vài phút (tải dependency + biên dịch Kotlin/Compose); các lần sau nhanh hơn.

**Kết quả GĐ1:** ✅ | Ngày nghiệm thu: 2026-09-14 | Người test: AI (tự động, theo checklist từng bước)

---

## GĐ2 — Kiến trúc Android

### Bước 2.1 — Tách ViewModel cho từng màn
- Trạng thái: ✅ Đạt
- File đã tạo/sửa:
  - `ui/viewmodel/` (mới): `VmFactory.kt` (factory + `LocalVmFactory`), `AuthViewModel`, `AlbumViewModel`, `ExpenseFormViewModel`, `StatsViewModel`, `ProfileViewModel`, `DetailViewModel` — dùng `viewModelScope` + `StateFlow`, UI đọc bằng `collectAsStateWithLifecycle()`.
  - `ui/screens/`: `AuthScreen`, `CameraScreen` (+ form + ảnh mẫu), `AlbumScreen` (+ card), `StatsScreen`, `ProfileScreen` tách từ MainActivity; `DetailScreen` refactor dùng `DetailViewModel` (mở theo id, nạp từ Flow chung).
  - `MainActivity.kt`: 617 → 105 dòng, chỉ còn dựng repo Demo/Real + tab + phiên đăng nhập.
- Kết quả test theo checklist:
  - [x] `assembleDebug` xanh (12–39s).
  - [x] Appium E2E Demo mode PASS 6/6: auth (login email bất kỳ) → camera (ảnh mẫu → lưu 75.000₫) → album (25 khoản) → detail (mở → back) → stats (AI phân tích) → profile (đăng xuất về Auth).
  - [x] Xoay màn hình: danh sách giữ nguyên số lượng; amount form giữ nguyên sau recreate (nhờ ViewModel).
  - [x] Không còn `repo.xxx()` trong composable (grep `ui/screens` = 0 match; chỉ còn trong `ui/viewmodel`).
- Kết quả đã đạt: `MainActivity` < 150 dòng; state + logic gọi repo nằm hết trong ViewModel.
- Ghi chú: E2E tìm ra 1 bug thật (`reset()` form xóa state khi mở lại) đã fix: reset sau khi lưu thành công. Còn warning deprecation `confirmValueChange` (có sẵn từ trước, không chặn build). Xoay màn hình recreate vẫn về tab Camera — Bước 2.2 sẽ lưu tab bằng NavHost.

### Bước 2.2 — Navigation-Compose + lưu state
- Trạng thái: ✅ Đạt
- File đã tạo/sửa:
  - `MainActivity.kt` (99 dòng) — `RootNav` với route `auth`, `home`, `detail/{id}`, `form?imageUri=`; logout xóa sạch stack về auth.
  - `ui/screens/HomeScreen.kt` (mới) — BottomBar 4 tab nằm trong `home`; tab + refreshTick dùng `rememberSaveable`.
  - `ui/screens/FormScreen.kt` (mới) — tách form sang route riêng (uri ảnh đi theo nav arg).
  - `ui/screens/CameraScreen.kt` — chỉ giữ preview/quyền, chụp xong `onCaptured` sang route form.
  - `ui/screens/DetailScreen.kt` — nhận `id` từ route; field sửa trong SavedStateHandle.
  - `ui/viewmodel/VmFactory.kt` + cả 6 ViewModel — chuyển sang `AbstractSavedStateViewModelFactory`, input quan trọng (email, amount, note, query, filter, range…) lưu bằng `SavedStateHandle.getStateFlow`.
- Kết quả test theo checklist:
  - [x] `assembleDebug` xanh, 0 warning/0 error.
  - [x] Appium E2E Demo PASS 6/6: auth → camera (route form → lưu → tự về Album) → album → detail (route, back về Album) → stats → profile (logout về auth, back thoát app không về home).
  - [x] Xoay màn hình: danh sách giữ nguyên; route form + amount giữ nguyên trực tiếp qua recreate.
- Kết quả đã đạt: Back stack đúng (Detail/Form back về đúng chỗ); xoay không mất dữ liệu form.
- Ghi chú: E2E tìm ra 1 bug thật (factory dùng owner Activity nên `handle` thiếu nav arg `id` → Detail treo loading) đã fix bằng cách truyền `id` trực tiếp từ route. Tab sau recreate về đúng tab nhờ `rememberSaveable`.

### Bước 2.3 — Đồng bộ Room entity với DTO
- Trạng thái: ✅ Đạt
- File đã tạo/sửa:
  - `data/local/LocalDb.kt` — `ExpenseEntity` thêm `aiConfidence: Double? = null`; `AppDatabase` bump version 2 + `MIGRATION_1_2` (`ALTER TABLE expenses ADD COLUMN aiConfidence REAL`).
  - `data/repository/RealRepository.kt` — map `aiConfidence` 2 chiều (bỏ gán `null`), `toEntity()` truyền `aiConfidence`.
- Kết quả test theo checklist:
  - [x] Real mode tạo expense (ảnh mẫu) → về Album thấy badge `AI xx%` (Appium assert text `AI `).
  - [x] Kill app mở lại (vào Real) → badge vẫn còn (đọc từ Room, không phải từ API).
  - [x] Không crash khi migrate DB cũ v1→v2 (DB v1 tạo ở phiên Real trước đó, mở app v2 chạy migration thành công).
- Kết quả đã đạt: Real mode sau refresh/reload vẫn hiện đúng badge `AI xx%`.
- Ghi chú: Nhờ test Real mode phát hiện + fix crash `SavedStateProvider already registered` (factory đổi sang `CreationExtras`/NavBackStackEntry ở bước 2.3 này để chuyển mode không crash).

### Bước 2.4 — Persist công tắc Demo/Real ⛔ KHÔNG CÒN ÁP DỤNG
- Trạng thái: ⛔ Không còn áp dụng (đã bỏ Demo/Mock, app chỉ dùng Real — xem điều chỉnh kiến trúc ở đầu tài liệu)
- Ghi chú: Trước đó đã hoàn thành (DataStore `isDemo`); sau đó gỡ bỏ toàn bộ theo yêu cầu người dùng.

### Bước 2.5 — Sửa Undo + dọn Mock
- Trạng thái: ✅ Đạt
- File đã tạo/sửa:
  - `data/repository/SnapSpendRepository.kt` — thêm `restoreExpense(expense: ExpenseDto): ExpenseDto`.
  - `RealRepository.kt` + `data/remote/Api.kt` — `restoreExpense` gọi `POST /api/expenses/restore` (kèm `ExpenseRestoreDto` giữ ảnh + aiConfidence).
  - `server/.../Endpoints/ExpenseEndpoints.cs` — thêm `POST /api/expenses/restore`; gom `AllowedCategories` dùng chung cho POST/PUT/restore.
  - `server/.../Services/AiService.cs` — heuristic `Normalize()` bỏ dấu (đồng bộ với server).
  - `ui/viewmodel/AlbumViewModel.kt` + `ui/screens/AlbumScreen.kt` — undo gọi `restoreExpense`; sửa bug double-delete (dùng `dismissState.currentValue` + guard `deletingIds`).
- Kết quả test theo checklist:
  - [x] Server: `POST /expenses` note `"Pho Thin"`, category=auto → `food` (conf 0.65); `POST /expenses/restore` giữ đủ `imageUrl` + `aiConfidence=0.85`.
  - [x] Real: swipe xóa → bấm `Hoàn tác` → bản ghi trở lại (restore endpoint).
  - [x] Build xanh cả server (0/0) và Android.
- Kết quả đã đạt: Undo khôi phục đầy đủ dữ liệu (Real endpoint giữ ảnh/aiConfidence); heuristic không phụ thuộc dấu.
- Ghi chú: Trước đây có MockRepository (đã bỏ theo điều chỉnh kiến trúc); heuristic bỏ dấu phía client không còn dùng, server vẫn giữ.

### Bước 2.6 — Token an toàn + xử lý 401
- Trạng thái: ✅ Đạt
- File đã tạo/sửa:
  - `data/remote/Network.kt` — `TokenStore` lưu token mã hóa AES/GCM bằng khóa trong AndroidKeyStore; thêm `unauthorized: StateFlow`; interceptor bắt HTTP 401 → `markUnauthorized()` (xóa token).
  - `data/remote/ApiErrors.kt` (mới) — `Throwable.toUserMessage()` chuyển lỗi mạng/HTTP thành thông báo tiếng Việt thống nhất.
  - `data/repository/RealRepository.kt` — bọc `net {}` để mọi lỗi mạng trả message thống nhất.
  - `MainActivity.kt` — quan sát `unauthorized` → điều hướng về route `auth`.
- Kết quả test theo checklist:
  - [x] Đổi `Jwt__Key` server (token cũ thành 401) → app tự động về màn Đăng nhập (Appium PASS).
  - [x] File `shared_prefs/snapspend_auth.xml` không chứa `eyJ` → token đã mã hóa, không plain text.
  - [x] Đăng nhập lại bằng user thật thành công (Appium PASS).
- Kết quả đã đạt: Token không lưu plain; 401 tự về Auth, không crash; lỗi mạng hiện thông báo thống nhất.
- Ghi chú: Dùng AndroidKeyStore sẵn có, không thêm dependency mới.

### Bước 2.7 — Nén ảnh trước upload
- Trạng thái: ✅ Đạt
- File đã tạo/sửa:
  - `data/repository/RealRepository.kt` — `createImagePart`: decode bounds → `inSampleSize` (cạnh dài ≤ 1600px), xoay theo EXIF, nén JPEG chất lượng 80; `createExpense` xóa file tạm trong `finally`.
- Kết quả test theo checklist:
  - [x] Upload ảnh (Real) → server lưu JPEG ~9KB (nén từ ảnh gốc), ảnh hiển thị đúng.
  - [x] `cacheDir` không còn file `expense_*.jpg` sau upload (không rò rỉ file tạm).
  - [~] Ảnh 8MP: **chưa kiểm chứng** — camera emulator không tạo được ảnh 8MP thật; code đã có downscale ≤1600px.
- Kết quả đã đạt: Ảnh nén rõ rệt, đúng chiều, dọn file tạm.
- Ghi chú: Giữ nguyên API multipart `@Part image`.

### Bước 2.8 — Thêm testTag/contentDescription
- Trạng thái: ✅ Đạt
- File đã tạo/sửa:
  - `MainActivity.kt` — bật `testTagsAsResourceId = true` để uiautomator2 đọc `testTag` qua `resource-id`.
  - Gắn `Modifier.testTag(...)`: `HomeScreen` (tab_album/camera/stats/profile), `CameraScreen` (btn_capture/btn_gallery/btn_sample), `FormScreen` (field_amount/field_note/btn_save/chip_auto/chip_category_*), `AlbumScreen` (field_search), `DetailScreen` (btn_edit/btn_share/btn_delete), `AuthScreen` (field_email/field_username/field_password/btn_submit).
- Kết quả test theo checklist:
  - [x] Dump UI thấy `resource-id` = tag (Appium tìm được `tab_camera`, `btn_sample`, `field_amount`, `btn_save`, `chip_auto`, `field_search`…).
  - [x] Test tìm element theo tag, không dùng toạ độ.
- Kết quả đã đạt: UI test tìm được element ổn định theo tag.
- Ghi chú: Là tiền đề cho bước 4.3 (UI test).

**Kết quả GĐ2:** ✅ | Ngày nghiệm thu: 2026-09-15 | Người test: AI (Appium + thao tác UI thật)

---

## GĐ3 — Tính năng & backend

### Bước 3.1 — Nguồn category duy nhất + `GET /categories`
- Trạng thái: ✅ Đạt
- File đã tạo/sửa:
  - `server/.../Models/CategoryCatalog.cs` (mới) — nguồn category duy nhất (9 mục).
  - `server/.../Endpoints/CategoryEndpoints.cs` (mới) — `GET /api/categories`; `Program.cs` map endpoint.
  - `server/.../Data/AppDbContext.cs` — seed dùng `CategoryCatalog.AsEntities()`.
  - `server/.../Endpoints/ExpenseEndpoints.cs` — validate category dùng `CategoryCatalog.Keys`.
  - `data/remote/Api.kt` + `SnapSpendRepository` + `MockRepository` + `RealRepository` — thêm `categories()`.
  - `ui/viewmodel/CategoryViewModel.kt` (mới) + `FormScreen`/`AlbumScreen` — chip danh mục lấy từ server (Real), fallback list local.
- Kết quả test theo checklist:
  - [x] `GET /api/categories` trả đủ 9 (`food,shopping,transport,entertainment,housing,health,education,bills,other`).
  - [x] Tạo expense category hợp lệ → OK; category lạ (`zzz`) → `400`.
  - [x] App Real hiển thị chip danh mục (9) lấy từ endpoint; fallback local khi lỗi mạng.
- Kết quả đã đạt: Thêm/đổi category chỉ sửa `CategoryCatalog`; client lấy được danh sách từ server.
- Ghi chú: Giữ nguyên category key (tương thích).

### Bước 3.2 — Xem chi tiêu được chia sẻ
- Trạng thái: ✅ Đạt
- File đã tạo/sửa:
  - `server/.../Endpoints/ExpenseEndpoints.cs` — thêm `GET /api/shared-with-me` (join `expense_shares` theo receiver), trả kèm `ownerUsername`.
  - `data/remote/Api.kt` + interface + `MockRepository` + `RealRepository` — thêm `sharedWithMe()`.
  - `ui/viewmodel/ProfileViewModel.kt` — `shared` StateFlow; `ProfileScreen.kt` — mục "Được chia sẻ với tôi (chỉ xem)".
- Kết quả test theo checklist:
  - [x] A share → `GET /api/shared-with-me` của B chứa đúng expense (kèm `ownerUsername`).
  - [x] B cố `PUT`/`DELETE` expense được share → `404` (không phải chủ sở hữu).
  - [x] Mock parity: `sharedWithMe()` mô phỏng từ `sharedPairs`.
- Kết quả đã đạt: B thấy khoản được chia sẻ; không sửa/xóa được.
- Ghi chú: Màn Profile tự tải lại danh sách mỗi lần mở tab. E2E FE (Appium) phần hiển thị chưa chạy trọn (thao tác tay); đã kiểm chứng server + UI có mục.

### Bước 3.3 — Phân trang danh sách ⛔ NGOÀI PHẠM VI
- Trạng thái: ⛔ Ngoài phạm vi (quy mô demo nhỏ, chưa cần)

### Bước 3.4 — Xóa ảnh khi xóa expense
- Trạng thái: ✅ Đạt
- File đã tạo/sửa:
  - `server/.../Services/StorageService.cs` — thêm `DeleteByUrl(url)` dùng chung (an toàn nếu file/đường dẫn sai).
  - `server/.../Endpoints/ExpenseEndpoints.cs` — `DELETE /expenses/{id}` xóa file ảnh sau khi xóa bản ghi.
  - `server/.../Endpoints/AccountEndpoints.cs` — dùng chung `DeleteByUrl` khi xóa tài khoản.
- Kết quả test theo checklist:
  - [x] Tạo expense có ảnh → xóa → file biến mất khỏi `wwwroot/uploads` (`ton tai=False`).
  - [x] Xóa expense không ảnh → không lỗi.
  - [x] Không xóa nhầm file khác (chỉ xóa theo đúng tên file trong `imageUrl`).
- Kết quả đã đạt: Xóa expense dọn luôn file ảnh.
- Ghi chú: Tách hàm dùng chung với account delete.

### Bước 3.5 — Camera nâng cấp ⛔ NGOÀI PHẠM VI
- Trạng thái: ⛔ Ngoài phạm vi (không thiết yếu, emulator khó kiểm chứng)

### Bước 3.6 — Kiểm chứng AI Gemini với key thật
- Trạng thái: ✅ Đạt
- File đã tạo/sửa:
  - `server/.../Services/AiService.cs` — model mặc định `gemini-3.6-flash`.
  - `server/.../appsettings.json`, `docker-compose.yml` — model `gemini-3.6-flash`.
  - `server/.env` (gitignored) — thêm `GEMINI_API_KEY` + `AI_MODEL=gemini-3.6-flash`.
- Kết quả test theo checklist:
  - [x] Key thật: `POST /ai/analyze` trả `summary/trends/anomalies/recommendations` thật (tiếng Việt, khác fallback).
  - [x] Classify ảnh thật: trả kết quả AI (conf 0.1 cho ảnh không phải đồ ăn) — khác heuristic fallback.
  - [x] Bỏ key → fallback hoạt động: analyze trả câu mẫu; classify note `pho` → `food` conf 0.65.
- Kết quả đã đạt: classify + analyze dùng Gemini thật; fallback đúng khi thiếu key.
- Ghi chú: Model `gemini-2.5-flash` đã bị Google khai tử với user mới → chuyển `gemini-3.6-flash`. Không commit key.

**Kết quả GĐ3:** ✅ | Ngày nghiệm thu: 2026-09-15 | Người test: AI (smoke API + UI thật trên emulator)

---

## GĐ4 — Kiểm thử & CI

### Bước 4.1 — Test server
- Trạng thái: ✅ Đạt
- File đã tạo/sửa:
  - `server/tests/SnapSpend.Api.Tests/` (mới) — xUnit + `WebApplicationFactory`; `ApiFactory` dùng **EF InMemory** (không phụ thuộc Postgres local), `ApiTests.cs`.
  - `server/src/SnapSpend.Api/Program.cs` — `public partial class Program {}`; ở môi trường `Testing` dùng `EnsureCreatedAsync()` thay vì migrate.
- Kết quả test theo checklist:
  - [x] `dotnet test` xanh: **11 passed / 0 failed**.
  - [x] Có test cho 400 (category/amount/stats sai), 401 (sai mật khẩu, thiếu auth), 409 (trùng email).
  - [x] Test độc lập DB local (InMemory riêng mỗi factory).
- Kết quả đã đạt: `dotnet test` xanh, phủ luồng lõi + trường hợp lỗi.
- Ghi chú: Có cảnh báo MSB3277 (trùng version EF Relational) không ảnh hưởng kết quả.

### Bước 4.2 — Unit test Android
- Trạng thái: ✅ Đạt
- File đã tạo/sửa:
  - `app/src/test/.../ui/format/FormatTest.kt` — `formatVnd` (0, nghìn, triệu, số âm).
  - `app/build.gradle.kts` — thêm `testImplementation("junit:junit:4.13.2")`.
- Kết quả test theo checklist:
  - [x] `.\gradlew.bat test` xanh: **FormatTest 4 passed / 0 failed**.
  - [x] Cover case biên (amount 0, số âm).
- Kết quả đã đạt: `gradlew test` xanh, có test cho logic thuần.
- Ghi chú: Sau khi bỏ Mock, các test `MockRepository` được gỡ; phân loại/stats chuyển về server (đã có test ở 4.1).

### Bước 4.3 — UI test
- Trạng thái: ✅ Đạt
- File đã tạo/sửa:
  - `android/ui-test/` (mới) — PoC E2E Appium: `e2e_real_flow.py`, `requirements.txt`, `README.md`.
- Kết quả test theo checklist:
  - [x] Chạy trên emulator xanh: đăng ký/đăng nhập Real → tạo chi tiêu (ảnh mẫu) → album → detail → stats/AI → logout.
  - [x] Tìm element theo `testTag` (resource-id) qua `testTagsAsResourceId`, không dùng toạ độ.
  - [x] Khi fail có lưu `fail_<bước>.png` + log.
- Kết quả đã đạt: Có kịch bản E2E chạy tự động trên backend thật.
- Ghi chú: App chỉ dùng Real nên test cần API đang chạy.

### Bước 4.4 — CI GitHub Actions ⛔ NGOÀI PHẠM VI
- Trạng thái: ⛔ Ngoài phạm vi (không push repo)

**Kết quả GĐ4:** ✅ | Ngày nghiệm thu: 2026-09-15 | Người test: AI (dotnet test 11/11, gradlew test 4/4, Appium E2E)

---

## GĐ5 — Production hardening ⛔ NGOÀI PHẠM VI

> Toàn bộ GĐ5 (5.1–5.10) nằm **ngoài phạm vi** bài tập lớn: rate limiting, HTTPS/CORS, refresh token,
> object storage, Swagger/ProblemDetails, logging, backup, moderation/strip EXIF, deploy, soft-delete.
> Lý do: vượt yêu cầu môn học (xem đầu `CAC_BUOC_CAN_LAM.md`).

| Bước | Nội dung | Trạng thái |
|------|----------|------------|
| 5.1 | Rate limiting | ⛔ Ngoài phạm vi |
| 5.2 | HTTPS + CORS | ⛔ Ngoài phạm vi |
| 5.3 | Refresh token / token ngắn hạn | ⛔ Ngoài phạm vi |
| 5.4 | Object storage cho ảnh | ⛔ Ngoài phạm vi |
| 5.5 | Swagger/OpenAPI + ProblemDetails | ⛔ Ngoài phạm vi |
| 5.6 | Logging structured + tracing | ⛔ Ngoài phạm vi |
| 5.7 | Seed/migration + backup | ⛔ Ngoài phạm vi |
| 5.8 | Image moderation/virus scan, strip EXIF | ⛔ Ngoài phạm vi |
| 5.9 | Deploy + cấu hình env | ⛔ Ngoài phạm vi |
| 5.10 | Soft-delete user | ⛔ Ngoài phạm vi |

**Kết quả GĐ5:** ⛔ Ngoài phạm vi

---

## GĐ6 — Polish UI/UX

### Bước 6.1 — Dark mode
- Trạng thái: ✅ Đạt
- File đã tạo/sửa:
  - `ui/theme/Color.kt` — thêm bảng màu tối (`DarkPrimary`, `DarkSurface`, `DarkOnSurface`…).
  - `ui/theme/Theme.kt` — `darkColorScheme` + chọn theo `isSystemInDarkTheme()`.
  - `res/values-night/themes.xml` (mới) — status/nav bar tối, `windowLightStatusBar=false`.
- Kết quả test theo checklist:
  - [x] Bật dark mode hệ thống (`adb shell cmd uimode night yes`) → app tối, chữ đọc được (screenshot xác nhận).
  - [x] Không hard-code trắng: màu lấy từ semantic token trong `Color.kt`/`Theme.kt`.
- Kết quả đã đạt: App hiển thị đúng cả light/dark theo hệ thống.
- Ghi chú: Tắt lại bằng `cmd uimode night no`.

### Bước 6.4 — Cải thiện empty/error state
- Trạng thái: ✅ Đạt
- File đã tạo/sửa: các màn có dữ liệu dùng chung `LoadingBox` / `EmptyState` / `ErrorRetry` (`ui/components/`).
- Kết quả test theo checklist:
  - [x] Album: loading khi tải, empty khi chưa có chi tiêu, error + nút "Thử lại" khi lỗi mạng; khi offline vẫn hiện dữ liệu cache (Room) — screenshot xác nhận.
  - [x] Stats/Detail/Profile đều có loading/empty/error.
- Kết quả đã đạt: 3 trạng thái loading/empty/error rõ ràng ở màn có dữ liệu.
- Ghi chú: Nút demo `failNext` (Mock) không còn áp dụng vì đã bỏ Mock; lỗi mạng thật dùng để kiểm chứng.

### Bước 6.2 — Animation + skeleton loading ⛔ NGOÀI PHẠM VI
- Trạng thái: ⛔ Ngoài phạm vi

### Bước 6.3 — Accessibility ⛔ NGOÀI PHẠM VI
- Trạng thái: ⛔ Ngoài phạm vi

### Bước 6.5 — Hoàn thiện tài liệu ⛔ NGOÀI PHẠM VI
- Trạng thái: ⛔ Ngoài phạm vi (đã có README + docs/2)

**Kết quả GĐ6:** ✅ | Ngày nghiệm thu: 2026-09-15 | Người test: AI (screenshot light/dark + offline)

---

## Tổng hợp tiến độ

| Giai đoạn | Số bước | Trong phạm vi | Đã đạt | Đang làm | Chưa làm | Ngoài phạm vi |
|-----------|---------|---------------|--------|----------|----------|----------------|
| GĐ1 — Nền tảng | 4 | 4 | 4 | 0 | 0 | 0 |
| GĐ2 — Kiến trúc Android | 8 | 7 | 7 | 0 | 0 | 1 (2.4 không còn áp dụng) |
| GĐ3 — Tính năng & backend | 6 | 4 | 4 | 0 | 0 | 2 (3.3, 3.5) |
| GĐ4 — Kiểm thử & CI | 4 | 3 | 3 | 0 | 0 | 1 (4.4) |
| GĐ5 — Production hardening | 10 | 0 | 0 | 0 | 0 | 10 (5.1–5.10) |
| GĐ6 — Polish UI/UX | 5 | 2 | 2 | 0 | 0 | 3 (6.2, 6.3, 6.5) |
| **Tổng** | **37** | **20** | **20** | **0** | **0** | **16 (+1 n/a)** |

> **Phạm vi rút gọn (2026-09-15):** làm **20 bước** (GĐ1–GĐ4 + 6.1, 6.4; trừ 2.4 không còn áp dụng);
> **16 bước ngoài phạm vi** (3.3, 3.5, 4.4, toàn bộ GĐ5, 6.2, 6.3, 6.5) — chi tiết lý do xem đầu `CAC_BUOC_CAN_LAM.md`.

## Changelog bổ sung (2026-09-15)

Các thay đổi/sửa lỗi phát sinh trong quá trình test thực tế:

| Nội dung | Chi tiết |
|----------|----------|
| Bỏ Demo/Mock, chỉ Real | Xóa `MockRepository`, `MockData`, `AppConfig.isDemo`, `LocalIsDemo`, công tắc Profile; `MainActivity` luôn dựng `RealRepository`. Cập nhật `AGENTS.md` + `README.md`. |
| Danh mục tiếng Việt | `Shopping → Mua sắm`, `Học tập → Giáo dục`; EF migration `UpdateCategoryNames`. |
| Phân loại AI | Gemini hết quota free tier (429/503) → **phân loại theo text dùng engine nội bộ** (tức thời); ảnh dùng Gemini khi còn, timeout 8s. Thêm ánh xạ nhãn tiếng Việt/đồng nghĩa. Model mặc định `gemini-3.8-flash`. |
| Phân tích hành vi | Tính từ chính số liệu (tổng, trung bình, nhóm cao nhất, ngày bất thường, gợi ý); hiển thị tên danh mục tiếng Việt. |
| Endpoint mới | `POST /api/ai/classify` (ảnh + note) để client xem/sửa danh mục trước khi lưu. |
| Luồng Camera | Bỏ chụp trực tiếp: chọn ảnh / ảnh mẫu / không ảnh → form nhập text → AI tự gợi ý → sửa thủ công → lưu. |
| **OCR hóa đơn (ML Kit) + chụp ảnh lại** | Thêm lại **chụp ảnh (CameraX)** + **chọn ảnh/thư viện**; **OCR ML Kit** (offline, không quota) đọc hóa đơn thành text → **ghi chú tóm tắt** (bỏ tiêu đề/boilerplate) + **trích số tiền**; engine nội bộ phân loại. Đã test: hóa đơn mì/bún → **Ăn uống** (lưu DB OK). Thêm dependency `com.google.mlkit:text-recognition`. Hạn chế: đôi khi trích số tiền nhầm dòng hàng (sửa tay được). |
| **Hóa đơn nhiều danh mục (Hướng 1)** | Server `POST /ai/classify` trả thêm `candidates` (mọi danh mục khớp). App hiển thị "Hóa đơn có thể gồm: …" để chọn **danh mục chính**; không đổi schema. Test: `"cơm tấm và grab"` → `[transport, food]`; app hiện gợi ý. |
| Sửa lỗi | Cảnh báo "số tiền phải lớn hơn 0" sai; double-delete khi swipe; Room cache không xóa bản ghi đã mất (`replaceAll`); Album/Stats không refresh khi mở lại tab; 401 → về Auth; crash `SavedStateProvider`. |
| Dữ liệu | Đã xóa sạch DB + ảnh upload; seed 10 chi phí chuẩn danh mục cho user `demo@snapspend.vn` / `secret123`. |

## Backlog ghi khi phát sinh lỗi/kế hoạch mới

| Ngày | Bước | Nội dung | Cách xử lý | Trạng thái |
|------|------|----------|------------|------------|
| ___ | ___ | ___ | ___ | ⬜ |
