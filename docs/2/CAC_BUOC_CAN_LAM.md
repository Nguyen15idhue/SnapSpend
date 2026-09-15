# SnapSpend — Các bước cần làm (chi tiết theo Phase)

> Nguồn: `KE_HOACH_HOAN_THIEN.md` + `PHAN_TICH_HIEN_TRANG.md` (cùng folder `docs/2`).
> Cách dùng: làm xong bước nào thì ghi kết quả vào `KET_QUA_DAT_DUOC.md` theo đúng số bước.
> Quy ước trạng thái: `⬜ Chưa làm | 🔄 Đang làm | ✅ Đạt | ❌ Fail (ghi rõ lỗi) | ⛔ Ngoài phạm vi`
> Mỗi bước gồm 4 phần: **Nội dung** · **Yêu cầu cần đạt** · **Checklist test** · **Ghi chú**.

---

## ⚠️ Phạm vi rút gọn (chốt ngày 2026-09-15)

Đây là **bài tập lớn**, không cần mức production hardening. Kế hoạch được rút gọn còn **21 bước**
(GĐ1–GĐ4 + 6.1, 6.4). Các bước sau **⛔ NGOÀI PHẠM VI** (vẫn giữ trong tài liệu để tham khảo, không bắt buộc làm):

| Bước bỏ | Lý do |
|---------|-------|
| 3.3 — Phân trang danh sách | Quy mô demo nhỏ, chưa cần |
| 3.5 — Camera nâng cấp (đổi cam/flash) | Không thiết yếu cho luồng chính; emulator khó kiểm chứng |
| 4.4 — CI GitHub Actions | Không push repo, không cần pipeline |
| 5.1–5.10 — Toàn bộ GĐ5 Production hardening | Rate limit, HTTPS/CORS, refresh token, object storage, Swagger, logging, backup, moderation, deploy, soft-delete — vượt yêu cầu môn học |
| 6.2 — Animation + skeleton | Polish nâng cao, không bắt buộc |
| 6.3 — Accessibility (TalkBack) | Polish nâng cao, không bắt buộc |
| 6.5 — Hoàn thiện tài liệu (API.md, CONTRIBUTING) | Đã có README + docs/2; không cần thêm |

**Giữ lại:** GĐ1 (1.1–1.4), GĐ2 (2.1–2.8), GĐ3 (3.1, 3.2, 3.4, 3.6), GĐ4 (4.1–4.3), GĐ6 (6.1, 6.4).

> **Cập nhật 2026-09-15 (lần 2):** App **bỏ hẳn Demo/Mock, chỉ dùng Real** (theo yêu cầu người dùng).
> Do đó **Bước 2.4 (persist công tắc Demo/Real) trở nên không còn áp dụng** (đã gỡ `AppConfig.isDemo`,
> `MockRepository`, công tắc ở Profile); phần "Mock" trong 2.5/3.1/3.2/6.4 cũng không còn.
> Backend bắt buộc chạy để app hoạt động.

---

## GĐ1 — Nền tảng (bắt buộc làm trước)

### Bước 1.1 — Chốt migration là nguồn schema duy nhất
**Mã liên quan:** FND-01, DB-01
**Nội dung:**
1. Xóa dòng mount `./db/schema.sql` khỏi `server/docker-compose.yml:14` (giữ file `db/schema.sql` làm tài liệu tham khảo, không dùng để init).
2. Reset DB local: trong `server/` chạy `docker compose down -v` rồi `docker compose up -d postgres`.
3. Chạy API lần đầu để `MigrateAsync()` tự tạo 5 bảng (`users`, `categories`, `expenses`, `friendships`, `expense_shares`) + bảng `__EFMigrationsHistory`.
4. Chuyển seed 9 category từ runtime (`Program.cs`) vào migration hoặc seed data, đảm bảo idempotent (chạy lại không lỗi/nhân đôi).

**Yêu cầu cần đạt:**
- Từ máy sạch, chỉ `docker compose up -d` + chạy API là có đủ bảng + 9 category.
- Không còn lỗi `relation "..." already exists`.
- Không còn 2 nguồn định nghĩa schema song song.

**Checklist test:**
- [ ] `docker compose down -v` xóa sạch volume; `up -d postgres` tạo DB trống.
- [ ] Chạy API → log có apply `InitialCreate`, không exception.
- [ ] `\dt` thấy đủ 5 bảng + `__EFMigrationsHistory`.
- [ ] `SELECT count(*) FROM categories;` = 9.
- [ ] Chạy API lần 2 không lỗi (migration idempotent).

**Ghi chú:** Nếu cần giữ dữ liệu cũ, backup `.sql` trước khi `down -v`; hiện dữ liệu local là dữ liệu test.

---

### Bước 1.2 — Dọn code server nhỏ
**Mã liên quan:** SRV-01, SRV-08
**Nội dung:**
1. Xóa các dòng `ToTable(...)` bị lặp trong `server/.../Data/AppDbContext.cs:16-26`.
2. Thêm validate `from <= to` cho `GET /stats` và `POST /ai/analyze`.
3. Thay `DateOnly.TryParse` bằng parse chặt `yyyy-MM-dd` (`DateOnly.TryParseExact`), trả 400 rõ ràng nếu sai.

**Yêu cầu cần đạt:**
- `AppDbContext` gọn, không trùng lệnh cấu hình.
- Stats/analyze trả 400 với `from > to` và ngày sai định dạng.

**Checklist test:**
- [ ] `dotnet build` xanh, 0 warning/0 error.
- [ ] `GET /stats?from=2026-09-30&to=2026-09-01` → 400 + `{ "message": ... }`.
- [ ] `GET /stats?from=01/09/2026&to=...` → 400 (không parse kiểu culture).
- [ ] `GET /stats` với input hợp lệ vẫn trả đúng dữ liệu.

**Ghi chú:** Giữ nguyên shape response hiện có để không phá Android.

---

### Bước 1.3 — Health check + cấu hình khởi động an toàn
**Mã liên quan:** SRV-11, SRV-12
**Nội dung:**
1. Thêm `GET /health` kiểm tra kết nối DB (trả 200/503).
2. Khi khởi động: kiểm tra `Jwt:Key` tồn tại và dài ≥ 32 ký tự; ném lỗi rõ ràng nếu là placeholder/thiếu.

**Yêu cầu cần đạt:**
- `/health` phản ánh đúng trạng thái DB.
- API từ chối chạy nếu JWT key yếu/placeholder.

**Checklist test:**
- [ ] `GET /health` → 200 khi DB chạy; 503 khi DB tắt.
- [ ] Chạy API với `Jwt__Key=short` → dừng ngay, log lỗi rõ.
- [ ] Chạy API với key hợp lệ → khởi động bình thường.

**Ghi chú:** `/health` dùng cho Docker healthcheck và CI sau này.

---

### Bước 1.4 — Cập nhật README + FILE_LIST
**Mã liên quan:** DOC-01, DOC-02, FND-02
**Nội dung:**
1. Viết lại `README.md`: kiến trúc, Demo/Real, các lệnh chạy local (3 lệnh), yêu cầu môi trường.
2. Tạo lại `FILE_LIST.txt` hoặc bỏ hẳn và mô tả cấu trúc thư mục trong README (khuyến nghị bỏ FILE_LIST, dùng README).

**Yêu cầu cần đạt:**
- Người mới đọc README có thể chạy được app + server mà không cần hỏi.

**Checklist test:**
- [ ] Đọc README, làm theo đúng lệnh → build xanh cả 2 phía.
- [ ] README không còn thông tin lỗi thời (bin/obj, file đã xóa).

**Ghi chú:** Ghi rõ mặc định là Demo mode để tránh nhầm cần server.

---

## GĐ2 — Kiến trúc Android

### Bước 2.1 — Tách ViewModel cho từng màn
**Mã liên quan:** AND-01
**Nội dung:**
1. Tạo package `ui/viewmodel/` gồm: `AuthViewModel`, `AlbumViewModel`, `ExpenseFormViewModel`, `StatsViewModel`, `ProfileViewModel`, `DetailViewModel`.
2. Chuyển state + logic gọi repo từ `MainActivity.kt` vào ViewModel; dùng `viewModelScope`, `StateFlow`, `collectAsStateWithLifecycle`.
3. Composable nhận state + callback, không tự gọi repo.

**Yêu cầu cần đạt:**
- `MainActivity.kt` chỉ còn điểm vào + NavHost (mục tiêu < 150 dòng).
- Không còn lời gọi `repo.xxx()` trực tiếp trong composable.

**Checklist test:**
- [ ] `assembleDebug` xanh.
- [ ] Đi hết flow ở Demo mode: login → tạo → album → detail → stats → profile.
- [ ] Xoay màn hình: dữ liệu form/danh sách giữ nguyên.

**Ghi chú:** Refactor từng màn, mỗi màn test tay xong mới sang màn kế để tránh hỏng dây chuyền.

---

### Bước 2.2 — Navigation-Compose + lưu state
**Mã liên quan:** AND-02, AND-01
**Nội dung:**
1. Dựng `NavHost` với route `auth`, `home`, `detail/{id}`, `form`; BottomBar 4 tab nằm trong `home`.
2. Thay `remember` bằng `rememberSaveable`/`SavedStateHandle` cho input quan trọng (email, amount, note, filter…).
3. Bổ sung điều hướng nội bộ tới `detail/{id}`.

**Yêu cầu cần đạt:**
- Back stack đúng (Back từ Detail về Album, không thoát app).
- Xoay màn hình không mất dữ liệu form.

**Checklist test:**
- [ ] Từ Album → Detail → Back quay lại đúng Album.
- [ ] Nhập form, xoay màn hình, nội dung còn nguyên.
- [ ] Logout quay về route `auth`, không còn back về home.

**Ghi chú:** Giữ `navigation-compose` đang có; không thêm lib mới.

---

### Bước 2.3 — Đồng bộ Room entity với DTO
**Mã liên quan:** AND-03
**Nội dung:**
1. Thêm `aiConfidence` (và `categorySource` nếu cần) vào `ExpenseEntity` + `ExpenseDao`.
2. Cập nhật map ở `RealRepository` (bỏ việc gán `aiConfidence = null`).
3. Bump version Room + viết `Migration` (hoặc `fallbackToDestructiveMigration` cho dev).

**Yêu cầu cần đạt:**
- Real mode sau khi refresh/reload vẫn hiện đúng badge `AI xx%`.

**Checklist test:**
- [ ] Tạo expense ở Real mode có `aiConfidence` → về Album thấy badge.
- [ ] Kill app, mở lại: badge vẫn còn (đọc từ Room).
- [ ] Không crash khi migrate DB cũ.

**Ghi chú:** Mock mode đã có sẵn `aiConfidence`; chỉ cần đảm bảo parity.

---

### Bước 2.4 — Persist công tắc Demo/Real
**Mã liên quan:** FND-03
**Nội dung:**
1. Lưu `isDemo` bằng DataStore Preferences (dependency đã có).
2. Đọc giá trị lúc khởi động thay vì hard-code `true`.

**Yêu cầu cần đạt:**
- Lựa chọn Demo/Real được nhớ qua các lần mở app.

**Checklist test:**
- [ ] Tắt Demo → thoát app → mở lại vẫn ở Real mode.
- [ ] Bật lại Demo → mở lại vẫn Demo.
- [ ] Đổi mode không crash.

**Ghi chú:** `MainActivity` cần đọc DataStore bất đồng bộ trước khi tạo repo.

---

### Bước 2.5 — Sửa Undo + dọn Mock
**Mã liên quan:** AND-06, AND-12, AND-13
**Nội dung:**
1. Undo khôi phục đầy đủ bản ghi cũ (amount, category, note, date, **ảnh**, aiConfidence) — lưu snapshot trước khi xóa và khôi phục.
2. Xóa dòng `check(old.id == id)` vô nghĩa trong `MockRepository.updateExpense`.
3. Chuẩn hóa heuristic phân loại: bỏ dấu tiếng Việt trước khi match (dùng chung logic với server).

**Yêu cầu cần đạt:**
- Undo trả lại đúng dữ liệu như trước khi xóa.
- `"Pho Thin"`, `"phở"` đều phân loại `food`.

**Checklist test:**
- [ ] Xóa expense có ảnh → Undo → ảnh và dữ liệu trở lại.
- [ ] Tạo expense note `"Pho Thin"` (không dấu) → category `food`.
- [ ] Build + flow Demo/Real không lỗi.

**Ghi chú:** Nếu repo chưa hỗ trợ khôi phục ảnh, thêm hàm `restoreExpense` vào interface + cả 2 impl.

---

### Bước 2.6 — Token an toàn + xử lý 401
**Mã liên quan:** AND-04, AND-14, AND-15
**Nội dung:**
1. Chuyển token sang EncryptedSharedPreferences/DataStore.
2. Thêm interceptor/`Authenticator` phát hiện 401 → xóa token + điều hướng về Auth.
3. Thêm lớp xử lý lỗi mạng chung để màn hình hiện thông báo thống nhất.

**Yêu cầu cần đạt:**
- Token hết hạn/không hợp lệ → tự động về màn đăng nhập, không crash.
- Token không lưu dạng plain text.

**Checklist test:**
- [ ] Giả lập token sai → gọi API → về Auth.
- [ ] Kiểm tra file prefs không đọc được token plain.
- [ ] Đăng nhập lại hoạt động bình thường.

**Ghi chú:** Encrypted prefs cần xử lý lỗi trên emulator/thiết bị cũ.

---

### Bước 2.7 — Nén ảnh trước upload
**Mã liên quan:** AND-07
**Nội dung:**
1. Trong `RealRepository.createImagePart`: decode ảnh, scale cạnh dài ≤ 1600px, nén JPEG chất lượng ~80 (mục tiêu < ~1.5MB).
2. Xử lý ảnh xoay theo EXIF (nếu có).
3. Dọn file tạm sau khi upload.

**Yêu cầu cần đạt:**
- Ảnh chụp độ phân giải cao vẫn upload nhanh, server nhận đúng.
- Kích thước file sau nén giảm rõ rệt.

**Checklist test:**
- [ ] So sánh dung lượng trước/sau nén (log hoặc kiểm tra server).
- [ ] Upload ảnh 8MP thành công, ảnh hiển thị đúng chiều.
- [ ] Không rò rỉ file tạm trong cache.

**Ghi chú:** Giữ nguyên API multipart hiện tại (`@Part image`).

---

### Bước 2.8 — Thêm testTag/contentDescription
**Mã liên quan:** AND-09
**Nội dung:**
1. Gắn `Modifier.testTag(...)` + `contentDescription` cho: 4 tab, nút chụp, nút Album/Ảnh mẫu, field amount/note, nút Lưu, nút xóa/sửa/share, chip category.

**Yêu cầu cần đạt:**
- UI test tìm được element theo tag ổn định.

**Checklist test:**
- [ ] Dump UI thấy `resource-id`/tag tương ứng.
- [ ] Test tìm theo tag không phụ thuộc vị trí/ toạ độ.

**Ghi chú:** Đây là tiền đề bắt buộc cho GĐ4 (UI test).

---

## GĐ3 — Tính năng & backend

### Bước 3.1 — Nguồn category duy nhất + `GET /categories`
**Mã liên quan:** SRV-02
**Nội dung:**
1. Tạo nguồn category dùng chung ở server (hằng số/`CategoryCatalog`).
2. Thêm `GET /api/categories`.
3. Validate expense dùng nguồn đó; seed cũng lấy từ đó.
4. Android: ưu tiên gọi endpoint, fallback về list local.

**Yêu cầu cần đạt:**
- Thêm/đổi category chỉ sửa 1 chỗ; client lấy được danh sách từ server.

**Checklist test:**
- [ ] `GET /categories` trả đủ 9 category (key/name/emoji).
- [ ] Tạo expense với category hợp lệ → OK; category lạ → 400.
- [ ] App hiển thị category từ server (khi Real) trùng với seed.

**Ghi chú:** Giữ tương thích: category key không đổi.

---

### Bước 3.2 — Xem chi tiêu được chia sẻ
**Mã liên quan:** SRV-05
**Nội dung:**
1. Thêm `GET /api/shared-with-me` (join `expense_shares` theo receiver = userId).
2. Thêm mục/màn hiển thị trong app (chỉ xem, không sửa/xóa).

**Yêu cầu cần đạt:**
- A share cho B → B thấy đúng khoản đó; B không sửa/xóa được.

**Checklist test:**
- [ ] Share từ A → B list shared chứa expense đúng.
- [ ] B cố update/delete expense được share → 403/404.
- [ ] Mock mode mô phỏng được shared list (parity).

**Ghi chú:** Bảng `expense_shares` đã có; chỉ thiếu API + UI.

---

### Bước 3.3 — Phân trang danh sách ⛔ NGOÀI PHẠM VI
**Mã liên quan:** SRV-07
**Nội dung:**
1. `GET /expenses?page=&size=` (hoặc cursor) trả kèm tổng.
2. Android load thêm khi cuộn; Room vẫn cache.

**Yêu cầu cần đạt:**
- 1000+ expense không tải một lần; UX cuộn mượt.

**Checklist test:**
- [ ] Seed nhiều expense → API trả đúng trang.
- [ ] App cuộn tới cuối tải thêm, không trùng/không sót.
- [ ] Trang rỗng/ngoài phạm vi trả đúng.

**Ghi chú:** Cân nhắc giữ tương thích khi không truyền `page` (trả mặc định).

---

### Bước 3.4 — Xóa ảnh khi xóa expense
**Mã liên quan:** SRV-09
**Nội dung:**
1. Trong `DELETE /expenses/{id}`: lấy `imageUrl` trước khi xóa, xóa file vật lý tương ứng.
2. Xử lý an toàn khi file không tồn tại/đường dẫn lạ.

**Yêu cầu cần đạt:**
- Xóa expense không để lại file rác trong `wwwroot/uploads`.

**Checklist test:**
- [ ] Tạo expense có ảnh → xóa → file biến mất khỏi uploads.
- [ ] Xóa expense không ảnh → không lỗi.
- [ ] Không xóa nhầm file của expense khác.

**Ghi chú:** Tách hàm xóa file dùng chung với account delete.

---

### Bước 3.5 — Camera nâng cấp ⛔ NGOÀI PHẠM VI
**Mã liên quan:** AND-08
**Nội dung:**
1. Thêm nút đổi camera trước/sau và flash.
2. Xử lý `onError` khi chụp thất bại → hiện thông báo.

**Yêu cầu cần đạt:**
- Chụp được cả 2 camera; lỗi chụp có phản hồi rõ.

**Checklist test:**
- [ ] Đổi camera trước/sau, preview đổi đúng.
- [ ] Bật/tắt flash hoạt động.
- [ ] Giả lập lỗi chụp → hiện thông báo, không im lặng.

**Ghi chú:** Giữ nút "Ảnh mẫu" cho emulator.

---

### Bước 3.6 — Kiểm chứng AI Gemini với key thật
**Mã liên quan:** SRV-03, SRV-04
**Nội dung:**
1. Cấu hình `GEMINI_API_KEY` (và `AI_MODEL=gemini-2.5-flash` nếu cần), xác nhận tên model đúng.
2. Xác nhận payload Gemini `generateContent` (`inline_data` cho ảnh + JSON output) đúng; lưu response mẫu.
3. Đảm bảo fallback heuristic vẫn chạy khi không có key/lỗi.

**Yêu cầu cần đạt:**
- classify ảnh + analyze trả kết quả thật; fallback đúng khi lỗi.

**Checklist test:**
- [ ] Tạo expense có ảnh ở Real mode → category + confidence hợp lý.
- [ ] `POST /ai/analyze` trả summary/trends/anomalies/recommendations thật.
- [ ] Bỏ key → fallback hoạt động.

**Ghi chú:** Không commit key; dùng `.env` (đã gitignore). Cấu hình: `Ai:ApiKey`, `Ai:Model`, `Ai:BaseUrl`.

---

## GĐ4 — Kiểm thử & CI

### Bước 4.1 — Test server
**Mã liên quan:** TST-01, TST-04
**Nội dung:**
1. Thêm project `SnapSpend.Api.Tests` (xUnit + `WebApplicationFactory`), DB test riêng (container hoặc DB tạm).
2. Test: register/login (sai mật khẩu 401, trùng email 409), expenses CRUD, stats biên (`from > to`), share cần friendship.

**Yêu cầu cần đạt:**
- `dotnet test` xanh, phủ các luồng lõi + trường hợp lỗi.

**Checklist test:**
- [ ] `dotnet test` xanh toàn bộ.
- [ ] Có test cho 400/401/404/409.
- [ ] Test không phụ thuộc DB local đang chạy sẵn.

**Ghi chú:** Cân nhắc `Testcontainers` để cô lập DB.

---

### Bước 4.2 — Unit test Android
**Mã liên quan:** TST-02
**Nội dung:**
1. Test `formatVnd`, `guessCategory`, tính `stats` mock, mapping Room↔DTO.

**Yêu cầu cần đạt:**
- `gradlew test` xanh, có test cho logic thuần.

**Checklist test:**
- [ ] `.\gradlew.bat test` xanh.
- [ ] Test cover các case biên (amount 0, ngày biên).

**Ghi chú:** Logic thuần không cần Android framework → JUnit thường.

---

### Bước 4.3 — UI test
**Mã liên quan:** TST-03
**Nội dung:**
1. Chọn hướng: **khuyến nghị Compose UI Test** cho luồng login → tạo expense → album → detail → stats.
2. Nếu cần E2E/bàn giao diện: dùng Appium (`uiautomator2`) hoặc Maestro; đưa PoC vào repo.

**Yêu cầu cần đạt:**
- Ít nhất 1–2 kịch bản E2E chạy tự động, có screenshot khi fail.

**Checklist test:**
- [ ] Chạy test trên emulator xanh.
- [ ] Test tìm element theo `testTag` (từ bước 2.8), không dùng toạ độ.
- [ ] Fail có screenshot/log.

**Ghi chú:** Đảm bảo `adb devices` thấy emulator trước khi chạy.

---

### Bước 4.4 — CI GitHub Actions ⛔ NGOÀI PHẠM VI
**Mã liên quan:** FND-05
**Nội dung:**
1. Workflow 2 job: (a) `dotnet build` + `dotnet test`; (b) `gradlew test` + `assembleDebug`.
2. Cache Gradle/NuGet.

**Yêu cầu cần đạt:**
- Mỗi push/PR tự chạy build + test.

**Checklist test:**
- [ ] Push lên nhánh → Actions chạy xanh.
- [ ] Fail build chặn được (thấy rõ trong PR).

**Ghi chú:** Thêm badge trạng thái vào README.

---

## GĐ5 — Production hardening (tùy nhu cầu deploy) ⛔ NGOÀI PHẠM VI

### Bước 5.1 — Rate limiting (SRV-11)
**Nội dung:** Thêm ASP.NET RateLimiter cho endpoint nhạy cảm (auth, tạo expense).
**Yêu cầu:** Chặn spam, trả 429 có thông báo.
**Checklist test:** [ ] Gọi liên tục vượt ngưỡng → 429. [ ] Luồng bình thường không bị chặn.
**Ghi chú:** Ngưỡng cấu hình theo môi trường.

### Bước 5.2 — HTTPS + CORS (SRV-11)
**Nội dung:** Cấu hình HTTPS và CORS theo môi trường (không mở toàn bộ).
**Yêu cầu:** Chỉ origin hợp lệ gọi được; production bắt buộc HTTPS.
**Checklist test:** [ ] Origin lạ bị chặn. [ ] App/domain hợp lệ gọi OK.
**Ghi chú:** Local dev có thể nới lỏng.

### Bước 5.3 — Refresh token / token ngắn hạn (AND-04)
**Nội dung:** Thêm refresh token, access token ngắn hạn; luồng tự refresh.
**Yêu cầu:** Token hết hạn tự refresh, không bắt đăng nhập lại liên tục.
**Checklist test:** [ ] Access token hết hạn → tự refresh. [ ] Refresh token hết hạn → về Auth.
**Ghi chú:** Liên quan bước 2.6.

### Bước 5.4 — Object storage cho ảnh (SRV-10)
**Nội dung:** Thay `StorageService` local bằng object storage (Supabase Storage/S3), cập nhật cấu hình + xóa ảnh.
**Yêu cầu:** Ảnh bền vững khi đổi máy/volume; URL truy cập được.
**Checklist test:** [ ] Upload → ảnh xem được từ URL public/CDN. [ ] Xóa expense xóa cả object.
**Ghi chú:** Giữ interface `StorageService` để đổi impl không phá endpoint.

### Bước 5.5 — Swagger/OpenAPI + ProblemDetails (SRV-11)
**Nội dung:** Bật OpenAPI, chuẩn hóa lỗi theo ProblemDetails.
**Yêu cầu:** Tài liệu API tự sinh; lỗi nhất quán.
**Checklist test:** [ ] Mở Swagger thấy đủ endpoint. [ ] Lỗi trả đúng chuẩn.
**Ghi chú:** Cân nhắc giữ `{ "message": ... }` cho tương thích client cũ.

### Bước 5.6 — Logging structured + tracing (SRV-13)
**Nội dung:** Thêm logging có cấu trúc, correlation id, health/metrics.
**Yêu cầu:** Dễ truy vết lỗi trên môi trường thật.
**Checklist test:** [ ] Log có id tương quan. [ ] Không log secret.
**Ghi chú:** Không log token/mật khẩu.

### Bước 5.7 — Seed/migration dữ liệu + backup (DB-01)
**Nội dung:** Quy trình migration rõ ràng, backup định kỳ.
**Yêu cầu:** Có thể dựng lại DB từ migration + seed.
**Checklist test:** [ ] DB mới + migration + seed = chạy được. [ ] Có script backup.
**Ghi chú:** Tài liệu hóa quy trình.

### Bước 5.8 — Image moderation/virus scan, strip EXIF
**Nội dung:** Thêm kiểm tra nội dung ảnh + quét virus + strip metadata.
**Yêu cầu:** Ảnh không phù hợp bị chặn; không lộ vị trí từ EXIF.
**Checklist test:** [ ] Ảnh sai loại/quá lớn bị chặn. [ ] EXIF bị loại bỏ.
**Ghi chú:** Có thể làm sau khi có object storage.

### Bước 5.9 — Deploy (host free) + cấu hình env
**Nội dung:** Docker hóa API, deploy lên Render/Railway/Fly/Oracle free; cấu hình connection string, JWT, storage.
**Yêu cầu:** APK trỏ domain thật chạy được ở mọi nơi.
**Checklist test:** [ ] Gọi `/health` từ internet OK. [ ] App Real mode trên máy thật hoạt động.
**Ghi chú:** Cập nhật `API_BASE_URL` release (FND-07).

### Bước 5.10 — Soft-delete user (DB-02)
**Nội dung:** Thêm cờ xóa mềm nếu cần khôi phục.
**Yêu cầu:** Xóa user không mất dữ liệu ngay (nếu nghiệp vụ cần).
**Checklist test:** [ ] User xóa mềm không đăng nhập được. [ ] Dữ liệu vẫn truy vết được.
**Ghi chú:** Chỉ làm nếu có yêu cầu nghiệp vụ.

---

## GĐ6 — Polish UI/UX

### Bước 6.1 — Dark mode
**Nội dung:** Thêm `darkColorScheme`, đổi status/nav bar theo theme.
**Yêu cầu:** App hiển thị đúng cả light/dark.
**Checklist test:** [ ] Bật dark mode hệ thống → app tối, chữ đọc được. [ ] Không hard-code trắng.
**Ghi chú:** `themes.xml` đang hard-code trắng.

### Bước 6.2 — Animation + skeleton loading ⛔ NGOÀI PHẠM VI
**Nội dung:** Thêm chuyển màn mượt, skeleton/shimmer khi tải.
**Yêu cầu:** Trải nghiệm mượt, không nhấp nháy.
**Checklist test:** [ ] Chuyển tab/màn có animation. [ ] Loading hiện skeleton.
**Ghi chú:** Không hy sinh hiệu năng.

### Bước 6.3 — Accessibility ⛔ NGOÀI PHẠM VI
**Nội dung:** Đảm bảo touch target ≥ 48dp, contrast, contentDescription đầy đủ.
**Yêu cầu:** Dùng được với TalkBack.
**Checklist test:** [ ] TalkBack đọc được control chính. [ ] Contrast đạt.
**Ghi chú:** Liên quan bước 2.8.

### Bước 6.4 — Cải thiện empty/error state
**Nội dung:** Empty/error state đẹp hơn; thêm nút demo `failNext` (mock).
**Yêu cầu:** 3 trạng thái loading/empty/error rõ ràng ở mọi màn có dữ liệu.
**Checklist test:** [ ] Demo được từng trạng thái. [ ] Không màn nào thiếu.
**Ghi chú:** Dùng `MockRepository.failNext`.

### Bước 6.5 — Hoàn thiện tài liệu ⛔ NGOÀI PHẠM VI
**Nội dung:** Cập nhật `API.md` (đủ status code, ví dụ response), thêm CONTRIBUTING.
**Yêu cầu:** Tài liệu khớp hành vi thực tế.
**Checklist test:** [ ] API.md khớp endpoint thật. [ ] CONTRIBUTING có quy trình commit/test.
**Ghi chú:** Giữ `AGENTS.md` đồng bộ.

---

## Phụ lục — Backlog dài hạn (chưa xếp lịch)

- Push notification nhắc nhập chi tiêu.
- Budget theo category, mục tiêu tiết kiệm, đa tiền tệ.
- Friends request/accept/reject đầy đủ + chặn bạn.
- Export CSV/PDF, widget home screen, i18n.
