package app.transmissionremote.ui

import app.transmissionremote.data.AppPrefs
import app.transmissionremote.data.Server
import app.transmissionremote.data.St
import app.transmissionremote.data.Torrent

data class ServerStatus(
    val online: Boolean,
    val error: String? = null,
    val down: Long = 0,
    val up: Long = 0,
    val altSpeed: Boolean = false,
    val version: String = "",
)

enum class StatusFilter(val label: String) {
    ALL("Все"),
    DOWNLOADING("Загрузка"),
    SEEDING("Раздача"),
    PAUSED("Пауза"),
    COMPLETED("Готово"),
    ACTIVE("Активные"),
    ERROR("Ошибки"),
    CHECKING("Проверка");

    fun matches(t: Torrent): Boolean = when (this) {
        ALL -> true
        DOWNLOADING -> t.status == St.DOWNLOAD || t.status == St.DL_WAIT
        SEEDING -> t.status == St.SEED || t.status == St.SEED_WAIT
        PAUSED -> t.status == St.STOPPED
        COMPLETED -> t.percentDone >= 1.0
        ACTIVE -> t.rateDownload > 0 || t.rateUpload > 0
        ERROR -> t.error != 0
        CHECKING -> t.status == St.CHECK || t.status == St.CHECK_WAIT
    }
}

enum class SortMode(val label: String) {
    NAME("Имя"),
    ADDED("Дата добавления"),
    PROGRESS("Прогресс"),
    SIZE("Размер"),
    DOWN("Скорость загрузки"),
    UP("Скорость отдачи"),
    RATIO("Рейтинг"),
    QUEUE("Очередь"),
}

data class UiState(
    val servers: List<Server> = emptyList(),
    val serversLoaded: Boolean = false,
    val torrents: List<Torrent> = emptyList(),
    val status: Map<String, ServerStatus> = emptyMap(),
    val serverFilter: String? = null,
    val statusFilter: StatusFilter = StatusFilter.ALL,
    val sort: SortMode = SortMode.ADDED,
    val sortDesc: Boolean = true,
    val query: String = "",
    val selected: Set<String> = emptySet(),
    val prefs: AppPrefs = AppPrefs(),
    val message: String? = null,
)

fun UiState.visible(): List<Torrent> {
    val cmp: Comparator<Torrent> = when (sort) {
        SortMode.NAME -> compareBy<Torrent, String>(String.CASE_INSENSITIVE_ORDER) { it.name }
        SortMode.ADDED -> compareBy<Torrent> { it.addedDate }
        SortMode.PROGRESS -> compareBy<Torrent> { it.percentDone }
        SortMode.SIZE -> compareBy<Torrent> { it.totalSize }
        SortMode.DOWN -> compareBy<Torrent> { it.rateDownload }
        SortMode.UP -> compareBy<Torrent> { it.rateUpload }
        SortMode.RATIO -> compareBy<Torrent> { it.uploadRatio }
        SortMode.QUEUE -> compareBy<Torrent> { it.queuePosition }
    }
    return torrents
        .filter {
            (serverFilter == null || it.serverId == serverFilter) &&
                statusFilter.matches(it) &&
                (query.isBlank() || it.name.contains(query, ignoreCase = true))
        }
        .sortedWith(if (sortDesc) cmp.reversed() else cmp)
}
