package app.transmissionremote.work

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.transmissionremote.MainActivity
import app.transmissionremote.TransmissionApp
import kotlinx.coroutines.flow.first

/** Раз в ~15 минут опрашивает серверы и уведомляет о торрентах, которые докачались с прошлой проверки. */
class CompletionWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    @SuppressLint("MissingPermission")
    override suspend fun doWork(): Result {
        val app = applicationContext as TransmissionApp
        val store = app.store
        if (!store.prefs.first().notify) return Result.success()

        val servers = store.servers.first()
        val doneNow = HashMap<String, String>() // key -> name
        for (s in servers) {
            try {
                val list = app.clients.api(s).torrents(s.id)
                for (t in list) if (t.percentDone >= 1.0 && t.hashString.isNotEmpty()) {
                    doneNow["${s.id}:${t.hashString}"] = "${s.name}: ${t.name}"
                }
            } catch (e: Exception) {
                // сервер недоступен — пропускаем
            }
        }

        val known = store.completedKnown()
        if (known != null) {
            val fresh = doneNow.filterKeys { it !in known }
            if (fresh.isNotEmpty()) {
                val pi = PendingIntent.getActivity(
                    applicationContext, 0,
                    Intent(applicationContext, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                for ((k, name) in fresh) {
                    val n = NotificationCompat.Builder(applicationContext, TransmissionApp.CHANNEL_DONE)
                        .setSmallIcon(android.R.drawable.stat_sys_download_done)
                        .setContentTitle("Загрузка завершена")
                        .setContentText(name)
                        .setContentIntent(pi)
                        .setAutoCancel(true)
                        .build()
                    try {
                        NotificationManagerCompat.from(applicationContext).notify(k.hashCode(), n)
                    } catch (e: SecurityException) {
                    }
                }
            }
        }
        store.saveCompleted((known ?: emptySet()) + doneNow.keys)
        return Result.success()
    }
}
