package app.duolian.local.outdoor

import android.app.Service
import android.content.Intent
import android.os.*
import app.duolian.local.domain.OutdoorPlace

/** Explicitly bound, non-exported worker. SDK client and APSService share :location_aid. */
class LocationAidService : Service() {
    private var consented = false
    private var listener: Messenger? = null
    private val engine by lazy { AmapLocationEngine(applicationContext,
        position = ::position, failure = ::failure) }
    private val commands = Messenger(Handler(Looper.getMainLooper()) { message ->
        when (message.what) {
            LocationAidProtocol.START -> {
                listener = message.replyTo
                if (consented && OutdoorService.canLocate(this)) engine.start()
                else failure("辅助定位未授权，系统定位仍在继续。")
            }
            LocationAidProtocol.STOP -> { engine.stop(); listener = null }
        }
        true
    })
    override fun onBind(intent: Intent?): IBinder? {
        consented = intent?.getBooleanExtra("consented", false) == true
        return if (consented) commands.binder else null
    }
    private fun position(place: OutdoorPlace) {
        val data = Bundle().apply {
            putDouble("lat", place.point.latitude); putDouble("lon", place.point.longitude)
            putLong("time", place.point.time); putFloat("accuracy", place.point.accuracy)
            putLong("tick", place.sampleTick); putBoolean("gcj02", place.gcj02)
            putBoolean("cached", place.cached); putString("source", place.source)
        }
        send(LocationAidProtocol.POSITION, data)
    }
    private fun failure(note: String) = send(LocationAidProtocol.ERROR, Bundle().apply { putString("note", note) })
    private fun send(code: Int, data: Bundle) {
        runCatching { listener?.send(Message.obtain(null, code).apply { this.data = data }) }
    }
    override fun onUnbind(intent: Intent?): Boolean {
        engine.stop(); listener = null; stopSelf(); return false
    }
    override fun onDestroy() { engine.stop(); listener = null; super.onDestroy() }
}
