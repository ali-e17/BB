package com.example.bb

import java.util.Locale

data class ParsedClassTime(
    val formatted: String,
    val minutesFromMidnight: Int
)

object ClassTimeUtils {

    fun parse(rawValue: String): ParsedClassTime? {
        val normalized = normalizeDigits(rawValue)
            .trim()
            .replace(" ", "")
            .replace("٫", ":")
            .replace(".", ":")

        if (normalized.isBlank()) return null

        val hour: Int
        val minute: Int

        if (normalized.contains(':')) {
            val parts = normalized.split(':')
            if (parts.size != 2 || parts.any { it.isBlank() || !it.all(Char::isDigit) }) return null
            hour = parts[0].toIntOrNull() ?: return null
            minute = parts[1].toIntOrNull() ?: return null
        } else {
            if (!normalized.all(Char::isDigit)) return null
            when (normalized.length) {
                1, 2 -> {
                    hour = normalized.toIntOrNull() ?: return null
                    minute = 0
                }
                3 -> {
                    hour = normalized.substring(0, 1).toIntOrNull() ?: return null
                    minute = normalized.substring(1, 3).toIntOrNull() ?: return null
                }
                4 -> {
                    hour = normalized.substring(0, 2).toIntOrNull() ?: return null
                    minute = normalized.substring(2, 4).toIntOrNull() ?: return null
                }
                else -> return null
            }
        }

        if (hour !in 0..23 || minute !in 0..59) return null

        return ParsedClassTime(
            formatted = String.format(Locale.US, "%02d:%02d", hour, minute),
            minutesFromMidnight = hour * 60 + minute
        )
    }

    fun normalizeDigits(value: String): String = buildString(value.length) {
        value.forEach { char ->
            append(
                when (char) {
                    '۰', '٠' -> '0'
                    '۱', '١' -> '1'
                    '۲', '٢' -> '2'
                    '۳', '٣' -> '3'
                    '۴', '٤' -> '4'
                    '۵', '٥' -> '5'
                    '۶', '٦' -> '6'
                    '۷', '٧' -> '7'
                    '۸', '٨' -> '8'
                    '۹', '٩' -> '9'
                    else -> char
                }
            )
        }
    }
}
