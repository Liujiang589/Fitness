package app.duolian.local

import android.app.ActivityManager
import android.app.Application
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.os.Process
import androidx.annotation.RequiresApi
import app.duolian.local.domain.CrashDiagnostics
import java.io.File
import java.time.Instant

/** Local diagnostics only. Preserve Android's crash handler; never swallow a fatal exception. */
internal object AppCrashReport {
    private fun file(context: Context)=File(context.filesDir,"diagnostics/last-crash.txt")
    fun install(app: Application) {
        val previous=Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val process=if(Build.VERSION.SDK_INT>=28) Application.getProcessName() else "${app.packageName} pid=${Process.myPid()}"
                val text=CrashDiagnostics.format("V${BuildConfig.VERSION_NAME} / ${BuildConfig.VERSION_CODE}",
                    process,thread.name,Instant.now().toString(),error)
                file(app).apply { parentFile?.mkdirs();writeText(text,Charsets.UTF_8) }
            }
            if(previous!=null)previous.uncaughtException(thread,error)
            else { Process.killProcess(Process.myPid());kotlin.system.exitProcess(10) }
        }
    }
    fun read(context: Context): String = runCatching {
        val local=file(context)
        val saved=if(local.isFile) local.bufferedReader().use { it.readText().take(CrashDiagnostics.MAX_CHARS) } else ""
        val system=if(Build.VERSION.SDK_INT>=30) runCatching { systemReport(context,local.lastModified()) }.getOrDefault("") else ""
        listOf(system,saved).filter { it.isNotBlank() }.joinToString("\n\n").ifBlank { "尚无可读取的异常记录。" }
    }.getOrDefault("暂时无法读取异常记录。")
    @RequiresApi(30)
    private fun systemReport(context: Context, savedAt: Long): String {
        val exit=context.getSystemService(ActivityManager::class.java)
            .getHistoricalProcessExitReasons(context.packageName,0,5)
            .firstOrNull { it.reason in listOf(ApplicationExitInfo.REASON_CRASH,ApplicationExitInfo.REASON_CRASH_NATIVE) &&
                it.timestamp>savedAt }
        return exit?.let { "系统异常退出记录\n时间：${Instant.ofEpochMilli(it.timestamp)}\n进程：${it.processName}\n"+
            "类型：${if(it.reason==ApplicationExitInfo.REASON_CRASH_NATIVE)"原生组件崩溃" else "Java异常"}\n"+
            CrashDiagnostics.sanitize(it.description.orEmpty()).take(1000) }.orEmpty()
    }
}
