package com.snapspend.app.data.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptOcrTest {

    private val sample = """
        HÓA ĐƠN BÁN HÀNG
        Mã số thuế: 0108892073
        Mì Hảo Hảo Tôm Chua Cay 30 Gói 4 5.000 20.000
        BÚN ĐÁI - BÚN NGÁN (BÀ DUNG) Bich 1 30.000 30.000
        Tổng tiền thanh toán (Total amount): 50.000
        Ngày 05 tháng 07 năm 2026
        Mã CQT: M2-26-JNEKJ-00000007363
        Số (No): 7363
    """.trimIndent()

    @Test fun `tong o dong tong`() = assertEquals(50000L, ReceiptOcr.extractTotal(sample))

    @Test fun `amount uu tien dong tong`() = assertEquals(50000L, ReceiptOcr.extractAmount(sample))

    @Test fun `tach duoc 2 mon`() {
        val items = ReceiptOcr.parseItems(sample)
        assertTrue(items.any { it.amount == 20000L && it.name.contains("Hảo Hảo") })
        assertTrue(items.any { it.amount == 30000L && it.name.contains("BÚN", ignoreCase = true) })
        assertTrue(items.none { it.name.contains("Tổng", ignoreCase = true) })
    }

    @Test fun `tom tat bo boilerplate`() {
        val s = ReceiptOcr.summarize(sample)
        assertTrue(s.contains("Hảo Hảo"))
        assertTrue(!s.lowercase().contains("mã số thuế"))
    }

    @Test fun `text rong tra null`() {
        assertEquals(null, ReceiptOcr.extractAmount(""))
        assertEquals(null, ReceiptOcr.extractTotal("   "))
        assertTrue(ReceiptOcr.parseItems("").isEmpty())
    }

    @Test fun `bo dong ma so hieu khi trich so`() {
        val text = "Số (No): 7363\nMã CQT: M2-26-JNEKJ-00000007363\nMì Hảo Hảo Tôm Chua Cay Gói 4 5.000 20.000"
        assertEquals(null, ReceiptOcr.extractTotal(text))
        // Phải lấy 20000 của món, không phải 7363 của số hóa đơn.
        assertEquals(20000L, ReceiptOcr.extractAmount(text))
        assertTrue(ReceiptOcr.parseItems(text).none { it.name.contains("CQT", ignoreCase = true) })
    }

    @Test fun `hoa don dien tom tat khong rong va khong tu dien so`() {
        val text = "Chi Tiết Giao Dịch\nĐIỆN LỰC MIỀN BẮC\n-191.268đ\nMã giao dịch 135245459888\nMã khách hàng PA05070021598\nTiền điện tháng 06/2026"
        val s = ReceiptOcr.summarize(text)
        assertTrue(s.isNotBlank())
        // Không tự điền số từ mã giao dịch/mã khách hàng (để trống cho người dùng nhập).
        assertEquals(null, ReceiptOcr.extractTotal(text))
    }
}
