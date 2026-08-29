package com.dar.app

/**
 * Minimal Gregorian <-> Ethiopian calendar converter, used only by the Daily
 * Analysis engine to build EC season windows (Meskerem&Tikimt .. Hamle&Nehase+Pagume).
 * There is no UI switch for this yet (that lands with the Calendar setting in
 * General Settings) — the engine can already run in either calendar, it's just
 * always called with GC for now.
 *
 * Algorithm: standard Julian-day-number bridge, accurate for the Gregorian era.
 */
data class EthiopianDate(val day: Int, val month: Int, val year: Int) // month 1..13, 13 = Pagume

object EthiopianCalendar {

    private const val JD_EPOCH_OFFSET_AMETE_MIHRET = 1723856 // JDN of 1 Meskerem, year 1 EC

    fun fromGregorian(day: Int, month: Int, year: Int): EthiopianDate {
        val jdn = gregorianToJdn(day, month, year)
        return jdnToEthiopian(jdn)
    }

    fun toGregorian(ec: EthiopianDate): Triple<Int, Int, Int> {
        val jdn = ethiopianToJdn(ec)
        return jdnToGregorian(jdn)
    }

    private fun gregorianToJdn(day: Int, month: Int, year: Int): Long {
        // Julian Day Number at noon; we only need date-level granularity so this is stable.
        val a = (14 - (month)) / 12
        val y = year + 4800 - a
        val m = month + 12 * a - 3
        return day + ((153L * m + 2) / 5) + 365L * y + (y / 4) - (y / 100) + (y / 400) - 32045
    }

    private fun jdnToGregorian(jdn: Long): Triple<Int, Int, Int> {
        val a = jdn + 32044
        val b = (4 * a + 3) / 146097
        val c = a - (146097 * b) / 4
        val d = (4 * c + 3) / 1461
        val e = c - (1461 * d) / 4
        val m = (5 * e + 2) / 153
        val day = (e - (153 * m + 2) / 5 + 1).toInt()
        val month = (m + 3 - 12 * (m / 10)).toInt()
        val year = (100 * b + d - 4800 + m / 10).toInt()
        return Triple(day, month, year)
    }

    private fun ethiopianToJdn(ec: EthiopianDate): Long {
        val year = ec.year
        return JD_EPOCH_OFFSET_AMETE_MIHRET.toLong() +
            365L * (year - 1) + (year / 4) +
            30L * (ec.month - 1) + (ec.day - 1)
    }

    private fun jdnToEthiopian(jdn: Long): EthiopianDate {
        val r = (jdn - JD_EPOCH_OFFSET_AMETE_MIHRET)
        val year = ((4 * r + 1463) / 1461).toInt()
        val dayOfYear = (r - (365L * (year - 1) + (year - 1) / 4)).toInt() + 1
        val month = ((dayOfYear - 1) / 30) + 1
        val day = dayOfYear - (month - 1) * 30
        return EthiopianDate(day, month, year)
    }
}
