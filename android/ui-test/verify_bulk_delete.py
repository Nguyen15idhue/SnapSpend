#!/usr/bin/env python3
"""E2E xóa hàng loạt 1 khoản tạm (tamxoaE2E), dữ liệu thật không đụng tới."""
import subprocess
import sys
import time

sys.path.insert(0, ".")
from e2e_real_flow import Flow, check, failed, log, make_driver, passed, rid, text_contains  # noqa: E402

from appium.webdriver.common.appiumby import AppiumBy  # noqa: E402
from selenium.webdriver.support import expected_conditions as EC  # noqa: E402
from selenium.webdriver.support.ui import WebDriverWait  # noqa: E402


def main():
    subprocess.run(["adb", "shell", "am", "force-stop", "com.snapspend.app"], check=False)
    time.sleep(1)
    subprocess.run(["adb", "shell", "am", "start", "-n", "com.snapspend.app/.MainActivity"], check=False)
    time.sleep(4)
    driver = make_driver()
    f = Flow(driver)
    try:
        # App giữ phiên demo (no_reset) -> vào thẳng, đợi khoản tạm hiện sau refresh.
        f.wait_text("Album", 30)
        try:
            f.tap_id("tab_album")
        except Exception:
            pass

        def bulk_delete_temp():
            # Cuộn lên đầu (LazyColumn chỉ compose item đang hiện).
            for _ in range(4):
                if driver.find_elements(AppiumBy.ANDROID_UIAUTOMATOR, 'new UiSelector().textContains("tamxoaE2E")'):
                    break
                driver.execute_script("mobile: scrollGesture", {
                    "left": 500, "top": 600, "width": 400, "height": 1200,
                    "direction": "down", "percent": 0.8})
                time.sleep(1)
            el = WebDriverWait(driver, 15).until(
                EC.presence_of_element_located(text_contains("tamxoaE2E")))
            driver.execute_script("mobile: longClickGesture", {"elementId": el.id, "duration": 900})
            f.wait_id("btn_delete_selected", 15)
            f.tap_id("btn_delete_selected")
            time.sleep(2)
            # Nút Xóa trong dialog: tap theo text chính xác (Compose Button là ViewGroup).
            cands = WebDriverWait(driver, 15).until(
                lambda dr: dr.find_elements(AppiumBy.ANDROID_UIAUTOMATOR, 'new UiSelector().text("Xóa")') or False)
            btn = cands[-1]
            loc, size = btn.location, btn.size
            driver.execute_script("mobile: clickGesture", {"x": loc["x"] + size["width"] // 2, "y": loc["y"] + size["height"] // 2})
            log("tap nut Xoa trong dialog")
            f.wait_text("10/10", 20)  # 10/11 -> 10/10 sau khi xoa
            time.sleep(1)
            assert not driver.find_elements(AppiumBy.ANDROID_UIAUTOMATOR, 'new UiSelector().textContains("tamxoaE2E")'), "khoan tam van con"
            driver.save_screenshot("bulk_deleted.png")
            log("xoa hang loat 1 khoan tam thanh cong")

        check("bulk_delete_temp", bulk_delete_temp, driver)
    finally:
        driver.quit()
    log(f"PASSED={passed} FAILED={failed}")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
