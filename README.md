# SnapSpend — MVP

SnapSpend là app Android ghi nhận chi tiêu bằng ảnh, phân loại theo category cố định, album chi tiêu, thống kê và AI phân tích hành vi.

## Thành phần

- `android/`: Kotlin + Jetpack Compose + CameraX + Room + Retrofit.
- `server/`: ASP.NET Core 10 + EF Core + PostgreSQL + JWT + lưu ảnh local + AI qua OpenAI Responses API.
- `docs/`: hướng dẫn vận hành, API contract, Play Console checklist, privacy policy mẫu.

## Lưu ý về trạng thái source

Đây là **MVP runnable source**: đủ luồng đăng ký/đăng nhập, camera/gallery, nhập tiền, lưu ảnh, category, album, thống kê, AI analysis, friends và delete account API. Trước khi production cần thêm rate limiting, refresh token, cloud object storage, HTTPS, monitoring, backup, image moderation/virus scanning và kiểm thử device thực tế.
