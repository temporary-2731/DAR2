package com.dar.app

import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.dar.app.data.AppDatabase
import kotlinx.coroutines.launch
import java.util.Locale

class DailyAnalysisResultActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private var dslaId: Long = -1L
    private var generalActionId: Long = -1L
    private var currentWeekday: Int = 1
    private var currentCalendar: DailySeasonCalendar.CalendarSystem = DailySeasonCalendar.CalendarSystem.GC

    private lateinit var tabContainer: LinearLayout
    private lateinit var resultsContainer: LinearLayout

    companion object {
        const val EXTRA_DSLA_ID = "extra_dsla_id"
        const val EXTRA_GENERAL_ACTION_ID = "extra_general_action_id"
        private val WEEKDAY_LABELS = arrayOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_daily_analysis_result)

        dslaId = intent.getLongExtra(EXTRA_DSLA_ID, -1L)
        generalActionId = intent.getLongExtra(EXTRA_GENERAL_ACTION_ID, -1L)
        db = AppDatabase.getInstance(applicationContext)

        tabContainer = findViewById(R.id.daily_result_weekday_tabs)
        resultsContainer = findViewById(R.id.daily_result_container)

        buildWeekdayTabs()

        val calendarBtn = findViewById<Button>(R.id.btn_daily_result_calendar)
        calendarBtn.setOnClickListener {
            currentCalendar = if (currentCalendar == DailySeasonCalendar.CalendarSystem.GC) {
                DailySeasonCalendar.CalendarSystem.EC
            } else {
                DailySeasonCalendar.CalendarSystem.GC
            }
            calendarBtn.text = if (currentCalendar == DailySeasonCalendar.CalendarSystem.GC) {
                getString(R.string.daily_result_calendar_gc)
            } else {
                getString(R.string.daily_result_calendar_ec)
            }
        }

        findViewById<Button>(R.id.btn_daily_result_run).setOnClickListener { runAnalysis() }

        runAnalysis()
    }

    private fun buildWeekdayTabs() {
        tabContainer.removeAllViews()
        for (i in 1..7) {
            val btn = Button(this)
            btn.text = WEEKDAY_LABELS[i - 1]
            btn.setOnClickListener {
                currentWeekday = i
                highlightSelectedTab()
                runAnalysis()
            }
            tabContainer.addView(btn)
        }
        highlightSelectedTab()
    }

    private fun highlightSelectedTab() {
        for (i in 0 until tabContainer.childCount) {
            val btn = tabContainer.getChildAt(i) as Button
            val weekdayOfButton = i + 1
            btn.setBackgroundColor(if (weekdayOfButton == currentWeekday) Color.parseColor("#1565C0") else Color.LTGRAY)
            btn.setTextColor(if (weekdayOfButton == currentWeekday) Color.WHITE else Color.BLACK)
        }
    }

    private fun runAnalysis() {
        resultsContainer.removeAllViews()
        addSectionTitle(getString(R.string.daily_result_loading))

        lifecycleScope.launch {
            val result = DailyAnalysisEngine.compute(db, dslaId, generalActionId, currentWeekday, currentCalendar)
            resultsContainer.removeAllViews()

            if (result.seasonInstances.isEmpty()) {
                addSectionTitle(getString(R.string.daily_result_no_data))
                return@launch
            }

            for (season in result.seasonInstances) {
                addSectionTitle("${season.seasonLabel} ${season.seasonYear}")

                for (action in season.actions) {
                    addBodyLine(
                        getString(
                            R.string.daily_result_action_line,
                            action.actionName,
                            action.frequencyAvg,
                            action.duration.seasonTotal,
                            action.duration.seasonAverage,
                            action.duration.avgStdDev,
                            formatRate(action.duration.percentRateSeasonAvg)
                        )
                    )
                }

                addBodyLine(
                    getString(
                        R.string.daily_result_general_row,
                        season.generalRow.durationTotal,
                        formatRate(season.generalRow.durationPercentRate)
                    )
                )

                addBodyLine(
                    getString(
                        R.string.daily_result_sort_line,
                        namesFor(season.sortedByAvgStdDevAsc, season.actions),
                        namesFor(season.sortedByFrequencyDesc, season.actions)
                    )
                )
            }

            addSectionTitle(getString(R.string.daily_result_grand_title))
            for (grand in result.grandTotals) {
                addBodyLine(
                    getString(
                        R.string.daily_result_grand_line,
                        grand.actionName,
                        grand.durationGrandTotal,
                        grand.durationGrandAverage,
                        grand.durationAvgStdDevAcrossSeasons,
                        formatRate(grand.durationPercentRateAvg)
                    )
                )
            }
        }
    }

    private fun namesFor(ids: List<Long>, actions: List<ActionSeasonResult>): String {
        val byId = actions.associateBy { it.actionId }
        return ids.mapNotNull { byId[it]?.actionName }.joinToString(" > ")
    }

    private fun formatRate(rate: Double?): String =
        if (rate == null) getString(R.string.daily_result_rate_undefined) else String.format(Locale.getDefault(), "%.1f%%", rate)

    private fun addSectionTitle(text: String) {
        val tv = TextView(this)
        tv.text = text
        tv.textSize = 16f
        tv.setTextColor(Color.parseColor("#1565C0"))
        tv.setTypeface(null, android.graphics.Typeface.BOLD)
        tv.setPadding(0, 24, 0, 8)
        resultsContainer.addView(tv)
    }

    private fun addBodyLine(text: String) {
        val tv = TextView(this)
        tv.text = text
        tv.textSize = 13f
        tv.setTextColor(Color.DKGRAY)
        tv.setPadding(0, 2, 0, 2)
        resultsContainer.addView(tv)
    }
}
