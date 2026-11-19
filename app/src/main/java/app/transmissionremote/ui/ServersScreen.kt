package app.transmissionremote.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.transmissionremote.data.Server
import app.transmissionremote.work.TrackerAuto
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServersScreen(vm: AppViewModel, onBack: () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    MessageEffect(vm, snackbar)
    var editing by remember { mutableStateOf<Server?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Server?>(null) }
    var extraText by remember { mutableStateOf(ui.prefs.trackerExtra) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Серверы и приложение") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { creating = true }) { Icon(Icons.Default.Add, "Добавить сервер") }
        },
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(12.dp, 8.dp, 12.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { SectionTitle("Серверы") }
            items(ui.servers, key = { it.id }) { s ->
                val st = ui.status[s.id]
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(s.name, style = MaterialTheme.typography.titleMedium)
                            Text(s.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                when {
                                    st == null -> "…"
                                    st.online -> "онлайн · Transmission ${st.version}"
                                    else -> "офлайн: ${st.error}"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = if (st?.online == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            )
                        }
                        IconButton(onClick = { editing = s }) { Icon(Icons.Default.Edit, "Изменить") }
                        IconButton(onClick = { deleting = s }) { Icon(Icons.Default.Delete, "Удалить") }
                    }
                }
            }
            if (ui.servers.isEmpty()) {
                item { Text("Нажмите «+», чтобы добавить сервер.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }

            item { SectionTitle("Приложение") }
            item {
                DropdownField(
                    label = "Интервал обновления",
                    options = listOf(1, 2, 3, 5, 10, 30).map { it to "$it с" },
                    selected = ui.prefs.pollSeconds,
                ) { vm.savePrefs(ui.prefs.copy(pollSeconds = it)) }
            }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Уведомления о завершении")
                        Text(
                            "Проверка в фоне примерно раз в 15 минут",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = ui.prefs.notify, onCheckedChange = { vm.savePrefs(ui.prefs.copy(notify = it)) })
                }
            }

            item { SectionTitle("Автотрекеры") }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Подтягивать трекеры автоматически")
                        Text(
                            "Только публичные торренты; приватные и magnet без метаданных пропускаются",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = ui.prefs.trackerAuto, onCheckedChange = { vm.savePrefs(ui.prefs.copy(trackerAuto = it)) })
                }
            }
            item {
                val presetOptions = buildList<Pair<String, String>> {
                    addAll(TrackerAuto.PRESETS)
                    add("" to "— не использовать —")
                    val cur = ui.prefs.trackerSource
                    if (cur.isNotBlank() && TrackerAuto.PRESETS.none { it.first == cur }) add(cur to "Прежний: $cur")
                }
                DropdownField(
                    label = "Готовый источник списка",
                    options = presetOptions,
                    selected = ui.prefs.trackerSource,
                ) { vm.savePrefs(ui.prefs.copy(trackerSource = it)) }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        OutlinedTextField(
                            value = extraText,
                            onValueChange = {
                                extraText = it
                                vm.savePrefs(ui.prefs.copy(trackerExtra = it))
                            },
                            label = { Text("Свои источники (ссылки, по одной в строке)") },
                            minLines = 2,
                            maxLines = 5,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Text(
                        "Вставьте ссылки на текстовые списки (например, …/trackers_all.txt на raw.githubusercontent.com): приложение само найдёт в них трекеры. Можно и сами трекеры (udp://…). Всё объединяется с готовым источником, свои идут первыми.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                DropdownField(
                    label = "Как часто обновлять в фоне",
                    options = listOf(1, 6, 12, 24).map { it to "раз в $it ч" },
                    selected = ui.prefs.trackerIntervalHours,
                ) { vm.savePrefs(ui.prefs.copy(trackerIntervalHours = it)) }
            }
            item {
                DropdownField(
                    label = "К каким торрентам",
                    options = listOf(0 to "Только незавершённые", 1 to "Все публичные"),
                    selected = ui.prefs.trackerScope,
                ) { vm.savePrefs(ui.prefs.copy(trackerScope = it)) }
            }
            item {
                var max by remember(ui.prefs.trackerMax) { mutableStateOf(ui.prefs.trackerMax.toString()) }
                OutlinedTextField(
                    value = max,
                    onValueChange = { v ->
                        max = v
                        v.trim().toIntOrNull()?.let { n -> vm.savePrefs(ui.prefs.copy(trackerMax = n.coerceAtLeast(0))) }
                    },
                    label = { Text("Макс. трекеров на торрент (0 — без лимита)") },
                    supportingText = { Text("В trackers_all больше 100 трекеров: при лимите 30 добавится не всё.") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedButton(onClick = { vm.applyTrackersNow(ui.prefs.copy(trackerExtra = extraText.trim())) }) { Text("Применить сейчас") }
                    if (ui.prefs.trackerLast.isNotBlank()) {
                        Text(
                            ui.prefs.trackerLast,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        "Пока приложение открыто, проверка идёт при появлении новых торрентов и раз в 5 минут; в фоне — по расписанию выше.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (creating) ServerDialog(null, vm) { creating = false }
    editing?.let { ServerDialog(it, vm) { editing = null } }
    deleting?.let { s ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Удалить «${s.name}»?") },
            text = { Text("Сервер исчезнет из приложения. Сам Transmission не изменится.") },
            confirmButton = { TextButton(onClick = { vm.deleteServer(s.id); deleting = null }) { Text("Удалить") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun ServerDialog(initial: Server?, vm: AppViewModel, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var host by remember { mutableStateOf(initial?.host.orEmpty()) }
    var port by remember { mutableStateOf((initial?.port ?: 9091).toString()) }
    var path by remember { mutableStateOf(initial?.path ?: "/transmission/rpc") }
    var https by remember { mutableStateOf(initial?.https ?: false) }
    var user by remember { mutableStateOf(initial?.username.orEmpty()) }
    var pass by remember { mutableStateOf(initial?.password.orEmpty()) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }

    fun build() = Server(
        id = initial?.id ?: UUID.randomUUID().toString(),
        name = name.trim().ifBlank { host.trim() },
        host = host.trim(),
        port = port.trim().toIntOrNull() ?: 9091,
        path = path.trim().ifBlank { "/transmission/rpc" },
        https = https,
        username = user.trim(),
        password = pass,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Новый сервер" else "Сервер") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Название") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(host, { host = it }, label = { Text("Адрес (IP или имя)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    port, { port = it }, label = { Text("Порт") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(path, { path = it }, label = { Text("Путь RPC") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("HTTPS", Modifier.weight(1f))
                    Switch(checked = https, onCheckedChange = { https = it })
                }
                OutlinedTextField(user, { user = it }, label = { Text("Логин (если задан)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    pass, { pass = it }, label = { Text("Пароль") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(
                    enabled = host.isNotBlank() && !testing,
                    onClick = {
                        testing = true
                        testResult = "Проверяю…"
                        scope.launch {
                            testResult = try {
                                vm.testServer(build())
                            } catch (e: Exception) {
                                "Ошибка: ${e.message}"
                            }
                            testing = false
                        }
                    },
                ) {
                    Icon(Icons.Default.Settings, null)
                    Text("Проверить соединение", Modifier.padding(start = 8.dp))
                }
                testResult?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(enabled = host.isNotBlank(), onClick = { vm.saveServer(build()); onDismiss() }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
