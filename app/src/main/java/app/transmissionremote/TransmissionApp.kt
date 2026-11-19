package app.transmissionremote

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import app.transmissionremote.data.AppStore
import app.transmissionremote.data.RpcClient
import app.transmissionremote.data.Server
import app.transmissionremote.data.TransmissionApi
import app.transmissionremote.work.CompletionWorker
import java.util.concurrent.TimeUnit

class ClientPool {
    private val map = HashMap<String, Pair<Server, TransmissionApi>>()

    @Synchronized
    fun api(server: Server): TransmissionApi {
        val cur = map[server.id]
        if (cur != null && cur.first == server) return cur.second
        val api = TransmissionApi(RpcClient(server))
        map[server.id] = server to api
        return api
    }
}

class TransmissionApp : Application() {
    val store by lazy { AppStore(this) }
    val clients = ClientPool()

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_DONE, "Загрузка завершена", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val req = PeriodicWorkRequestBuilder<CompletionWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork("completion-check", ExistingPeriodicWorkPolicy.KEEP, req)
    }

    companion object {
        const val CHANNEL_DONE = "done"
    }
}
