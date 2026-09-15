# SnapSpend — Phân tích hiện trạng mã nguồn

> Ngày phân tích: 2026-09-14
> Phạm vi: `android/`, `server/`, `docs/`, git/DevOps
> Phương pháp: đọc toàn bộ source, build thật (`dotnet build`, `:app:assembleDebug`), chạy API + emulator, smoke test end-to-end.
> Tài liệu liên quan: `KE_HOACH_HOAN_THIEN.md` (kế hoạch khắc phục), `../1/` (tài liệu giai đoạn trước).

---

## 1. Tổng quan kiến trúc

```
┌─────────────────┐        HTTPS/JSON        ┌──────────────────┐        ┌────────────┐
│  android/       │  ───────────────────────▶│  server/         │───────▶│ PostgreSQL │
│  Kotlin +       │   JWT + multipart ảnh    │  ASP.NET Core 10 │        │ local      │
│  Jetpack Compose│◀─────────────────────── │  EF Core 10      │        └────────────┘
└─────────────────┘                          │  + Gemini        │
                                              └──────────────────┘
```

- `android/`: client. UI chỉ gọi **1 interface** `SnapSpendRepository`, có 2 impl:
  - `MockRepository` — data giả trong bộ nhớ, **không cần server/mạng** (Demo mode).
  - `RealRepository` — Retrofit + Room + backend thật (Real mode).
- Công tắc `AppConfig.isDemo` (`data/AppConfig.kt`) chọn impl. Mặc định `true` (Demo).
- `server/`: REST API + EF Core + PostgreSQL + JWT + lưu ảnh local + Google Gemini API.
- `docs/`: tài liệu.

**Đây là hướng đi hợp lý và đã được hiện thực nhất quán**: tách UI khỏi nguồn dữ liệu, demo offline được, nối backend không phải sửa UI.

---

## 2. Quy mô mã nguồn

| Khu vực | Số file chính | Ghi chú |
|---------|---------------|---------|
| Android Kotlin | 15 | `MainActivity.kt` chiếm 617 dòng (monolith) |
| Server C# | 12 | endpoints + services + data + models + migration |
| Docs | 9 (docs/1) | khá đầy đủ |
| Test | **0** | không có unit/instrumented/UI test |

---

## 3. Đã làm được

### Android
- Luồng đầy đủ: Auth → Camera → Album → Detail → Stats → Profile.
- Trừu tượng data đúng qua `SnapSpendRepository` + Mock/Real.
- Mock data phong phú (24 expense VNĐ, 3 bạn, analysis TV) — `data/mock/MockData.kt`.
- Design system: semantic color, spacing 4/8, shape, Material icons (`ui/theme/`, `ui/components/`).
- CameraX preview + capture; Photo Picker; nút "Ảnh mẫu" cho emulator.
- Album: search / filter / sort / pull-refresh / swipe-delete + Undo / empty+error state.
- Detail: sửa / xóa / share; Stats: 7D/30D/tháng + BarChart + % category.
- Room cache; JWT Bearer interceptor; TokenStore.

### Server
- Auth JWT (PBKDF2 SHA256 120k vòng), expenses CRUD + multipart upload, stats, ai/analyze, friends, account delete.
- `StorageService` validate dung lượng/định dạng ảnh; `AiService` gọi Gemini + fallback heuristic.
- Docker Compose (Postgres 17 + API), `schema.sql`, seed 9 category.
- **Mới**: EF Core Migrations (`InitialCreate`) + `DesignTimeDbFactory` (thay `EnsureCreated`).

### Kiểm chứng thực tế (đã chạy)
- `dotnet build`: 0 warning / 0 error.
- `:app:assembleDebug`: BUILD SUCCESSFUL, APK ~23.8MB.
- API + Postgres local: smoke test register → login → expense → stats → AI → delete: PASS.
- API + Supabase cloud: PASS (dùng để xác nhận migration hoạt động với DB có sẵn schema ngoài).
- App trên emulator API 36.1: login demo → 4 tab không crash, số liệu Stats khớp.

---

## 4. Vấn đề hiện có (theo mã)

Quy ước mức độ: **P0** = chặn/không chạy được · **P1** = lỗi/logic/UX · **P2** = cải thiện.

### 4.1. Nền tảng & cấu hình (FND)

| Mã | Mức | Vấn đề | Vị trí |
|----|-----|--------|--------|
| FND-01 | **P0** | **Xung đột 2 nguồn schema**: `docker-compose.yml:14` mount `schema.sql` tạo bảng, nhưng `Program.cs:35` dùng `MigrateAsync()`. DB local có 5 bảng nhưng **không có `__EFMigrationsHistory`** → API sẽ lỗi `relation already exists` khi chạy `InitialCreate`. | `docker-compose.yml:14`, `Program.cs:35` |
| FND-02 | P2 | `FILE_LIST.txt` lỗi thời: thiếu gần hết file mới (AppConfig, MockData, repository, ui/*), còn liệt kê `bin/obj` (đã bỏ khỏi git). | `FILE_LIST.txt` |
| FND-03 | P1 | `AppConfig.isDemo` chỉ in-memory → **mở lại app luôn reset về Demo**, không nhớ lựa chọn. | `data/AppConfig.kt:10` |
| FND-04 | P2 | `docs/2/` trống trước tài liệu này; docs phân tán giữa `docs/1` và gốc. | `docs/` |
| FND-05 | P2 | Không có CI/CD (GitHub Actions) build/lint/test. | — |
| FND-06 | P2 | Toolchain rất mới: `compileSdk/targetSdk = 37`, AGP `9.3.1`, Kotlin `2.3.21`, compose-bom `2026.08.00`, Gradle `9.5.0` → rủi ro tái lập môi trường. | `app/build.gradle.kts:9,14`, `android/build.gradle.kts` |
| FND-07 | P1 | Release `API_BASE_URL` vẫn là placeholder `https://YOUR_DOMAIN/api/` → release build không dùng được. | `app/build.gradle.kts:38` |
| FND-08 | P2 | Chưa có tài liệu cấu hình cloud (Supabase) và deploy; `JWT_KEY` mặc định là placeholder. | `appsettings.json:3`, `.env.example` |
| FND-09 | P2 | Lịch sử git từng chứa `bin/obj` (giờ đã ignore + bỏ track nhưng history vẫn phình). | `.gitignore` |

### 4.2. Android (AND)

| Mã | Mức | Vấn đề | Vị trí |
|----|-----|--------|--------|
| AND-01 | **P1** | `MainActivity.kt` monolith 617 dòng, **không ViewModel**; dùng `remember` (không `rememberSaveable`) → mất state khi xoay màn hình / process death. | `MainActivity.kt` |
| AND-02 | P1 | Không dùng **Navigation-Compose** dù đã khai báo dependency; điều hướng thủ công bằng enum `Tab` + `openedId`, không back stack/deep link. | `MainActivity.kt:150-192`, `app/build.gradle.kts:55` |
| AND-03 | **P1** | Room entity **thiếu `aiConfidence`** (và `categorySource`) → Real mode đọc cache trả `null`, mất badge AI sau reload. | `data/repository/RealRepository.kt:22`, `data/local/LocalDb.kt:14-21` |
| AND-04 | P1 | Token lưu `SharedPreferences` plain text; **không auto-logout khi 401**; không refresh token. | `data/remote/Network.kt:10-18` |
| AND-05 | P2 | Friends state bị load trùng ở `SnapSpendApp` và `ProfileScreen` → 2 nguồn không đồng bộ. | `MainActivity.kt:165,548` |
| AND-06 | P1 | Undo xóa tạo lại expense **mất ảnh** (`imageUrl=null`) và id mới. | `MainActivity.kt:379-390` |
| AND-07 | P1 | Ảnh **không nén** trước upload → payload lớn, chậm, tốn băng thông. | `RealRepository.kt:63-69` |
| AND-08 | P2 | Camera chỉ camera sau; `onError` rỗng (không báo lỗi chụp); không đổi camera/flash. | `MainActivity.kt:293-298` |
| AND-09 | P2 | Thiếu `contentDescription`/`testTag` ở nhiều control → khó UI test tự động (đã gặp khi thử Appium). | `MainActivity.kt` |
| AND-10 | P2 | Không có **dark mode** (chỉ `lightColorScheme`); status/nav bar hard-code trắng. | `ui/theme/Theme.kt`, `res/values/themes.xml` |
| AND-11 | **P1** | Không có unit/UI test. | `app/src/test`, `app/src/androidTest` (không tồn tại) |
| AND-12 | P2 | `MockRepository.updateExpense` có `check(old.id == id)` vô nghĩa. | `MockRepository.kt:75` |
| AND-13 | P2 | Heuristic phân loại nhạy dấu tiếng Việt: `"Pho Thin"` (không dấu) → `other`. | `MockRepository.kt:139-150` |
| AND-14 | P2 | Không xử lý 401/lỗi mạng tập trung; mỗi màn tự `runCatching`. | nhiều nơi |
| AND-15 | P2 | `loggedIn` chỉ dựa vào sự tồn tại token, không validate hết hạn. | `MainActivity.kt:154` |
| AND-16 | P2 | `formatVnd` tạo `NumberFormat` mỗi lần gọi (hiệu năng nhỏ). | `ui/format/Format.kt` |

### 4.3. Server (SRV)

| Mã | Mức | Vấn đề | Vị trí |
|----|-----|--------|--------|
| SRV-01 | P2 | `OnModelCreating` lặp `ToTable(...)` 2 lần (dòng 16-26) — code chết. | `Data/AppDbContext.cs:16-26` |
| SRV-02 | **P1** | Danh sách category lặp **4 nơi**: seed `Program.cs:37-46`, validate `ExpenseEndpoints.cs:24,45`, `model/Models.kt:32-42`, `schema.sql:50-59` → dễ lệch; chưa có `GET /categories`. | nhiều nơi |
| SRV-03 | **P1** | Đã chốt dùng **Google Gemini** (`gemini-2.5-flash`) thay OpenAI; **cần key thật** để kiểm chứng chất lượng phân loại/phân tích. | `Services/AiService.cs` |
| SRV-04 | P1 | Cần kiểm chứng payload Gemini `generateContent` (inline_data ảnh + JSON output) với key thật; hiện fallback heuristic khi thiếu key/lỗi. | `Services/AiService.cs` |
| SRV-05 | P1 | Có bảng `expense_shares` nhưng **không có `GET shared-with-me`** và không UI xem chi tiêu được chia sẻ. | `Endpoints/ExpenseEndpoints.cs` |
| SRV-06 | P2 | Friends add là `accepted` ngay; không request/accept/reject. | `Endpoints/FriendEndpoints.cs:22` |
| SRV-07 | P1 | `GET /expenses` **không phân trang**; không filter/sort server-side. | `Endpoints/ExpenseEndpoints.cs:14-19` |
| SRV-08 | P2 | Không validate `from <= to`; `DateOnly.TryParse` phụ thuộc culture. | `Endpoints/StatsEndpoints.cs:15` |
| SRV-09 | P1 | Xóa expense **không xóa file ảnh** (chỉ account delete mới xóa). | `Endpoints/ExpenseEndpoints.cs:51-55` |
| SRV-10 | P1 | Storage **local**, ảnh mất khi đổi máy/volume; chưa có object storage. | `Services/StorageService.cs` |
| SRV-11 | P1 | Thiếu rate-limit, HTTPS, CORS, Swagger/ProblemDetails, health check. | `Program.cs` |
| SRV-12 | P1 | `JWT_KEY` placeholder, không kiểm tra độ dài tối thiểu khi khởi động. | `appsettings.json:3`, `Services/AuthService.cs` |
| SRV-13 | P2 | Không logging structured/tracing. | `Program.cs` |

### 4.4. Database (DB)

| Mã | Mức | Vấn đề | Vị trí |
|----|-----|--------|--------|
| DB-01 | P1 | FND-01: cần chốt migration là nguồn schema duy nhất; seed category nên chuyển vào migration/seed data. | `docker-compose.yml`, `Program.cs` |
| DB-02 | P2 | Không có soft-delete user; xóa cứng. | `Models/Entities.cs` |
| DB-03 | P2 | `expenses` lưu `note`/`image_url` không giới hạn độ dài cấp validation app. | `Endpoints/ExpenseEndpoints.cs` |

### 4.5. Tài liệu (DOC)

| Mã | Mức | Vấn đề |
|----|-----|--------|
| DOC-01 | P1 | `README.md` chưa cập nhật trạng thái mới (migration, chạy local, Demo/Real). |
| DOC-02 | P2 | `FILE_LIST.txt` lỗi thời (trùng FND-02). |
| DOC-03 | P2 | `API.md` thiếu mã lỗi/status code và ví dụ response. |
| DOC-04 | P1 | Thiếu hướng dẫn deploy (host free) và cấu hình cloud. |
| DOC-05 | P2 | Thiếu CONTRIBUTING/AGENTS cho dev. |

### 4.6. Kiểm thử (TST)

| Mã | Mức | Vấn đề |
|----|-----|--------|
| TST-01 | **P1** | Không có test server (xUnit integration cho auth/expenses/stats). |
| TST-02 | P1 | Không có unit test Android (format, guessCategory, tính stats mock). |
| TST-03 | P1 | Không có UI test; PoC Appium mới nằm ở temp, chưa đưa vào repo. |
| TST-04 | P2 | Không test migration/DB. |

---

## 5. Tổng hợp theo mức độ

| Mức | Số lượng | Nhóm chính |
|-----|----------|------------|
| **P0** | 1 | FND-01 (xung đột schema/migration) |
| **P1** | ~20 | Android state/nav/test, Room aiConfidence, token, ảnh nén; server category/pagination/AI/storage; docs deploy |
| **P2** | ~18 | dark mode, camera, dọn code, FILE_LIST, CI, logging |

---

## 6. Kết luận

- **Sản phẩm ở mức "MVP demo chạy được"**: hoàn thiện về luồng UI (Demo mode) và backend cơ bản, đã kiểm chứng build + chạy thật.
- **Nợ kỹ thuật tập trung ở 3 chỗ**: (1) nền tảng schema/migration còn xung đột, (2) kiến trúc Android chưa có ViewModel/Navigation/Test, (3) backend chưa siết production (bảo mật, phân trang, storage, AI).
- **Không có gì chặn về mặt ý tưởng**; các vấn đề đều có cách xử lý rõ ràng và đã được lập kế hoạch trong `KE_HOACH_HOAN_THIEN.md`.
