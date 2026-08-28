package com.example.bb

import android.text.SpannableStringBuilder

/**
 * قواعد یکپارچه نمایش عدد در اپ:
 *
 * - اگر متن حداقل یک حرف انگلیسی داشته باشد، رقم‌ها انگلیسی نمایش داده می‌شوند.
 * - در غیر این صورت، رقم‌ها فارسی نمایش داده می‌شوند.
 * - مقدار اصلی مدل/ورودی تغییر نمی‌کند؛ این کلاس فقط برای نمایش است.
 */
object UiTextFormatter {

    fun smartDigits(value: CharSequence?): CharSequence {
        if (value == null) return ""
        if (value.isEmpty()) return value

        val useEnglishDigits = value.any { it in 'A'..'Z' || it in 'a'..'z' }
        val result = SpannableStringBuilder(value)
        var changed = false

        for (index in 0 until result.length) {
            val original = result[index]
            val replacement = if (useEnglishDigits) {
                toEnglishDigit(original)
            } else {
                toPersianDigit(original)
            }

            if (replacement != original) {
                result.replace(index, index + 1, replacement.toString())
                changed = true
            }
        }

        return if (changed) result else value
    }

    fun smartDigitsString(value: String?): String =
        smartDigits(value.orEmpty()).toString()

    /**
     * برای داده‌ای که باید به Backend فرستاده شود یا در فیلد رمز استفاده شود.
     */
    fun normalizeEnglishDigits(value: CharSequence?): String {
        if (value == null) return ""

        return buildString(value.length) {
            value.forEach { append(toEnglishDigit(it)) }
        }
    }

    fun toEnglishDigit(character: Char): Char = when (character) {
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
        else -> character
    }

    fun toPersianDigit(character: Char): Char = when (character) {
        '0', '٠' -> '۰'
        '1', '١' -> '۱'
        '2', '٢' -> '۲'
        '3', '٣' -> '۳'
        '4', '٤' -> '۴'
        '5', '٥' -> '۵'
        '6', '٦' -> '۶'
        '7', '٧' -> '۷'
        '8', '٨' -> '۸'
        '9', '٩' -> '۹'
        else -> character
    }
}
