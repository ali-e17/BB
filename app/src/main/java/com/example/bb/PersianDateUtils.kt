package com.example.bb

object PersianDateUtils {

    data class PersianDate(
        val year: Int,
        val month: Int,
        val day: Int
    )

    data class GregorianDate(
        val year: Int,
        val month: Int,
        val day: Int
    )

    val monthNames = arrayOf(
        "فروردین",
        "اردیبهشت",
        "خرداد",
        "تیر",
        "مرداد",
        "شهریور",
        "مهر",
        "آبان",
        "آذر",
        "دی",
        "بهمن",
        "اسفند"
    )

    fun gregorianToPersian(
        gregorianYear: Int,
        gregorianMonth: Int,
        gregorianDay: Int
    ): PersianDate {

        val cumulativeGregorianDays = intArrayOf(
            0, 31, 59, 90, 120, 151,
            181, 212, 243, 273, 304, 334
        )

        var gy = gregorianYear
        var jy: Int

        if (gy > 1600) {
            jy = 979
            gy -= 1600
        } else {
            jy = 0
            gy -= 621
        }

        val gy2 = if (gregorianMonth > 2) gy + 1 else gy

        var days =
            365 * gy +
                    (gy2 + 3) / 4 -
                    (gy2 + 99) / 100 +
                    (gy2 + 399) / 400 -
                    80 +
                    gregorianDay +
                    cumulativeGregorianDays[gregorianMonth - 1]

        jy += 33 * (days / 12053)
        days %= 12053

        jy += 4 * (days / 1461)
        days %= 1461

        if (days > 365) {
            jy += (days - 1) / 365
            days = (days - 1) % 365
        }

        val jm: Int
        val jd: Int

        if (days < 186) {
            jm = 1 + days / 31
            jd = 1 + days % 31
        } else {
            jm = 7 + (days - 186) / 30
            jd = 1 + (days - 186) % 30
        }

        return PersianDate(jy, jm, jd)
    }

    fun persianToGregorian(
        persianYear: Int,
        persianMonth: Int,
        persianDay: Int
    ): GregorianDate {

        var jy = persianYear
        var gy: Int

        if (jy > 979) {
            gy = 1600
            jy -= 979
        } else {
            gy = 621
        }

        var days =
            365 * jy +
                    (jy / 33) * 8 +
                    ((jy % 33) + 3) / 4 +
                    78 +
                    persianDay

        days += if (persianMonth < 7) {
            (persianMonth - 1) * 31
        } else {
            (persianMonth - 7) * 30 + 186
        }

        gy += 400 * (days / 146097)
        days %= 146097

        if (days > 36524) {
            days--
            gy += 100 * (days / 36524)
            days %= 36524

            if (days >= 365) {
                days++
            }
        }

        gy += 4 * (days / 1461)
        days %= 1461

        if (days > 365) {
            gy += (days - 1) / 365
            days = (days - 1) % 365
        }

        var gd = days + 1

        val gregorianMonthDays = intArrayOf(
            0,
            31,
            if (isGregorianLeapYear(gy)) 29 else 28,
            31,
            30,
            31,
            30,
            31,
            31,
            30,
            31,
            30,
            31
        )

        var gm = 1

        while (
            gm <= 12 &&
            gd > gregorianMonthDays[gm]
        ) {
            gd -= gregorianMonthDays[gm]
            gm++
        }

        return GregorianDate(gy, gm, gd)
    }

    fun monthLength(
        persianYear: Int,
        persianMonth: Int
    ): Int = when (persianMonth) {
        in 1..6 -> 31
        in 7..11 -> 30
        12 -> if (isPersianLeapYear(persianYear)) 30 else 29
        else -> throw IllegalArgumentException("Invalid Persian month: $persianMonth")
    }

    fun isPersianLeapYear(
        persianYear: Int
    ): Boolean {
        val gregorian = persianToGregorian(
            persianYear,
            12,
            30
        )

        val back = gregorianToPersian(
            gregorian.year,
            gregorian.month,
            gregorian.day
        )

        return back.year == persianYear &&
                back.month == 12 &&
                back.day == 30
    }

    fun toPersianDigits(value: String): String {
        val persianDigits = charArrayOf(
            '۰', '۱', '۲', '۳', '۴',
            '۵', '۶', '۷', '۸', '۹'
        )

        return buildString(value.length) {
            value.forEach { char ->
                if (char in '0'..'9') {
                    append(persianDigits[char - '0'])
                } else {
                    append(char)
                }
            }
        }
    }

    private fun isGregorianLeapYear(year: Int): Boolean =
        year % 4 == 0 &&
                (year % 100 != 0 || year % 400 == 0)
}
