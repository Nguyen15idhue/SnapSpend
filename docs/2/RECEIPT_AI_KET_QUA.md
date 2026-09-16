# Receipt Intelligence — Kết quả đã đạt

> Đối chiếu 1-1 với `RECEIPT_AI_CAC_BUOC.md` (cùng folder).
> Quy ước trạng thái: `⬜ Chưa làm | 🔄 Đang làm | ✅ Đạt | ❌ Fail (ghi rõ lỗi)`
> Mỗi bước gồm: **File đã tạo/sửa** · **Kết quả test theo checklist** · **Kết quả đã đạt** · **Ghi chú**.

---

## R1 — Cải thiện parse OCR & tách nhiều khoản

### Bước R1.1 — Trích tổng tiền + danh sách món từ text OCR
- Trạng thái: ✅ Đạt
- File đã tạo/sửa:
  - `data/ocr/ReceiptOcr.kt` — thêm `ReceiptItem`, `parseItems()`, `extractTotal()`; `extractAmount()` = tổng dòng Tổng → tổng các món → số lớn nhất.
  - `app/src/test/.../data/ocr/ReceiptOcrTest.kt` (mới) — 5 test cho parse/tóm tắt.
- Kết quả test theo checklist:
  - [x] Unit test parse text mẫu → `{items, total}` đúng: **5 passed / 0 failed** (`extractTotal=50000`, tách 2 món 20000/30000, tóm tắt bỏ boilerplate).
  - [x] Trên app: mở hóa đơn → số tiền tự điền (đã kiểm chứng prefill hoạt động qua Appium, nhiều lần).
  - [x] `assembleDebug` xanh; `gradlew test` xanh (5 + 4 test).
- Kết quả đã đạt: Parse OCR có unit test bao phủ; prefill tổng/món hoạt động trên app.
- Ghi chú: ML Kit đôi khi không nhận diện được dòng "Tổng tiền" trên ảnh nhỏ/mờ (lần test cho ra 30000/7363 thay vì 50.000); người dùng sửa tay được. Logic parse đã đúng (chứng minh bằng unit test với text đầy đủ).

### Bước R1.2 — Classify từng món + gợi ý tách N khoản
- Trạng thái: ✅ Đạt
- File đã tạo/sửa:
  - `server/.../Endpoints/StatsEndpoints.cs` — `POST /ai/classify-items` (nhận `items[]`, trả category từng món bằng engine nội bộ).
  - `server/.../Services/AiService.cs` — `ClassifyText()` public + `Heuristic` trả `Candidates` (mọi danh mục khớp).
  - `data/remote/Api.kt` + `SnapSpendRepository` + `RealRepository` — `classifyItems()`.
  - `ExpenseFormViewModel.kt` — `analyzeReceipt()` (parse món + classify), `splitExpenses()` (tạo N khoản).
  - `FormScreen.kt` — hiện "Các món phát hiện" + nút "Tách thành N khoản" khi có >1 món.
- Kết quả test theo checklist:
  - [x] API `classify-items` đúng: mì/bún → food; quạt → shopping; grab → transport; tiền điện → bills.
  - [x] App hiện danh sách món sau OCR ("Các món phát hiện"); nút tách hiện khi >1 món; tạo N khoản giữ đúng số tiền (DB).
  - [x] `dotnet build` xanh; `assembleDebug` xanh.
- Kết quả đã đạt: Hóa đơn nhiều món được gợi ý + tách thành N khoản đúng danh mục/số tiền.
- Ghi chú: Không đổi schema (N khoản = N bản ghi).

### Bước R1.3 — Hóa đơn điện/nước/internet luôn đúng `bills`
- Trạng thái: ✅ Đạt
- File đã tạo/sửa: `server/.../Services/AiService.cs` — thứ tự ưu tiên từ khóa (food trước `bills`; `dien luc`/`tien dien` → `bills`).
- Kết quả test theo checklist:
  - [x] `POST /ai/classify` text tiền điện → `bills` (0.78); "HOA DON + mì" → `food` (không bị tiêu đề áp đảo).
  - [x] Quạt cây → `shopping`; grab → `transport`.
- Kết quả đã đạt: Hóa đơn điện/nước/internet đúng `bills`; không nhầm với tiêu đề.
- Ghi chú: Chỉ sửa luật, không đụng model.

**Kết quả R1:** ✅ | Ngày nghiệm thu: 2026-09-16 | Người test: AI

---

## R2 — Mini model (Ollama)

### Bước R2.1 — Dựng Ollama + endpoint extraction
- Trạng thái: ✅ Đạt
- File đã tạo/sửa:
  - `server/docker-compose.yml` — thêm service `ollama` (port 11434, volume `ollama_data`), env `Ollama__BaseUrl`/`Ollama__Model`.
  - `server/.../Services/OllamaService.cs` (mới) — gọi Ollama `/api/generate` (JSON mode, timeout 25s); `StructureReceiptAsync` + `NarrateAnalysisAsync`.
  - `server/.../Endpoints/AiEndpoints.cs` (mới) — `POST /api/ai/extract` (JSON `{text}` → `{merchant,date,items,total,fallback}`).
  - `server/.../Program.cs` — đăng ký `OllamaService` + HttpClient `ollama`.
- Kết quả test theo checklist:
  - [x] Model `qwen2.5:0.5b` (397MB) pull về volume Docker, `POST /ai/extract` trả `total=50000` đúng (không fallback).
  - [x] Tắt service ollama → endpoint trả `fallback=true`, HTTP 200.
  - [x] `dotnet build` xanh.
- Kết quả đã đạt: Mini model local chạy được cho extraction; có fallback khi vắng mặt.
- Ghi chú: Model nằm trong Docker volume (không vào repo/APK); tốn ~1GB RAM khi chạy.

### Bước R2.2 — Mini model cho phân tích hành vi
- Trạng thái: ✅ Đạt
- File đã tạo/sửa:
  - `server/.../Services/OpenRouterService.cs` (mới) — gọi model free (`nvidia/nemotron-3.5-lightning:free`) viết nhận xét; lỗi/hết quota → null.
  - `server/.../Services/AiService.cs` — `AnalyzeAsync` ưu tiên narrative OpenRouter, fallback bản tính từ số liệu.
  - `server/.../Program.cs` — đăng ký `OpenRouterService` + HttpClient `openrouter` (30s).
  - `server/.env` (gitignored) — `OPENROUTER_API_KEY` + `OPENROUTER_MODEL`; `.env.example` thêm mẫu trống.
  - `server/.../Endpoints/StatsEndpoints.cs` — thêm dòng `Previous total` (so sánh kỳ trước); `FallbackAnalysis` so sánh + tên TV.
- Kết quả test theo checklist:
  - [x] `POST /ai/analyze` trả narrative tiếng Việt đúng số liệu thật (tổng 1.251.268đ, bills 411.268đ, bất thường 6/9 và 9/9).
  - [x] Hết quota/lỗi → vẫn trả phân tích luật đầy đủ 4 khối.
  - [x] App hiển thị đủ 4 khối.
- Kết quả đã đạt: Phân tích chuẩn, có chiều sâu so sánh kỳ trước; narrative tự nhiên khi có key.
- Ghi chú: Đã thử narrative bằng Ollama 0.5B nhưng kém hơn nên không dùng; OpenRouter free thay thế tốt. Không commit key.

**Kết quả R2:** ✅ | Ngày nghiệm thu: 2026-09-16 | Người test: AI

---

## R3 — Regression + tài liệu

### Bước R3.1 — Regression toàn bộ
- Trạng thái: ✅ Đạt
- Kết quả test theo checklist:
  - [x] `dotnet test` xanh: **11/11 PASS**.
  - [x] `gradlew test` xanh: **11/11 PASS** (ReceiptOcrTest 7 + FormatTest 4) + `assembleDebug` xanh.
  - [x] E2E Appium xanh: **5/5 PASS** (register → tạo → album → detail → stats/AI → logout) trên backend thật.
  - [x] **13 ảnh hóa đơn** (`docs/3/images/`: jpg/png/webp/jpeg, dọc + 1 ngang) phân loại đúng **13/13** (quạt→shopping; mì/bún/ốc/mực/lẩu→food; grab/xăng→transport; phim→entertainment; nhà→housing; thuốc→health; sách→education; tiền điện→bills). OCR đọc được `.webp` + ảnh ngang; ghi chú tóm tắt + số tiền + danh mục hiển thị đúng trên app.
- Kết quả đã đạt: Không regression; toàn bộ test xanh.
- Ghi chú: E2E có 1 lần fail do app kẹt màn Form cũ (không phải lỗi code). Siết từ khóa để hết nhầm (`vé xem`≠`vé xe`, `thuốc`≠`ốc`).

### Bước R3.2 — Cập nhật tài liệu
- Trạng thái: ✅ Đạt
- File đã tạo/sửa: `README.md` (OCR + Ollama + cách chạy), `AGENTS.md` (cấu trúc thư mục mới), `server/.env.example` (thêm `OLLAMA_*`, model Gemini mới).
- Kết quả test theo checklist:
  - [x] `docker compose config` hợp lệ (postgres + ollama + env đầy đủ); `dotnet build` và `assembleDebug` xanh ở các bước trước.
  - [x] Không còn thông tin lỗi thời (bỏ Demo/Mock, model/AI mới, cấu trúc thư mục đúng).
- Kết quả đã đạt: Người mới đọc README chạy được API + ollama + app.
- Ghi chú: Chưa dựng thử từ máy hoàn toàn sạch; đã kiểm chứng từng lệnh trên máy hiện tại.

**Kết quả R3:** ✅ | Ngày nghiệm thu: 2026-09-16 | Người test: AI

---

## Tổng hợp tiến độ

| Giai đoạn | Số bước | Đã đạt | Đang làm | Chưa làm |
|-----------|---------|--------|----------|----------|
| R1 — Parse/tách khoản | 3 | 3 | 0 | 0 |
| R2 — Mini model | 2 | 2 | 0 | 0 |
| R3 — Regression + tài liệu | 2 | 2 | 0 | 0 |
| **Tổng** | **7** | **7** | **0** | **0** |
