# SnapSpend — Kế hoạch hoàn thiện mã nguồn

> Ngày lập: 2026-09-14
> Nguồn: `PHAN_TICH_HIEN_TRANG.md` (các mã vấn đề FND/AND/SRV/DB/DOC/TST)
> Mục tiêu: đưa repo từ "MVP demo chạy được" lên "sản phẩm ổn định, có kiểm thử, sẵn sàng deploy".
> Nguyên tắc: mỗi giai đoạn phải **build xanh + chạy thật** trước khi sang giai đoạn sau; làm xong bước nào ghi kết quả ngay.

Quy ước trạng thái: `⬜ Chưa làm | 🔄 Đang làm | ✅ Đạt | ❌ Fail`

> **Quyết định kỹ thuật đã chốt (2026-09-14):**
> - **Database**: PostgreSQL local (docker compose); migration là nguồn schema duy nhất.
> - **AI provider**: **Google Gemini** (`gemini-2.5-flash`) thay OpenAI; cấu hình qua `Ai:ApiKey` / `Ai:Model` / `Ai:BaseUrl`; giữ fallback heuristic khi thiếu key hoặc lỗi.
> - **Ảnh**: lưu local (chưa dùng cloud storage).

---

## 0. Tiêu chí DONE toàn cục

- [ ] `dotnet build` và `gradlew :app:assembleDebug` xanh, không warning nghiêm trọng.
- [ ] Cả Demo mode và Real mode (Postgres local) đi hết flow không crash.
- [ ] Migration là nguồn schema duy nhất; `docker compose up -d` từ máy sạch tạo đủ bảng + seed.
- [ ] Có test tự động cho các luồng lõi (server + Android).
- [ ] Có CI chạy build + test khi push.
- [ ] README + tài liệu khớp trạng thái thực tế.

---

## 1. Ưu tiên & thứ tự thực hiện

| Thứ tự | Giai đoạn | Vì sao trước | Ước lượng |
|--------|-----------|--------------|-----------|
| 1 | **GĐ1 — Nền tảng** | FND-01 đang chặn chạy Real local; mọi việc khác dựa trên nền này | 0.5–1 ngày |
| 2 | **GĐ2 — Kiến trúc Android** | Rủi ro cao nhất về bảo trì; chạm nhiều file nên làm sớm | 2–3 ngày |
| 3 | **GĐ3 — Tính năng & backend** | Sau khi khung UI sạch | 2–3 ngày |
| 4 | **GĐ4 — Kiểm thử & CI** | Cần code ổn định để viết test | 1.5–2 ngày |
| 5 | **GĐ5 — Production hardening** | Tùy nhu cầu triển khai thật | 2–4 ngày |
| 6 | **GĐ6 — Polish UI/UX** | Làm cuối, không chặn chức năng | 1–2 ngày |

---

## GĐ1 — Nền tảng (bắt buộc trước)

### Bước 1.1: Chốt migration là nguồn schema duy nhất (FND-01, DB-01)
- Trạng thái: ⬜
- Việc làm:
  1. Xóa mount `./db/schema.sql` khỏi `docker-compose.yml:14` (giữ file làm tài liệu tham khảo).
  2. Reset DB local: `docker compose down -v` → `docker compose up -d postgres`.
  3. Chạy API lần đầu để `MigrateAsync()` tạo 5 bảng + `__EFMigrationsHistory`.
  4. Chuyển seed 9 category (`Program.cs:36-48`) vào migration/seed data (hoặc giữ runtime seed nhưng idempotent).
- Tiêu chí đạt:
  - [ ] DB sạch tạo đủ bảng chỉ bằng migration.
  - [ ] `GET /` OK; register/login/chạy được.
  - [ ] Không còn lỗi `relation already exists`.

### Bước 1.2: Dọn code server nhỏ (SRV-01, SRV-08)
- Trạng thái: ⬜
- Việc làm: xóa các dòng `ToTable` lặp trong `AppDbContext.cs:16-26`; thêm validate `from <= to` trong stats; dùng `DateOnly.ParseExact("yyyy-MM-dd")`.
- Tiêu chí: build xanh; `GET /stats` với `from > to` trả 400 rõ ràng.

### Bước 1.3: Health check + cấu hình khởi động (SRV-11, SRV-12)
- Trạng thái: ⬜
- Việc làm: thêm `GET /health` (kiểm tra DB); kiểm tra `Jwt:Key` dài ≥ 32 ký tự khi khởi động, báo lỗi rõ nếu thiếu.
- Tiêu chí: `/health` trả trạng thái DB; API từ chối chạy với key placeholder.

### Bước 1.4: Cập nhật README + FILE_LIST (DOC-01, DOC-02, FND-02)
- Trạng thái: ⬜
- Việc làm: viết lại `README.md` (kiến trúc, Demo/Real, chạy local 3 lệnh); tạo lại `FILE_LIST.txt` hoặc bỏ hẳn và mô tả cấu trúc trong README.
- Tiêu chí: người mới đọc README `docker compose up -d` → chạy app là được.

**Kết quả GĐ1:** ⬜ | Ngày: ___ | Ghi chú: ___

---

## GĐ2 — Kiến trúc Android

### Bước 2.1: Tách ViewModel cho từng màn (AND-01)
- Trạng thái: ⬜
- Việc làm: tạo `ui/viewmodel/` với `AuthViewModel`, `AlbumViewModel`, `CameraViewModel`(hoặc Form), `StatsViewModel`, `ProfileViewModel`, `DetailViewModel`. Chuyển state + gọi repo từ `MainActivity.kt` vào VM. Dùng `viewModelScope`, `StateFlow`, `collectAsStateWithLifecycle`.
- File ảnh hưởng: `MainActivity.kt` (giảm mạnh), thêm `ui/viewmodel/*.kt`.
- Tiêu chí:
  - [ ] `MainActivity.kt` chỉ còn điểm vào + NavHost (mục tiêu < 150 dòng).
  - [ ] Không còn business call trực tiếp trong composable.

### Bước 2.2: Navigation-Compose + lưu state (AND-02, AND-01)
- Trạng thái: ⬜
- Việc làm: dựng `NavHost` với route `auth`, `home`, `detail/{id}`, `form`; giữ BottomBar 4 tab. Thay `remember` bằng `rememberSaveable`/`SavedStateHandle` cho input quan trọng.
- Tiêu chí:
  - [ ] Back stack đúng; xoay màn hình không mất dữ liệu form.
  - [ ] Deep link `detail/{id}` hoạt động (ít nhất điều hướng nội bộ).

### Bước 2.3: Đồng bộ Room entity với DTO (AND-03)
- Trạng thái: ⬜
- Việc làm: thêm `aiConfidence` (và `categorySource` nếu cần) vào `ExpenseEntity` + `ExpenseDao`; cập nhật map ở `RealRepository`; bump DB version + viết migration Room.
- Tiêu chí: Real mode reload vẫn hiện badge AI đúng.

### Bước 2.4: Persist công tắc Demo/Real (FND-03)
- Trạng thái: ⬜
- Việc làm: lưu `isDemo` bằng DataStore Preferences (đã có dependency) thay vì `object` in-memory; đọc lúc khởi động.
- Tiêu chí: tắt/bật Demo rồi mở lại app vẫn nhớ.

### Bước 2.5: Sửa Undo + dọn Mock (AND-06, AND-12, AND-13)
- Trạng thái: ⬜
- Việc làm: Undo khôi phục cả ảnh/`aiConfidence` (lưu bản ghi tạm đầy đủ, hoặc repo hỗ trợ `restoreExpense`); bỏ `check` vô nghĩa; chuẩn hóa heuristic bỏ dấu tiếng Việt (dùng chung logic với server).
- Tiêu chí: Undo khôi phục đúng dữ liệu cũ; `"Pho Thin"` → `food`.

### Bước 2.6: Token an toàn + xử lý 401 (AND-04, AND-14, AND-15)
- Trạng thái: ⬜
- Việc làm: chuyển token sang EncryptedSharedPreferences/DataStore; thêm OkHttp `Authenticator`/interceptor phát hiện 401 → xóa token + điều hướng về Auth; thêm lớp xử lý lỗi mạng chung.
- Tiêu chí: token hết hạn → tự về màn đăng nhập, không crash.

### Bước 2.7: Nén ảnh trước upload (AND-07)
- Trạng thái: ⬜
- Việc làm: decode + scale + nén JPEG (mục tiêu < ~1.5MB, cạnh dài ≤ 1600px) trong `RealRepository.createImagePart`.
- Tiêu chí: ảnh chụp 8MP vẫn upload nhanh, server nhận đúng.

### Bước 2.8: Thêm testTag/contentDescription (AND-09)
- Trạng thái: ⬜
- Việc làm: gắn `Modifier.testTag(...)` + `contentDescription` cho control chính (tab, nút chụp, nút Lưu, field, nút xóa/sửa/share).
- Tiêu chí: tìm được element theo tag khi UI test.

**Kết quả GĐ2:** ⬜ | Ngày: ___ | Ghi chú: ___

---

## GĐ3 — Tính năng & backend

### Bước 3.1: Nguồn category duy nhất + `GET /categories` (SRV-02)
- Trạng thái: ⬜
- Việc làm: tạo hằng số category dùng chung ở server; thêm endpoint `GET /api/categories`; validate dựa trên nguồn đó. Android vẫn có fallback local nhưng ưu tiên gọi endpoint.
- Tiêu chí: thêm/đổi category chỉ sửa 1 chỗ.

### Bước 3.2: Xem chi tiêu được chia sẻ (SRV-05)
- Trạng thái: ⬜
- Việc làm: thêm `GET /api/shared-with-me`; thêm màn/tab hoặc mục trong Profile hiển thị; đảm bảo chỉ xem, không sửa.
- Tiêu chí: share từ tài khoản A → tài khoản B thấy đúng khoản đó.

### Bước 3.3: Phân trang danh sách (SRV-07)
- Trạng thái: ⬜
- Việc làm: `GET /expenses?page=&size=` hoặc cursor; Android load thêm khi cuộn (Room vẫn cache).
- Tiêu chí: dữ liệu 1000+ expense không tải một lần.

### Bước 3.4: Xóa ảnh khi xóa expense (SRV-09)
- Trạng thái: ⬜
- Việc làm: trong `DELETE /expenses/{id}` xóa file tương ứng; xử lý an toàn khi file không tồn tại.
- Tiêu chí: xóa expense → không còn file rác trong `uploads`.

### Bước 3.5: Camera nâng cấp (AND-08)
- Trạng thái: ⬜
- Việc làm: nút đổi camera trước/sau, flash, báo lỗi khi `takePicture` fail.
- Tiêu chí: chụp được cả 2 camera, có thông báo lỗi rõ.

### Bước 3.6: Kiểm chứng AI Gemini với key thật (SRV-03, SRV-04)
- Trạng thái: ⬜
- Việc làm: cấu hình `GEMINI_API_KEY`, xác nhận model (`gemini-2.5-flash`) + payload `generateContent` (inline_data ảnh, JSON output) đúng; ghi lại response mẫu.
- Tiêu chí: classify + analyze trả kết quả thật; fallback vẫn hoạt động khi không có key.

**Kết quả GĐ3:** ⬜ | Ngày: ___ | Ghi chú: ___

---

## GĐ4 — Kiểm thử & CI

### Bước 4.1: Test server (TST-01, TST-04)
- Trạng thái: ⬜
- Việc làm: thêm project `SnapSpend.Api.Tests` (xUnit + `WebApplicationFactory` + Postgres test container/DB riêng). Test: register/login (kể cả sai mật khẩu, trùng email), expenses CRUD, stats biên (`from>to`), share yêu cầu friendship.
- Tiêu chí: `dotnet test` xanh, phủ luồng lõi.

### Bước 4.2: Unit test Android (TST-02)
- Trạng thái: ⬜
- Việc làm: test `formatVnd`, `guessCategory`, tính `stats` mock, mapping Room↔DTO.
- Tiêu chí: `gradlew test` xanh.

### Bước 4.3: UI test (TST-03)
- Trạng thái: ⬜
- Việc làm: chọn 1 hướng — **khuyến nghị Compose UI Test** (chạy cùng process, ổn định, nhẹ) cho login → tạo expense → album → detail → stats. Nếu muốn E2E đa nền tảng mới dùng Appium/Maestro. Đưa PoC Appium hiện có vào repo nếu chọn Appium.
- Tiêu chí: 1–2 kịch bản E2E chạy tự động, có screenshot khi fail.

### Bước 4.4: CI GitHub Actions (FND-05)
- Trạng thái: ⬜
- Việc làm: workflow 2 job: (a) `dotnet build` + `dotnet test`; (b) `gradlew test` + `assembleDebug`. Cache Gradle/NuGet.
- Tiêu chí: PR/push tự chạy; badge trong README.

**Kết quả GĐ4:** ⬜ | Ngày: ___ | Ghi chú: ___

---

## GĐ5 — Production hardening (tùy nhu cầu deploy)

| Bước | Việc | Mã | Ưu tiên |
|------|------|----|---------|
| 5.1 | Rate limiting (ASP.NET RateLimiter) | SRV-11 | Cao |
| 5.2 | HTTPS + CORS cấu hình theo môi trường | SRV-11 | Cao |
| 5.3 | Refresh token / token ngắn hạn | AND-04 | Cao |
| 5.4 | Object storage cho ảnh (Supabase Storage/S3) thay local | SRV-10 | Cao |
| 5.5 | Swagger/OpenAPI + ProblemDetails chuẩn hóa lỗi | SRV-11 | Trung bình |
| 5.6 | Logging structured + tracing + health/metrics | SRV-13 | Trung bình |
| 5.7 | Seed/migration dữ liệu, backup định kỳ | DB-01 | Trung bình |
| 5.8 | Image moderation/virus scan, strip EXIF | — | Thấp |
| 5.9 | Deploy: Docker lên host free (Render/Railway/Oracle) + cấu hình env | FND-08, DOC-04 | Cao (nếu release) |
| 5.10 | Xóa cứng → soft-delete user (nếu cần) | DB-02 | Thấp |

**Kết quả GĐ5:** ⬜ | Ngày: ___ | Ghi chú: ___

---

## GĐ6 — Polish UI/UX

| Bước | Việc | Mã |
|------|------|----|
| 6.1 | Dark mode + đổi status/nav bar theo theme | AND-10 |
| 6.2 | Animation chuyển màn, skeleton/shimmer loading | AND-10 |
| 6.3 | Accessibility: touch target, contrast, contentDescription đầy đủ | AND-09 |
| 6.4 | Empty/error state đẹp hơn; demo `failNext` có nút bật | AND-13 |
| 6.5 | Cải thiện tài liệu (CONTRIBUTING/AGENTS, API đầy đủ status code) | DOC-03, DOC-05 |

**Kết quả GĐ6:** ⬜ | Ngày: ___ | Ghi chú: ___

---

## Phụ lục A — Bản đồ vấn đề → bước xử lý

| Mã | Bước |
|----|------|
| FND-01 | 1.1 |
| FND-02 | 1.4 |
| FND-03 | 2.4 |
| FND-05 | 4.4 |
| FND-07 | 5.9 |
| FND-08 | 5.9 |
| AND-01 | 2.1, 2.2 |
| AND-02 | 2.2 |
| AND-03 | 2.3 |
| AND-04 | 2.6, 5.3 |
| AND-06 | 2.5 |
| AND-07 | 2.7 |
| AND-08 | 3.5 |
| AND-09 | 2.8, 6.3 |
| AND-10 | 6.1, 6.2 |
| AND-12, AND-13 | 2.5 |
| AND-14, AND-15 | 2.6 |
| SRV-01, SRV-08 | 1.2 |
| SRV-02 | 3.1 |
| SRV-03, SRV-04 | 3.6 |
| SRV-05 | 3.2 |
| SRV-07 | 3.3 |
| SRV-09 | 3.4 |
| SRV-10 | 5.4 |
| SRV-11 | 1.3, 5.1, 5.2, 5.5 |
| SRV-12 | 1.3 |
| SRV-13 | 5.6 |
| DB-01 | 1.1, 5.7 |
| DB-02 | 5.10 |
| DOC-01, DOC-02 | 1.4 |
| DOC-03, DOC-05 | 6.5 |
| DOC-04 | 5.9 |
| TST-01..04 | 4.1–4.3 |

## Phụ lục B — Rủi ro & giảm thiểu

| Rủi ro | Giảm thiểu |
|--------|------------|
| Reset DB local mất dữ liệu test | Dữ liệu hiện là rác; backup `.sql` nếu cần trước khi `down -v` |
| Refactor ViewModel làm hỏng UI đang chạy | Làm từng màn, mỗi màn build + test tay xong mới sang màn kế |
| Migration Room cần version mới | Viết `Migration` hoặc `fallbackToDestructiveMigration` cho môi trường dev |
| Toolchain quá mới gây lỗi CI | Pin version SDK trong CI; cân nhắc hạ compileSdk nếu 37 gây vấn đề |
| UI test giòn do locator | Dùng `testTag` (2.8) trước khi viết test |
| AI thật tốn phí | Giữ fallback heuristic; test AI thật có kiểm soát |

## Phụ lục C — Backlog dài hạn (chưa xếp lịch)

- Push notification nhắc nhập chi tiêu.
- Đa tiền tệ, ngân sách (budget) theo category, mục tiêu tiết kiệm.
- Friends request/accept/reject đầy đủ + chặn bạn.
- Export dữ liệu (CSV/PDF).
- Widget home screen.
- Đa ngôn ngữ (i18n).
