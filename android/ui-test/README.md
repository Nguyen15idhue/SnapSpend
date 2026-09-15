# UI test (E2E) — SnapSpend

PoC E2E bằng **Appium + uiautomator2**, tìm element theo `testTag` (được bật thành
`resource-id` nhờ `testTagsAsResourceId = true` ở `MainActivity`), không dùng toạ độ.

> App chỉ dùng backend thật → cần API đang chạy (emulator trỏ `http://10.0.2.2:5080`).

## Yêu cầu
- Emulator đang chạy (`adb devices` thấy `emulator-5554`).
- Backend API đang chạy.
- Appium server:
  ```
  node <npm-global>/appium/build/lib/main.js server --port 4723
  ```
- Cài client: `pip install -r requirements.txt`

## Chạy
```
python e2e_real_flow.py
```
Script **không xóa dữ liệu app**. Nếu app đang ở màn đăng nhập thì **đăng ký tài khoản thật mới**,
nếu đã đăng nhập thì bỏ qua, rồi:
tạo chi tiêu (ảnh mẫu) → album → detail → stats/AI → profile/logout.
Khi fail, ảnh chụp được lưu `fail_<bước>.png`.

## Tag quan trọng
| Tag | Vị trí |
|-----|--------|
| `field_email`, `field_password`, `btn_submit` | Auth |
| `tab_album`, `tab_camera`, `tab_stats`, `tab_profile` | BottomBar |
| `btn_sample`, `btn_capture`, `btn_gallery` | Camera |
| `field_amount`, `field_note`, `btn_save`, `chip_auto`, `chip_category_*` | Form |
| `field_search` | Album |
| `btn_edit`, `btn_share`, `btn_delete` | Detail |
