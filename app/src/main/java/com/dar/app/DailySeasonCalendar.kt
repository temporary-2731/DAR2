package com.dar.app

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Defines the "season" groupings the Daily analysis engine works on:
 *  GC: Jan&Feb, Mar&Apr, May&Jun, Jul&Aug, Sep&Oct, Nov&Dec
 *  EC: Meskerem&Tikimt, Hidar&Tahsas, Tir&Yekatit, Megabit&Miazia, Ginbot&Sene,
 *      Hamle&Nehase+Pagume (Pagume merged into the last group, per spec)
 *
 * A "season instance" is one concrete occurrence of a season within a specific
 * year of the DSLA's lifetime, e.g. "Jan-Feb 2026" and "Jan-Feb 2027" are two
 * different instances of the same season.
 */
object DailySeasonCalendar {

    enum class CalendarSystem { GC, EC }

    val GC_SEASON_LABELS = listOf("Jan-Feb", "Mar-Apr", "May-Jun", "Jul-Aug", "Sep-Oct", "Nov-Dec")
    val EC_SEASON_LABELS = listOf(
        "Meskerem-Tikimt", "Hidar-Tahsas", "Tir-Yekatit",
        "Megabit-Miazia", "Ginbot-Sene", "Hamle-Nehase-Pagume"
    )

    private const val DATE_FORMAT = "dd/MM/yyyy"

    data class SeasonKey(val seasonIndex: Int, val seasonYear: Int, val calendar: CalendarSystem) {
        val label: String
            get() = if (calendar == CalendarSystem.GC) GC_SEASON_LABELS[seasonIndex] else EC_SEASON_LABELS[seasonIndex]
    }

    /** 1=Monday .. 7=Sunday (ISO order), matching the "seven days" choice in the form UI. */
    fun isoWeekday(dateStr: String): Int? {
        val sdf = SimpleDateFormat(DATE_FORMAT, Locale.getDefault())
        val date = sdf.parse(dateStr) ?: return null
        val cal = Calendar.getInstance()
        cal.time = date
        val javaDow = cal.get(Calendar.DAY_OF_WEEK) // Calendar.SUNDAY=1 .. SATURDAY=7
        return ((javaDow + 5) % 7) + 1 // remap so Monday=1 .. Sunday=7
    }

    fun seasonKeyFor(dateStr: String, calendar: CalendarSystem): SeasonKey? {
        val sdf = SimpleDateFormat(DATE_FORMAT, Locale.getDefault())
        val date = sdf.parse(dateStr) ?: return null
        val cal = Calendar.getInstance()
        cal.time = date
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val month = cal.get(Calendar.MONTH) + 1
        val year = cal.get(Calendar.YEAR)

        return when (calendar) {
            CalendarSystem.GC -> SeasonKey((month - 1) / 2, year, CalendarSystem.GC)
            CalendarSystem.EC -> {
                val ec = EthiopianCalendar.fromGregorian(day, month, year)
                val idx = if (ec.month >= 11) 5 else (ec.month - 1) / 2
                SeasonKey(idx, ec.year, CalendarSystem.EC)
            }
        }
    }

    /** All calendar dates (DD/MM/YYYY) between [start] and [end] inclusive that fall on [weekday] (1=Mon..7=Sun). */
    fun datesForWeekdayInRange(start: String, end: String, weekday: Int): List<String> {
        val sdf = SimpleDateFormat(DATE_FORMAT, Locale.getDefault())
        val startDate = sdf.parse(start) ?: return emptyList()
        val endDate = sdf.parse(end) ?: return emptyList()
        val cal = Calendar.getInstance()
        cal.time = startDate

        val results = mutableListOf<String>()
        while (!cal.time.after(endDate)) {
            val dateStr = sdf.format(cal.time)
            if (isoWeekday(dateStr) == weekday) {
                results.add(dateStr)
            }
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
        return results
    }
}
