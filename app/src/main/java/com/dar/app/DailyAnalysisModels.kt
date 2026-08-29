package com.dar.app

/** A single quantity's (time / duration / quan1) statistics for one action, one season instance. */
data class QuantityStats(
    val meanVector: List<Double>,
    val stdDevVector: List<Double>,
    val avgStdDev: Double,        // scalar mean of stdDevVector's own components
    val perDaySums: List<Double>, // one scalar per weekday-occurrence (e.g. per Monday) in this season
    val seasonTotal: Double,      // sum of perDaySums
    val seasonAverage: Double,    // seasonTotal / occurrence count
    val parameterSum: Double,     // reference target, summed from the form's parameter vector (padded to the season's max dimension)
    val percentRatePerDay: List<Double?>, // null where parameterSum == 0 for that day's form
    val percentRateSeasonAvg: Double?     // average of the non-null per-day rates, null if all were undefined
)

data class ActionSeasonResult(
    val actionId: Long,
    val actionName: String,
    val occurrenceDates: List<String>, // the concrete Monday/Tuesday/... dates covered by a form in this season instance
    val frequencyAvg: Double,          // average raw-record-count (r) per occurrence
    val time: QuantityStats?,          // null when the DSLA has time disabled
    val duration: QuantityStats,
    val quan1: QuantityStats
)

data class GeneralRowSeasonResult(
    val durationTotal: Double,
    val durationParameterTotal: Double,
    val durationPercentRate: Double?,
    val quan1Total: Double,
    val quan1ParameterTotal: Double,
    val quan1PercentRate: Double?
)

data class SeasonInstanceResult(
    val seasonLabel: String,   // e.g. "May-Jun"
    val seasonYear: Int,
    val weekday: Int,          // 1=Mon..7=Sun
    val actions: List<ActionSeasonResult>,
    val generalRow: GeneralRowSeasonResult,
    /** actionId ordering, per the sorting engine, for this season instance only. */
    val sortedByAvgStdDevAsc: List<Long>,
    val sortedByAvgStdDevDesc: List<Long>,
    val sortedByFrequencyAsc: List<Long>,
    val sortedByFrequencyDesc: List<Long>
)

data class ActionGrandTotal(
    val actionId: Long,
    val actionName: String,
    val durationGrandTotal: Double,
    val durationGrandAverage: Double,     // per occurrence, across the whole DSLA lifetime
    val durationAvgStdDevAcrossSeasons: Double,
    val durationPercentRateAvg: Double?
)

/** Everything the engine produced for one General Action + one weekday, across the DSLA's whole lifetime. */
data class DailyAnalysisResult(
    val generalActionId: Long,
    val weekday: Int,
    val calendar: DailySeasonCalendar.CalendarSystem,
    val seasonInstances: List<SeasonInstanceResult>,
    val grandTotals: List<ActionGrandTotal>,
    val grandSortedByAvgStdDevAsc: List<Long>,
    val grandSortedByAvgStdDevDesc: List<Long>
)
