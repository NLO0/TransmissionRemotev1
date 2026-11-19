package app.transmissionremote.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.transmissionremote.util.formatBytes
import app.transmissionremote.util.formatDuration
import app.transmissionremote.util.formatSpeed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val stats by vm.stats.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.loadStats() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Статистика") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } },
                actions = { IconButton(onClick = { vm.loadStats() }) { Icon(Icons.Default.Refresh, "Обновить") } },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(stats, key = { it.server.id }) { st ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.padding(vertical = 12.dp)) {
                        Text(st.server.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp))
                        if (st.error != null || st.stats == null || st.session == null) {
                            Text("Ошибка: ${st.error}", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                        } else {
                            val s = st.stats
                            val cur = s.optJSONObject("current-stats")
                            val cum = s.optJSONObject("cumulative-stats")
                            KeyValue("Версия", "${st.session.optString("version")} (RPC ${st.session.optInt("rpc-version")})")
                            KeyValue("Торрентов", "${s.optInt("torrentCount")} (активных ${s.optInt("activeTorrentCount")}, на паузе ${s.optInt("pausedTorrentCount")})")
                            KeyValue("Скорость", "↓ ${formatSpeed(s.optLong("downloadSpeed"))}  ↑ ${formatSpeed(s.optLong("uploadSpeed"))}")
                            KeyValue("Свободно на диске", formatBytes(st.free))
                            if (cur != null) {
                                SectionTitle("Эта сессия")
                                KeyValue("Скачано / отдано", "${formatBytes(cur.optLong("downloadedBytes"))} / ${formatBytes(cur.optLong("uploadedBytes"))}")
                                KeyValue("Активность", formatDuration(cur.optLong("secondsActive")))
                            }
                            if (cum != null) {
                                SectionTitle("За всё время")
                                KeyValue("Скачано / отдано", "${formatBytes(cum.optLong("downloadedBytes"))} / ${formatBytes(cum.optLong("uploadedBytes"))}")
                                KeyValue("Активность", formatDuration(cum.optLong("secondsActive")))
                                KeyValue("Запусков", cum.optInt("sessionCount").toString())
                            }
                        }
                    }
                }
            }
        }
    }
}
