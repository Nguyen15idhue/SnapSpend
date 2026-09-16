#!/usr/bin/env python3
"""
Verify F1 (08_...): OCR 3 bill khó qua gallery picker.
Gate: số tiền đúng HOẶC (trống + hint) — không bao giờ điền sai im lặng.
- bill13.jpg -> 225000 (giữ dòng trùng tên)
- bill5.webp  -> 7751000 (nghiêng, chữ đè)
- bill7.jpg   -> trống + hint (viết tay)

Yêu cầu: emulator + API + Appium server (4723) đang chạy; APK đã cài.
Chạy: python android/ui-test/verify_f1_ocr.py  (workdir: repo root)
"""
import random
import subprocess
import sys
import time

from appium import webdriver
from appium.options.android import UiAutomator2Options
from appium.webdriver.common.appiumby import AppiumBy
from selenium.webdriver.support import expected_conditions as EC
from selenium.webdriver.support.ui import WebDriverWait

APPIUM_URL = "http://127.0.0.1:4723"
PKG = "com.snapspend.app"
HINT_TOTAL = "kiểm tra lại số tiền"
HINT_BLANK = "nhập tay số tiền"
BILLS = [("bill5.webp", "7751000"), ("bill13.jpg", "225000"), ("bill7.jpg", "")]
results = []


def log(msg):
    line = f"[{time.strftime('%H:%M:%S')}] {msg}"
    try:
        print(line, flush=True)
    except UnicodeEncodeError:
        print(line.encode("ascii", "backslashreplace").decode(), flush=True)


def rid(value):
    return (AppiumBy.ANDROID_UIAUTOMATOR, f'new UiSelector().resourceId("{value}")')


def text_contains(value):
    return (AppiumBy.ANDROID_UIAUTOMATOR, f'new UiSelector().textContains("{value}")')


class Flow:
    def __init__(self, driver):
        self.d = driver

    def wait_id(self, i, timeout=25):
        return WebDriverWait(self.d, timeout).until(EC.presence_of_element_located(rid(i)))

    def tap_id(self, i, timeout=25):
        el = self.wait_id(i, timeout)
        loc, size = el.location, el.size
        self.d.execute_script("mobile: clickGesture", {"x": loc["x"] + size["width"] // 2, "y": loc["y"] + size["height"] // 2})
        log(f"tap #{i}")

    def type_id(self, i, value):
        el = self.wait_id(i)
        el.click()
        el.clear()
        el.send_keys(value)
        try:
            self.d.hide_keyboard()
        except Exception:
            pass

    def has_text(self, t):
        return bool(self.d.find_elements(*text_contains(t)))

    def shot(self, name):
        try:
            self.d.save_screenshot(f"android/ui-test/f1_{name}.png")
        except Exception:
            pass


def pick_first_photo(f):
    """System picker (photopicker): thumbnail là android.view.View clickable, lưới 3 cột."""
    time.sleep(4)
    f.shot("picker")
    els = f.d.find_elements(
        AppiumBy.ANDROID_UIAUTOMATOR,
        'new UiSelector().className("android.view.View").clickable(true)')
    big = [e for e in els if e.size.get("height", 0) >= 300 and e.location.get("y", 0) >= 1000]
    if not big:
        raise RuntimeError("không thấy thumbnail nào trong picker")
    el = sorted(big, key=lambda e: (e.location["y"], e.location["x"]))[0]
    loc, size = el.location, el.size
    f.d.execute_script("mobile: clickGesture", {"x": loc["x"] + size["width"] // 2, "y": loc["y"] + size["height"] // 2})
    log("đã chọn ảnh mới nhất trong picker")


def read_amount(f, timeout=70):
    """Poll ô số tiền + hint tới khi ổn định (OCR + classify nền xong)."""
    amount, hint = None, None
    end = time.time() + timeout
    while time.time() < end:
        try:
            amount = (f.wait_id("field_amount", 5).text or "").strip()
        except Exception:
            amount = None
        hint = HINT_TOTAL if f.has_text(HINT_TOTAL) else (HINT_BLANK if f.has_text(HINT_BLANK) else None)
        if amount or hint:
            time.sleep(6)  # chờ thêm cho AI nền/classify ổn định rồi đọc lại
            try:
                amount = (f.wait_id("field_amount", 5).text or "").strip()
            except Exception:
                pass
            hint = HINT_TOTAL if f.has_text(HINT_TOTAL) else (HINT_BLANK if f.has_text(HINT_BLANK) else None)
            break
        time.sleep(3)
    return amount or "", hint


def verify_bill(f, fname, expected):
    # Re-push để ảnh thành mới nhất trong picker.
    subprocess.run(["adb", "push", f"docs/3/images/{fname}", f"/sdcard/Pictures/{fname}"],
                   check=False, capture_output=True)
    subprocess.run(["adb", "shell", "am", "broadcast", "-a", "android.intent.action.MEDIA_SCANNER_SCAN_FILE",
                    "-d", "file:///sdcard/Pictures"], check=False, capture_output=True)
    f.tap_id("tab_camera")
    f.tap_id("btn_gallery")
    pick_first_photo(f)
    WebDriverWait(f.d, 30).until(EC.presence_of_element_located(text_contains("Chi tiêu mới")))
    amount, hint = read_amount(f)
    log(f"{fname}: amount='{amount}' hint='{hint}' (kỳ vọng '{expected}')")
    if amount == expected and expected:
        results.append((fname, "PASS", f"đúng {expected}"))
    elif amount == "" and hint:
        results.append((fname, "PASS", "trống + hint (chấp nhận)"))
    elif amount and amount != expected and hint:
        results.append((fname, "WARN", f"sai ({amount}) nhưng có hint"))
    elif amount and amount != expected:
        f.shot(f"wrong_{fname.replace('.', '_')}")
        results.append((fname, "FAIL", f"điền sai im lặng: {amount}"))
    else:
        f.shot(f"blank_{fname.replace('.', '_')}")
        results.append((fname, "FAIL", "trống mà không có hint"))
    f.d.back()  # về Camera, không lưu để khỏi bẩn DB
    time.sleep(2)


def ensure_authed(f, driver, n):
    """Về trạng thái rõ ràng: home (tab_album) hoặc auth (field_email) rồi mới xử lý."""
    home, auth = False, False
    end = time.time() + 40
    while time.time() < end:
        if driver.find_elements(*rid("tab_album")):
            home = True
            break
        if driver.find_elements(*rid("field_email")):
            auth = True
            break
        time.sleep(2)
    if home and not auth:
        f.tap_id("tab_profile")
        WebDriverWait(driver, 20).until(EC.presence_of_element_located(text_contains("Bạn bè")))
        els = driver.find_elements(AppiumBy.ANDROID_UIAUTOMATOR, 'new UiSelector().textContains("Đăng xuất")')
        if els:
            e = els[-1]
            loc, size = e.location, e.size
            driver.execute_script("mobile: clickGesture", {"x": loc["x"] + size["width"] // 2, "y": loc["y"] + size["height"] // 2})
        f.wait_id("field_email", 20)
        auth = True
    if auth:
        WebDriverWait(driver, 20).until(EC.presence_of_element_located(text_contains("Đăng ký")))
        els = driver.find_elements(AppiumBy.ANDROID_UIAUTOMATOR, 'new UiSelector().textContains("Đăng ký")')
        e = els[-1]
        loc, size = e.location, e.size
        driver.execute_script("mobile: clickGesture", {"x": loc["x"] + size["width"] // 2, "y": loc["y"] + size["height"] // 2})
        WebDriverWait(driver, 20).until(EC.presence_of_element_located(text_contains("Tạo tài khoản")))
        f.type_id("field_email", f"f1{n}@test.local")
        f.type_id("field_username", f"f1{n}")
        f.type_id("field_password", "secret123")
        f.tap_id("btn_submit")
        try:
            f.wait_id("tab_album", 45)
        except Exception:
            f.shot("register_fail")
            raise
        log("đăng ký xong")
    else:
        log("đã đăng nhập sẵn, dùng phiên hiện tại")


def main():
    subprocess.run(["adb", "shell", "pm", "grant", PKG, "android.permission.CAMERA"], check=False)
    subprocess.run(["adb", "shell", "am", "start", "-n", f"{PKG}/.MainActivity"], check=False)
    time.sleep(6)
    opts = UiAutomator2Options()
    opts.platform_name = "Android"
    opts.automation_name = "UiAutomator2"
    opts.app_package = PKG
    opts.app_activity = ".MainActivity"
    opts.no_reset = True
    opts.new_command_timeout = 180
    driver = webdriver.Remote(APPIUM_URL, options=opts)
    f = Flow(driver)
    n = random.randint(100000, 999999)
    try:
        ensure_authed(f, driver, n)
        for fname, expected in BILLS:
            try:
                verify_bill(f, fname, expected)
            except Exception as exc:  # noqa: BLE001
                f.shot(f"err_{fname.replace('.', '_')}")
                results.append((fname, "FAIL", str(exc)[:200]))
                try:
                    f.d.back()
                except Exception:
                    pass
    finally:
        driver.quit()
    npass = sum(1 for _, s, _ in results if s == "PASS")
    nfail = sum(1 for _, s, _ in results if s == "FAIL")
    for fname, s, note in results:
        log(f"{s}: {fname} — {note}")
    log(f"KET QUA F1: {npass} PASS / {nfail} FAIL / {len(results) - npass - nfail} WARN")
    sys.exit(1 if nfail else 0)


if __name__ == "__main__":
    main()
