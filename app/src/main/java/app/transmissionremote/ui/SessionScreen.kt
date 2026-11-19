package app.transmissionremote.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.json.JSONObject

private enum class FK { BOOL, INT, DOUBLE, TEXT, TIME, ENUM, ENUM_INT }

private data class FieldDef(
    val key: String,
    val label: String,
    val kind: FK,
    val options: List<Pair<String, String>> = emptyList(),
    val hint: String? = null,
)

private data class FieldGroup(val title: String, val fields: List<FieldDef>)

private val GROUPS = listOf(
    FieldGroup(
        "Шифрование и сеть",
        listOf(
            FieldDef(
                "encryption", "Шифрование пиров", FK.ENUM,
                listOf("required" to "Обязательное", "preferred" to "Предпочитать", "tolerated" to "Разрешить (без шифрования)"),
            ),
            FieldDef("peer-port", "Порт для входящих", FK.INT),
            FieldDef("peer-port-random-on-start", "Случайный порт при запуске", FK.BOOL),
            FieldDef("port-forwarding-enabled", "Проброс портов (UPnP / NAT-PMP)", FK.BOOL),
            FieldDef("dht-enabled", "DHT", FK.BOOL),
            FieldDef("pex-enabled", "PEX", FK.BOOL),
            FieldDef("lpd-enabled", "Поиск пиров в локальной сети (LPD)", FK.BOOL),
            FieldDef("utp-enabled", "µTP", FK.BOOL),
            FieldDef("peer-limit-global", "Макс. пиров всего", FK.INT),
            FieldDef("peer-limit-per-torrent", "Макс. пиров на торрент", FK.INT),
        ),
    ),
    FieldGroup(
        "Скорость",
        listOf(
            FieldDef("speed-limit-down-enabled", "Ограничить загрузку", FK.BOOL),
            FieldDef("speed-limit-down", "Лимит загрузки, КБ/с", FK.INT),
            FieldDef("speed-limit-up-enabled", "Ограничить отдачу", FK.BOOL),
            FieldDef("speed-limit-up", "Лимит отдачи, КБ/с", FK.INT),
            FieldDef("alt-speed-enabled", "Альтернативные лимиты включены", FK.BOOL),
            FieldDef("alt-speed-down", "Альт. загрузка, КБ/с", FK.INT),
            FieldDef("alt-speed-up", "Альт. отдача, КБ/с", FK.INT),
            FieldDef("alt-speed-time-enabled", "Альт. лимиты по расписанию", FK.BOOL),
            FieldDef("alt-speed-time-begin", "Начало (ЧЧ:ММ)", FK.TIME),
            FieldDef("alt-speed-time-end", "Конец (ЧЧ:ММ)", FK.TIME),
            FieldDef(
                "alt-speed-time-day", "Дни расписания", FK.ENUM_INT,
                listOf("127" to "Каждый день", "62" to "Будни", "65" to "Выходные"),
            ),
        ),
    ),
    FieldGroup(
        "Загрузки",
        listOf(
            FieldDef("download-dir", "Папка загрузки", FK.TEXT),
            FieldDef("incomplete-dir-enabled", "Отдельная папка для незавершённых", FK.BOOL),
            FieldDef("incomplete-dir", "Папка незавершённых", FK.TEXT),
            FieldDef("start-added-torrents", "Запускать добавленные торренты", FK.BOOL),
            FieldDef("trash-original-torrent-files", "Удалять исходные .torrent", FK.BOOL),
            FieldDef("rename-partial-files", "Добавлять .part к незавершённым", FK.BOOL),
            FieldDef("cache-size-mb", "Кэш диска, МБ", FK.INT),
            FieldDef("sequential_download", "Последовательная загрузка по умолчанию", FK.BOOL),
            FieldDef("sequentialDownload", "Последовательная загрузка по умолчанию", FK.BOOL),
        ),
    ),
    FieldGroup(
        "Очереди и раздача",
        listOf(
            FieldDef("download-queue-enabled", "Очередь загрузок", FK.BOOL),
            FieldDef("download-queue-size", "Одновременных загрузок", FK.INT),
            FieldDef("seed-queue-enabled", "Очередь раздач", FK.BOOL),
            FieldDef("seed-queue-size", "Одновременных раздач", FK.INT),
            FieldDef("queue-stalled-enabled", "Считать зависшими", FK.BOOL),
            FieldDef("queue-stalled-minutes", "Зависшим считать через, мин", FK.INT),
            FieldDef("seedRatioLimited", "Ограничить рейтинг раздачи", FK.BOOL),
            FieldDef("seedRatioLimit", "Целевой рейтинг", FK.DOUBLE),
            FieldDef("idle-seeding-limit-enabled", "Останавливать раздачу при простое", FK.BOOL),
            FieldDef("idle-seeding-limit", "Простой раздачи, мин", FK.INT),
        ),
    ),
    FieldGroup(
        "Блоклист",
        listOf(
            FieldDef("blocklist-enabled", "Использовать блоклист", FK.BOOL),
            FieldDef("blocklist-url", "URL блоклиста", FK.TEXT),
        ),
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionScreen(vm: AppViewModel, sid: String, onBack: () -> Unit) {
    val s by vm.session.collectAsStateWithLifecycle()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    MessageEffect(vm, snackbar)
    val edits = remember { mutableStateMapOf<String, Any>() }
    var tick by remember { mutableStateOf(0) }
    val serverName = ui.servers.find { it.id == sid }?.name.orEmpty()

    LaunchedEffect(sid) { vm.loadSession(sid) }
    LaunchedEffect(s.version) { edits.clear() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Настройки: $serverName") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } },
                actions = {
                    IconButton(enabled = edits.isNotEmpty(), onClick = { vm.saveSession(sid, edits.toMap()) }) {
                        Icon(Icons.Default.Check, "Сохранить")
                    }
                },
            )
        },
    ) { pad ->
        val json = s.json
        when {
            s.loading -> Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            json == null -> Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(s.error ?: "Не удалось загрузить настройки", color = MaterialTheme.colorScheme.error)
            }
            else -> Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
                Text(
                    "Transmission ${json.optString("version")} · RPC ${json.optInt("rpc-version")}" +
                        if (edits.isNotEmpty()) "  ·  изменено: ${edits.size}" else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                DiagnosticsCard(diagnose(json)) {
                    val r = recommended(json)
                    r.forEach { (k, v) -> edits[k] = v }
                    tick++
                    vm.toast(if (r.isEmpty()) "Рекомендуемые значения уже стоят" else "Изменений: ${r.size}. Нажмите ✓ вверху, чтобы сохранить")
                }
                GROUPS.forEach { g ->
                    val present = g.fields.filter { json.has(it.key) }
                    if (present.isEmpty()) return@forEach
                    SectionTitle(g.title)
                    present.forEach { f -> SettingRow(f, json, edits, s.version * 1000 + tick) }
                    when (g.title) {
                        "Шифрование и сеть" -> OutlinedButton(
                            onClick = { vm.portTest(sid) },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        ) { Text("Проверить порт") }
                        "Блоклист" -> {
                            if (json.has("blocklist-size")) {
                                Text(
                                    "Правил в блоклисте: ${json.optInt("blocklist-size")}",
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            OutlinedButton(
                                onClick = { vm.blocklistUpdate(sid) },
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            ) { Text("Обновить блоклист") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingRow(def: FieldDef, session: JSONObject, edits: SnapshotStateMap<String, Any>, version: Int) {
    val pad = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
    when (def.kind) {
        FK.BOOL -> {
            val v = (edits[def.key] as? Boolean) ?: session.optBoolean(def.key)
            Row(pad, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(def.label, Modifier.weight(1f))
                Switch(checked = v, onCheckedChange = { edits[def.key] = it })
            }
        }

        FK.ENUM -> DropdownField(
            label = def.label,
            options = def.options,
            selected = (edits[def.key] as? String) ?: session.optString(def.key),
            modifier = pad,
        ) { edits[def.key] = it }

        FK.ENUM_INT -> DropdownField(
            label = def.label,
            options = def.options,
            selected = ((edits[def.key] as? Int) ?: session.optInt(def.key)).toString(),
            modifier = pad,
        ) { edits[def.key] = it.toInt() }

        else -> {
            var text by remember(def.key, version) { mutableStateOf(initialText(def, session, edits[def.key])) }
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    parseInto(def, it, edits)
                },
                label = { Text(def.label) },
                singleLine = true,
                keyboardOptions = when (def.kind) {
                    FK.INT -> KeyboardOptions(keyboardType = KeyboardType.Number)
                    FK.DOUBLE -> KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    else -> KeyboardOptions.Default
                },
                modifier = pad,
            )
        }
    }
}

private fun initialText(def: FieldDef, s: JSONObject, edit: Any?): String = when (def.kind) {
    FK.INT -> ((edit as? Int) ?: s.optInt(def.key)).toString()
    FK.DOUBLE -> ((edit as? Double) ?: s.optDouble(def.key)).toString()
    FK.TIME -> {
        val m = (edit as? Int) ?: s.optInt(def.key)
        String.format("%02d:%02d", m / 60, m % 60)
    }
    else -> (edit as? String) ?: s.optString(def.key)
}

private fun parseInto(def: FieldDef, raw: String, edits: MutableMap<String, Any>) {
    val t = raw.trim()
    when (def.kind) {
        FK.INT -> t.toIntOrNull()?.let { edits[def.key] = it }
        FK.DOUBLE -> t.replace(',', '.').toDoubleOrNull()?.let { edits[def.key] = it }
        FK.TEXT -> edits[def.key] = raw
        FK.TIME -> {
            val m = Regex("^(\\d{1,2}):(\\d{2})$").matchEntire(t)
            if (m != null) {
                val h = m.groupValues[1].toInt()
                val min = m.groupValues[2].toInt()
                if (h in 0..23 && min in 0..59) edits[def.key] = h * 60 + min
            }
        }
        else -> {}
    }
}

private fun diagnose(j: JSONObject): List<String> = buildList {
    if (j.optString("encryption") == "required") {
        add("Шифрование «Обязательное»: пиры без шифрования отсекаются. Для скорости лучше «Предпочитать».")
    }
    if (j.optBoolean("speed-limit-down-enabled")) add("Включён лимит загрузки: ${j.optInt("speed-limit-down")} КБ/с.")
    if (j.optBoolean("alt-speed-enabled")) add("Включены альтернативные лимиты (загрузка ${j.optInt("alt-speed-down")} КБ/с).")
    if (j.has("dht-enabled") && !j.optBoolean("dht-enabled")) add("DHT выключен: для публичных торрентов пиров будет меньше.")
    if (j.has("pex-enabled") && !j.optBoolean("pex-enabled")) add("PEX выключен: пиры не обмениваются адресами друг с другом.")
    if (j.has("utp-enabled") && !j.optBoolean("utp-enabled")) add("µTP выключен: хуже проходит через NAT и ограничения провайдера.")
    if (j.has("port-forwarding-enabled") && !j.optBoolean("port-forwarding-enabled")) {
        add("Автопроброс порта выключен. Проверьте порт кнопкой ниже или пробросьте его на роутере вручную.")
    }
    if (j.has("peer-limit-per-torrent") && j.optInt("peer-limit-per-torrent") < 80) {
        add("Мало пиров на торрент (${j.optInt("peer-limit-per-torrent")}). Для быстрых каналов 100–150.")
    }
    if (j.has("peer-limit-global") && j.optInt("peer-limit-global") < 200) {
        add("Мало пиров всего (${j.optInt("peer-limit-global")}).")
    }
    if (j.has("cache-size-mb") && j.optInt("cache-size-mb") < 16) {
        add("Маленький кэш диска (${j.optInt("cache-size-mb")} МБ): на медленных накопителях это тормозит запись.")
    }
    if (j.optBoolean("download-queue-enabled") && j.optInt("download-queue-size") in 1..3) {
        add("Очередь: качается одновременно только ${j.optInt("download-queue-size")} торрент(а), остальные ждут.")
    }
}

/** Безопасные значения «для скорости». Лимиты скорости не трогаются. */
private fun recommended(j: JSONObject): Map<String, Any> {
    val m = LinkedHashMap<String, Any>()
    fun bool(k: String, v: Boolean) { if (j.has(k) && j.optBoolean(k) != v) m[k] = v }
    fun atLeast(k: String, v: Int) { if (j.has(k) && j.optInt(k) < v) m[k] = v }
    if (j.optString("encryption") == "required") m["encryption"] = "preferred"
    bool("dht-enabled", true)
    bool("pex-enabled", true)
    bool("lpd-enabled", true)
    bool("utp-enabled", true)
    bool("port-forwarding-enabled", true)
    bool("queue-stalled-enabled", true)
    atLeast("peer-limit-per-torrent", 120)
    atLeast("peer-limit-global", 400)
    atLeast("cache-size-mb", 32)
    atLeast("download-queue-size", 5)
    return m
}

@Composable
private fun DiagnosticsCard(warnings: List<String>, onApply: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                if (warnings.isEmpty()) "В настройках явных причин медленной загрузки нет" else "Что может тормозить загрузку",
                style = MaterialTheme.typography.titleSmall,
            )
            warnings.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
            Text(
                "«Рекомендуемые» включают DHT/PEX/LPD/µTP и проброс порта, поднимают лимиты пиров, кэш диска (до 32 МБ — на роутере с малой RAM уменьшите) и очередь. Лимиты скорости не меняются.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = onApply) { Text("Подставить рекомендуемые") }
        }
    }
}
