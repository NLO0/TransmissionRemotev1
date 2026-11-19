package app.transmissionremote.ui

import android.app.Application
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.transmissionremote.TransmissionApp
import app.transmissionremote.data.AppPrefs
import app.transmissionremote.data.RpcClient
import app.transmissionremote.data.Server
import app.transmissionremote.data.Torrent
import app.transmissionremote.data.TorrentDetail
import app.transmissionremote.data.TransmissionApi
import app.transmissionremote.work.TrackerAuto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class PendingAdd(val magnet: String? = null, val uris: List<Uri> = emptyList())

data class SessionUi(
    val loading: Boolean = true,
    val json: JSONObject? = null,
    val error: String? = null,
    val version: Int = 0,
)

data class ServerStats(
    val server: Server,
    val error: String?,
    val session: JSONObject?,
    val stats: JSONObject?,
    val free: Long,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val tapp = app as TransmissionApp
    private val store = tapp.store

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private val _detail = MutableStateFlow<TorrentDetail?>(null)
    val detail: StateFlow<TorrentDetail?> = _detail.asStateFlow()

    private val _session = MutableStateFlow(SessionUi())
    val session: StateFlow<SessionUi> = _session.asStateFlow()

    private val _stats = MutableStateFlow<List<ServerStats>>(emptyList())
    val stats: StateFlow<List<ServerStats>> = _stats.asStateFlow()

    val pending = MutableStateFlow<PendingAdd?>(null)

    private var detailTarget: Pair<String, Int>? = null
    private var sessionVersion = 0
    private var lastSched: Pair<Boolean, Int>? = null
    private var seenKeys: Set<String>? = null
    private var lastAutoRun = 0L
    private var autoBusy = false

    init {
        viewModelScope.launch {
            store.servers.collect { s ->
                _ui.update {
                    it.copy(
                        servers = s,
                        serversLoaded = true,
                        serverFilter = it.serverFilter?.takeIf { f -> s.any { x -> x.id == f } },
                    )
                }
                refreshAll()
            }
        }
        viewModelScope.launch {
            store.prefs.collect { p ->
                _ui.update { it.copy(prefs = p) }
                val sched = p.trackerAuto to p.trackerIntervalHours
                if (sched != lastSched) {
                    lastSched = sched
                    TrackerAuto.schedule(tapp, p)
                }
            }
        }
    }

    private fun Throwable.msg(): String = message ?: javaClass.simpleName
    fun toast(msg: String) = _ui.update { it.copy(message = msg) }
    fun clearMessage() = _ui.update { it.copy(message = null) }

    // ---------------- Опрос ----------------

    suspend fun pollLoop() {
        while (true) {
            refreshAll()
            delay(_ui.value.prefs.pollSeconds * 1000L)
        }
    }

    private suspend fun fetchServer(s: Server): Pair<List<Torrent>?, ServerStatus> = try {
        val api = tapp.clients.api(s)
        coroutineScope {
            val a = async { api.torrents(s.id) }
            val b = async { api.sessionGet(listOf("alt-speed-enabled", "version")) }
            val list = a.await()
            val sess = b.await()
            list to ServerStatus(
                online = true,
                down = list.sumOf { it.rateDownload },
                up = list.sumOf { it.rateUpload },
                altSpeed = sess.optBoolean("alt-speed-enabled"),
                version = sess.optString("version"),
            )
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null to ServerStatus(online = false, error = e.msg())
    }

    suspend fun refreshAll() {
        val st = _ui.value
        if (!st.serversLoaded) return
        val servers = st.servers
        val results = coroutineScope {
            servers.map { s -> async { s.id to fetchServer(s) } }.awaitAll()
        }
        val old = _ui.value.torrents
        val merged = results.flatMap { (sid, r) -> r.first ?: old.filter { it.serverId == sid } }
        val keys = merged.mapTo(HashSet()) { it.key }
        _ui.update {
            it.copy(
                torrents = merged,
                status = results.associate { r -> r.first to r.second.second },
                selected = it.selected.intersect(keys),
            )
        }
        maybeAutoTrackers(keys)
    }

    // ---------------- Фильтры / выбор ----------------

    fun setServerFilter(id: String?) = _ui.update { it.copy(serverFilter = id) }
    fun setStatusFilter(f: StatusFilter) = _ui.update { it.copy(statusFilter = f) }
    fun setSort(m: SortMode) = _ui.update { it.copy(sort = m) }
    fun toggleSortDir() = _ui.update { it.copy(sortDesc = !it.sortDesc) }
    fun setQuery(q: String) = _ui.update { it.copy(query = q) }

    fun toggleSelect(key: String) = _ui.update {
        it.copy(selected = if (key in it.selected) it.selected - key else it.selected + key)
    }

    fun clearSelection() = _ui.update { it.copy(selected = emptySet()) }
    fun selectAll(keys: Collection<String>) = _ui.update { it.copy(selected = keys.toSet()) }

    // ---------------- Действия над торрентами ----------------

    private fun act(keys: Collection<String>, block: suspend (TransmissionApi, List<Int>) -> Unit) {
        val groups = keys.groupBy({ it.substringBefore('|') }, { it.substringAfter('|').toInt() })
        viewModelScope.launch {
            for ((sid, ids) in groups) {
                val s = _ui.value.servers.find { it.id == sid } ?: continue
                try {
                    block(tapp.clients.api(s), ids)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    toast("${s.name}: ${e.msg()}")
                }
            }
            refreshAll()
        }
    }

    fun start(keys: Collection<String>) = act(keys) { a, ids -> a.start(ids) }
    fun startNow(keys: Collection<String>) = act(keys) { a, ids -> a.startNow(ids) }
    fun pause(keys: Collection<String>) = act(keys) { a, ids -> a.stop(ids) }
    fun verify(keys: Collection<String>) = act(keys) { a, ids -> a.verify(ids) }
    fun reannounce(keys: Collection<String>) = act(keys) { a, ids -> a.reannounce(ids) }
    fun remove(keys: Collection<String>, deleteData: Boolean) =
        act(keys) { a, ids -> a.remove(ids, deleteData) }

    fun move(keys: Collection<String>, location: String) =
        act(keys) { a, ids -> a.setLocation(ids, location, true) }

    fun queueMove(direction: String, keys: Collection<String>) =
        act(keys) { a, ids -> a.queueMove(direction, ids) }

    private fun serversFor(sid: String?): List<Server> =
        if (sid == null) _ui.value.servers else _ui.value.servers.filter { it.id == sid }

    fun pauseAll(sid: String?) {
        viewModelScope.launch {
            serversFor(sid).forEach { s -> safeCall(s) { it.stop(null) } }
            refreshAll()
        }
    }

    fun resumeAll(sid: String?) {
        viewModelScope.launch {
            serversFor(sid).forEach { s -> safeCall(s) { it.start(null) } }
            refreshAll()
        }
    }

    /** Одновременно загружаемых торрентов: 1 — строго по одному (остальные ждут в очереди). */
    fun setDownloadQueue(sid: String?, n: Int) {
        val size = n.coerceAtLeast(1)
        viewModelScope.launch {
            serversFor(sid).forEach { s ->
                safeCall(s) {
                    it.sessionSet(JSONObject().put("download-queue-enabled", true).put("download-queue-size", size))
                }
            }
            toast("Очередь: одновременно загружается $size")
            refreshAll()
        }
    }

    fun toggleAltSpeed(sid: String?) {
        viewModelScope.launch {
            serversFor(sid).forEach { s ->
                val cur = _ui.value.status[s.id]?.altSpeed ?: false
                safeCall(s) { it.sessionSet(JSONObject().put("alt-speed-enabled", !cur)) }
            }
            refreshAll()
        }
    }

    private suspend fun safeCall(s: Server, block: suspend (TransmissionApi) -> Unit) {
        try {
            block(tapp.clients.api(s))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            toast("${s.name}: ${e.msg()}")
        }
    }

    /** Универсальный вызов API для одного сервера (экраны деталей и настроек). */
    fun run(serverId: String, okMessage: String? = null, block: suspend (TransmissionApi) -> Unit): Job =
        viewModelScope.launch {
            val s = _ui.value.servers.find { it.id == serverId } ?: return@launch
            try {
                block(tapp.clients.api(s))
                if (okMessage != null) toast(okMessage)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                toast(e.msg())
            }
            refreshAll()
            fetchDetail()
        }

    // ---------------- Детали торрента ----------------

    suspend fun watchDetail(sid: String, id: Int) {
        detailTarget = sid to id
        _detail.value = null
        try {
            while (true) {
                fetchDetail()
                delay(2000)
            }
        } finally {
            detailTarget = null
        }
    }

    private suspend fun fetchDetail() {
        val (sid, id) = detailTarget ?: return
        val s = _ui.value.servers.find { it.id == sid } ?: return
        try {
            _detail.value = tapp.clients.api(s).detail(sid, id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // временная ошибка — оставляем прошлые данные
        }
    }

    // ---------------- Настройки сессии ----------------

    fun loadSession(sid: String) {
        viewModelScope.launch {
            _session.value = SessionUi(loading = true, version = sessionVersion)
            val s = _ui.value.servers.find { it.id == sid }
            if (s == null) {
                _session.value = SessionUi(loading = false, error = "Сервер не найден")
                return@launch
            }
            try {
                val j = tapp.clients.api(s).sessionGet()
                sessionVersion++
                _session.value = SessionUi(loading = false, json = j, version = sessionVersion)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _session.value = SessionUi(loading = false, error = e.msg(), version = sessionVersion)
            }
        }
    }

    fun saveSession(sid: String, edits: Map<String, Any>) {
        viewModelScope.launch {
            val s = _ui.value.servers.find { it.id == sid } ?: return@launch
            try {
                tapp.clients.api(s).sessionSet(JSONObject(edits))
                toast("Сохранено")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                toast(e.msg())
            }
            loadSession(sid)
            refreshAll()
        }
    }

    fun portTest(sid: String) {
        viewModelScope.launch {
            val s = _ui.value.servers.find { it.id == sid } ?: return@launch
            try {
                val open = tapp.clients.api(s).portTest()
                toast(if (open) "Порт открыт" else "Порт закрыт")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                toast(e.msg())
            }
        }
    }

    fun blocklistUpdate(sid: String) {
        viewModelScope.launch {
            val s = _ui.value.servers.find { it.id == sid } ?: return@launch
            try {
                val n = tapp.clients.api(s).blocklistUpdate()
                toast("Блоклист обновлён: $n правил")
                loadSession(sid)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                toast(e.msg())
            }
        }
    }

    suspend fun defaultDir(sid: String): String {
        val s = _ui.value.servers.find { it.id == sid } ?: return ""
        return try {
            tapp.clients.api(s).sessionGet(listOf("download-dir")).optString("download-dir")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ""
        }
    }

    // ---------------- Статистика ----------------

    fun loadStats() {
        viewModelScope.launch {
            val servers = _ui.value.servers
            val list = coroutineScope {
                servers.map { s ->
                    async {
                        try {
                            val api = tapp.clients.api(s)
                            val sess = api.sessionGet()
                            val stats = api.sessionStats()
                            val free = api.freeSpace(sess.optString("download-dir"))
                            ServerStats(s, null, sess, stats, free)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            ServerStats(s, e.msg(), null, null, -1)
                        }
                    }
                }.awaitAll()
            }
            _stats.value = list
        }
    }

    // ---------------- Добавление торрентов ----------------

    suspend fun addTorrents(
        serverId: String,
        magnets: List<String>,
        uris: List<Uri>,
        dir: String,
        paused: Boolean,
        priority: Int,
        labels: List<String>,
        sequential: Boolean = false,
    ): String {
        val s = _ui.value.servers.find { it.id == serverId } ?: error("Сервер не найден")
        val api = tapp.clients.api(s)
        val results = mutableListOf<String>()
        for (m in magnets) results += api.add(m, null, dir, paused, priority, labels, sequential)
        for (u in uris) {
            val bytes = withContext(Dispatchers.IO) {
                getApplication<Application>().contentResolver.openInputStream(u)?.use { it.readBytes() }
            } ?: error("Не удалось прочитать файл")
            results += api.add(null, Base64.encodeToString(bytes, Base64.NO_WRAP), dir, paused, priority, labels, sequential)
        }
        refreshAll()
        return results.joinToString("\n")
    }

    // ---------------- Трекеры ----------------

    /** Пока приложение открыто: при появлении новых торрентов и не реже раза в 5 минут. */
    private fun maybeAutoTrackers(keys: Set<String>) {
        val prev = seenKeys
        seenKeys = keys
        if (!_ui.value.prefs.trackerAuto || autoBusy) return
        val now = System.currentTimeMillis()
        val hasNew = prev != null && (keys - prev).isNotEmpty()
        if ((hasNew && now - lastAutoRun > 30_000) || now - lastAutoRun > 5 * 60_000) {
            autoBusy = true
            lastAutoRun = now
            viewModelScope.launch {
                try {
                    TrackerAuto.run(tapp)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // молча, повторим позже
                } finally {
                    autoBusy = false
                }
            }
        }
    }

    fun applyTrackersNow(p: AppPrefs? = null) {
        viewModelScope.launch {
            if (p != null) store.savePrefs(p)
            toast("Обновляю трекеры…")
            try {
                toast(TrackerAuto.run(tapp))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                toast(e.msg())
            }
            fetchDetail()
        }
    }

    /** Ручное добавление списка URL в выбранные торренты (без проверки на приватность). */
    fun addTrackers(keys: Collection<String>, urls: List<String>) {
        act(keys) { a, ids -> a.addTrackers(ids, urls) }
        toast("Трекеров отправлено: ${urls.size}")
    }

    /** Добавление трекеров из источника в выбранные торренты; приватные пропускаются. */
    fun addTrackersFromSource(keys: Collection<String>) {
        viewModelScope.launch {
            toast("Загружаю список…")
            val list = try {
                TrackerAuto.sourceList(tapp).list
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                toast(e.msg())
                return@launch
            }
            if (list.isEmpty()) {
                toast("Список трекеров пуст")
                return@launch
            }
            var touched = 0
            var added = 0
            val groups = keys.groupBy({ it.substringBefore('|') }, { it.substringAfter('|').toInt() })
            for ((sid, ids) in groups) {
                val s = _ui.value.servers.find { it.id == sid } ?: continue
                try {
                    val (t, a) = TrackerAuto.applyList(tapp.clients.api(s), list, false, 0, ids.toSet())
                    touched += t
                    added += a
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    toast("${s.name}: ${e.msg()}")
                }
            }
            toast("Добавлено трекеров: $added в $touched торрентах (приватные пропущены)")
            refreshAll()
            fetchDetail()
        }
    }

    // ---------------- Серверы и настройки приложения ----------------

    fun saveServer(s: Server) {
        viewModelScope.launch {
            val list = _ui.value.servers.toMutableList()
            val i = list.indexOfFirst { it.id == s.id }
            if (i >= 0) list[i] = s else list += s
            store.saveServers(list)
        }
    }

    fun deleteServer(id: String) {
        viewModelScope.launch { store.saveServers(_ui.value.servers.filter { it.id != id }) }
    }

    suspend fun testServer(s: Server): String {
        val j = TransmissionApi(RpcClient(s)).sessionGet(listOf("version", "rpc-version"))
        return "Подключено: Transmission ${j.optString("version")} (RPC ${j.optInt("rpc-version")})"
    }

    fun savePrefs(p: AppPrefs) {
        viewModelScope.launch { store.savePrefs(p) }
    }
}
