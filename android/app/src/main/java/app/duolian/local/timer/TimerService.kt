package app.duolian.local.timer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import app.duolian.local.MainActivity
import app.duolian.local.R
import app.duolian.local.domain.Codec
import app.duolian.local.domain.HiitEngine
import app.duolian.local.domain.HiitPhaseKind
import app.duolian.local.domain.HiitTemplate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel

/**
 * Foreground countdown used by the rest timer and HIIT intervals, so the countdown stays
 * accurate (and audible) while the app is in the background or the screen is off.
 */
class TimerService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private var tone: ToneGenerator? = null

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL) == null) {
                manager.createNotificationChannel(NotificationChannel(CHANNEL, "训练计时",
                    NotificationManager.IMPORTANCE_LOW).apply {
                    description = "组间休息与 HIIT 间歇计时"
                    setShowBadge(false)
                })
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_REST -> {
                val remaining = (intent.getLongExtra(EXTRA_DEADLINE, 0L) - System.currentTimeMillis()).coerceAtLeast(0L)
                startRest(remaining)
            }
            ACTION_HIIT -> {
                val json = intent.getStringExtra(EXTRA_TEMPLATE) ?: return stopNow()
                val template = runCatching { Codec.decodeFromString<HiitTemplate>(json) }.getOrNull()
                    ?: return stopNow()
                startHiit(template)
            }
            ACTION_STOP -> return stopNow()
            else -> return stopNow()
        }
        return START_NOT_STICKY
    }

    private fun stopNow(): Int {
        job?.cancel()
        stopSelf()
        return START_NOT_STICKY
    }

    private fun startRest(remainingMs: Long) {
        val seconds = ((remainingMs + 999) / 1000).toInt()
        startForegroundCompat(restNotification(seconds, remainingMs))
        job?.cancel()
        job = scope.launch {
            delay(remainingMs)
            beep(2)
            notifyRestDone()
            delay(4_000)
            stopSelf()
        }
    }

    private fun startHiit(template: HiitTemplate) {
        startForegroundCompat(hiitNotification(template, HiitEngine.phaseAt(template, 0)))
        job?.cancel()
        job = scope.launch {
            val start = SystemClock.elapsedRealtime()
            var visited = ""
            while (isActive) {
                val phase = HiitEngine.phaseAt(template, SystemClock.elapsedRealtime() - start)
                if (phase.kind == HiitPhaseKind.DONE) {
                    beep(3)
                    manager().notify(ID_TIMER, doneNotification(template)
                        .setContentText("已完成 ${template.rounds} 轮，可以休息了").build())
                    delay(5_000)
                    stopSelf()
                    break
                }
                val key = "${phase.kind}#${phase.round}"
                if (key != visited) {
                    visited = key
                    if (phase.kind == HiitPhaseKind.WORK) beep(1)
                    if (phase.kind == HiitPhaseKind.REST) beep(2)
                    manager().notify(ID_TIMER, hiitNotification(template, phase))
                }
                delay(200)
            }
        }
    }

    private fun restNotification(seconds: Int, remainingMs: Long): Notification =
        base("组间休息", "还剩 $seconds 秒")
            .setWhen(System.currentTimeMillis() + remainingMs)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setOngoing(true)
            .addAction(0, "结束计时", stopIntent())
            .build()

    private fun notifyRestDone() {
        manager().notify(ID_TIMER, base("休息结束", "继续下一组吧").setAutoCancel(true)
            .addAction(0, "打开独练", openIntent()).build())
    }

    private fun hiitNotification(template: HiitTemplate, phase: app.duolian.local.domain.HiitPhase): Notification {
        val title = when (phase.kind) {
            HiitPhaseKind.PREPARE -> "准备开始"
            else -> "第 ${phase.round}/${template.rounds} 轮 · ${phase.kind.label}"
        }
        return base(title, "还剩 ${phase.remainingSeconds} 秒")
            .setWhen(System.currentTimeMillis() + phase.remainingMs)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setOngoing(true)
            .setProgress(template.rounds, phase.round, false)
            .addAction(0, "结束训练", stopIntent())
            .build()
    }

    private fun doneNotification(template: HiitTemplate): NotificationCompat.Builder =
        base("HIIT 完成", "${template.name} · ${template.rounds} 轮")
            .setAutoCancel(true)
            .addAction(0, "打开独练", openIntent())

    private fun base(title: String, text: String): NotificationCompat.Builder =
        NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_duolian_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openIntent())
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)

    private fun startForegroundCompat(notification: Notification) {
        // API 34 requires an explicit foreground service type; older versions reject unknown types.
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(ID_TIMER, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(ID_TIMER, notification)
        }
    }

    private fun manager(): NotificationManager = getSystemService(NotificationManager::class.java)

    private fun openIntent(): PendingIntent = PendingIntent.getActivity(this, 0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun stopIntent(): PendingIntent = PendingIntent.getService(this, 1,
        Intent(this, TimerService::class.java).setAction(ACTION_STOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun beep(times: Int) {
        runCatching {
            val generator = tone ?: ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80).also { tone = it }
            repeat(times) { generator.startTone(ToneGenerator.TONE_PROP_BEEP, 180); Thread.sleep(260) }
        }
    }

    override fun onDestroy() {
        job?.cancel()
        scope.cancel()
        runCatching { tone?.release() }
        tone = null
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "duolian_timer"
        private const val ID_TIMER = 4201
        const val ACTION_REST = "app.duolian.local.action.REST"
        const val ACTION_HIIT = "app.duolian.local.action.HIIT"
        const val ACTION_STOP = "app.duolian.local.action.STOP_TIMER"
        private const val EXTRA_DEADLINE = "deadline"
        private const val EXTRA_TEMPLATE = "template"

        /** Starts (or replaces) the rest countdown; silent no-op when the service cannot start. */
        fun startRest(context: Context, deadlineMs: Long) {
            val intent = Intent(context, TimerService::class.java).setAction(ACTION_REST)
                .putExtra(EXTRA_DEADLINE, deadlineMs)
            runCatching { context.startForegroundService(intent) }
        }

        fun startHiit(context: Context, template: HiitTemplate) {
            val intent = Intent(context, TimerService::class.java).setAction(ACTION_HIIT)
                .putExtra(EXTRA_TEMPLATE, Codec.encodeToString(HiitTemplate.serializer(), template))
            runCatching { context.startForegroundService(intent) }
        }

        fun stop(context: Context) {
            runCatching { context.startService(Intent(context, TimerService::class.java).setAction(ACTION_STOP)) }
            runCatching { context.stopService(Intent(context, TimerService::class.java)) }
        }
    }
}
