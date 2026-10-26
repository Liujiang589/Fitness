package app.duolian.local.outdoor

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.*
import app.duolian.local.BuildConfig
import app.duolian.local.domain.OutdoorPlace
import app.duolian.local.domain.RoutePoint

/** Only IPC runs in the recorder process. A third-party locator cannot terminate the workout process. */
internal class NetworkLocationAid(private val context: Context,
    private val position: (OutdoorPlace) -> Unit, private val failure: (String) -> Unit) {
    private var connection: ServiceConnection? = null
    private var worker: Messenger? = null
    private var generation = 0
    fun start() {
        if (connection != null || !BuildConfig.AMAP_CONFIGURED || !enabled(context)) return
        val token = ++generation
        val replies = Messenger(Handler(Looper.getMainLooper()) { message ->
            if (token == generation && connection != null) {
                if (message.what == LocationAidProtocol.POSITION) {
                    val data = message.data
                    position(OutdoorPlace(RoutePoint(data.getDouble("lat", Double.NaN),
                        data.getDouble("lon", Double.NaN), data.getLong("time"),
                        data.getFloat("accuracy", Float.POSITIVE_INFINITY)), data.getLong("tick"),
                        data.getBoolean("gcj02"), data.getBoolean("cached"), data.getString("source") ?: "网络辅助定位"))
                } else if (message.what == LocationAidProtocol.ERROR)
                    failure(message.data.getString("note") ?: "辅助定位暂不可用，系统定位仍在继续。")
            }
            true
        })
        val service = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                if (token != generation) return
                worker = Messenger(binder)
                runCatching { worker!!.send(Message.obtain(null, LocationAidProtocol.START).apply { replyTo = replies }) }
                    .onFailure { stop(); failure("辅助定位连接失败，系统定位仍在继续。") }
            }
            override fun onServiceDisconnected(name: ComponentName) {
                if (token == generation) { stop(); failure("辅助定位已中断，计时与系统定位继续。") }
            }
            override fun onBindingDied(name: ComponentName) = onServiceDisconnected(name)
            override fun onNullBinding(name: ComponentName) = onServiceDisconnected(name)
        }
        connection = service
        try {
            val intent = Intent(context, LocationAidService::class.java).putExtra("consented", true)
            if (!context.bindService(intent, service, Context.BIND_AUTO_CREATE or Context.BIND_IMPORTANT)) {
                connection = null; failure("辅助定位未启动，系统定位仍在继续。")
            }
        } catch (_: Exception) { stop(); failure("辅助定位暂不可用，系统定位仍在继续。") }
    }
    fun stop() {
        generation++
        val service = connection; connection = null
        runCatching { worker?.send(Message.obtain(null, LocationAidProtocol.STOP)) }; worker = null
        if (service != null) runCatching { context.unbindService(service) }
    }
    companion object {
        const val CONSENT_KEY = "use-amap-v1"
        fun enabled(context: Context) = context.getSharedPreferences("outdoor-consent", 0)
            .getBoolean(CONSENT_KEY, false)
    }
}

internal object LocationAidProtocol {
    const val START = 1
    const val STOP = 2
    const val POSITION = 3
    const val ERROR = 4
}
