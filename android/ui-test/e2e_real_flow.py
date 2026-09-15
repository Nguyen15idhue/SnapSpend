#!/usr/bin/env python3
"""
PoC E2E cho SnapSpend (backend thật) bằng Appium/uiautomator2.
Tìm element theo testTag (resource-id) thay vì toạ độ; chụp screenshot khi fail.

Yêu cầu: emulator đang chạy, API backend đang chạy (emulator trỏ 10.0.2.2:5080),
Appium server: node <appium>/build/lib/main.js server --port 4723

Chạy: python e2e_real_flow.py
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
SHOT_DIR = "."
passed, failed = [], []


def log(msg):
    print(f"[{time.strftime('%H:%M:%S')}] {msg}", flush=True)


def make_driver():
    opts = UiAutomator2Options()
    opts.platform_name = "Android"
    opts.automation_name = "UiAutomator2"
    opts.app_package = PKG
    opts.app_activity = ".MainActivity"
    opts.no_reset = True
    opts.new_command_timeout = 120
    opts.set_capability("appium:uiautomator2ServerLaunchTimeout", 120000)
    opts.set_capability("appium:adbExecTimeout", 60000)
    return webdriver.Remote(APPIUM_URL, options=opts)


def rid(value):
    return (AppiumBy.ANDROID_UIAUTOMATOR, f'new UiSelector().resourceId("{value}")')


def text_contains(value):
    return (AppiumBy.ANDROID_UIAUTOMATOR, f'new UiSelector().textContains("{value}")')


class Flow:
    def __init__(self, driver):
        self.d = driver

    def wait_id(self, i, timeout=25):
        return WebDriverWait(self.d, timeout).until(EC.presence_of_element_located(rid(i)))

    def tap_id(self, i):
        el = self.wait_id(i)
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
        log(f"nhap #{i}={value}")

    def wait_text(self, t, timeout=25):
        WebDriverWait(self.d, timeout).until(EC.presence_of_element_located(text_contains(t)))
        log(f"thay '{t}'")

    def tap_text(self, t, timeout=25):
        els = WebDriverWait(self.d, timeout).until(
            lambda dr: dr.find_elements(AppiumBy.ANDROID_UIAUTOMATOR, f'new UiSelector().textContains("{t}")') or False)
        el = max(els, key=lambda e: e.location["y"])
        loc, size = el.location, el.size
        self.d.execute_script("mobile: clickGesture", {"x": loc["x"] + size["width"] // 2, "y": loc["y"] + size["height"] // 2})
        log(f"tap '{t}'")


def check(name, fn, driver):
    try:
        fn()
        passed.append(name)
        log(f"PASS: {name}")
    except Exception as exc:  # noqa: BLE001
        failed.append(name)
        log(f"FAIL: {name} -> {str(exc)[:200]}")
        try:
            driver.save_screenshot(f"{SHOT_DIR}/fail_{name}.png")
        except Exception:
            pass


def main():
    subprocess.run(["adb", "shell", "pm", "grant", PKG, "android.permission.CAMERA"], check=False)
    subprocess.run(["adb", "shell", "am", "start", "-n", f"{PKG}/.MainActivity"], check=False)
    time.sleep(4)

    driver = make_driver()
    f = Flow(driver)
    n = random.randint(100000, 999999)
    try:
        # Đảm bảo có phiên hợp lệ: nếu đang đăng nhập thì đăng xuất để đăng ký tài khoản mới.
        def register():
            f.wait_text("Đăng ký")
            f.tap_text("Đăng ký")
            f.wait_text("Tạo tài khoản")
            f.type_id("field_email", f"e2e{n}@test.local")
            f.type_id("field_username", f"e2e{n}")
            f.type_id("field_password", "secret123")
            f.tap_id("btn_submit")
            f.wait_text("Album", 30)
        if not driver.find_elements(*rid("field_email")):
            try:
                f.tap_id("tab_profile")
                f.wait_text("Bạn bè")
                f.tap_text("Đăng xuất")
                f.wait_text("Đăng nhập")
            except Exception:  # noqa: BLE001
                pass
        check("register_real", register, driver)

        check("create_expense", lambda: (
            f.tap_id("tab_camera"),
            f.tap_id("btn_sample"),
            f.wait_text("Chi tiêu mới"),
            f.type_id("field_amount", "75000"),
            f.tap_id("btn_save"),
            f.wait_text("khoản chi"),
        ), driver)

        check("album_detail_back", lambda: (
            f.tap_id("tab_album"),
            f.wait_id("field_search"),
            f.tap_text("20"),
            f.wait_text("Chi tiết"),
            f.d.back(),
            f.wait_text("khoản chi"),
        ), driver)

        check("stats", lambda: (
            f.tap_id("tab_stats"),
            f.wait_text("Thống kê"),
            f.tap_text("AI phân tích hành vi"),
            f.wait_text("AI phân tích", 40),
        ), driver)

        check("logout", lambda: (
            f.tap_id("tab_profile"),
            f.wait_text("Bạn bè"),
            f.tap_text("Đăng xuất"),
            f.wait_text("Đăng nhập"),
        ), driver)
    finally:
        driver.quit()

    log(f"KET QUA: {len(passed)} PASS / {len(failed)} FAIL")
    if failed:
        log("FAIL: " + ", ".join(failed))
        sys.exit(1)


if __name__ == "__main__":
    main()
