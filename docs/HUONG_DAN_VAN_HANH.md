# Hướng dẫn vận hành & Demo SnapSpend (Mock Mode — đủ trình bày, không cần backend)

> Phạm vi giai đoạn hiện tại: chạy app bằng **mock data**, không cần server/Postgres/OpenAI.
> File này thay thế nội dung cũ bị lẫn XML manifest.

## 1. Yêu cầu máy demo

- Android Studio Ladybug trở lên, JDK 17.
- Emulator API 30+ (khuyến nghị API 33 để test quyền ảnh mới) hoặc máy thật.
- Không cần mạng, không cần Docker/backend.

## 2. Build & chạy (chế độ Demo mặc định)

```bash
cd android
./gradlew :app:assembleDebug
# Cài APK: build/app/outputs/apk/debug/app-debug.apk
```

- Mở app là thấy ngay data mẫu (chế độ Demo bật sẵn, xem toggle trong Profile).
- Nếu máy không có camera (emulator): ở tab Camera bấm **“Dùng ảnh mẫu”** để qua form nhập.

## 3. Tài khoản mock

| Email | Mật khẩu | Ghi chú |
|-------|----------|---------|
| `demo@snapspend.vn` | `123456` | Bất kỳ email hợp lệ + pass >= 6 đều login được ở mock |

## 4. Kịch bản demo 5 phút

1. **(30s) Auth:** login `demo@snapspend.vn / 123456`.
2. **(60s) Camera:** “Dùng ảnh mẫu” → nhập `65000` → để `✨ AI tự phân loại` → Lưu → Album thấy món mới lên đầu.
3. **(60s) Album:** search `phở`, filter chip `food`, swipe xóa 1 món → Snackbar Undo → Undo.
4. **(45s) Detail:** chạm card `Zara 799k` → Sửa thành `749k` → Share cho `minh_tran`.
5. **(60s) Stats:** đổi range `7D / 30D / Tháng này`, chỉ BarChart + list byCategory.
6. **(45s) AI:** bấm “AI phân tích”, đọc 1 insight + 1 gợi ý tiếng Việt.
7. **(30s) Friends/Profile:** thêm bạn mới, bật/tắt toggle Demo, kết luận “nối backend không sửa UI”.

## 5. Toggle Demo / Real (trong Profile)

- Bật **Demo (Mock)**: full data mẫu, airplane mode vẫn chạy.
- Tắt (Real): app sẽ gọi `http://10.0.2.2:5080/api/` — cần chạy backend. Chưa nối backend thì báo lỗi mạng là **bình thường**.

## 6. Xử lý sự cố demo

| Hiện tượng | Cách xử lý |
|------------|-----------|
| Emulator không có camera, màn đen | Bấm “Dùng ảnh mẫu”, không cần fix camera |
| Pick ảnh gallery trống | Dùng emulator có cài sẵn ảnh mẫu, hoặc kéo file ảnh vào emulator |
| Form bị cắt nút Lưu (máy nhỏ) | Cuộn xuống, đã chuyển category sang FlowRow wrap |
| Muốn reset data mẫu | Logout → login lại, hoặc xóa app cài lại |

## 7. Giới hạn mock hiện tại (không phải lỗi)

- Ảnh expense hiện placeholder màu + emoji theo category, không phải ảnh thật.
- Số liệu AI phân tích là mẫu tiếng Việt khớp mock, chưa gọi OpenAI.
- Thêm bạn / share chỉ sửa list local, reload app sẽ reset.
- Chế độ Real cần backend (làm ở giai đoạn sau).

## 8. Liên quan kỹ thuật (ghi cho dev)

- Room hiện chỉ là local cache (`snapspend.db`, bảng `expenses`). Ở mock mode không dùng Room, dùng `StateFlow` trong `MockRepository`.
- Khi nối backend: giữ nguyên UI, chỉ đổi `AppConfig.isDemo=false` để dùng `RealRepository` (Retrofit + Room).
