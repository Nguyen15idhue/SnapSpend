package com.snapspend.app.data.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    // --- F1: số cuối dòng = thành tiền (bảng SL/đơn giá/thành tiền) ---

    @Test fun `so cuoi dong la thanh tien`() {
        val items = ReceiptOcr.parseItems("Mì tôm 2 25.000 50.000")
        assertEquals(1, items.size)
        assertEquals(50000L, items[0].amount)
    }

    @Test fun `giu dong trung ten de doi chieu tong`() {
        val text = "Coca 2 25.000 50.000\nSprite 2 25.000 50.000\nCoca 2 25.000 50.000\nTonic 2 25.000 50.000\nSoda 1 25.000 25.000"
        val items = ReceiptOcr.parseItems(text)
        assertEquals(5, items.size)
        assertEquals(225000L, items.sumOf { it.amount })
    }

    // --- F1: đối chiếu tổng ---

    @Test fun `validate khop tong`() {
        val items = listOf(ReceiptOcr.ReceiptItem("Coca", 50000L), ReceiptOcr.ReceiptItem("Sprite", 50000L))
        assertTrue(ReceiptOcr.validateTotal(100000L, items))
    }

    @Test fun `validate lech vat`() {
        val items = listOf(ReceiptOcr.ReceiptItem("Trà", 27000L), ReceiptOcr.ReceiptItem("Pizza", 65000L))
        assertFalse(ReceiptOcr.validateTotal(105800L, items))
    }

    @Test fun `validate nguong 5 phan tram`() {
        assertTrue(ReceiptOcr.validateTotal(100000L, listOf(ReceiptOcr.ReceiptItem("a", 95000L))))
        assertFalse(ReceiptOcr.validateTotal(100000L, listOf(ReceiptOcr.ReceiptItem("a", 94000L))))
        assertFalse(ReceiptOcr.validateTotal(0L, listOf(ReceiptOcr.ReceiptItem("a", 1000L))))
        assertFalse(ReceiptOcr.validateTotal(100000L, emptyList()))
    }

    // --- F1: dòng Tổng cuối cùng ---

    @Test fun `total lay dong cuoi ne subtotal`() {
        val text = "Tổng tiền trước thuế: 5.825.000\nThuế VAT: 582.500\nTổng tiền thanh toán: 6.407.500"
        assertEquals(6407500L, ReceiptOcr.extractTotal(text))
    }

    @Test fun `total nhan t cong`() {
        assertEquals(225000L, ReceiptOcr.extractTotal("T.Cộng 9 225,000\nTIỀN MẶT 225,000"))
    }

    @Test fun `total null khi chi co tien mat`() {
        // "TIỀN MẶT" không phải dòng Tổng — rơi xuống tổng các món (MEDIUM).
        assertNull(ReceiptOcr.extractTotal("TIỀN MẶT 1,053,000"))
    }

    // --- F1: thang tin cậy ---

    @Test fun `guess high khi khop`() {
        val g = ReceiptOcr.guessAmount(sample)
        assertEquals(50000L, g?.amount)
        assertEquals(ReceiptOcr.AmountLevel.HIGH, g?.level)
        assertEquals(50000L, ReceiptOcr.extractAmount(sample))
    }

    @Test fun `guess medium khi lech vat`() {
        val text = "Trà Lipton sữa 1 27.000 27.000\nPizza hải sản 1 65.000 65.000\nTổng cộng Sub total 92.000\nThành tiền Total 105.800"
        val g = ReceiptOcr.guessAmount(text)
        assertEquals(105800L, g?.amount)
        assertEquals(ReceiptOcr.AmountLevel.MEDIUM, g?.level)
        // MEDIUM vẫn tự điền (kèm nhắc kiểm tra ở UI).
        assertEquals(105800L, ReceiptOcr.extractAmount(text))
    }

    @Test fun `guess medium tong mon khi khong co dong tong`() {
        val text = "Mì Hảo Hảo 1 5.000 20.000\nBún bò 1 30.000 30.000"
        val g = ReceiptOcr.guessAmount(text)
        assertEquals(50000L, g?.amount)
        assertEquals(ReceiptOcr.AmountLevel.MEDIUM, g?.level)
    }

    @Test fun `guess low khong tu dien`() {
        val g = ReceiptOcr.guessAmount("12345678")
        assertEquals(12345678L, g?.amount)
        assertEquals(ReceiptOcr.AmountLevel.LOW, g?.level)
        // LOW không tự điền để tránh sai im lặng.
        assertNull(ReceiptOcr.extractAmount("12345678"))
    }

    @Test fun `guess null text rong`() {
        assertNull(ReceiptOcr.guessAmount(""))
        assertNull(ReceiptOcr.guessAmount("   "))
        assertNull(ReceiptOcr.extractAmount(""))
    }
}
