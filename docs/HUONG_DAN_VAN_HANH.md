<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.CAMERA" />
    <uses-permission android:name="android.permission.INTERNET" />

    <application
        android:allowBackup="true"
        android:label="SnapSpend"
        android:supportsRtl="true"
        android:theme="@style/Theme.SnapSpend"
>
        <activity android:name=".MainActivity" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>

## 11. Giới hạn MVP hiện tại

Room được dùng như local cache cho danh sách expense đã tải từ server. Chức năng tạo expense hiện cần API trả thành công mới ghi vào cache; chưa có offline upload queue. Production nên bổ sung outbox/sync queue + WorkManager nếu muốn offline-first hoàn chỉnh.
