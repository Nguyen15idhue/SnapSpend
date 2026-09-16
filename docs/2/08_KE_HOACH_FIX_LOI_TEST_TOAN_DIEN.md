# 08 — Kế hoạch fix lỗi review + test toàn diện (2026-09-16)

> Nguồn: code review 2026-09-16 (đối chiếu `04_KET_QUA_DAT_DUOC.md` + `07_RECEIPT_AI_KET_QUA.md`).
> Base code: commit `6d044ee` (đã push GitHub). Test nền đang xanh: `dotnet test` 32/32, `gradlew test` 11/11, `assembleDebug` OK.
> Kết quả nghiệm thu ghi vào `09_KET_QUA_FIX_TEST_TOAN_DIEN.md` (tạo khi bắt đầu).
> **Sửa đổi 2026-09-16 (lần 2, đã duyệt): chuyển sang kiến trúc cascade 3 tầng NHẸ — Tầng 0 trên máy (ML Kit + tiền xử lý) → Tầng 1 server luật (2 quy tắc, 0 thêm RAM) → Tầng 2 cloud free chỉ khi không tin cậy; VietOCR/Donut chỉ chạy lab tay rồi tắt, KHÔNG service thường trú; Ollama → compose profile `ai`. Lý do: máy 16GB chỉ còn ~1.6GB trống + gánh container project khác.**
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
| B8 | Ollama thường trú nặng (image ~5.5GB, RAM ~1GB) trong khi máy chỉ còn ~1.6GB trống → chuyển sang compose profile `ai` (mặc định chỉ Postgres + API) | Ưu tiên 2 | Nhẹ máy |
| B7 | File rác test UI (png/xml/cache) — ĐÃ xử lý qua `.gitignore` 2026-09-16 | ✅ Xong | Vệ sinh |

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

## F1 — SỐ TIỀN: tầng 0 (trên máy) + tầng 1 (luật server, 0 thêm RAM) ⬜

**File:** `ReceiptOcr.kt`, `CameraScreen.kt`/`FormScreen.kt`, `ReceiptOcrTest.kt`

**Nguyên tắc: cascade nhẹ — ML Kit + luật xử lý 10/13 bill; bill khó mới lên cloud (F2). Không thêm service thường trú.**

**Nội dung:**
1. **(A1) Tầng 0 — tiền xử lý trên máy (0 tốn server)**: ảnh < 1000px cạnh dài → upscale ×2; grayscale + tăng tương phản (`ColorMatrix`, API Android sẵn có, không dependency mới). ML Kit chạy 2 lượt: lượt gốc thiếu dòng Tổng → lượt đã tiền xử lý.
2. **(A2 + A4) Tầng 1 — đúng 2 quy tắc parser (code thuần)**: (a) số **CUỐI cùng** của dòng = thành tiền (chuẩn bảng VN: SL, đơn giá, thành tiền — né bẫy cột CK/số lượng ở bill6/bill10/bill12); (b) số ở **dòng Tổng cuối cùng** = tổng (né bẫy VAT bill4, phụ thu bill6, tiền giờ bill8, cột thuế bill12). Thêm `validateTotal(total, items)`: lệch ≤ 5% → tin cậy cao; lệch hơn → KHÔNG tự điền + hint.
3. **(A3) Thang tin cậy**: `extractAmount` trả kèm `level` (total-line = cao, sum-items = trung, largest = **thấp → không tự điền**, chỉ hint). UI nhắc "cần kiểm tra" khi level thấp.
4. Unit test thuần Kotlin, chạy nhanh không cần máy.

**Yêu cầu cần đạt:**
- 12/13 fixture (trừ bill7 viết tay) cho đúng `total` theo `expected.json`, hoặc bị từ chối tự điền chủ động — **không bao giờ tự điền sai im lặng**.
- bill7 viết tay → LUÔN để trống + hint (không đoán).
- Spot-check: bill12 → 6.407.500 (cột cuối, không phải 5.825.000); bill4 → 324.555; bill6 → 3.584.000; bill13 giữ đủ 2 dòng Coca trùng tên (tổng 225.000).

**Checklist test:**
- [ ] Unit: ≥ 12 case (13 bill − bill7 + ca biên: tổng lệch, không dòng tổng, hóa đơn điện, discount, dòng trùng tên).
- [ ] `gradlew test` xanh toàn bộ.
- [ ] App (Appium): bill7/bill13/bill5 — đúng HOẶC trống + hint, không điền nhầm số mã/tổng sai.

---

## F2 — DANH MỤC + NỘI DUNG + TÁCH KHOẢN (tầng 1 + tầng 2 cloud + lab tay) ⬜

**File:** `AiService.cs`, `CategoryCatalog.cs`, `ExpenseEndpoints.cs`, `StatsEndpoints.cs`, `Api.kt`, `ExpenseFormViewModel.kt`, `FormScreen.kt`, `ClassifyTests.cs`

**Nội dung:**
1. **(A7) Danh mục từ catalog — không ML mới**: chuyển Rules/aliases hardcode trong `AiService` vào `CategoryCatalog` (nguồn duy nhất) + luật "merchant thắng món lẻ" (bill8 bida → entertainment; bill12 "Nhà hàng" → food). Đo tự động: 13 merchant/note thật từ `expected.json` → mục tiêu **13/13 đúng nhóm**.
2. **(A5) Endpoint `POST /api/expenses/bulk`**: nhận `[{amount, category, note, expenseDate}]` (cap 20, validate từng phần tử, 1 SaveChanges — lỗi 1 phần tử → 400, không ghi cái nào). `splitExpenses` đổi sang endpoint này, xóa logic tạo tuần tự.
3. **(A6) `/ai/extract` xuống vai trò bổ trợ**: fallback kèm `reason` (`model_unavailable`/…); giới hạn `Text` 4.000 ký tự (400 khi vượt). Ollama chỉ bật khi test lab (xem B8 ở F3).
4. **Tầng 2 — cloud free cho bill tầng 1 báo yếu** (bill7 viết tay, bill13 mờ): dùng `POST /ai/classify` (Gemini vision, đã có) / OpenRouter vision; hết quota → về luật + nhập tay có hint. Không thêm service local.
5. **Lab offline 1 lần (không vào compose)**: chạy VietOCR/Donut bằng tay trên 13 bill để đối chiếu sinh `expected.json`, xong dừng. Donut fine-tune tiếng Việt chỉ xem xét nếu gate F4.5 rớt.
6. **Nội dung (note)**: hậu kiểm `summarize` — rỗng/chỉ-số → "Chi tiêu {category}"; 13/13 fixture không lọt MST/mã CQT/số hóa đơn.
7. `classify-items`: thêm test tiếng Việt có dấu + không dấu đồng kết quả.

**Yêu cầu cần đạt:**
- Hóa đơn hỗn hợp (mì + quạt) → tách đúng 2 khoản 2 danh mục, tổng các khoản = tổng hóa đơn.
- Bulk tạo thất bại một nửa không bao giờ xảy ra.

**Checklist test:**
- [ ] `dotnet test` thêm ≥ 6 case (bulk endpoint: valid, phần tử sai category/amount, vượt cap, sai quyền user khác); 13/13 classify.
- [ ] `gradlew test`: parseItems/validateTotal/summarize xanh.
- [ ] Appium: 1 ảnh → OCR → danh sách món → "Tách thành N khoản" → DB đúng N bản ghi, tổng khớp `expected.json`.
- [ ] `/ai/extract`: lab bật Ollama → JSON đúng; mặc định tắt → 200 + fallback + reason.

---

## F3 — Fix bug nền (B1, B2, B3, B4, B5, B6) ⬜

**File:** `AlbumViewModel.kt`, `StatsViewModel.kt`, `StatsScreen.kt`, `StatsEndpoints.cs`, `ExpenseEndpoints.cs`, `appsettings.json`, `docker-compose.yml`, `README.md`

**Nội dung:**
1. **(B1 + B6 — làm chung) Chuyển tìm kiếm/lọc về server**: `GET /api/expenses?page&pageSize&search=&category=` (ILIKE note + filter, giữ nguyên `PagedExpensesDto`). `AlbumViewModel`: xóa `ensureFullForSearch`/`refreshAllExpenses`, tìm kiếm có **debounce 400ms** (kotlinx.coroutines `debounce`, không thêm dependency), mỗi lần đổi query/filter/sort → reload trang hiện tại. Giới hạn ngầm 1000 biến mất.
2. **(B2) Stats lọc theo ngày qua server**: dùng lại query `GET /api/expenses?...&from=DATE&to=DATE` ở trên; màn lọc ngày trong Stats gọi endpoint thay vì lọc `repo.expenses` (cache 1 trang).
3. **(B3) Undo cho bulk delete**: snackbar "Đã xóa n khoản" + hành động `Hoàn tác` → gọi lại `restoreExpense` từng bản ghi (n ≤ 100, tuần tự trong `viewModelScope`, fail thì báo số khôi phục được).
4. **(B4) Gỡ `POST /api/ai/analyze` cũ** khỏi `StatsEndpoints.cs` (client không dùng — kiểm tra grep toàn repo trước khi xóa).
5. **(B5) Config BaseUrl ảnh**: `appsettings.json` về `http://localhost:5080/uploads`; README + `.env.example` ghi rõ: emulator set `STORAGE_BASE_URL=http://10.0.2.2:5080/uploads`, máy thật set IP LAN.
6. **(B8) Ollama → compose profile `ai` (nhẹ máy)**: `docker-compose.yml` thêm `profiles: ["ai"]` cho service ollama; mặc định `up -d` chỉ Postgres + API (~500MB). README ghi cách bật khi lab (`--profile ai` + pull model 1 lần). Kiểm tra app chạy bình thường khi ollama tắt.

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
- [ ] Auth: register/login/409/401 (đã có) — giữ xanh.
- [ ] CRUD + phân trang + search/filter mới + bulk-create (F2) + bulk-delete (đã có) + restore + shared.
- [ ] Stats: `from>to`/sai format/rỗng kỳ; `analyze-basic` shape đủ trường; `analyze-full` **cache**: lần 2 nhanh hơn + đổi dữ liệu xong cache phải invalidate (key có versionCount/MaxUpdated — verify).
- [ ] `/ai/classify` (text + ảnh null), `/ai/classify-items` cap 20, `/ai/extract` fallback (dùng fake handler), 401 khi thiếu token.
- [ ] Ảnh: upload loại sai (`.exe`) → 400; xóa expense → file bay.

### F4.2 Android unit (`gradlew test`)
- [ ] ReceiptOcr fixtures ≥ 15 case (13 ảnh + ca biên: ảnh ngang, hóa đơn điện, discount).
- [ ] FormatTest (đã có).
- [ ] AlbumViewModel: debounce + phân trang + bulk selection (cần `kotlinx-coroutines-test` — **hỏi trước** vì là dependency mới theo AGENTS.md 4.1).

### F4.3 Smoke API thật (curl/Invoke-RestMethod, DB Postgres thật)
- [ ] Luồng: register → login → tạo 12 expense (1 có ảnh) → list page=1..2 → search → bulk-delete 2 → undo restore → stats → analyze-basic → analyze-full ×2 (đo thời gian lần 2 < 50ms) → extract (Ollama) → share → shared-with-me → xóa expense có ảnh → xác nhận file trong `wwwroot/uploads` bị xóa → 401 (đổi JWT key) → delete account (sạch DB + ảnh).
- [ ] Case lỗi: amount ≤ 0, category `zzz`, ngày sai format, `from > to`, bulk-delete id của user khác → `deleted=0`.

### F4.4 E2E Appium (emulator + API + Ollama chạy)
- [ ] Kịch bản hiện có `e2e_real_flow.py` (giữ xanh).
- [ ] Mới: **luồng hóa đơn** — 3 ảnh tiêu biểu (ngang/dọc/webp): mở app → chọn ảnh → chờ OCR → kiểm số tiền tự điền theo `expected.json` → kiểm danh mục gợi ý → tách khoản → album thấy N khoản.
- [ ] Mới: phân trang >10 khoản + xóa trang cuối tự lùi.
- [ ] Mới: debounce search (đếm request qua log server).
- [ ] Mới: dark mode screenshot các màn Stats/Album/Form.
- [ ] Loading/empty/error: bật chế độ máy bay giữa lúc tải → hiện `ErrorRetry` → tắt → Thử lại thành công.

### F4.5 Nghiệm thu nhận diện (gate quan trọng nhất) ⬜
> Script `docs/3/scripts/score_recognition.ps1` (viết ở bước này, gọi API + so `expected.json`).
- [ ] **Số tiền**: tự điền đúng 12/13 (bill7 viết tay được phép TRỐNG + hint); 0 case tự điền sai im lặng.
- [ ] **Danh mục**: 13/13 đúng nhóm (heuristic, không cần key).
- [ ] **Nội dung**: summarize không chứa MST/mã CQT/số hóa đơn; chứa ≥ 1 tên món thật (13/13).
- [ ] Kết quả + bảng chi tiết từng ảnh ghi vào `09_...`.

---

## F5 — Tài liệu & chốt ⬜

1. `README.md`: cách chạy Ollama pull model, `STORAGE_BASE_URL` cho emulator/máy thật, endpoint mới (search/filter, bulk-create).
2. `04_KET_QUA_DAT_DUOC.md` + `07_RECEIPT_AI_KET_QUA.md`: thêm ghi chú "được bổ sung/thay thế bởi 08–09" tại mục liên quan — **không sửa kết quả cũ**.
3. `AGENTS.md`: cập nhật cấu trúc + quy ước số tài liệu docs/2.
4. Bảng kết quả cuối trong `09_...`: mỗi mã A/B → PASS/FAIL + bằng chứng.

**Definition of Done của toàn kế hoạch:** dotnet test + gradlew test + assembleDebug xanh; smoke + E2E đạt; gate F4.5 đạt; 09 điền đủ bằng chứng; chỉ commit khi được yêu cầu.

---

## Thứ tự thực hiện khuyến nghị

`F0 → F1 → F2 → F3 → F4 → F5` — riêng F4.5 chạy lại ở cuối. Tổng ước lượng: 2–3 buổi làm việc.

## Rủi ro

| Rủi ro | Giảm thiểu |
|---|---|
| ML Kit vẫn fail ảnh mờ | Cơ chế "không tự điền + hint" đảm bảo không sai im lặng; user sửa tay; bill khó lên cloud |
| Qwen 0.5b JSON lệch schema | Xuống vai trò bổ trợ lab (tắt mặc định qua profile); luật đứng trước |
| RAM máy chật (~1.6GB trống) | Không thêm service thường trú; VietOCR/Donut chỉ lab tay rồi tắt |
| Search server-side đổi API shape | `PagedExpensesDto` giữ nguyên, chỉ thêm query param optional — tương thích lùi |
| `kotlinx-coroutines-test` lệch version | ĐÃ DUYỆT thêm ở F4.2 — pin đúng version coroutines resolved (dải 1.10.x), kiểm tra bằng `:app:dependencies` lúc implement |
