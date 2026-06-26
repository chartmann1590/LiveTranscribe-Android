package com.charles.livecaptionn.data.feedback

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "feedback_bug_reports")

class BugReportRepo(private val context: Context) {

    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val listType = Types.newParameterizedType(List::class.java, BugReport::class.java)
    private val adapter = moshi.adapter<List<BugReport>>(listType)

    val bugReports: Flow<List<BugReport>> = context.dataStore.data.map { prefs ->
        val json = prefs[BUG_REPORTS_LIST_KEY] ?: return@map emptyList()
        try {
            adapter.fromJson(json) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun saveBugReport(report: BugReport) {
        context.dataStore.edit { prefs ->
            val current = getReports(prefs)
            val mutable = current.toMutableList()
            val existingIndex = mutable.indexOfFirst { it.number == report.number }
            if (existingIndex >= 0) {
                mutable[existingIndex] = report
            } else {
                mutable.add(report)
            }
            mutable.sortByDescending { it.number }
            prefs[BUG_REPORTS_LIST_KEY] = adapter.toJson(mutable)
        }
    }

    suspend fun updateBugReports(reports: List<BugReport>) {
        context.dataStore.edit { prefs ->
            val sorted = reports.sortedByDescending { it.number }
            prefs[BUG_REPORTS_LIST_KEY] = adapter.toJson(sorted)
        }
    }

    suspend fun getBugReportsList(): List<BugReport> {
        return try {
            context.dataStore.data.map { prefs ->
                val json = prefs[BUG_REPORTS_LIST_KEY] ?: return@map emptyList<BugReport>()
                try {
                    adapter.fromJson(json) ?: emptyList()
                } catch (_: Exception) {
                    emptyList()
                }
            }.first()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun getReports(prefs: androidx.datastore.preferences.core.Preferences): List<BugReport> {
        val json = prefs[BUG_REPORTS_LIST_KEY] ?: return emptyList()
        return try {
            adapter.fromJson(json) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    companion object {
        private val BUG_REPORTS_LIST_KEY = stringPreferencesKey("bug_reports_list")
    }
}
