package app.transmissionremote.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class RpcException(message: String) : Exception(message)

/** Низкоуровневый клиент Transmission RPC: сам обрабатывает 409 / X-Transmission-Session-Id и Basic-авторизацию. */
class RpcClient(private val server: Server) {
    @Volatile
    private var sessionId: String = ""

    suspend fun call(method: String, args: JSONObject = JSONObject()): JSONObject =
        withContext(Dispatchers.IO) {
            val payload = JSONObject().put("method", method).put("arguments", args).toString()
            try {
                for (attempt in 0..1) {
                    val builder = Request.Builder()
                        .url(server.url)
                        .header("X-Transmission-Session-Id", sessionId)
                        .post(payload.toRequestBody(JSON))
                    if (server.username.isNotBlank()) {
                        builder.header("Authorization", Credentials.basic(server.username, server.password))
                    }
                    val resp = http.newCall(builder.build()).execute()
                    val code = resp.code
                    val sid = resp.header("X-Transmission-Session-Id")
                    val text = resp.body?.string().orEmpty()
                    resp.close()

                    if (code == 409) {
                        if (sid != null) sessionId = sid
                        continue
                    }
                    if (code == 401) throw RpcException("Неверный логин или пароль")
                    if (code !in 200..299) throw RpcException("HTTP $code")

                    val json = JSONObject(text)
                    val result = json.optString("result")
                    if (result != "success") throw RpcException(result.ifBlank { "Ошибка RPC" })
                    return@withContext json.optJSONObject("arguments") ?: JSONObject()
                }
                throw RpcException("Не удалось получить session id")
            } catch (e: IllegalArgumentException) {
                throw RpcException("Неверный адрес сервера")
            } catch (e: IOException) {
                throw RpcException(e.message ?: "Ошибка сети")
            }
        }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private val http: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}

data class TrackerInfo(
    val id: Int,
    val isPrivate: Boolean,
    val hasMetadata: Boolean,
    val complete: Boolean,
    val announces: List<String>,
)

class TransmissionApi(private val rpc: RpcClient) {

    private fun idsArgs(ids: List<Int>?): JSONObject =
        JSONObject().apply { if (ids != null) put("ids", JSONArray(ids)) }

    // ---- Сессия ----
    suspend fun sessionGet(fields: List<String>? = null): JSONObject {
        val a = JSONObject()
        if (fields != null) a.put("fields", JSONArray(fields))
        return rpc.call("session-get", a)
    }

    suspend fun sessionSet(args: JSONObject) {
        rpc.call("session-set", args)
    }

    suspend fun sessionStats(): JSONObject = rpc.call("session-stats")

    suspend fun freeSpace(path: String): Long =
        rpc.call("free-space", JSONObject().put("path", path)).optLong("size-bytes", -1)

    suspend fun portTest(): Boolean = rpc.call("port-test").optBoolean("port-is-open")

    suspend fun blocklistUpdate(): Long = rpc.call("blocklist-update").optLong("blocklist-size")

    // ---- Торренты ----
    suspend fun torrents(serverId: String): List<Torrent> {
        val r = rpc.call("torrent-get", JSONObject().put("fields", JSONArray(TORRENT_LIST_FIELDS)))
        return r.optJSONArray("torrents").objects().map { it.toTorrent(serverId) }
    }

    suspend fun detail(serverId: String, id: Int): TorrentDetail? {
        val r = rpc.call(
            "torrent-get",
            JSONObject().put("ids", JSONArray(listOf(id))).put("fields", JSONArray(TORRENT_DETAIL_FIELDS)),
        )
        return r.optJSONArray("torrents").objects().firstOrNull()?.toDetail(serverId)
    }

    suspend fun trackerInfo(): List<TrackerInfo> {
        val fields = listOf("id", "isPrivate", "percentDone", "metadataPercentComplete", "trackers")
        val r = rpc.call("torrent-get", JSONObject().put("fields", JSONArray(fields)))
        return r.optJSONArray("torrents").objects().map { o ->
            TrackerInfo(
                id = o.getInt("id"),
                isPrivate = o.optBoolean("isPrivate"),
                hasMetadata = o.optDouble("metadataPercentComplete", 1.0) >= 1.0,
                complete = o.optDouble("percentDone", 0.0) >= 1.0,
                announces = o.optJSONArray("trackers").objects().map { it.optString("announce") },
            )
        }
    }

    suspend fun addTrackers(ids: List<Int>, urls: List<String>) {
        torrentSet(ids, JSONObject().put("trackerAdd", JSONArray(urls)))
    }

    suspend fun start(ids: List<Int>?) { rpc.call("torrent-start", idsArgs(ids)) }
    suspend fun startNow(ids: List<Int>?) { rpc.call("torrent-start-now", idsArgs(ids)) }
    suspend fun stop(ids: List<Int>?) { rpc.call("torrent-stop", idsArgs(ids)) }
    suspend fun verify(ids: List<Int>?) { rpc.call("torrent-verify", idsArgs(ids)) }
    suspend fun reannounce(ids: List<Int>?) { rpc.call("torrent-reannounce", idsArgs(ids)) }

    suspend fun remove(ids: List<Int>, deleteData: Boolean) {
        rpc.call("torrent-remove", idsArgs(ids).put("delete-local-data", deleteData))
    }

    suspend fun setLocation(ids: List<Int>, location: String, move: Boolean) {
        rpc.call("torrent-set-location", idsArgs(ids).put("location", location).put("move", move))
    }

    /** direction: top | up | down | bottom */
    suspend fun queueMove(direction: String, ids: List<Int>) {
        rpc.call("queue-move-$direction", idsArgs(ids))
    }

    suspend fun torrentSet(ids: List<Int>, args: JSONObject) {
        args.put("ids", JSONArray(ids))
        rpc.call("torrent-set", args)
    }

    suspend fun renamePath(id: Int, path: String, name: String) {
        rpc.call("torrent-rename-path", idsArgs(listOf(id)).put("path", path).put("name", name))
    }

    suspend fun add(
        source: String?,
        metainfoBase64: String?,
        dir: String,
        paused: Boolean,
        priority: Int,
        labels: List<String>,
        sequential: Boolean = false,
    ): String {
        val a = JSONObject()
        if (source != null) a.put("filename", source)
        if (metainfoBase64 != null) a.put("metainfo", metainfoBase64)
        if (dir.isNotBlank()) a.put("download-dir", dir)
        a.put("paused", paused).put("bandwidthPriority", priority)
        // имя поля различается между версиями; неизвестные ключи сервер игнорирует
        if (sequential) a.put("sequential_download", true).put("sequentialDownload", true)
        if (labels.isNotEmpty()) a.put("labels", JSONArray(labels))
        val r = rpc.call("torrent-add", a)
        r.optJSONObject("torrent-added")?.let { return "Добавлен: ${it.optString("name")}" }
        r.optJSONObject("torrent-duplicate")?.let { return "Уже есть: ${it.optString("name")}" }
        return "Готово"
    }
}
