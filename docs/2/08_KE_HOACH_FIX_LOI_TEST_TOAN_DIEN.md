# 08 — Kế hoạch fix lỗi review + test toàn diện (2026-09-16)

> Nguồn: code review 2026-09-16 (đối chiếu `04_KET_QUA_DAT_DUOC.md` + `07_RECEIPT_AI_KET_QUA.md`).
> Base code: commit `6d044ee` (đã push GitHub). Test nền đang xanh: `dotnet test` 32/32, `gradlew test` 11/11, `assembleDebug` OK.
> Kết quả nghiệm thu ghi vào `09_KET_QUA_FIX_TEST_TOAN_DIEN.md` (tạo khi bắt đầu).
> **Sửa đổi 2026-09-16 (lần 3, đã duyệt): bỏ cascade/ML Kit-luật, chuyển sang ENGINE DB DUY NHẤT Ở SERVER — 5 bảng keyword (`total_keywords`, `noise_patterns`, `category_keywords`, `category_aliases`, `boilerplate_patterns`) + `RecognitionService` (C#) + endpoint `/ai/parse` + `/ai/verify-total` (OpenRouter, chỉ khi engine bó tay); XÓA Ollama khỏi pipeline (code + compose profile); không hardcode keyword ở cả 2 phía. Client chỉ OCR + geometry, gọi engine qua API.**
> **Ưu tiên cao nhất (theo yêu cầu người dùng): nhận diện ảnh hóa đơn phải tách đúng SỐ TIỀN — DANH MỤC — NỘI DUNG.**
> Quy ước: `⬜ Chưa làm | 🔄 Đang làm | ✅ Đạt | ❌ Fail (ghi rõ lỗi)`

---

## 0. Bảng tổng hợp lỗi/công việc

| Mã | Vấn đề | Mức | Nhóm |
|----|--------|-----|------|
| A1 | OCR hỏng khi ảnh nhỏ/mờ → mất dòng "Tổng tiền", fallback lấy sai số | **Ưu tiên 1** | Nhận diện |
| A2 | Không đối chiếu "Tổng dòng Tổng" vs "tổng các món" → dễ tin số sai | **Ưu tiên 1** | Nhận diện |
| A3 | `extractAmount` fallback "số lớn nhất" nguy hiểm, không báo cho user biết số ít tin cậy | Ưu tiên 1 | Nhận diện |
| A4 | `parseItems` lấy số **max** trong dòng — hóa đơn có cột SL/đơn tiền/thành tiền dễ nhầm | Ưu tiên 1 | Nhận diện |
| A5 | `splitExpenses` tạo N khoản tuần tự, lỗi giữa chừng → tạo dở dang, không rollback | Ưu tiên 1 | Nhận diện |
| A6 | `/ai/extract` fallback im lặng khi vắng model; server không giới hạn độ dài text | Ưu tiên 1 | Nhận diện |
| A7 | **Không có bộ dataset + chỉ số đo** độ chính xác nhận diện trên 13 ảnh mẫu | Ưu tiên 1 | Nhận diện |
| B1 | Album: mỗi ký tự gõ tìm kiếm gọi `refreshAllExpenses` (spam request, không debounce) | Ưu tiên 2 | Bug FE |
| B2 | Stats lọc theo ngày dùng cache Room = **chỉ trang hiện tại** của Album → thiếu khoản | Ưu tiên 2 | Bug FE |
| B3 | Bulk delete không có Undo | Ưu tiên 3 | Bug FE |
| B4 | `POST /api/ai/analyze` cũ thành dead code (client dùng analyze-basic/full) | Ưu tiên 3 | Rác |
| B5 | `Storage:BaseUrl` hardcode `10.0.2.2` trong appsettings → vỡ ảnh trên máy thật | Ưu tiên 2 | Config |
| B6 | Tìm/lọc toàn bộ giới hạn 1000 khoản ngầm (20 trang × 50), không báo cho user | Ưu tiên 2 | Thiết kế |
| B7 | File rác test UI (png/xml/cache) — ĐÃ xử lý qua `.gitignore` 2026-09-16 | ✅ Xong | Vệ sinh |
| B8 | Ollama → compose profile `ai` | ✅ Xong code (gỡ hẳn service + endpoint, compose profile) | Nhẹ máy |

> **Tình trạng các mã (2026-09-16, backend):** A1–A4, A6–A7 ✅ xong tầng engine DB (`RecognitionService` + migration + 62 test xanh, chưa push); A5 ⬜ còn (endpoint bulk); B4 🔄 còn (`/ai/analyze` cũ chưa gỡ; `/ai/extract` đã gỡ cùng Ollama); B8 ✅ xong; B1/B2/B3/B5/B6 ⬜ chưa làm.

---

## F0 — Chuẩn bị (0.5 ngày)

**Nội dung:**
1. Tạo `09_KET_QUA_FIX_TEST_TOAN_DIEN.md` khung đối chiếu 1-1 các bước F1–F5.
2. Chụp lại baseline: chạy `dotnet test` + `gradlew :app:test` + `:app:assembleDebug` → ghi số liệu vào 09.
3. Chuẩn bị `docs/3/expected.json` — bảng đáp án thủ công 13 ảnh: `{file, merchant, total, items[], category}` (tổng 13 bill đã chốt trong chat 2026-09-16: 326k / 1.053k / 1.795k / 54k / 324.555 / 7.751k / 3.584k / 1.284k / 136k / 260k / 1.550k / 6.407.500 / 225k; chỉ cần điền thêm merchant/items/category).

**Checklist:**
- [ ] `dotnet test` 32/32; `gradlew test` 11/11; `assembleDebug` xanh (baseline).
- [ ] `expected.json` đủ 13 ảnh, có xác nhận của người dùng.

---

## F1 — SỐ TIỀN: engine DB ở server + client gọi API 🔄

**File:** `Models/Recognition.cs`, `Data/RecognitionSeed.cs`, `Services/RecognitionService.cs`, `Endpoints/AiEndpoints.cs` (`/ai/parse`), migration `RecognitionKeywords`; client: `ReceiptOcr.kt` (giữ OCR + thang tin cậy), `FormScreen.kt`.

**Kiến trúc (đã duyệt lần 3): KHÔNG luật hardcode.** Tổng = thang `total_keywords.priority` + dòng quyết toán cuối + scope exclude (VAT/phụ thu); món = số cuối dòng + merge tên/số khác dòng + giữ dòng trùng; đối chiếu ±5% → HIGH/MEDIUM/LOW. Thêm case = thêm row + migration, không release app.

**Tình trạng:**
- ✅ Backend xong (migration + engine + `/ai/parse`, `dotnet test` 62/62, build 0 warning — chưa push).
- ✅ Client F1 cũ xong (local, chưa push): tiền xử lý ảnh, thang tin cậy, hint, 13 unit test.
- ⬜ Client chuyển sang gọi `/ai/parse` (thay parser local) — user tự làm (frontend).
- ⬜ Appium 3 bill.

**Checklist test:**
- [x] `dotnet test` engine: thang dòng Tổng (bill4/6/8/12/13...), tách món, validate 5%, 19 classify cũ + case mới (bida/bia/hải sản/quán nhậu).
- [ ] Fixture OCR text 13 bill (`docs/3/ocr/*.txt`) đối chiếu `expected.json` — test data-driven (đang làm).
- [x] `gradlew test` 24/24 + `assembleDebug` xanh (parser local cũ).
- [ ] Appium bill7/bill13/bill5 (chờ emulator + API).

---

## F2 — DANH MỤC + NỘI DUNG + TÁCH KHOẢN (engine DB + bulk + client) 🔄

**File:** engine DB (xong — xem F1); còn: `ExpenseEndpoints.cs` (`POST /expenses/bulk`), client `Api.kt`/`ExpenseFormViewModel.kt`/`FormScreen.kt`.

**Tình trạng:**
- ✅ Engine classify/summarize từ DB xong (`category_keywords` + merchant +10 + word-boundary, `category_aliases`, `boilerplate_patterns`); `classify-items` đã nối engine; 13/13 nhóm bill đúng (bill13 food nhờ keyword nước ngọt mới).
- ✅ `/ai/verify-total` (OpenRouter, chỉ khi engine LOW/null; thiếu key → `confident:false`, không 500).
- ✅ Xóa Ollama (service + `/ai/extract` + HttpClient + compose profile).
- ⬜ **(A5) `POST /api/expenses/bulk`** — độc lập, làm song song được (xem bản đồ dưới).
- ⬜ Cổng kiểm note AI ("Mì, Bún, Tôm" bịa) — độc lập, làm song song được.
- ⬜ Client: gọi `/ai/parse` + `/verify-total`, `splitExpenses` qua bulk — user tự làm (frontend).

**Nội dung còn lại:**
1. **(A5) Endpoint `POST /api/expenses/bulk`** (độc lập — xem bản đồ song song): nhận `[{amount, category, note, expenseDate}]` (cap 20, validate từng phần tử, 1 SaveChanges — lỗi 1 phần tử → 400, không ghi cái nào).
2. **Cổng kiểm note AI** (độc lập): note AI chỉ đè note luật khi... (quyết lúc làm); chặn case "Mì, Bún, Tôm" bịa.
3. Client (frontend, user tự làm): gọi `/ai/parse` thay parser local; `splitExpenses` qua bulk; gọi `/verify-total` khi engine LOW/null.

**Yêu cầu cần đạt (còn lại — bulk):**
- Bulk: 1 transaction, lỗi 1 phần tử → 400 không ghi cái nào; cap 20; test ≥ 6 case (valid, sai category/amount, vượt cap, sai quyền).
- Hóa đơn hỗn hợp (mì + quạt) → tách đúng 2 khoản 2 danh mục, tổng các khoản = tổng hóa đơn (chờ client + emulator).

**Checklist test:**
- [x] `dotnet test` classify/parse/verify (62 xanh).
- [ ] Test bulk endpoint (độc lập — làm song song được).
- [ ] Appium tách N khoản (chờ client + emulator).

---

## F3 — Fix bug nền (B1, B2, B3, B4, B5, B6) ⬜ — ĐỘC LẬP, song song được với F2 còn lại

**File:** `AlbumViewModel.kt`, `StatsViewModel.kt`, `StatsScreen.kt`, `StatsEndpoints.cs`, `ExpenseEndpoints.cs`, `appsettings.json`, `README.md` (B8 xong, không còn `docker-compose.yml` ở đây)

**Nội dung:**
1. **(B1 + B6 — làm chung) Chuyển tìm kiếm/lọc về server**: `GET /api/expenses?page&pageSize&search=&category=` (ILIKE note + filter, giữ nguyên `PagedExpensesDto`). `AlbumViewModel`: xóa `ensureFullForSearch`/`refreshAllExpenses`, tìm kiếm có **debounce 400ms** (kotlinx.coroutines `debounce`, không thêm dependency), mỗi lần đổi query/filter/sort → reload trang hiện tại. Giới hạn ngầm 1000 biến mất.
2. **(B2) Stats lọc theo ngày qua server**: dùng lại query `GET /api/expenses?...&from=DATE&to=DATE` ở trên; màn lọc ngày trong Stats gọi endpoint thay vì lọc `repo.expenses` (cache 1 trang).
3. **(B3) Undo cho bulk delete**: snackbar "Đã xóa n khoản" + hành động `Hoàn tác` → gọi lại `restoreExpense` từng bản ghi (n ≤ 100, tuần tự trong `viewModelScope`, fail thì báo số khôi phục được).
4. **(B4) Gỡ `POST /api/ai/analyze` cũ** khỏi `StatsEndpoints.cs` (client không dùng — kiểm tra grep toàn repo trước khi xóa; `/ai/extract` đã gỡ cùng Ollama).
5. **(B5) Config BaseUrl ảnh**: `appsettings.json` về `http://localhost:5080/uploads`; README + `.env.example` ghi rõ: emulator set `STORAGE_BASE_URL=http://10.0.2.2:5080/uploads`, máy thật set IP LAN.
6. ~~(B8)~~ ✅ đã xong (gỡ Ollama + compose profile) — bỏ khỏi F3.

**Checklist test:**
- [ ] Server test: search "phở" (có dấu + bỏ dấu) trả đúng; filter category; kết hợp page.
- [ ] Log server (`docker compose logs api -f`): gõ "abc" 3 ký tự nhanh → chỉ **1** request /expenses.
- [ ] Stats: mở tab, bấm cột ngày có khoản ở trang 2 của Album → vẫn hiện đủ khoản ngày đó.
- [ ] Bulk xóa 3 khoản → Hoàn tác → cả 3 lại có mặt, đủ ảnh + aiConfidence.
- [ ] `dotnet build` 0 warning + `dotnet test` xanh; `assembleDebug` xanh.
- [ ] Ollama tắt mặc định: app chạy bình thường, `/ai/extract` trả 200 + fallback + reason.
- [ ] Ảnh load OK khi set `STORAGE_BASE_URL` đúng (kiểm trên emulator); nêu rõ chưa test được máy thật.

---

## F4 — Test toàn diện (ma trận) ⬜

> Chạy SAU F1–F3. Mọi ô phải có kết quả (PASS/FAIL/chưa kiểm chứng + lý do) trong `09_...`.

### F4.1 Server unit/integration (`dotnet test`)
- [x] Auth/CRUD/phân trang/bulk-delete/restore/shared (giữ xanh).
- [x] Engine nhận diện: thang dòng Tổng, tách món, validate, classify, summarize, `/ai/parse`, `/ai/verify-total` thiếu key, `/ai/classify-items` (62 xanh).
- [ ] Fixture OCR text 13 bill (`docs/3/ocr/*.txt`) đối chiếu `expected.json` — test data-driven (đang làm, song song được với mọi việc khác).
- [ ] Stats: `analyze-basic` shape; `analyze-full` **cache** (lần 2 nhanh + invalidate khi đổi dữ liệu).
- [ ] `/ai/classify` (text + ảnh null), 401 thiếu token, upload `.exe` → 400, xóa expense → file bay.

### F4.2 Android unit (`gradlew test`)
- [x] ReceiptOcr 20 case + FormatTest 4 (parser local cũ — giữ xanh tới khi client chuyển sang `/ai/parse`).
- [ ] AlbumViewModel: debounce + phân trang + bulk selection (`kotlinx-coroutines-test` ĐÃ DUYỆT — pin version resolved, kiểm tra `:app:dependencies`).
- [ ] Fixture 13 bill cho parser local (nếu client còn dùng trước khi chuyển).

### F4.3 Smoke API thật (DB Postgres thật)
- [ ] Luồng: register → login → `POST /ai/parse` (bill13 text) → tạo 12 expense (1 có ảnh) → list page=1..2 → bulk-create 2 (F2 xong) → bulk-delete 2 → undo restore → stats → analyze-basic → analyze-full ×2 (lần 2 < 50ms) → verify-total (có key thì confident, không key thì unavailable) → share → shared-with-me → xóa expense có ảnh (file bay) → 401 (đổi JWT key) → delete account.
- [ ] Case lỗi: amount ≤ 0, category `zzz`, ngày sai format, `from > to`, bulk-delete id user khác → `deleted=0`, parse text rỗng/dài → 400.

### F4.4 E2E Appium (emulator + API chạy; user tự làm phần frontend)
- [ ] Kịch bản hiện có `e2e_real_flow.py` (giữ xanh).
- [ ] Mới: **luồng hóa đơn** — 3 ảnh tiêu biểu: mở app → chọn ảnh → chờ OCR+`/ai/parse` → kiểm số tiền tự điền theo `expected.json` → kiểm danh mục → tách khoản → album thấy N khoản.
- [ ] Mới: phân trang >10 khoản + xóa trang cuối tự lùi; debounce search (đếm request log); dark mode screenshot; offline → `ErrorRetry` → Thử lại.

### F4.5 Nghiệm thu nhận diện (gate quan trọng nhất) ⬜
> Chạy bằng `BillFixtureTests` (data-driven: `docs/3/ocr/*.txt` + `expected.json`), không cần emulator.
- [ ] **Số tiền**: 12/12 bill in đúng total (bill4/6/8 MEDIUM vẫn điền đúng số; bill7 viết tay LOW/trống).
- [ ] **Danh mục**: 13/13 đúng nhóm (12 food + bill8 entertainment).
- [ ] **Nội dung**: summarize không chứa MST/mã CQT/số hóa đơn; chứa tên món thật.
- [ ] Kết quả + bảng chi tiết từng ảnh ghi vào `09_...`.

---

## F5 — Tài liệu & chốt ⬜

1. `README.md`: engine DB (5 bảng + seed qua migration), `STORAGE_BASE_URL` cho emulator/máy thật, endpoint mới (`/ai/parse`, `/ai/verify-total`, bulk), xóa mọi hướng dẫn Ollama/`/ai/extract` cũ.
2. `04_KET_QUA_DAT_DUOC.md` + `07_RECEIPT_AI_KET_QUA.md`: thêm ghi chú "được bổ sung/thay thế bởi 08–09" tại mục liên quan — **không sửa kết quả cũ**.
3. `AGENTS.md`: cập nhật cấu trúc + quy ước số tài liệu docs/2.
4. Bảng kết quả cuối trong `09_...`: mỗi mã A/B → PASS/FAIL + bằng chứng.

**Definition of Done của toàn kế hoạch:** dotnet test + gradlew test + assembleDebug xanh; smoke + E2E đạt; gate F4.5 đạt; 09 điền đủ bằng chứng; chỉ commit khi được yêu cầu.

---

## Bản đồ song song (để chia việc làm cùng lúc)

```
                        ┌─ F2 còn lại: bulk endpoint ────┐
                        │  (server, ~0.5 buổi)            │
F1 engine ✅ ───────────┼─ F2 còn lại: cổng kiểm note ───┼─→ F4.5 gate ─→ F5 chốt
(backend xong)          │  (server, ~0.5 buổi)            │
                        └─ F3: B1/B2/B3/B5/B6 ───────────┘
                           (client+server, ~1 buổi,
                            KHÔNG đụng engine/bulk)
```

- Nhánh bulk, nhánh note-gate, nhánh F3: **không phụ thuộc nhau** — 3 người (hoặc 3 buổi xen kẽ) làm song song được.
- F4.1-fixture, F4.2-VM, smoke: chạy bất cứ lúc nào khi nhánh tương ứng xong.
- F4.4 Appium + client (`/ai/parse`, split qua bulk): chờ F2-client, user tự làm (frontend).
- F5: chốt sau cùng (nháp song song được).

## Rủi ro

| Rủi ro | Giảm thiểu |
|---|---|
| OCR text đầu vào xấu (bill mờ/viết tay) | Engine trả LOW + hint, không đoán; OpenRouter `/verify-total` chỉ khi LOW; user nhập tay |
| OpenRouter hết quota/key vắng | `confident:false`, về luật + hint — không 500, không chặn |
| Thêm keyword phá case cũ (VD `nike` trong "Heniken") | Word-boundary + test 62 case hồi quy; thêm row phải chạy full suite |
| Search server-side đổi API shape | `PagedExpensesDto` giữ nguyên, chỉ thêm query param optional — tương thích lùi |
| `kotlinx-coroutines-test` lệch version | ĐÃ DUYỆT — pin đúng version resolved, kiểm tra `:app:dependencies` |
