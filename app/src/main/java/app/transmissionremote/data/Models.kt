package app.transmissionremote.data

import org.json.JSONArray
import org.json.JSONObject

data class Server(
    val id: String,
    val name: String,
    val host: String,
    val port: Int = 9091,
    val path: String = "/transmission/rpc",
    val https: Boolean = false,
    val username: String = "",
    val password: String = "",
) {
    val url: String
        get() {
            val p = if (path.startsWith("/")) path else "/$path"
            return "${if (https) "https" else "http"}://$host:$port$p"
        }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("name", name).put("host", host).put("port", port)
        .put("path", path).put("https", https).put("username", username).put("password", password)

    companion object {
        fun fromJson(o: JSONObject) = Server(
            id = o.getString("id"),
            name = o.optString("name"),
            host = o.optString("host"),
            port = o.optInt("port", 9091),
            path = o.optString("path", "/transmission/rpc"),
            https = o.optBoolean("https", false),
            username = o.optString("username"),
            password = o.optString("password"),
        )
    }
}

object St {
    const val STOPPED = 0
    const val CHECK_WAIT = 1
    const val CHECK = 2
    const val DL_WAIT = 3
    const val DOWNLOAD = 4
    const val SEED_WAIT = 5
    const val SEED = 6
}

val TORRENT_LIST_FIELDS = listOf(
    "id", "name", "status", "percentDone", "recheckProgress", "rateDownload", "rateUpload",
    "totalSize", "sizeWhenDone", "leftUntilDone", "eta", "uploadRatio", "peersConnected",
    "addedDate", "doneDate", "error", "errorString", "downloadDir", "hashString", "isFinished",
    "queuePosition", "uploadedEver", "downloadedEver", "labels", "bandwidthPriority",
)

val TORRENT_DETAIL_FIELDS = TORRENT_LIST_FIELDS + listOf(
    "comment", "creator", "pieceCount", "pieceSize", "magnetLink", "dateCreated", "activityDate",
    "secondsDownloading", "secondsSeeding", "corruptEver", "haveValid", "downloadLimit",
    "downloadLimited", "uploadLimit", "uploadLimited", "honorsSessionLimits", "seedRatioLimit",
    "seedRatioMode", "seedIdleLimit", "seedIdleMode", "peer-limit", "files", "fileStats", "peers",
    "trackerStats", "isPrivate", "peersSendingToUs", "peersGettingFromUs",
    "sequential_download", "sequentialDownload",
)

data class Torrent(
    val serverId: String,
    val id: Int,
    val name: String,
    val status: Int,
    val percentDone: Double,
    val recheckProgress: Double,
    val rateDownload: Long,
    val rateUpload: Long,
    val totalSize: Long,
    val sizeWhenDone: Long,
    val leftUntilDone: Long,
    val eta: Long,
    val uploadRatio: Double,
    val peersConnected: Int,
    val addedDate: Long,
    val doneDate: Long,
    val error: Int,
    val errorString: String,
    val downloadDir: String,
    val hashString: String,
    val isFinished: Boolean,
    val queuePosition: Int,
    val uploadedEver: Long,
    val downloadedEver: Long,
    val labels: List<String>,
    val bandwidthPriority: Int,
) {
    val key: String get() = "$serverId|$id"
}

data class TFile(
    val index: Int,
    val name: String,
    val length: Long,
    val done: Long,
    val wanted: Boolean,
    val priority: Int,
)

data class Peer(
    val address: String,
    val port: Int,
    val client: String,
    val progress: Double,
    val rateDown: Long,
    val rateUp: Long,
    val flags: String,
    val encrypted: Boolean,
    val utp: Boolean,
)

data class Tracker(
    val id: Int,
    val announce: String,
    val host: String,
    val result: String,
    val ok: Boolean,
    val seeders: Int,
    val leechers: Int,
    val lastAnnounce: Long,
    val nextAnnounce: Long,
    val tier: Int,
)

data class TorrentDetail(
    val base: Torrent,
    val comment: String,
    val creator: String,
    val pieceCount: Int,
    val pieceSize: Long,
    val magnet: String,
    val dateCreated: Long,
    val activityDate: Long,
    val secondsDownloading: Long,
    val secondsSeeding: Long,
    val corruptEver: Long,
    val haveValid: Long,
    val downloadLimit: Int,
    val downloadLimited: Boolean,
    val uploadLimit: Int,
    val uploadLimited: Boolean,
    val honorsSessionLimits: Boolean,
    val seedRatioLimit: Double,
    val seedRatioMode: Int,
    val seedIdleLimit: Int,
    val seedIdleMode: Int,
    val peerLimit: Int,
    val isPrivate: Boolean,
    val files: List<TFile>,
    val peers: List<Peer>,
    val trackers: List<Tracker>,
    /** Имя поля последовательной загрузки на этом сервере; null — сервер не поддерживает (нужен Transmission 4.1+). */
    val seqKey: String?,
    val sequential: Boolean,
)

internal fun JSONArray?.strings(): List<String> =
    if (this == null) emptyList() else (0 until length()).map { getString(it) }

internal fun JSONArray?.objects(): List<JSONObject> =
    if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }

internal fun JSONObject.toTorrent(serverId: String) = Torrent(
    serverId = serverId,
    id = getInt("id"),
    name = optString("name"),
    status = optInt("status"),
    percentDone = optDouble("percentDone", 0.0),
    recheckProgress = optDouble("recheckProgress", 0.0),
    rateDownload = optLong("rateDownload"),
    rateUpload = optLong("rateUpload"),
    totalSize = optLong("totalSize"),
    sizeWhenDone = optLong("sizeWhenDone"),
    leftUntilDone = optLong("leftUntilDone"),
    eta = optLong("eta", -1),
    uploadRatio = optDouble("uploadRatio", 0.0),
    peersConnected = optInt("peersConnected"),
    addedDate = optLong("addedDate"),
    doneDate = optLong("doneDate"),
    error = optInt("error"),
    errorString = optString("errorString"),
    downloadDir = optString("downloadDir"),
    hashString = optString("hashString"),
    isFinished = optBoolean("isFinished"),
    queuePosition = optInt("queuePosition"),
    uploadedEver = optLong("uploadedEver"),
    downloadedEver = optLong("downloadedEver"),
    labels = optJSONArray("labels").strings(),
    bandwidthPriority = optInt("bandwidthPriority"),
)

internal fun JSONObject.toDetail(serverId: String): TorrentDetail {
    val fs = optJSONArray("files").objects()
    val st = optJSONArray("fileStats").objects()
    val files = fs.mapIndexed { i, f ->
        val s = st.getOrNull(i)
        TFile(
            index = i,
            name = f.optString("name"),
            length = f.optLong("length"),
            done = s?.optLong("bytesCompleted") ?: f.optLong("bytesCompleted"),
            wanted = s?.optBoolean("wanted", true) ?: true,
            priority = s?.optInt("priority") ?: 0,
        )
    }
    val peers = optJSONArray("peers").objects().map {
        Peer(
            address = it.optString("address"),
            port = it.optInt("port"),
            client = it.optString("clientName"),
            progress = it.optDouble("progress", 0.0),
            rateDown = it.optLong("rateToClient"),
            rateUp = it.optLong("rateToPeer"),
            flags = it.optString("flagStr"),
            encrypted = it.optBoolean("isEncrypted"),
            utp = it.optBoolean("isUTP"),
        )
    }
    val seqKey = listOf("sequential_download", "sequentialDownload").firstOrNull { has(it) }
    val trackers = optJSONArray("trackerStats").objects().map {
        Tracker(
            id = it.optInt("id"),
            announce = it.optString("announce"),
            host = it.optString("host"),
            result = it.optString("lastAnnounceResult"),
            ok = it.optBoolean("lastAnnounceSucceeded"),
            seeders = it.optInt("seederCount", -1),
            leechers = it.optInt("leecherCount", -1),
            lastAnnounce = it.optLong("lastAnnounceTime"),
            nextAnnounce = it.optLong("nextAnnounceTime"),
            tier = it.optInt("tier"),
        )
    }
    return TorrentDetail(
        base = toTorrent(serverId),
        comment = optString("comment"),
        creator = optString("creator"),
        pieceCount = optInt("pieceCount"),
        pieceSize = optLong("pieceSize"),
        magnet = optString("magnetLink"),
        dateCreated = optLong("dateCreated"),
        activityDate = optLong("activityDate"),
        secondsDownloading = optLong("secondsDownloading"),
        secondsSeeding = optLong("secondsSeeding"),
        corruptEver = optLong("corruptEver"),
        haveValid = optLong("haveValid"),
        downloadLimit = optInt("downloadLimit"),
        downloadLimited = optBoolean("downloadLimited"),
        uploadLimit = optInt("uploadLimit"),
        uploadLimited = optBoolean("uploadLimited"),
        honorsSessionLimits = optBoolean("honorsSessionLimits", true),
        seedRatioLimit = optDouble("seedRatioLimit", 2.0),
        seedRatioMode = optInt("seedRatioMode"),
        seedIdleLimit = optInt("seedIdleLimit", 30),
        seedIdleMode = optInt("seedIdleMode"),
        peerLimit = optInt("peer-limit", 50),
        isPrivate = optBoolean("isPrivate"),
        files = files,
        peers = peers,
        trackers = trackers,
        seqKey = seqKey,
        sequential = seqKey?.let { optBoolean(it) } ?: false,
    )
}
