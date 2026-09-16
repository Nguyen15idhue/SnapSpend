# SnapSpend

App Android ghi chi tiêu bằng ảnh: chụp/chọn hóa đơn → **OCR offline (ML Kit)** đọc thành text → **AI gợi ý danh mục** (sửa tay được, tách nhiều khoản) → lưu album → thống kê → AI phân tích hành vi. Có backend riêng (ASP.NET Core + PostgreSQL + mini model Ollama).

App **luôn dùng backend thật** — cần chạy server trước khi dùng.

## Kiến trúc

- UI Android **chỉ gọi** interface `SnapSpendRepository` (`data/repository/`), không gọi thẳng Retrofit/Room từ composable.
  - `RealRepository` — backend thật (Retrofit + Room cache).
- `server/`: ASP.NET Core 10 + EF Core 10 + PostgreSQL 17 + JWT. Migration là nguồn schema duy nhất (xem `Migrations/`).
  - AI: engine nội bộ (phân loại text, heuristic bỏ dấu) + optional Gemini/key và **Ollama local** cho trích xuất/phân tích.
- `docs/1/`: tài liệu giai đoạn MVP cũ. `docs/2/`: đánh số 00–09 theo `00_DOC_MUC_LUC.md` (01–04 nền tảng, 05–07 Receipt AI, 08 kế hoạch fix lỗi + test toàn diện hiện tại).

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
# 1) Postgres + Ollama (chạy trong server/)
cd server
docker compose up -d postgres ollama
docker exec snapspend-ollama ollama pull qwen2.5:0.5b   # mini model, ~397MB (1 lần)

# 2) API (chạy trong server/src/SnapSpend.Api/)
# Bắt buộc: Jwt__Key dài >= 32 ký tự. Thiếu GEMINI_API_KEY vẫn chạy (dùng engine nội bộ + Ollama).
$env:Jwt__Key = "dev-local-secret-key-for-snapspend-1234567890"
dotnet run
# API lên tại http://localhost:5080, kiểm tra: GET /health -> 200

# 3) App Android (chạy trong android/)
.\gradlew.bat :app:assembleDebug
```

Chạy lần đầu, API tự apply migration tạo 5 bảng (`users`, `categories`, `expenses`, `friendships`, `expense_shares`) + seed 9 category. File `server/db/schema.sql` chỉ để tham khảo, không dùng để init DB.

Emulator truy cập backend host qua `http://10.0.2.2:5080` (xem `BuildConfig.API_BASE_URL`). Đăng ký/đăng nhập tài khoản thật trong app.

Ảnh (`Storage:BaseUrl`, mặc định `http://localhost:5080/uploads` trong `appsettings.json`): khi chạy qua compose, ghi đè bằng `STORAGE_BASE_URL` trong `.env` — emulator dùng `http://10.0.2.2:5080/uploads`, máy thật dùng IP LAN của máy chạy API (ví dụ `http://192.168.1.10:5080/uploads`).

## Cấu trúc thư mục

```
SnapSpend/
├── AGENTS.md                     # quy ước dự án cho AI/agent (đọc trước khi sửa code)
├── README.md
├── android/
│   ├── app/build.gradle.kts
│   └── app/src/main/java/com/snapspend/app/
│   ├── MainActivity.kt
│       ├── data/{local,ocr,remote,repository}/
│       ├── model/Models.kt
│       └── ui/{components,format,screens,theme,viewmodel}/
├── server/
│   ├── docker-compose.yml        # postgres + ollama (+ api khi deploy)
│   ├── .env.example              # mẫu JWT_KEY, GEMINI_API_KEY, AI_MODEL, OLLAMA_*...
│   ├── db/schema.sql             # tham khảo, KHÔNG dùng init DB
│   └── src/SnapSpend.Api/
│       ├── Program.cs
│       ├── Data/{AppDbContext,DesignTimeDbFactory}.cs
│       ├── Migrations/
│       ├── Endpoints/            # Auth, Expenses (+restore), Categories, Stats (+ai/*), Friends, Account, Ai
│       ├── Services/{AuthService,AiService,OllamaService,StorageService}.cs
│       └── Models/{Entities,CategoryCatalog}.cs
└── docs/{1,2,3}/
```

## Smoke test nhanh backend

```powershell
$base = "http://localhost:5080"
$r = Invoke-RestMethod -Method Post -Uri "$base/api/auth/register" -ContentType "application/json" -Body '{"email":"a@b.c","username":"a","password":"secret123"}'
$tok = $r.token
Invoke-RestMethod -Uri "$base/api/stats?from=2026-09-01&to=2026-09-30" -Headers @{Authorization="Bearer $tok"}
```

Tài liệu chi tiết: xem mục lục `docs/2/00_DOC_MUC_LUC.md` (01 phân tích → 08 kế hoạch fix lỗi + test toàn diện hiện đang chờ làm).
