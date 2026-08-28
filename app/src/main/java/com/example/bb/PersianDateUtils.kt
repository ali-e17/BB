package com.example.bb

import java.util.Locale

/**
 * ابزار مرکزی تاریخ شمسی برنامه.
 *
 * همه تبدیل‌های تاریخ نمایش داده‌شده در UI باید از این کلاس عبور کنند
 * تا یک منطق واحد برای سال کبیسه و تبدیل میلادی/شمسی وجود داشته باشد.
 */
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

    private val dateRegex =
        Regex("(?<!\\d)(\\d{4})[-/](\\d{1,2})[-/](\\d{1,2})(?!\\d)")

    private val timeRegex =
        Regex("(?:T|\\s)+(\\d{1,2}):(\\d{2})(?::(\\d{2}))?")

    fun gregorianToPersian(
        gregorianYear: Int,
        gregorianMonth: Int,
        gregorianDay: Int
    ): PersianDate {

        require(isValidGregorianDate(gregorianYear, gregorianMonth, gregorianDay)) {
            "Invalid Gregorian date: $gregorianYear-$gregorianMonth-$gregorianDay"
        }

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

        require(isValidPersianDate(persianYear, persianMonth, persianDay)) {
            "Invalid Persian date: $persianYear-$persianMonth-$persianDay"
        }

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
        /*
         * تشخیص کبیسه با Round-trip همان الگوریتم مرکزی انجام می‌شود؛
         * بنابراین انتخاب‌گر تاریخ و نمایش تاریخ هیچ‌وقت دو منطق متفاوت ندارند.
         */
        val candidate = runCatching {
            persianToGregorianUnchecked(
                persianYear,
                12,
                30
            )
        }.getOrNull() ?: return false

        val back = runCatching {
            gregorianToPersian(
                candidate.year,
                candidate.month,
                candidate.day
            )
        }.getOrNull() ?: return false

        return back.year == persianYear &&
            back.month == 12 &&
            back.day == 30
    }

    /**
     * تاریخ API/DB را به yyyy/MM/dd شمسی تبدیل می‌کند.
     * ورودی‌های رایج مثل yyyy-MM-dd و yyyy/MM/dd پشتیبانی می‌شوند.
     * اگر ورودی از قبل شمسی باشد دوباره تبدیل نمی‌شود.
     */
    fun formatDate(
        value: String?,
        fallback: String = "—"
    ): String {
        val normalized = normalizeDigitsToEnglish(value.orEmpty()).trim()
        if (normalized.isBlank()) return fallback

        val match = dateRegex.find(normalized) ?: return normalized
        val year = match.groupValues[1].toIntOrNull() ?: return normalized
        val month = match.groupValues[2].toIntOrNull() ?: return normalized
        val day = match.groupValues[3].toIntOrNull() ?: return normalized

        return when {
            looksLikePersianYear(year) && isValidPersianDate(year, month, day) ->
                String.format(Locale.US, "%04d/%02d/%02d", year, month, day)

            isValidGregorianDate(year, month, day) -> {
                val persian = gregorianToPersian(year, month, day)
                String.format(
                    Locale.US,
                    "%04d/%02d/%02d",
                    persian.year,
                    persian.month,
                    persian.day
                )
            }

            else -> normalized
        }
    }

    /**
     * تاریخ و ساعت API/DB را به تاریخ شمسی + ساعت تبدیل می‌کند.
     * ساعت DB تغییر timezone داده نمی‌شود چون timestampهای پروژه بر اساس ساعت ایران ذخیره می‌شوند.
     */
    fun formatDateTime(
        value: String?,
        fallback: String = "—"
    ): String {
        val normalized = normalizeDigitsToEnglish(value.orEmpty()).trim()
        if (normalized.isBlank()) return fallback

        val dateMatch = dateRegex.find(normalized) ?: return normalized
        val date = formatDate(dateMatch.value, fallback)

        val timeMatch =
            timeRegex.find(normalized, dateMatch.range.last + 1)

        if (timeMatch == null) {
            return date
        }

        val hour = timeMatch.groupValues[1].toIntOrNull()
        val minute = timeMatch.groupValues[2].toIntOrNull()

        if (
            hour == null ||
            minute == null ||
            hour !in 0..23 ||
            minute !in 0..59
        ) {
            return date
        }

        return String.format(
            Locale.US,
            "%s %02d:%02d",
            date,
            hour,
            minute
        )
    }

    /**
     * تمام تاریخ‌های میلادی واضح داخل یک متن را شمسی می‌کند.
     * برای مثال متن اعلان حضور و غیاب قدیمی:
     * 2026/08/28 -> 1405/06/06
     *
     * تاریخ‌هایی که از قبل شمسی هستند دست‌نخورده می‌مانند.
     */
    fun convertGregorianDatesInText(
        value: CharSequence?
    ): String {
        val normalized = normalizeDigitsToEnglish(value?.toString().orEmpty())
        if (normalized.isBlank()) return normalized

        return dateRegex.replace(normalized) { match ->
            val year = match.groupValues[1].toIntOrNull()
            val month = match.groupValues[2].toIntOrNull()
            val day = match.groupValues[3].toIntOrNull()

            if (
                year != null &&
                month != null &&
                day != null &&
                !looksLikePersianYear(year) &&
                isValidGregorianDate(year, month, day)
            ) {
                val persian = gregorianToPersian(year, month, day)
                String.format(
                    Locale.US,
                    "%04d/%02d/%02d",
                    persian.year,
                    persian.month,
                    persian.day
                )
            } else {
                match.value
            }
        }
    }

    fun toPersianDigits(value: String): String {
        return buildString(value.length) {
            value.forEach { char ->
                append(UiTextFormatter.toPersianDigit(char))
            }
        }
    }

    fun normalizeDigitsToEnglish(value: String): String =
        UiTextFormatter.normalizeEnglishDigits(value)

    private fun looksLikePersianYear(year: Int): Boolean =
        year in 1200..1699

    private fun isValidPersianDate(
        year: Int,
        month: Int,
        day: Int
    ): Boolean {
        if (year <= 0 || month !in 1..12 || day <= 0) return false

        return runCatching {
            day <= when (month) {
                in 1..6 -> 31
                in 7..11 -> 30
                12 -> if (isPersianLeapYearInternal(year)) 30 else 29
                else -> 0
            }
        }.getOrDefault(false)
    }

    private fun isPersianLeapYearInternal(
        persianYear: Int
    ): Boolean {
        val candidate = runCatching {
            persianToGregorianUnchecked(persianYear, 12, 30)
        }.getOrNull() ?: return false

        val back = runCatching {
            gregorianToPersian(
                candidate.year,
                candidate.month,
                candidate.day
            )
        }.getOrNull() ?: return false

        return back.year == persianYear &&
            back.month == 12 &&
            back.day == 30
    }

    /**
     * نسخه داخلی تبدیل برای بررسی کبیسه؛
     * validation شمسی را دوباره فراخوانی نمی‌کند تا recursion ایجاد نشود.
     */
    private fun persianToGregorianUnchecked(
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
            if (days >= 365) days++
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
        while (gm <= 12 && gd > gregorianMonthDays[gm]) {
            gd -= gregorianMonthDays[gm]
            gm++
        }

        return GregorianDate(gy, gm, gd)
    }

    private fun isValidGregorianDate(
        year: Int,
        month: Int,
        day: Int
    ): Boolean {
        if (year <= 0 || month !in 1..12 || day <= 0) return false

        val monthLength = when (month) {
            1, 3, 5, 7, 8, 10, 12 -> 31
            4, 6, 9, 11 -> 30
            2 -> if (isGregorianLeapYear(year)) 29 else 28
            else -> return false
        }

        return day <= monthLength
    }

    private fun isGregorianLeapYear(year: Int): Boolean =
        year % 4 == 0 &&
            (year % 100 != 0 || year % 400 == 0)
}
