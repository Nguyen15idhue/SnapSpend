# Google Play release checklist

## Build

- [ ] `targetSdk = 36`
- [ ] Release build dùng AAB
- [ ] Release keystore được backup
- [ ] Production API dùng HTTPS
- [ ] Không có debug logging nhạy cảm

## Store

- [ ] App name
- [ ] Short description
- [ ] Full description
- [ ] App icon
- [ ] Screenshots
- [ ] Contact email
- [ ] Privacy Policy URL
- [ ] Account deletion web URL

## Data/privacy

App xử lý có thể bao gồm:

- Email/username
- Ảnh người dùng chọn/chụp
- Chi tiêu
- AI classification metadata
- Statistics/behavior analysis

Phải khai báo Data Safety đúng với cách triển khai production thực tế.

## Testing

- [ ] Camera permission
- [ ] Photo Picker
- [ ] Offline/local failure
- [ ] Upload failure
- [ ] Duplicate expense
- [ ] Delete expense
- [ ] Delete account
- [ ] AI unavailable fallback
- [ ] Wrong login
- [ ] Large image
- [ ] Old Android devices
- [ ] Android 16 behavior
