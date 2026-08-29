package com.dar.app

import com.dar.app.data.AnalysisForm
import com.dar.app.data.AppDatabase
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Runs the "Daily" inbuilt analysis for one General Action, one weekday, across
 * the whole life of a DSLA (every season instance the DSLA's forms cover, plus
 * the grand totals across all of them).
 *
 * This intentionally works from the already-computed [com.dar.app.data.RecordingRow]
 * data (actionName match) and the parameter vectors the user typed into the
 * DAILY-period forms via [FormFillActivity]. Gaps between forms are skipped, not
 * zero-filled (confirmed). Missing/undersized recorded data is grouped/padded per
 * [DailyVectorMath.groupToDimension].
 */
object DailyAnalysisEngine {

    private const val DATE_FORMAT = "dd/MM/yyyy"

    suspend fun compute(
        db: AppDatabase,
        dslaId: Long,
        generalActionId: Long,
        weekday: Int,
        calendar: DailySeasonCalendar.CalendarSystem,
        asOfDate: String = today()
    ): DailyAnalysisResult {
        val dsla = db.dslaDao().getById(dslaId)
        val timeEnabled = dsla?.timeEnabled ?: true

        val members = db.generalActionDao().getActionsInGeneral(generalActionId).first()
        val forms = db.analysisFormDao().getFormsForOnce(generalActionId, "DAILY")

        if (members.isEmpty() || forms.isEmpty()) {
            return DailyAnalysisResult(generalActionId, weekday, calendar, emptyList(), emptyList(), emptyList(), emptyList())
        }

        // 1. Collect every covered occurrence date (weekday match) per form, skipping gaps.
        data class Occurrence(val date: String, val form: AnalysisForm)
        val occurrences = mutableListOf<Occurrence>()
        for (form in forms) {
            val effectiveEnd = form.endDate ?: asOfDate
            val dates = DailySeasonCalendar.datesForWeekdayInRange(form.beginDate, effectiveEnd, weekday)
            for (d in dates) occurrences.add(Occurrence(d, form))
        }
        if (occurrences.isEmpty()) {
            return DailyAnalysisResult(generalActionId, weekday, calendar, emptyList(), emptyList(), emptyList(), emptyList())
        }

        // 2. Group occurrences into season instances.
        val bySeasonKey = occurrences.groupBy { DailySeasonCalendar.seasonKeyFor(it.date, calendar) }

        val seasonInstances = mutableListOf<SeasonInstanceResult>()

        for ((seasonKey, seasonOccurrences) in bySeasonKey) {
            if (seasonKey == null) continue
            val sortedOccurrences = seasonOccurrences.sortedBy { parseDate(it.date) }

            val actionResults = mutableListOf<ActionSeasonResult>()

            for (action in members) {
                // Per-date raw pulls + each date's own form dimension.
                data class DayData(
                    val date: String,
                    val dimension: Int,
                    val rawTime: List<Double>,
                    val rawDuration: List<Double>,
                    val rawQuan1: List<Double>,
                    val paramTime: List<Double>,
                    val paramDuration: List<Double>,
                    val paramQuan1: List<Double>
                )

                val perDay = mutableListOf<DayData>()
                for (occ in sortedOccurrences) {
                    val rows = db.recordingDao().getRowsForDate(dslaId, occ.date)
                        .filter { it.actionName == action.name }
                    val dim = db.analysisFormDao().getDimension(occ.form.id, action.id)?.dimension ?: 1
                    val param = db.analysisFormDao().getParam(occ.form.id, action.id, weekday)

                    perDay.add(
                        DayData(
                            date = occ.date,
                            dimension = dim,
                            rawTime = rows.mapNotNull { it.timeValue.toDoubleOrNull() },
                            rawDuration = rows.mapNotNull { it.durationValue.toDoubleOrNull() },
                            rawQuan1 = rows.mapNotNull { it.quan1.toDoubleOrNull() },
                            paramTime = DailyVectorMath.parseVector(param?.timeVector ?: ""),
                            paramDuration = DailyVectorMath.parseVector(param?.durationVector ?: ""),
                            paramQuan1 = DailyVectorMath.parseVector(param?.quan1Vector ?: "")
                        )
                    )
                }

                if (perDay.isEmpty()) continue

                val seasonMaxDim = perDay.maxOf { it.dimension }

                val groupedTime = perDay.map { DailyVectorMath.padToDimension(DailyVectorMath.groupToDimension(it.rawTime, it.dimension), seasonMaxDim) }
                val groupedDuration = perDay.map { DailyVectorMath.padToDimension(DailyVectorMath.groupToDimension(it.rawDuration, it.dimension), seasonMaxDim) }
                val groupedQuan1 = perDay.map { DailyVectorMath.padToDimension(DailyVectorMath.groupToDimension(it.rawQuan1, it.dimension), seasonMaxDim) }

                val paddedParamTime = perDay.map { DailyVectorMath.padToDimension(it.paramTime, seasonMaxDim) }
                val paddedParamDuration = perDay.map { DailyVectorMath.padToDimension(it.paramDuration, seasonMaxDim) }
                val paddedParamQuan1 = perDay.map { DailyVectorMath.padToDimension(it.paramQuan1, seasonMaxDim) }

                val timeStats = if (timeEnabled) buildQuantityStats(groupedTime, paddedParamTime, hasPercentRate = false) else null
                val durationStats = buildQuantityStats(groupedDuration, paddedParamDuration, hasPercentRate = true)
                val quan1Stats = buildQuantityStats(groupedQuan1, paddedParamQuan1, hasPercentRate = true)

                val frequencyAvg = perDay.map { it.rawDuration.size.toDouble() }.average()

                actionResults.add(
                    ActionSeasonResult(
                        actionId = action.id,
                        actionName = action.name,
                        occurrenceDates = perDay.map { it.date },
                        frequencyAvg = frequencyAvg,
                        time = timeStats,
                        duration = durationStats,
                        quan1 = quan1Stats
                    )
                )
            }

            if (actionResults.isEmpty()) continue

            val generalRow = GeneralRowSeasonResult(
                durationTotal = actionResults.sumOf { it.duration.seasonTotal },
                durationParameterTotal = actionResults.sumOf { it.duration.parameterSum },
                durationPercentRate = DailyVectorMath.percentageRate(
                    actionResults.sumOf { it.duration.seasonTotal },
                    actionResults.sumOf { it.duration.parameterSum }
                ),
                quan1Total = actionResults.sumOf { it.quan1.seasonTotal },
                quan1ParameterTotal = actionResults.sumOf { it.quan1.parameterSum },
                quan1PercentRate = DailyVectorMath.percentageRate(
                    actionResults.sumOf { it.quan1.seasonTotal },
                    actionResults.sumOf { it.quan1.parameterSum }
                )
            )

            seasonInstances.add(
                SeasonInstanceResult(
                    seasonLabel = seasonKey.label,
                    seasonYear = seasonKey.seasonYear,
                    weekday = weekday,
                    actions = actionResults,
                    generalRow = generalRow,
                    sortedByAvgStdDevAsc = actionResults.sortedBy { it.duration.avgStdDev }.map { it.actionId },
                    sortedByAvgStdDevDesc = actionResults.sortedByDescending { it.duration.avgStdDev }.map { it.actionId },
                    sortedByFrequencyAsc = actionResults.sortedBy { it.frequencyAvg }.map { it.actionId },
                    sortedByFrequencyDesc = actionResults.sortedByDescending { it.frequencyAvg }.map { it.actionId }
                )
            )
        }

        // 3. Grand totals across every season instance, per action.
        val grandTotals = members.mapNotNull { action ->
            val perSeason = seasonInstances.mapNotNull { si -> si.actions.find { it.actionId == action.id } }
            if (perSeason.isEmpty()) return@mapNotNull null

            val totalOccurrences = perSeason.sumOf { it.occurrenceDates.size }
            val durationGrandTotal = perSeason.sumOf { it.duration.seasonTotal }
            val rates = perSeason.mapNotNull { it.duration.percentRateSeasonAvg }

            ActionGrandTotal(
                actionId = action.id,
                actionName = action.name,
                durationGrandTotal = durationGrandTotal,
                durationGrandAverage = if (totalOccurrences > 0) durationGrandTotal / totalOccurrences else 0.0,
                durationAvgStdDevAcrossSeasons = perSeason.map { it.duration.avgStdDev }.average(),
                durationPercentRateAvg = if (rates.isEmpty()) null else rates.average()
            )
        }

        return DailyAnalysisResult(
            generalActionId = generalActionId,
            weekday = weekday,
            calendar = calendar,
            seasonInstances = seasonInstances.sortedWith(compareBy({ it.seasonYear }, { it.seasonLabel })),
            grandTotals = grandTotals,
            grandSortedByAvgStdDevAsc = grandTotals.sortedBy { it.durationAvgStdDevAcrossSeasons }.map { it.actionId },
            grandSortedByAvgStdDevDesc = grandTotals.sortedByDescending { it.durationAvgStdDevAcrossSeasons }.map { it.actionId }
        )
    }

    private fun buildQuantityStats(
        groupedVectors: List<List<Double>>,
        paddedParamVectors: List<List<Double>>,
        hasPercentRate: Boolean
    ): QuantityStats {
        val mean = DailyVectorMath.meanVector(groupedVectors)
        val stdDev = DailyVectorMath.stdDevVector(groupedVectors, mean)
        val avgStdDev = DailyVectorMath.scalarMean(stdDev)

        val perDaySums = groupedVectors.map { DailyVectorMath.sum(it) }
        val seasonTotal = perDaySums.sum()
        val seasonAverage = if (perDaySums.isNotEmpty()) seasonTotal / perDaySums.size else 0.0

        val paramSums = paddedParamVectors.map { DailyVectorMath.sum(it) }
        val parameterSum = if (paramSums.isNotEmpty()) paramSums.average() else 0.0

        val percentRatePerDay: List<Double?> = if (hasPercentRate) {
            perDaySums.indices.map { i -> DailyVectorMath.percentageRate(perDaySums[i], paramSums.getOrElse(i) { 0.0 }) }
        } else {
            perDaySums.map { null }
        }
        val validRates = percentRatePerDay.filterNotNull()
        val percentRateAvg = if (validRates.isEmpty()) null else validRates.average()

        return QuantityStats(
            meanVector = mean,
            stdDevVector = stdDev,
            avgStdDev = avgStdDev,
            perDaySums = perDaySums,
            seasonTotal = seasonTotal,
            seasonAverage = seasonAverage,
            parameterSum = parameterSum,
            percentRatePerDay = percentRatePerDay,
            percentRateSeasonAvg = percentRateAvg
        )
    }

    private fun parseDate(dateStr: String): Long {
        val sdf = SimpleDateFormat(DATE_FORMAT, Locale.getDefault())
        return sdf.parse(dateStr)?.time ?: 0L
    }

    private fun today(): String {
        val sdf = SimpleDateFormat(DATE_FORMAT, Locale.getDefault())
        return sdf.format(Calendar.getInstance().time)
    }
}
