# Receipt Intelligence — Các bước thực hiện

> Quy ước trạng thái: `⬜ Chưa làm | 🔄 Đang làm | ✅ Đạt | ❌ Fail (ghi rõ lỗi)`
> Mỗi bước gồm: **Nội dung** · **Yêu cầu cần đạt** · **Checklist test** · **Ghi chú**.

---

## R1 — Cải thiện parse OCR & tách nhiều khoản (không cần model mới)

### Bước R1.1 — Trích tổng tiền + danh sách món từ text OCR
**Nội dung:**
1. Trong `ReceiptOcr.kt`: tách `items` (tên + thành tiền) từ text theo cột bảng hóa đơn.
2. Ưu tiên số ở dòng "Tổng/Tổng tiền thanh toán/Total"; đối chiếu với tổng các món.
3. Trả về `{ items, total }` cho form.

**Yêu cầu cần đạt:**
- hoadon2: tổng = 50.000; nhận diện được món "Mì Hảo Hảo", "BÚN ĐÁI".
- hoadon1: tổng = 4.000.000 (quạt).
- Không nhận nhầm mã hàng/ngày thành số tiền.

**Checklist test:**
- [ ] Unit test parse text mẫu → `{items, total}` đúng.
- [ ] Trên app: mở hóa đơn 2 → số tiền tự điền 50.000.
- [ ] `assembleDebug` xanh.

**Ghi chú:** Giữ nguyên API; logic thuần Kotlin để test nhanh.

---

### Bước R1.2 — Classify từng món + gợi ý tách N khoản
**Nội dung:**
1. Server: `POST /api/ai/classify-items` nhận `items[]` → trả `category` từng món (dùng engine luật hiện có).
2. Android: sau OCR, gọi endpoint → nếu >1 danh mục, hiển thị gợi ý + nút **"Tách thành N khoản"**.
3. Nút tách: tạo N expense (mỗi món 1 khoản, giữ số tiền từng món), user xác nhận từng khoản hoặc lưu hàng loạt.

**Yêu cầu cần đạt:**
- Hóa đơn mì/bún → 1 khoản Ăn uống; hóa đơn hỗn hợp → gợi ý đúng các danh mục.
- Số tiền từng khoản đúng như trên hóa đơn.

**Checklist test:**
- [ ] API `classify-items` trả đúng category từng món (curl).
- [ ] App hiện gợi ý + tạo N khoản đúng số tiền (Appium + kiểm DB).
- [ ] `dotnet build` xanh; `assembleDebug` xanh.

**Ghi chú:** Không đổi schema (N khoản = N bản ghi `expenses`).

---

### Bước R1.3 — Hóa đơn điện/nước/internet luôn đúng `bills`
**Nội dung:**
1. Ưu tiên merchant "Điện lực/nước/internet" hơn tiêu đề "HÓA ĐƠN" trong engine luật.
2. Test với hoadon3 (bỏ qua ở vòng trước thì nay kiểm chứng lại).

**Yêu cầu cần đạt:**
- hoadon3 (tiền điện) → `bills`, không còn `other`.

**Checklist test:**
- [ ] `POST /ai/classify` với text tiền điện → `bills`.
- [ ] Lưu từ app → DB đúng `bills`.

**Ghi chú:** Chỉ sửa thứ tự/từ khóa luật, không đụng model.

---

## R2 — Mini model (Ollama) cho extraction + phân tích

### Bước R2.1 — Dựng Ollama + endpoint extraction
**Nội dung:**
1. `docker-compose` thêm service `ollama` (model Qwen2.5 0.5B trước; cấu hình qua env `OLLAMA_MODEL`).
2. Server thêm `Receipt Intelligence service` gọi Ollama (HTTP, JSON mode, timeout 20s) → `{merchant, date, items, total}`.
3. Endpoint `POST /api/ai/extract` (multipart ảnh) trả JSON có cấu trúc; lỗi/timeout → fallback parser luật (R1).

**Yêu cầu cần đạt:**
- `docker compose up -d` dựng được ollama; endpoint trả JSON đúng shape khi model sẵn.
- Không có ollama → endpoint vẫn trả kết quả fallback, không 500.

**Checklist test:**
- [ ] `curl` ảnh hóa đơn 2 → JSON có `merchant/items/total` hợp lý.
- [ ] Tắt service ollama → endpoint trả fallback 200.
- [ ] `dotnet build` xanh.

**Ghi chú:** Cache kết quả theo hash ảnh để không gọi lại cùng ảnh.

---

### Bước R2.2 — Mini model cho phân tích hành vi
**Nội dung:**
1. Gửi số liệu tổng hợp (tổng, trung bình, top danh mục, so sánh kỳ trước, bất thường) cho mini model → nhận xét tiếng Việt.
2. Giữ fallback luật (hiện có) khi model lỗi; thêm **so sánh kỳ trước** vào số liệu đầu vào.

**Yêu cầu cần đạt:**
- Nhận xét nhắc đúng số liệu thật (tổng, top danh mục, kỳ trước).
- Không bịa số liệu ngoài dữ liệu đầu vào.

**Checklist test:**
- [ ] `POST /ai/analyze` trả đoạn văn có nhắc đúng tổng/top danh mục của user test.
- [ ] Tắt ollama → vẫn trả phân tích luật (đã có).
- [ ] App hiển thị đủ 4 khối.

**Ghi chú:** Prompt yêu cầu "chỉ dùng số liệu cho trước, không bịa".

---

## R3 — Regression full + tài liệu

### Bước R3.1 — Regression toàn bộ
**Nội dung:** Chạy lại: `dotnet test` (11), `gradlew test` (4), E2E Appium (auth → tạo → album → detail → stats → profile), smoke API (categories/shared/restore/stats/401/409), kiểm 3 hóa đơn mẫu.

**Yêu cầu cần đạt:** Tất cả xanh; không regression.

**Checklist test:**
- [ ] `dotnet test` xanh.
- [ ] `gradlew test` xanh + `assembleDebug` xanh.
- [ ] E2E Appium xanh.
- [ ] 3 hóa đơn mẫu phân loại đúng.

**Ghi chú:** Ghi kết quả vào file kết quả (mục 3).

### Bước R3.2 — Cập nhật tài liệu
**Nội dung:** Cập nhật `README.md` (OCR + mini model + cách chạy ollama), `AGENTS.md` (nếu đổi quy ước), và file kết quả.

**Yêu cầu cần đạt:** Người mới đọc README chạy được cả API + ollama + app.

**Checklist test:**
- [ ] Làm theo README từ máy sạch → build xanh.
- [ ] Không còn thông tin lỗi thời.

**Ghi chú:** Không commit secret/key.
