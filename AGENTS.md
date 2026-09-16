# AGENTS.md — Nguyên tắc dự án SnapSpend

> File này định hình **quy ước dự án** và **nguyên tắc làm việc cho AI/agent** khi viết code trong repo này.
> Đọc kỹ trước khi sửa bất kỳ file nào. Khi mâu thuẫn, ưu tiên: yêu cầu người dùng > file này > thói quen chung.

---

## 1. Tổng quan dự án

SnapSpend là app Android ghi chi tiêu bằng ảnh, có backend riêng.

- `android/` — Kotlin + Jetpack Compose + CameraX + Room + Retrofit + Coil.
- `server/` — ASP.NET Core 10 + EF Core 10 + PostgreSQL + JWT + lưu ảnh local + OpenAI.
- `docs/` — tài liệu; `docs/1/` là giai đoạn cũ, `docs/2/` là phân tích + kế hoạch hiện tại.

**Kiến trúc bắt buộc**: UI Android **chỉ được gọi** interface `SnapSpendRepository` (`data/repository/`). Không gọi thẳng Retrofit/Room từ composable.

> **Cập nhật 2026-09-15:** App **chỉ dùng backend thật** (`RealRepository`). Đã bỏ hẳn Demo/Mock
> (`MockRepository`, `AppConfig.isDemo`, công tắc Demo ở Profile). Mọi tính năng mới chỉ cần impl Real.
> Backend bắt buộc chạy (emulator trỏ `http://10.0.2.2:5080/api/`).

Tài liệu nền: `docs/2/01_PHAN_TICH_HIEN_TRANG.md` (vấn đề hiện có) và `docs/2/02_KE_HOACH_HOAN_THIEN.md` (kế hoạch). Xem `docs/2/00_DOC_MUC_LUC.md` để biết tài liệu nào đang chạy.

---

## 2. Môi trường & lệnh quan trọng

| Thành phần | Phiên bản / lệnh |
|-----------|-------------------|
| JDK | 17 (Temurin) |
| .NET | 10 SDK |
| Android SDK | compileSdk/targetSdk 37, minSdk 26 |
| Gradle | wrapper 9.5.0 |
| PostgreSQL | docker compose (Postgres 17) |
| Emulator | AVD `Medium_Phone_API_36.1` |

**Build & chạy** (chạy trong đúng thư mục):

```powershell
# Android (workdir: android/)
.\gradlew.bat :app:assembleDebug          # build APK debug
.\gradlew.bat test                        # unit test

# Server (workdir: server/)
docker compose up -d                      # Postgres + API local
dotnet build                              # build
dotnet test                               # test (khi có project test)
```

Không tự ý đổi `compileSdk`, plugin version hay nâng/hạ thư viện nếu chưa hỏi — toolchain đang rất mới, dễ vỡ build.

---

## 3. Ngôn ngữ & định dạng văn bản

- **Trả lời người dùng**: tiếng Việt, có dấu, ngắn gọn, đúng trọng tâm.
- **Commit message**: tiếng Việt, mô tả ngắn gọn việc đã làm (ví dụ: `Server: them endpoint GET /categories`).
- **Comment trong code**: tiếng Việt có dấu, chỉ viết khi cần giải thích "vì sao" — không comment thừa.
- **Text hiển thị trong UI**: tiếng Việt có dấu.
- **Tên biến/hàm/class**: tiếng Anh (giữ convention hiện có).
- **Tài liệu markdown**: tiếng Việt có dấu.

### 3.1. Quy tắc bắt buộc khi viết script PowerShell (chống lỗi font tiếng Việt)

Windows PowerShell 5.1 mặc định không dùng UTF-8 → tiếng Việt dễ thành ký tự rác (`Ã¡`, `?`). Luôn:

```powershell
# Đặt encoding UTF-8 ở đầu script
chcp 65001 > $null
$OutputEncoding = [System.Text.Encoding]::UTF8
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

# Ghi file: PS 5.1 dùng "-Encoding UTF8" (có BOM). Nếu cần không BOM, dùng .NET:
[System.IO.File]::WriteAllText($path, $text, [System.Text.UTF8Encoding]::new($false))
```

Quy tắc thêm:
- **Không** dùng `Out-File`/`>` mặc định (PS 5.1 ghi UTF-16) để lưu nội dung tiếng Việt.
- Khi truyền tiếng Việt vào lệnh native (`git`, `docker`, `curl`), ưu tiên ghi ra file UTF-8 rồi tham chiếu file (`--data-binary @file`, `-F`...), tránh nhồi chuỗi dài inline.
- Với tham số chứa ký tự đặc biệt (`@ # % & =`), dùng mảng đối số hoặc quote đúng; cân nhắc `--%` hoặc call operator `&`.
- Dùng tên cmdlet đầy đủ (`Get-Content`, `Set-Content`...) thay alias.
- Lệnh chạy lâu (build/test/docker/emulator/Appium) phải chia bước nhỏ + đặt timeout rõ ràng; fail thì retry tối đa 1 lần, vẫn fail thì ghi "chưa kiểm chứng được + lý do" theo Mục 5.3, không để treo.
- **Không bao giờ** in/greet hay ghi log ra file bất kỳ secret (connection string, JWT key, API key, token).
- File dấu xuống dòng: repo có thể cảnh báo `LF will be replaced by CRLF`; giữ nguyên, không tự đổi cấu hình git toàn cục.

---

## 4. Nguyên tắc viết code

### 4.1. Chung
- Đọc kỹ file liên quan **trước khi sửa**; bám theo style hiện có.
- Không thêm thư viện/dependency mới nếu chưa có trong `build.gradle.kts`/`.csproj` mà chưa hỏi.
- Không để lại code chết, biến trùng, import thừa, `TODO` mơ hồ.
- Mọi thay đổi phải **build xanh** trước khi coi là xong.
- Không sửa định dạng/schema dữ liệu mà không cập nhật DTO, migration và tài liệu liên quan.

### 4.2. Android
- UI chỉ gọi `SnapSpendRepository`; thêm hàm mới phải thêm ở interface + `RealRepository`.
- Dùng semantic token trong `ui/theme/` (không hard-code màu), spacing theo thang 4/8 trong `Spacing`.
- Dùng component chung trong `ui/components/` thay vì tự vẽ lẻ.
- State trong composable cần tồn tại qua xoay màn hình → dùng `rememberSaveable`, không dùng `remember`.
- Gắn `contentDescription`/`Modifier.testTag(...)` cho control quan trọng để test được.
- Chuỗi hiển thị đặt tiếng Việt; format tiền dùng `formatVnd()`.

### 4.3. Server
- Xác thực mọi endpoint cần bảo vệ (`RequireAuthorization`); luôn lọc theo `UserId` của token.
- Validate input: amount > 0, category thuộc danh sách, ngày đúng `yyyy-MM-dd`, `from <= to`.
- Trả lỗi dạng `{ "message": "..." }` và đúng mã HTTP (400/401/404/409).
- Không commit secret; đọc từ biến môi trường/`.env` (đã gitignore).
- Thay đổi schema → tạo EF migration mới; **migration là nguồn schema duy nhất** (không dựng bảng song song bằng SQL tay).
- Ảnh: validate loại/dung lượng; nhớ dọn file khi xóa dữ liệu liên quan.

### 4.4. Dữ liệu
- Mọi thao tác CRUD đi qua `SnapSpendRepository` → backend thật.
- Màn hình có dữ liệu phải có đủ trạng thái loading / empty / error (error khi mất mạng/server lỗi).

---

## 5. Nguyên tắc kiểm thử (BẮT BUỘC cả BE và FE)

Không coi một task là xong nếu chưa kiểm chứng. Thứ tự:

### 5.1. Backend (BE)
1. `dotnet build` — 0 warning/0 error.
2. `dotnet test` — nếu có test (đang bổ sung ở GĐ4).
3. **Smoke test API thật** bằng `curl`/`Invoke-RestMethod`: register → login → tạo expense → list → stats → AI → xóa. Xác nhận mã HTTP và dữ liệu trả về.
4. Test cả trường hợp lỗi: trùng email (409), sai mật khẩu (401), input không hợp lệ (400).

### 5.2. Frontend (Android / app)
1. `.\gradlew.bat :app:assembleDebug` — build xanh.
2. `.\gradlew.bat test` — unit test (GĐ4).
3. **UI test trên emulator**:
   - Ưu tiên **Compose UI Test** cho test ổn định, chạy cùng process.
   - Dùng **Appium** (driver `uiautomator2`) cho E2E/bàn giao diện; đã cài sẵn. Lưu ý khởi động server Appium bằng `node .../appium/build/lib/main.js server --port 4723` (npm shim có thể không lên).
   - Đảm bảo `adb devices` thấy emulator trước khi chạy.
4. Kiểm tra trên **backend thật** (Real, trỏ backend local).
5. Xác nhận có đủ 3 trạng thái loading/empty/error ở màn có dữ liệu.

### 5.3. Khi không thể test (thiếu thiết bị/dịch vụ)
- Phải nói rõ **chưa kiểm chứng được** phần nào và vì sao, không mặc định "đã xong".

---

## 6. Git & quy trình

- Chỉ commit/push khi người dùng yêu cầu rõ. Khi commit: xem `git status`/`git diff`, chỉ stage file đúng mục đích.
- Commit tiếng Việt, ngắn, theo dạng `Phạm vi: việc đã làm`.
- **Không commit**: `.env`, `local.properties`, token, keystore, `bin/`, `obj/`, `build/`, file máy tự sinh (`gradle-daemon-jvm.properties`).
- Không force-push, không amend commit đã push, không tạo commit rỗng.
- Thay đổi lớn nên tách commit theo chủ đề.

---

## 7. Nguyên tắc dành riêng cho AI/agent

1. **Trả lời tiếng Việt có dấu**, ngắn gọn, thực tế; báo rõ khi chưa chắc chắn.
2. **Đọc trước, sửa sau — tiết kiệm token**: tìm symbol/quan hệ bằng **codebase-memory-mcp** (`search_graph`, `trace_path`) và gom ngữ cảnh bằng Task subagent TRƯỚC khi đọc file; chỉ đọc file thật sự liên quan, không đọc lan man. Với refactor chạm nhiều tầng (repository/DTO/Room/endpoint): `search_graph` tìm symbol, `trace_path` xem callers/callees, `detect_changes` tính vùng ảnh hưởng; kiểm tra `coverage` trước khi tin kết quả âm tính.
3. **Không tự ý mở rộng phạm vi**: làm đúng việc được giao; cải tiến ngoài phạm vi thì đề xuất, không tự làm.
4. **Không cài đặt hay thay đổi môi trường hệ thống** (XAMPP, Docker desktop, đổi biến môi trường máy...) trừ khi được yêu cầu.
5. **Không lộ secret** trong chat, log, commit hay file sinh ra.
6. Sau khi sửa: **tự build + test theo Mục 5**, rồi báo kết quả kèm bằng chứng (output tóm tắt).
7. Nếu phát hiện vấn đề mới ngoài phạm vi, ghi vào `docs/2/` (backlog) thay vì âm thầm sửa.
8. Khi có nhiều cách, đề xuất ngắn gọn 2–3 lựa chọn và khuyến nghị, thay vì tự quyết việc lớn.
9. Cập nhật tài liệu/`AGENTS.md` nếu thay đổi quy ước hoặc lệnh.

---

## 8. Cấu trúc thư mục tham chiếu

```
SnapSpend/
├── AGENTS.md                     # file này
├── README.md
├── android/
│   ├── app/build.gradle.kts
│   └── app/src/main/java/com/snapspend/app/
│       ├── MainActivity.kt
│       ├── data/{local,ocr,remote,repository}/
│       ├── model/Models.kt
│       └── ui/{components,format,screens,theme,viewmodel}/
├── server/
│   ├── docker-compose.yml
│   └── src/SnapSpend.Api/
│       ├── Program.cs
│       ├── Data/{AppDbContext,DesignTimeDbFactory}.cs
│       ├── Migrations/
│       ├── Endpoints/
│       ├── Services/{AuthService,AiService,OllamaService,StorageService}.cs
│       └── Models/{Entities,CategoryCatalog}.cs
└── docs/{1,2,3}/
```

---

## 9. Definition of Done (checklist chung)

- [ ] Code đúng convention, không code chết, không secret.
- [ ] `dotnet build` xanh; `gradlew :app:assembleDebug` xanh.
- [ ] Test BE (smoke API) và FE (emulator) đã chạy; ghi rõ kết quả.
- [ ] Real mode hoạt động đúng với backend.
- [ ] Tài liệu liên quan (`docs/2/`, `README.md`, API) cập nhật nếu cần.
- [ ] Commit tiếng Việt, đúng phạm vi (chỉ khi được yêu cầu push).
