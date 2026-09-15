# SnapSpend

App Android ghi chi tiêu bằng ảnh: chụp hóa đơn → AI gợi ý category → lưu album → thống kê → AI phân tích hành vi. Có backend riêng (ASP.NET Core + PostgreSQL).

App **luôn dùng backend thật** — cần chạy server trước khi dùng.

## Kiến trúc

- UI Android **chỉ gọi** interface `SnapSpendRepository` (`data/repository/`), không gọi thẳng Retrofit/Room từ composable.
  - `RealRepository` — backend thật (Retrofit + Room cache).
- `server/`: ASP.NET Core 10 + EF Core 10 + PostgreSQL 17 + JWT. Migration là nguồn schema duy nhất (xem `Migrations/`).
- `docs/1/`: tài liệu giai đoạn MVP cũ. `docs/2/`: phân tích hiện trạng + kế hoạch + kết quả đang làm.

## Yêu cầu môi trường

| Thành phần | Yêu cầu |
|---|---|
| JDK | 17 (Temurin) |
| .NET | 10 SDK |
| Android SDK | compileSdk/targetSdk 37, minSdk 26, Gradle wrapper 9.5.0 |
| Docker | để chạy Postgres 17 local |
| Emulator | AVD `Medium_Phone_API_36.1` |

## Chạy local (3 lệnh)

```powershell
# 1) Postgres (chạy trong server/)
cd server
docker compose up -d postgres

# 2) API (chạy trong server/src/SnapSpend.Api/)
# Bắt buộc: Jwt__Key dài >= 32 ký tự. GEMINI_API_KEY để trống vẫn chạy (AI dùng fallback).
$env:Jwt__Key = "dev-local-secret-key-for-snapspend-1234567890"
dotnet run
# API lên tại http://localhost:5080, kiểm tra: GET /health -> 200

# 3) App Android (chạy trong android/)
.\gradlew.bat :app:assembleDebug
```

Chạy lần đầu, API tự apply migration tạo 5 bảng (`users`, `categories`, `expenses`, `friendships`, `expense_shares`) + seed 9 category. File `server/db/schema.sql` chỉ để tham khảo, không dùng để init DB.

Emulator truy cập backend host qua `http://10.0.2.2:5080` (xem `BuildConfig.API_BASE_URL`). Đăng ký/đăng nhập tài khoản thật trong app.

## Cấu trúc thư mục

```
SnapSpend/
├── AGENTS.md                     # quy ước dự án cho AI/agent (đọc trước khi sửa code)
├── README.md
├── android/
│   ├── app/build.gradle.kts
│   └── app/src/main/java/com/snapspend/app/
│       ├── MainActivity.kt
│       ├── data/{AppConfig,local,mock,remote,repository}/
│       ├── model/Models.kt
│       └── ui/{components,format,screens,theme}/
├── server/
│   ├── docker-compose.yml        # service postgres (+ api khi deploy)
│   ├── .env.example              # mẫu JWT_KEY, GEMINI_API_KEY, AI_MODEL...
│   ├── db/schema.sql             # tham khảo, KHÔNG dùng init DB
│   └── src/SnapSpend.Api/
│       ├── Program.cs
│       ├── Data/{AppDbContext,DesignTimeDbFactory}.cs
│       ├── Migrations/
│       ├── Endpoints/
│       ├── Services/{AuthService,AiService,StorageService}.cs
│       └── Models/Entities.cs
└── docs/{1,2}/
```

## Smoke test nhanh backend

```powershell
$base = "http://localhost:5080"
$r = Invoke-RestMethod -Method Post -Uri "$base/api/auth/register" -ContentType "application/json" -Body '{"email":"a@b.c","username":"a","password":"secret123"}'
$tok = $r.token
Invoke-RestMethod -Uri "$base/api/stats?from=2026-09-01&to=2026-09-30" -Headers @{Authorization="Bearer $tok"}
```

Tài liệu chi tiết: `docs/2/PHAN_TICH_HIEN_TRANG.md`, `docs/2/KE_HOACH_HOAN_THIEN.md`, `docs/2/CAC_BUOC_CAN_LAM.md`, `docs/2/KET_QUA_DAT_DUOC.md`.
