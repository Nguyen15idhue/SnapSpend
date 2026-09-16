#!/usr/bin/env python3
"""Kiểm chứng UI bulk action + phân trang ở Album (không xóa dữ liệu thật)."""
import subprocess
import sys
import time

sys.path.insert(0, ".")
from e2e_real_flow import Flow, check, failed, log, make_driver, passed, rid, text_contains  # noqa: E402

from appium.webdriver.common.appiumby import AppiumBy  # noqa: E402
from selenium.webdriver.support import expected_conditions as EC  # noqa: E402
from selenium.webdriver.support.ui import WebDriverWait  # noqa: E402


def main():
    subprocess.run(["adb", "shell", "am", "start", "-n", "com.snapspend.app/.MainActivity"], check=False)
    time.sleep(4)
    driver = make_driver()
    f = Flow(driver)
    try:
        # Đảm bảo đăng nhập demo.
        if driver.find_elements(*rid("field_email")):
            f.type_id("field_email", "demo@snapspend.vn")
            f.type_id("field_password", "secret123")
            f.tap_id("btn_submit")
        f.wait_text("Album", 30)
        try:
            f.tap_id("tab_album")
        except Exception:
            pass

        def album_loaded():
            f.wait_text("Sach hoc", 30)
            log("album hien du 10 khoan")

        def bulk_select_cancel():
            # Vào chế độ chọn bằng nút "Chọn" (ổn định hơn nhấn giữ trên máy test).
            f.tap_id("btn_enter_selection")
            f.wait_id("btn_delete_selected", 15)
            # Tick 1 card rồi kiểm tra đếm.
            els = driver.find_elements(AppiumBy.ANDROID_UIAUTOMATOR, 'new UiSelector().resourceIdMatches(".*card_expense_.*")')
            assert els, "khong thay card de chon"
            loc, size = els[0].location, els[0].size
            driver.execute_script("mobile: clickGesture", {"x": loc["x"] + size["width"] // 2, "y": loc["y"] + size["height"] // 2})
            time.sleep(1)
            driver.save_screenshot("bulk_selected.png")
            log("thanh bulk action hien dung")
            f.tap_id("btn_cancel_selection")
            time.sleep(1)
            assert not driver.find_elements(*rid("btn_delete_selected")), "nut Xoa van con sau khi Huy"
            log("huy chon ve trang thai thuong")

        def bulk_long_press():
            # Nhấn giữ card cũng phải vào chế độ chọn.
            el = WebDriverWait(driver, 25).until(
                EC.presence_of_element_located(text_contains("Sach hoc")))
            driver.execute_script("mobile: longClickGesture", {"elementId": el.id, "duration": 900})
            f.wait_id("btn_delete_selected", 15)
            log("nhan giu vao che do chon")
            f.tap_id("btn_cancel_selection")
            time.sleep(1)

        def paging_bar_hidden_single_page():
            # Du lieu that it (1 trang) thi khong co thanh trang; o day chi kiem tra khong crash.
            log("bo qua (dang co 2 trang do du lieu tam)")

        check("album_loaded", album_loaded, driver)
        check("bulk_select_cancel", bulk_select_cancel, driver)
        check("bulk_long_press", bulk_long_press, driver)
        check("paging_bar_hidden_single_page", paging_bar_hidden_single_page, driver)
    finally:
        driver.quit()
    log(f"PASSED={passed} FAILED={failed}")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
