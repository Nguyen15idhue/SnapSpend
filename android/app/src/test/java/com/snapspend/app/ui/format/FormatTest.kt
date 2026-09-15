package com.snapspend.app.ui.format

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {
    @Test fun `format 0`() = assertEquals("0 ₫", formatVnd(0))

    @Test fun `format nghin`() = assertEquals("1.000 ₫", formatVnd(1000))

    @Test fun `format trieu`() = assertEquals("1.234.567 ₫", formatVnd(1234567))

    @Test fun `format so am`() = assertEquals("-2.500 ₫", formatVnd(-2500))
}
