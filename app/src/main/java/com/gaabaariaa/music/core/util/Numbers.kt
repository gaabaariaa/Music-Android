package com.gaabaariaa.music.core.util

import java.text.NumberFormat
import java.util.Locale

/** Locale-aware number text (Persian digits in Persian), without grouping separators. */
fun formatNumber(value: Double, maxFractionDigits: Int = 0, locale: Locale = Locale.getDefault()): String =
    NumberFormat.getNumberInstance(locale).apply {
        isGroupingUsed = false
        minimumFractionDigits = 0
        maximumFractionDigits = maxFractionDigits
    }.format(value)

fun formatNumber(value: Int, locale: Locale = Locale.getDefault()): String = formatNumber(value.toDouble(), 0, locale)
