package com.dar.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One row per (Action, weekday) inside a Daily Analysis Form, holding the parameter
 * vectors the user typed for that weekday tab: initial time, duration, quan1
 * (time enabled) or quan1/quan2/quan3 (time disabled).
 *
 * weekday: 1=Monday .. 7=Sunday for DAILY forms. Non-daily period types (Weekly,
 * Monthly, Yearly, All-time) don't have weekday tabs yet, so they store a single
 * row with weekday = 0.
 *
 * The vector dimension itself is NOT stored here anymore — see
 * [AnalysisFormActionDimension], which is shared across all 7 weekday rows of the
 * same action so the dimension can never drift between tabs.
 *
 * Vectors are stored as comma-separated decimal strings, e.g. "3,9,16".
 */
@Entity(tableName = "analysis_form_action_param")
data class AnalysisFormActionParam(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val formId: Long,
    val actionId: Long,
    val weekday: Int = 0,
    val timeVector: String = "",
    val durationVector: String = "",
    val quan1Vector: String = "",
    val quan2Vector: String = "",
    val quan3Vector: String = ""
)
