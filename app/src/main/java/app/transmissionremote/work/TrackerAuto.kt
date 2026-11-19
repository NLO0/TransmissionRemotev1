package app.transmissionremote.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.transmissionremote.TransmissionApp
import app.transmissionremote.data.AppPrefs
import app.transmissionremote.data.DEFAULT_TRACKER_SOURCE
import app.transmissionremote.data.TransmissionApi
import app.transmissionremote.util.formatDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Подтягивание публичных трекеров из внешнего списка и добавление их в торренты. */
object TrackerAuto {
    private const val BASE = "https://raw.githubusercontent.com/ngosang/trackerslist/master/"
    const val CUSTOM = "custom"
    private const val WORK_NAME = "tracker-auto"

    val PRESETS: List<Pair<String, String>> = listOf(
        DEFAULT_TRACKER_SOURCE to "Лучшие, ~20 шт. (ngosang)",
        BASE + "trackers_all.txt" to "Все (ngosang)",
        BASE + "trackers_all_udp.txt" to "Только UDP (ngosang)",
        BASE + "trackers_all_http.txt" to "Только HTTP (ngosang)",
        "https://cdn.jsdelivr.net/gh/ngosang/trackerslist@master/trackers_best.txt" to "Лучшие — зеркало jsDelivr",
    )

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val trackerRe = Regex("(?i)\\b(?:udp|https?|wss?)://[^\\s\"'<>,;()\\[\\]{}]+")
    private val blobRe = Regex("^https?://github\\.com/([^/]+)/([^/]+)/blob/(.+)$", RegexOption.IGNORE_CASE)

    private fun norm(u: String) = u.trim().lowercase().trimEnd('/')

    /** Находит в произвольном тексте (список, markdown, JSON, HTML) announce-URL без дубликатов. */
    fun parse(text: String): List<String> =
        trackerRe.findAll(text).map { it.value.trimEnd('.') }.distinctBy { norm(it) }.toList()

    /** Ссылку github.com/…/blob/… превращаем в raw, чтобы скачался сам файл, а не страница. */
    private fun toRaw(u: String): String {
        val m = blobRe.matchEntire(u.trim()) ?: return u.trim()
        return "https://raw.githubusercontent.com/${m.groupValues[1]}/${m.groupValues[2]}/${m.groupValues[3]}"
    }

    private fun isDirectTracker(u: String) =
        u.startsWith("udp://", true) || u.startsWith("ws://", true) || u.startsWith("wss://", true)

    suspend fun download(url: String): List<String> = withContext(Dispatchers.IO) {
        http.newCall(Request.Builder().url(toRaw(url)).build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            val body = r.body?.string().orEmpty()
            val all = parse(body)
            if (body.trimStart().startsWith("<")) {
                // HTML-страница: оставляем только похожее на трекеры
                all.filter { it.startsWith("udp", true) || it.startsWith("ws", true) || it.contains("announce", true) }
            } else {
                all
            }
        }
    }

    data class Sources(val list: List<String>, val cached: Boolean, val report: String)

    private fun label(u: String): String =
        u.substringAfter("://").substringBefore('/') + "/…/" + u.trimEnd('/').substringAfterLast('/')

    /**
     * Объединяет готовый источник и все свои URL (свои идут первыми — они в приоритете при лимите).
     * В «свои источники» можно писать и ссылки на списки, и сами трекеры (udp://…).
     * Если ни один источник не ответил — берётся последний сохранённый список.
     */
    suspend fun sourceList(app: TransmissionApp): Sources {
        val store = app.store
        val p = store.prefs.first()
        val urls = (p.trackerExtra.split(Regex("[\\s,;]+")) + p.trackerSource)
            .map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val merged = LinkedHashMap<String, String>()
        val report = mutableListOf<String>()
        var ok = 0
        if (urls.isEmpty()) report += "Источники не заданы"
        for (u in urls) {
            if (isDirectTracker(u)) {
                merged.putIfAbsent(norm(u), u)
                report += "• $u: добавлен напрямую"
                ok++
                continue
            }
            try {
                val got = download(u)
                if (got.isEmpty() && u.contains("announce", true)) {
                    merged.putIfAbsent(norm(u), u)
                    report += "• ${label(u)}: добавлен как трекер"
                } else {
                    got.forEach { merged.putIfAbsent(norm(it), it) }
                    report += "• ${label(u)}: найдено ${got.size}"
                }
                ok++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                report += "• ${label(u)}: ошибка (${e.message ?: e.javaClass.simpleName})"
            }
        }
        if (ok == 0 || merged.isEmpty()) return Sources(parse(store.trackerCache()), true, report.joinToString("\n"))
        val list = merged.values.toList()
        store.saveTrackerCache(list.joinToString("\n"))
        return Sources(list, false, report.joinToString("\n"))
    }

    /**
     * Добавляет недостающие трекеры. Приватные торренты и торренты без метаданных (magnet) пропускаются.
     * Возвращает (сколько торрентов изменено, сколько трекеров добавлено суммарно).
     */
    suspend fun applyList(
        api: TransmissionApi,
        list: List<String>,
        onlyIncomplete: Boolean,
        max: Int,
        only: Set<Int>? = null,
    ): Pair<Int, Int> {
        val groups = HashMap<List<String>, MutableList<Int>>()
        for (t in api.trackerInfo()) {
            if (only != null && t.id !in only) continue
            if (t.isPrivate || !t.hasMetadata) continue
            if (onlyIncomplete && t.complete) continue
            val have = t.announces.map { norm(it) }.toSet()
            var missing = list.filter { norm(it) !in have }
            if (max > 0) missing = missing.take((max - t.announces.size).coerceAtLeast(0))
            if (missing.isNotEmpty()) groups.getOrPut(missing) { mutableListOf() }.add(t.id)
        }
        var touched = 0
        var added = 0
        for ((urls, ids) in groups) {
            api.addTrackers(ids, urls)
            touched += ids.size
            added += urls.size * ids.size
        }
        return touched to added
    }

    /** Полный прогон по всем серверам по текущим настройкам. Возвращает итоговое сообщение. */
    suspend fun run(app: TransmissionApp): String {
        val store = app.store
        val prefs = store.prefs.first()
        val src = sourceList(app)
        val list = src.list
        val cached = src.cached
        if (list.isEmpty()) return "Не удалось получить список трекеров\n" + src.report
        var touched = 0
        var added = 0
        var failed = 0
        for (s in store.servers.first()) {
            try {
                val (t, a) = applyList(app.clients.api(s), list, prefs.trackerScope == 0, prefs.trackerMax)
                touched += t
                added += a
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed++
            }
        }
        val msg = "Добавлено трекеров: $added в $touched торрентах" +
            (if (cached) " (список из кэша)" else "") +
            (if (failed > 0) "; серверов с ошибкой: $failed" else "") +
            "\n" + src.report
        store.saveTrackerLast("${formatDate(System.currentTimeMillis() / 1000)} — $msg")
        return msg
    }

    fun schedule(ctx: Context, p: AppPrefs) {
        val wm = WorkManager.getInstance(ctx)
        if (!p.trackerAuto) {
            wm.cancelUniqueWork(WORK_NAME)
            return
        }
        val req = PeriodicWorkRequestBuilder<TrackerWorker>(p.trackerIntervalHours.toLong(), TimeUnit.HOURS).build()
        wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, req)
    }
}

class TrackerWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        TrackerAuto.run(applicationContext as TransmissionApp)
        return Result.success()
    }
}
