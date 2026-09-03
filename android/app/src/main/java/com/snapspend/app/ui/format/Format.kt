package com.snapspend.app.ui.format

import java.text.NumberFormat
import java.util.Locale

fun formatVnd(value: Long): String =
    NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN")).format(value) + " ₫"
