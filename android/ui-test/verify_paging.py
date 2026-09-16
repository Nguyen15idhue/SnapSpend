#!/usr/bin/env python3
"""Kiểm chứng chuyển trang ở Album (cần >10 khoản để có 2 trang)."""
import subprocess
import sys
import time

sys.path.insert(0, ".")
from e2e_real_flow import Flow, check, failed, log, make_driver, passed, rid, text_contains  # noqa: E402

from appium.webdriver.common.appiumby import AppiumBy  # noqa: E402
from selenium.webdriver.support import expected_conditions as EC  # noqa: E402
from selenium.webdriver.support.ui import WebDriverWait  # noqa: E402


def scroll_end(driver, tries=5):
    """Cuộn xuống cuối (PageBar nằm cuối LazyColumn, chỉ compose khi hiện)."""
    for _ in range(tries):
        if driver.find_elements(*rid("btn_page_2")):
            return True
        try:
            driver.find_element(AppiumBy.ANDROID_UIAUTOMATOR,
                "new UiScrollable(new UiSelector().scrollable(true)).scrollForward()")
        except Exception:
            pass
        time.sleep(1)
    return bool(driver.find_elements(*rid("btn_page_2")))


def main():
    subprocess.run(["adb", "shell", "am", "force-stop", "com.snapspend.app"], check=False)
    time.sleep(1)
    subprocess.run(["adb", "shell", "am", "start", "-n", "com.snapspend.app/.MainActivity"], check=False)
    time.sleep(4)
    driver = make_driver()
    f = Flow(driver)
    try:
        f.wait_text("Album", 30)
        try:
            f.tap_id("tab_album")
        except Exception:
            pass

        def goto_page2():
            assert scroll_end(driver), "khong thay thanh chuyen trang"
            f.tap_id("btn_page_2")
            # Trang 2 chứa các khoản cũ nhất.
            WebDriverWait(driver, 20).until(
                EC.presence_of_element_located(text_contains("Com tam suon")))
            log("sang trang 2 dung")

        def back_page1():
            f.tap_id("btn_page_prev")
            WebDriverWait(driver, 20).until(
                EC.presence_of_element_located(text_contains("trangtest1")))
            log("ve trang 1 dung")

        check("goto_page2", goto_page2, driver)
        check("back_page1", back_page1, driver)
    finally:
        driver.quit()
    log("PASSED=%s FAILED=%s" % (passed, failed))
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
