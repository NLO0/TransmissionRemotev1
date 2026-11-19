package app.transmissionremote.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "transmission_remote")

const val DEFAULT_TRACKER_SOURCE =
    "https://raw.githubusercontent.com/ngosang/trackerslist/master/trackers_best.txt"

data class AppPrefs(
    val pollSeconds: Int = 3,
    val notify: Boolean = true,
    val trackerAuto: Boolean = false,
    val trackerSource: String = DEFAULT_TRACKER_SOURCE,
    /** свои источники: URL по одному в строке; объединяются с trackerSource */
    val trackerExtra: String = "",
    val trackerIntervalHours: Int = 12,
    /** 0 — только незавершённые торренты, 1 — все публичные */
    val trackerScope: Int = 0,
    /** максимум трекеров на торрент (0 — без ограничения) */
    val trackerMax: Int = 30,
    val trackerLast: String = "",
)

class AppStore(private val context: Context) {
    private val kServers = stringPreferencesKey("servers")
    private val kPoll = intPreferencesKey("poll_seconds")
    private val kNotify = booleanPreferencesKey("notify")
    private val kCompleted = stringSetPreferencesKey("completed")
    private val kSeeded = booleanPreferencesKey("completed_seeded")
    private val kTrAuto = booleanPreferencesKey("tr_auto")
    private val kTrSource = stringPreferencesKey("tr_source")
    private val kTrExtra = stringPreferencesKey("tr_extra")
    private val kTrInterval = intPreferencesKey("tr_interval_h")
    private val kTrScope = intPreferencesKey("tr_scope")
    private val kTrMax = intPreferencesKey("tr_max")
    private val kTrLast = stringPreferencesKey("tr_last")
    private val kTrCache = stringPreferencesKey("tr_cache")

    val servers: Flow<List<Server>> = context.dataStore.data.map { p ->
        val arr = JSONArray(p[kServers] ?: "[]")
        (0 until arr.length()).map { Server.fromJson(arr.getJSONObject(it)) }
    }

    val prefs: Flow<AppPrefs> = context.dataStore.data.map { p ->
        AppPrefs(
            pollSeconds = p[kPoll] ?: 3,
            notify = p[kNotify] ?: true,
            trackerAuto = p[kTrAuto] ?: false,
            trackerSource = p[kTrSource] ?: DEFAULT_TRACKER_SOURCE,
            trackerExtra = p[kTrExtra] ?: "",
            trackerIntervalHours = p[kTrInterval] ?: 12,
            trackerScope = p[kTrScope] ?: 0,
            trackerMax = p[kTrMax] ?: 30,
            trackerLast = p[kTrLast] ?: "",
        )
    }

    suspend fun saveServers(list: List<Server>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        context.dataStore.edit { it[kServers] = arr.toString() }
    }

    suspend fun savePrefs(p: AppPrefs) {
        context.dataStore.edit {
            it[kPoll] = p.pollSeconds
            it[kNotify] = p.notify
            it[kTrAuto] = p.trackerAuto
            it[kTrSource] = p.trackerSource
            it[kTrExtra] = p.trackerExtra
            it[kTrInterval] = p.trackerIntervalHours
            it[kTrScope] = p.trackerScope
            it[kTrMax] = p.trackerMax
        }
    }

    suspend fun saveTrackerLast(text: String) {
        context.dataStore.edit { it[kTrLast] = text }
    }

    suspend fun trackerCache(): String = context.dataStore.data.first()[kTrCache] ?: ""

    suspend fun saveTrackerCache(text: String) {
        context.dataStore.edit { it[kTrCache] = text }
    }

    /** null — ещё ни разу не запоминали (первый запуск воркера). */
    suspend fun completedKnown(): Set<String>? {
        val p = context.dataStore.data.first()
        return if (p[kSeeded] == true) (p[kCompleted] ?: emptySet()) else null
    }

    suspend fun saveCompleted(set: Set<String>) {
        context.dataStore.edit {
            it[kCompleted] = set
            it[kSeeded] = true
        }
    }
}
