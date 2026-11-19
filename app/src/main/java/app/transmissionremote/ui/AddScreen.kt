package app.transmissionremote.ui

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddScreen(vm: AppViewModel, onBack: () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val initial = remember { vm.pending.value }
    var text by remember { mutableStateOf(initial?.magnet.orEmpty()) }
    var uris by remember { mutableStateOf(initial?.uris ?: emptyList<Uri>()) }
    var serverId by remember { mutableStateOf(ui.serverFilter ?: ui.servers.firstOrNull()?.id.orEmpty()) }
    var dir by remember { mutableStateOf("") }
    var defaultDir by remember { mutableStateOf("") }
    var paused by remember { mutableStateOf(false) }
    var priority by remember { mutableStateOf(0) }
    var labels by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var sequential by remember { mutableStateOf(false) }

    LaunchedEffect(ui.servers) {
        if (serverId.isEmpty() || ui.servers.none { it.id == serverId }) serverId = ui.servers.firstOrNull()?.id.orEmpty()
    }
    LaunchedEffect(serverId) {
        if (serverId.isNotEmpty()) defaultDir = vm.defaultDir(serverId)
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { picked ->
        if (picked.isNotEmpty()) uris = uris + picked
    }

    fun fileName(u: Uri): String {
        runCatching {
            ctx.contentResolver.query(u, null, null, null, null)?.use { c ->
                val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (i >= 0 && c.moveToFirst()) return c.getString(i)
            }
        }
        return u.lastPathSegment ?: u.toString()
    }

    val magnets = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
    val canAdd = serverId.isNotEmpty() && (magnets.isNotEmpty() || uris.isNotEmpty()) && !busy

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Добавить торрент") },
                navigationIcon = {
                    IconButton(onClick = { vm.pending.value = null; onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                    }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (ui.servers.size > 1) {
                DropdownField("Сервер", ui.servers.map { it.id to it.name }, serverId) { serverId = it }
            }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Magnet-ссылки или URL (по одной в строке)") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, null)
                Text(".torrent файлы…", Modifier.padding(start = 8.dp))
            }
            uris.forEach { u ->
                Text("• ${fileName(u)}", style = MaterialTheme.typography.bodyMedium)
            }
            OutlinedTextField(
                value = dir,
                onValueChange = { dir = it },
                label = { Text("Папка загрузки на сервере") },
                placeholder = { Text(defaultDir.ifBlank { "по умолчанию" }) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            DropdownField("Приоритет", listOf(-1 to "Низкий", 0 to "Обычный", 1 to "Высокий"), priority) { priority = it }
            OutlinedTextField(
                value = labels,
                onValueChange = { labels = it },
                label = { Text("Метки (через запятую)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Добавить на паузе")
                    Text(
                        "Затем можно выбрать нужные файлы во вкладке «Файлы»",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = paused, onCheckedChange = { paused = it })
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Последовательная загрузка")
                    Text(
                        "Части качаются по порядку (удобно смотреть видео). Нужен Transmission 4.1+, на старых версиях игнорируется; на популярных раздачах может быть чуть медленнее.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = sequential, onCheckedChange = { sequential = it })
            }
            Button(
                enabled = canAdd,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    busy = true
                    scope.launch {
                        try {
                            val msg = vm.addTorrents(
                                serverId, magnets, uris, dir.trim(), paused, priority,
                                labels.split(',').map { it.trim() }.filter { it.isNotEmpty() },
                                sequential,
                            )
                            vm.pending.value = null
                            vm.toast(msg)
                            onBack()
                        } catch (e: Exception) {
                            busy = false
                            snackbar.showSnackbar(e.message ?: "Ошибка")
                        }
                    }
                },
            ) {
                if (busy) CircularProgressIndicator(Modifier.padding(end = 8.dp), strokeWidth = 2.dp)
                Text("Добавить")
            }
        }
    }
}
