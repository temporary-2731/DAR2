package com.dar.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * The vector dimension the user picked for one Action inside one Analysis Form.
 * Per spec: chosen once (by touching the dimension cell), longest 10, and then
 * reused for every weekday tab / every column (time, duration, quan1..3) of
 * that action inside this form. Kept as its own table so it is never duplicated
 * or allowed to drift across the 7 weekday rows in [AnalysisFormActionParam].
 */
@Entity(tableName = "analysis_form_action_dimension")
data class AnalysisFormActionDimension(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val formId: Long,
    val actionId: Long,
    val dimension: Int = 1 // 1..10
)
