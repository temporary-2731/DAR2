package com.dar.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AnalysisFormDao {

    @Insert
    suspend fun insertForm(form: AnalysisForm): Long

    @Update
    suspend fun updateForm(form: AnalysisForm)

    @Delete
    suspend fun deleteForm(form: AnalysisForm)

    @Query("SELECT * FROM analysis_form WHERE generalActionId = :generalActionId AND periodType = :periodType ORDER BY beginDate ASC")
    fun getFormsFor(generalActionId: Long, periodType: String): Flow<List<AnalysisForm>>

    @Query("SELECT * FROM analysis_form WHERE generalActionId = :generalActionId AND periodType = :periodType ORDER BY beginDate ASC")
    suspend fun getFormsForOnce(generalActionId: Long, periodType: String): List<AnalysisForm>

    @Query("SELECT * FROM analysis_form WHERE id = :formId")
    suspend fun getFormById(formId: Long): AnalysisForm?

    // ---- Per-action vector dimension (shared across all weekday tabs) ----

    @Query("SELECT * FROM analysis_form_action_dimension WHERE formId = :formId AND actionId = :actionId LIMIT 1")
    suspend fun getDimension(formId: Long, actionId: Long): AnalysisFormActionDimension?

    @Query("SELECT * FROM analysis_form_action_dimension WHERE formId = :formId")
    suspend fun getDimensionsForForm(formId: Long): List<AnalysisFormActionDimension>

    @Insert
    suspend fun insertDimension(dimension: AnalysisFormActionDimension): Long

    @Update
    suspend fun updateDimension(dimension: AnalysisFormActionDimension)

    @Query("DELETE FROM analysis_form_action_dimension WHERE formId = :formId")
    suspend fun deleteDimensionsForForm(formId: Long)

    // ---- Per-action, per-weekday parameter vectors ----

    @Insert
    suspend fun insertParam(param: AnalysisFormActionParam): Long

    @Update
    suspend fun updateParam(param: AnalysisFormActionParam)

    @Query("SELECT * FROM analysis_form_action_param WHERE formId = :formId")
    fun getParamsForForm(formId: Long): Flow<List<AnalysisFormActionParam>>

    @Query("SELECT * FROM analysis_form_action_param WHERE formId = :formId")
    suspend fun getParamsForFormOnce(formId: Long): List<AnalysisFormActionParam>

    @Query("SELECT * FROM analysis_form_action_param WHERE formId = :formId AND weekday = :weekday")
    suspend fun getParamsForFormAndWeekday(formId: Long, weekday: Int): List<AnalysisFormActionParam>

    @Query("SELECT * FROM analysis_form_action_param WHERE formId = :formId AND actionId = :actionId AND weekday = :weekday LIMIT 1")
    suspend fun getParam(formId: Long, actionId: Long, weekday: Int): AnalysisFormActionParam?

    @Query("DELETE FROM analysis_form_action_param WHERE formId = :formId")
    suspend fun deleteParamsForForm(formId: Long)
}
