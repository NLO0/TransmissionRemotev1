package app.transmissionremote.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.transmissionremote.data.St
import app.transmissionremote.data.Torrent
import app.transmissionremote.util.formatBytes
import app.transmissionremote.util.formatEta
import app.transmissionremote.util.formatPercent
import app.transmissionremote.util.formatRatio
import app.transmissionremote.util.formatSpeed

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ListScreen(
    vm: AppViewModel,
    onOpen: (Torrent) -> Unit,
    onAdd: () -> Unit,
    onServers: () -> Unit,
    onSession: (String) -> Unit,
    onStats: () -> Unit,
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    MessageEffect(vm, snackbar)

    var searching by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var selMenu by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var removeDialog by remember { mutableStateOf(false) }
    var moveDialog by remember { mutableStateOf(false) }
    var trackersDialog by remember { mutableStateOf(false) }
    var queueDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = ui.selected.isNotEmpty()) { vm.clearSelection() }
    BackHandler(enabled = searching && ui.selected.isEmpty()) {
        searching = false
        vm.setQuery("")
    }

    val visible = remember(ui.torrents, ui.serverFilter, ui.statusFilter, ui.sort, ui.sortDesc, ui.query) {
        ui.visible()
    }
    val serverNames = remember(ui.servers) { ui.servers.associate { it.id to it.name } }
    val selecting = ui.selected.isNotEmpty()
    val scopedServer = ui.serverFilter ?: ui.servers.singleOrNull()?.id

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            when {
                selecting -> TopAppBar(
                    title = { Text("Выбрано: ${ui.selected.size}") },
                    navigationIcon = {
                        IconButton(onClick = { vm.clearSelection() }) { Icon(Icons.Default.Close, "Снять выбор") }
                    },
                    actions = {
                        IconButton(onClick = { vm.start(ui.selected) }) { Icon(Icons.Default.PlayArrow, "Старт") }
                        IconButton(onClick = { vm.pause(ui.selected) }) { Icon(Icons.Default.Pause, "Пауза") }
                        IconButton(onClick = { vm.selectAll(visible.map { it.key }) }) {
                            Icon(Icons.Default.SelectAll, "Выбрать все")
                        }
                        Box {
                            IconButton(onClick = { selMenu = true }) { Icon(Icons.Default.MoreVert, "Ещё") }
                            DropdownMenu(expanded = selMenu, onDismissRequest = { selMenu = false }) {
                                DropdownMenuItem(text = { Text("Старт без очереди") }, onClick = { selMenu = false; vm.startNow(ui.selected) })
                                DropdownMenuItem(text = { Text("Проверить данные") }, onClick = { selMenu = false; vm.verify(ui.selected) })
                                DropdownMenuItem(text = { Text("Переанонсировать") }, onClick = { selMenu = false; vm.reannounce(ui.selected) })
                                DropdownMenuItem(text = { Text("В начало очереди") }, onClick = { selMenu = false; vm.queueMove("top", ui.selected) })
                                DropdownMenuItem(text = { Text("В конец очереди") }, onClick = { selMenu = false; vm.queueMove("bottom", ui.selected) })
                                DropdownMenuItem(text = { Text("Добавить трекеры…") }, onClick = { selMenu = false; trackersDialog = true })
                                DropdownMenuItem(text = { Text("Трекеры из источника") }, onClick = { selMenu = false; vm.addTrackersFromSource(ui.selected); vm.clearSelection() })
                                DropdownMenuItem(text = { Text("Переместить данные…") }, onClick = { selMenu = false; moveDialog = true })
                                DropdownMenuItem(text = { Text("Удалить…") }, onClick = { selMenu = false; removeDialog = true })
                            }
                        }
                    },
                )

                searching -> TopAppBar(
                    title = {
                        TextField(
                            value = ui.query,
                            onValueChange = vm::setQuery,
                            placeholder = { Text("Поиск") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { searching = false; vm.setQuery("") }) { Icon(Icons.Default.Close, "Закрыть поиск") }
                    },
                )

                else -> TopAppBar(
                    title = { Text("Transmission") },
                    actions = {
                        IconButton(onClick = { searching = true }) { Icon(Icons.Default.Search, "Поиск") }
                        Box {
                            IconButton(onClick = { sortMenu = true }) { Icon(Icons.Default.Sort, "Сортировка") }
                            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                                SortMode.values().forEach { m ->
                                    DropdownMenuItem(
                                        text = { Text(m.label + if (ui.sort == m) (if (ui.sortDesc) "  ↓" else "  ↑") else "") },
                                        onClick = {
                                            if (ui.sort == m) vm.toggleSortDir() else vm.setSort(m)
                                            sortMenu = false
                                        },
                                    )
                                }
                            }
                        }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Меню") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text("Пауза всем") }, onClick = { menu = false; vm.pauseAll(ui.serverFilter) })
                                DropdownMenuItem(text = { Text("Запустить все") }, onClick = { menu = false; vm.resumeAll(ui.serverFilter) })
                                DropdownMenuItem(text = { Text("Альт. скорость вкл/выкл") }, onClick = { menu = false; vm.toggleAltSpeed(ui.serverFilter) })
                                if (scopedServer != null) {
                                    DropdownMenuItem(text = { Text("Настройки сервера") }, onClick = { menu = false; onSession(scopedServer) })
                                }
                                DropdownMenuItem(text = { Text("Статистика") }, onClick = { menu = false; onStats() })
                                DropdownMenuItem(text = { Text("Серверы и приложение") }, onClick = { menu = false; onServers() })
                            }
                        }
                    },
                )
            }
        },
        bottomBar = {
            if (selecting) {
                BottomAppBar(
                    actions = {
                        IconButton(onClick = { vm.start(ui.selected) }) { Icon(Icons.Default.PlayArrow, "Старт") }
                        IconButton(onClick = { vm.pause(ui.selected) }) { Icon(Icons.Default.Pause, "Пауза") }
                        IconButton(onClick = { vm.verify(ui.selected) }) { Icon(Icons.Default.Refresh, "Проверить данные") }
                        IconButton(onClick = { vm.reannounce(ui.selected) }) { Icon(Icons.Default.Sync, "Реанонс") }
                        IconButton(onClick = { trackersDialog = true }) { Icon(Icons.Default.Link, "Добавить трекеры") }
                        IconButton(onClick = { moveDialog = true }) { Icon(Icons.Default.Folder, "Переместить") }
                        IconButton(onClick = { removeDialog = true }) { Icon(Icons.Default.Delete, "Удалить") }
                    },
                )
            }
        },
        floatingActionButton = {
            if (ui.servers.isNotEmpty() && !selecting) {
                FloatingActionButton(onClick = onAdd) { Icon(Icons.Default.Add, "Добавить торрент") }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (ui.serversLoaded && ui.servers.isEmpty()) {
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Серверов пока нет", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Добавьте адрес Transmission (например, 192.168.1.10:9091).",
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = onServers) { Text("Добавить сервер") }
                }
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        FilterChip(selected = ui.serverFilter == null, onClick = { vm.setServerFilter(null) }, label = { Text("Все") })
                    }
                    items(ui.servers, key = { it.id }) { s ->
                        val st = ui.status[s.id]
                        FilterChip(
                            selected = ui.serverFilter == s.id,
                            onClick = { vm.setServerFilter(s.id) },
                            label = {
                                Text(
                                    when {
                                        st == null -> s.name
                                        st.online -> "${s.name}  ↓${formatSpeed(st.down)}"
                                        else -> "${s.name} · офлайн"
                                    }
                                )
                            },
                            leadingIcon = {
                                Box(
                                    Modifier.size(8.dp).background(
                                        when (st?.online) {
                                            true -> Color(0xFF4CAF50)
                                            false -> Color(0xFFE53935)
                                            null -> Color.Gray
                                        },
                                        CircleShape,
                                    )
                                )
                            },
                        )
                    }
                }
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(StatusFilter.values().toList()) { f ->
                        FilterChip(selected = ui.statusFilter == f, onClick = { vm.setStatusFilter(f) }, label = { Text(f.label) })
                    }
                }

                val scopeServers = ui.servers.filter { ui.serverFilter == null || it.id == ui.serverFilter }
                val altOn = scopeServers.any { ui.status[it.id]?.altSpeed == true }
                val downloadingKeys = ui.torrents
                    .filter { (ui.serverFilter == null || it.serverId == ui.serverFilter) && it.status == St.DOWNLOAD }
                    .map { it.key }
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AssistChip(onClick = { vm.resumeAll(ui.serverFilter) }, label = { Text("Запустить все") }, leadingIcon = { Icon(Icons.Default.PlayArrow, null) })
                    AssistChip(onClick = { vm.pauseAll(ui.serverFilter) }, label = { Text("Пауза всем") }, leadingIcon = { Icon(Icons.Default.Pause, null) })
                    FilterChip(selected = altOn, onClick = { vm.toggleAltSpeed(ui.serverFilter) }, label = { Text("Альт. скорость") }, leadingIcon = { Icon(Icons.Default.Speed, null) })
                    AssistChip(onClick = { vm.reannounce(downloadingKeys) }, label = { Text("Реанонс загрузок") }, leadingIcon = { Icon(Icons.Default.Sync, null) })
                    AssistChip(onClick = { vm.addTrackersFromSource(downloadingKeys) }, label = { Text("Трекеры загрузкам") }, leadingIcon = { Icon(Icons.Default.Link, null) })
                    AssistChip(onClick = { queueDialog = true }, label = { Text("Очередь загрузок") }, leadingIcon = { Icon(Icons.Default.Sort, null) })
                    if (scopedServer != null) {
                        AssistChip(onClick = { onSession(scopedServer) }, label = { Text("Настройки сервера") }, leadingIcon = { Icon(Icons.Default.Settings, null) })
                    }
                    AssistChip(onClick = onStats, label = { Text("Статистика") }, leadingIcon = { Icon(Icons.Default.BarChart, null) })
                }

                val scoped = ui.torrents.filter { ui.serverFilter == null || it.serverId == ui.serverFilter }
                Text(
                    "${visible.size} из ${scoped.size}  ·  ↓ ${formatSpeed(scoped.sumOf { it.rateDownload })}  ↑ ${formatSpeed(scoped.sumOf { it.rateUpload })}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )

                if (visible.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Нет торрентов", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                        items(visible, key = { it.key }) { t ->
                            TorrentRow(
                                t = t,
                                serverName = if (ui.servers.size > 1) serverNames[t.serverId] else null,
                                selected = t.key in ui.selected,
                                selecting = selecting,
                                onClick = { if (selecting) vm.toggleSelect(t.key) else onOpen(t) },
                                onLongClick = { vm.toggleSelect(t.key) },
                                onToggle = {
                                    if (t.status == St.STOPPED) vm.start(listOf(t.key)) else vm.pause(listOf(t.key))
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (removeDialog) {
        var deleteData by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { removeDialog = false },
            title = { Text("Удалить ${ui.selected.size} торрент(ов)?") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = deleteData, onCheckedChange = { deleteData = it })
                    Text("Удалить также скачанные файлы")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.remove(ui.selected, deleteData)
                    vm.clearSelection()
                    removeDialog = false
                }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { removeDialog = false }) { Text("Отмена") } },
        )
    }

    if (queueDialog) {
        TextInputDialog(
            title = "Очередь загрузок",
            label = "Одновременно качать (1 — строго по одному)",
            initial = "1",
            confirm = "Применить",
            onDismiss = { queueDialog = false },
            onConfirm = { n ->
                vm.setDownloadQueue(ui.serverFilter, n.toIntOrNull() ?: 1)
                queueDialog = false
            },
        )
    }

    if (trackersDialog) {
        TrackersDialog(onDismiss = { trackersDialog = false }) { urls ->
            vm.addTrackers(ui.selected, urls)
            vm.clearSelection()
            trackersDialog = false
        }
    }

    if (moveDialog) {
        TextInputDialog(
            title = "Переместить данные",
            label = "Новая папка на сервере",
            initial = ui.torrents.firstOrNull { it.key in ui.selected }?.downloadDir.orEmpty(),
            confirm = "Переместить",
            onDismiss = { moveDialog = false },
            onConfirm = {
                vm.move(ui.selected, it)
                vm.clearSelection()
                moveDialog = false
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TorrentRow(
    t: Torrent,
    serverName: String?,
    selected: Boolean,
    selecting: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggle: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val accent = when {
        t.error != 0 -> cs.error
        t.status == St.DOWNLOAD -> cs.primary
        t.status == St.SEED -> cs.tertiary
        t.status == St.STOPPED -> cs.outline
        else -> cs.secondary
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = if (selected) cs.secondaryContainer else cs.surfaceContainer),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Row(
            Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(t.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    LinearProgressIndicator(
                        progress = { t.percentDone.toFloat().coerceIn(0f, 1f) },
                        color = accent,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        formatPercent(t.percentDone),
                        style = MaterialTheme.typography.labelLarge,
                        color = accent,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                val info = buildString {
                    if (t.status == St.DOWNLOAD) {
                        append("↓ ${formatSpeed(t.rateDownload)}  ")
                        append("↑ ${formatSpeed(t.rateUpload)}  ·  ")
                        append("${formatBytes(t.sizeWhenDone - t.leftUntilDone)} / ${formatBytes(t.sizeWhenDone)}  ·  ")
                        append(formatEta(t.eta))
                    } else {
                        append(formatBytes(t.totalSize))
                        append("  ·  рейтинг ${formatRatio(t.uploadRatio)}")
                        if (t.rateUpload > 0) append("  ·  ↑ ${formatSpeed(t.rateUpload)}")
                    }
                }
                Text(info, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                Row(Modifier.padding(top = 2.dp)) {
                    Text(statusLabel(t), style = MaterialTheme.typography.labelMedium, color = accent)
                    if (serverName != null) {
                        Text("  ·  $serverName", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                    }
                }
                if (t.error != 0 && t.errorString.isNotBlank()) {
                    Text(t.errorString, style = MaterialTheme.typography.bodySmall, color = cs.error, maxLines = 2)
                }
            }
            if (selecting) {
                Checkbox(checked = selected, onCheckedChange = { onLongClick() })
            } else {
                IconButton(onClick = onToggle) {
                    Icon(if (t.status == St.STOPPED) Icons.Default.PlayArrow else Icons.Default.Pause, null)
                }
            }
        }
    }
}
