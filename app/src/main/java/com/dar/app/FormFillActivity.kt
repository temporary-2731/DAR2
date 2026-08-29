package com.dar.app

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.dar.app.data.AnalysisFormActionDimension
import com.dar.app.data.AnalysisFormActionParam
import com.dar.app.data.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class FormFillActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private var dslaId: Long = -1L
    private var formId: Long = -1L
    private var currentWeekday: Int = 1 // 1=Mon..7=Sun; 0 for non-daily periods
    private var timeEnabled: Boolean = true
    private var isDaily: Boolean = true

    private lateinit var actionsContainer: LinearLayout
    private lateinit var weekdayTabContainer: LinearLayout
    private val cardViews = mutableMapOf<Long, android.view.View>() // actionId -> inflated card

    companion object {
        const val EXTRA_DSLA_ID = "extra_dsla_id"
        const val EXTRA_FORM_ID = "extra_form_id"
        private val WEEKDAY_LABELS = arrayOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_form_fill)

        dslaId = intent.getLongExtra(EXTRA_DSLA_ID, -1L)
        formId = intent.getLongExtra(EXTRA_FORM_ID, -1L)
        db = AppDatabase.getInstance(applicationContext)

        actionsContainer = findViewById(R.id.form_fill_actions_container)
        weekdayTabContainer = findViewById(R.id.weekday_tab_container)
        findViewById<Button>(R.id.btn_form_fill_save).setOnClickListener { saveCurrentWeekday(showToast = true) }

        lifecycleScope.launch { setup() }
    }

    private suspend fun setup() {
        val form = db.analysisFormDao().getFormById(formId) ?: run {
            Toast.makeText(this, R.string.form_fill_not_found, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        val dsla = db.dslaDao().getById(dslaId)
        timeEnabled = dsla?.timeEnabled ?: true
        isDaily = form.periodType == "DAILY"

        val rangeLabel = findViewById<TextView>(R.id.form_fill_range_label)
        rangeLabel.text = if (form.endDate != null) {
            getString(R.string.form_range_format, form.beginDate, form.endDate)
        } else {
            getString(R.string.form_range_ongoing, form.beginDate)
        }

        if (isDaily) {
            findViewById<android.widget.HorizontalScrollView>(R.id.weekday_tab_scroll).visibility = android.view.View.VISIBLE
            buildWeekdayTabs()
        } else {
            currentWeekday = 0
        }

        val members = db.generalActionDao().getActionsInGeneral(form.generalActionId).first()
        buildActionCards(members.map { it.id to it.name })
        loadWeekday(currentWeekday)
    }

    private fun buildWeekdayTabs() {
        weekdayTabContainer.removeAllViews()
        for (i in 1..7) {
            val btn = Button(this)
            btn.text = WEEKDAY_LABELS[i - 1]
            btn.setOnClickListener { switchWeekday(i) }
            weekdayTabContainer.addView(btn)
        }
        highlightSelectedTab()
    }

    private fun highlightSelectedTab() {
        for (i in 0 until weekdayTabContainer.childCount) {
            val btn = weekdayTabContainer.getChildAt(i) as Button
            val weekdayOfButton = i + 1
            btn.setBackgroundColor(if (weekdayOfButton == currentWeekday) Color.parseColor("#1565C0") else Color.LTGRAY)
            btn.setTextColor(if (weekdayOfButton == currentWeekday) Color.WHITE else Color.BLACK)
        }
    }

    private fun switchWeekday(newWeekday: Int) {
        if (newWeekday == currentWeekday) return
        // Auto-save what's on screen before switching tabs, so nothing typed is lost.
        saveCurrentWeekday(showToast = false)
        currentWeekday = newWeekday
        highlightSelectedTab()
        lifecycleScope.launch { loadWeekday(currentWeekday) }
    }

    private fun buildActionCards(actions: List<Pair<Long, String>>) {
        actionsContainer.removeAllViews()
        cardViews.clear()
        for ((actionId, name) in actions) {
            val card = LayoutInflater.from(this).inflate(R.layout.item_form_fill_action_card, actionsContainer, false)
            card.findViewById<TextView>(R.id.card_action_name).text = name

            val timeField = card.findViewById<EditText>(R.id.card_time)
            val quan2Field = card.findViewById<EditText>(R.id.card_quan2)
            val quan3Field = card.findViewById<EditText>(R.id.card_quan3)

            timeField.visibility = if (timeEnabled) android.view.View.VISIBLE else android.view.View.GONE
            quan2Field.visibility = if (timeEnabled) android.view.View.GONE else android.view.View.VISIBLE
            quan3Field.visibility = if (timeEnabled) android.view.View.GONE else android.view.View.VISIBLE

            cardViews[actionId] = card
            actionsContainer.addView(card)
        }
    }

    private suspend fun loadWeekday(weekday: Int) {
        for ((actionId, card) in cardViews) {
            val dimension = db.analysisFormDao().getDimension(formId, actionId)?.dimension ?: 1
            val param = db.analysisFormDao().getParam(formId, actionId, weekday)

            card.findViewById<EditText>(R.id.card_dimension).setText(dimension.toString())
            card.findViewById<EditText>(R.id.card_time).setText(param?.timeVector ?: "")
            card.findViewById<EditText>(R.id.card_duration).setText(param?.durationVector ?: "")
            card.findViewById<EditText>(R.id.card_quan1).setText(param?.quan1Vector ?: "")
            card.findViewById<EditText>(R.id.card_quan2).setText(param?.quan2Vector ?: "")
            card.findViewById<EditText>(R.id.card_quan3).setText(param?.quan3Vector ?: "")
        }
    }

    private fun saveCurrentWeekday(showToast: Boolean) {
        val weekday = currentWeekday
        val snapshot = cardViews.mapValues { (_, card) ->
            FieldSnapshot(
                dimension = card.findViewById<EditText>(R.id.card_dimension).text.toString().toIntOrNull()?.coerceIn(1, 10) ?: 1,
                time = card.findViewById<EditText>(R.id.card_time).text.toString(),
                duration = card.findViewById<EditText>(R.id.card_duration).text.toString(),
                quan1 = card.findViewById<EditText>(R.id.card_quan1).text.toString(),
                quan2 = card.findViewById<EditText>(R.id.card_quan2).text.toString(),
                quan3 = card.findViewById<EditText>(R.id.card_quan3).text.toString()
            )
        }

        lifecycleScope.launch {
            for ((actionId, s) in snapshot) {
                // Dimension is shared across all 7 weekday tabs for this action — upsert once.
                val existingDim = db.analysisFormDao().getDimension(formId, actionId)
                if (existingDim == null) {
                    db.analysisFormDao().insertDimension(AnalysisFormActionDimension(formId = formId, actionId = actionId, dimension = s.dimension))
                } else if (existingDim.dimension != s.dimension) {
                    db.analysisFormDao().updateDimension(existingDim.copy(dimension = s.dimension))
                }

                val existingParam = db.analysisFormDao().getParam(formId, actionId, weekday)
                val newParam = AnalysisFormActionParam(
                    id = existingParam?.id ?: 0,
                    formId = formId,
                    actionId = actionId,
                    weekday = weekday,
                    timeVector = s.time,
                    durationVector = s.duration,
                    quan1Vector = s.quan1,
                    quan2Vector = s.quan2,
                    quan3Vector = s.quan3
                )
                if (existingParam == null) {
                    db.analysisFormDao().insertParam(newParam)
                } else {
                    db.analysisFormDao().updateParam(newParam)
                }
            }
            if (showToast) {
                Toast.makeText(this@FormFillActivity, R.string.form_fill_saved, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // Draft-safety net, matching Recording section's "save as you go" behavior.
        saveCurrentWeekday(showToast = false)
    }

    private data class FieldSnapshot(
        val dimension: Int,
        val time: String,
        val duration: String,
        val quan1: String,
        val quan2: String,
        val quan3: String
    )
}
