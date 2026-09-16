# Receipt Intelligence — Kế hoạch tổng quan

> Phạm vi: nâng cấp luồng hóa đơn (OCR → trích xuất có cấu trúc → phân loại/tách khoản → phân tích hành vi)
> bằng mini model + engine luật. Không chạm các bước ngoài phạm vi đã chốt.

## 1. Mục tiêu
1. Từ ảnh hóa đơn (chụp/chọn/không ảnh + text): trích đúng **tổng tiền**, **danh sách món** (tên + thành tiền), **nhà cung cấp/ngày** (nếu có).
2. **Tự phân loại đúng** từng khoản: hóa đơn nhiều món thì **tách thành N khoản** đúng danh mục (VD mì/bún → Ăn uống, quạt → Mua sắm, tiền điện → Hóa đơn).
3. **AI phân tích hành vi** ra nhận xét hợp lý từ dữ liệu (tổng, trung bình, top danh mục, so sánh kỳ trước, bất thường, gợi ý).
4. Mọi chức năng khác vẫn hoạt động (regression xanh).

## 2. Kiến trúc đề xuất
```
ảnh → ML Kit OCR (sẵn có) → Structured Extraction (mini model / luật)
      → { merchant, date, items[{name, amount}], total }
      → classify từng món → 1 khoản (cùng danh mục) hoặc N khoản (khác danh mục)
      → user xác nhận → lưu → phân tích hành vi (mini model + fallback luật)
```

## 3. Lựa chọn mini model (đã chốt theo duyệt)
- **Server-side Ollama + Qwen2.5 0.5B–1.5B**, gọi qua HTTP, **JSON mode**.
- Lý do: miễn phí, không quota, không tăng APK; CPU chạy được (vài giây/lần, chấp nhận được).
- **Không** dùng LLM on-device lúc này (APK +0.5–1GB, emulator không chạy nổi).
- **Luôn có fallback luật** khi model lỗi/timeout/vắng mặt.

## 4. Nguyên tắc
- Thêm endpoint/field mới, **không đổi shape API cũ** (tương thích app hiện tại).
- Không đổi schema DB cho tính năng tách khoản (N khoản = N bản ghi `expenses`).
- UI: user **luôn xác nhận/sửa** trước khi lưu (số tiền, danh mục từng khoản).
- Mọi thay đổi: build xanh → test theo checklist → ghi kết quả.

## 5. Rủi ro & giảm thiểu
| Rủi ro | Giảm thiểu |
|---|---|
| Ollama nặng/chậm | Model 0.5B trước; cache theo hash ảnh; timeout 20s → fallback luật |
| OCR sai thứ tự dòng | Parse theo cột + từ khóa "Tổng"; cho sửa tay số tiền |
| Vượt phạm vi | Không chạm GĐ5; chỉ thêm 1 service + endpoint + UI xác nhận |

## 6. Tiến độ & Definition of Done
- 3 giai đoạn: R1 (parse/tách khoản) → R2 (mini model) → R3 (regression + tài liệu).
- Done khi: 3 hóa đơn mẫu phân loại đúng; tách N khoản đúng số tiền; phân tích ra nhận xét hợp lý; regression xanh; tài liệu cập nhật.
