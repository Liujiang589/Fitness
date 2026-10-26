package app.duolian.local.domain

import java.util.Collections
import java.util.IdentityHashMap

object CrashDiagnostics {
    const val MAX_CHARS = 16000
    fun sanitize(value: String): String = value
        .replace(Regex("(?i)(api[_-]?key|token|secret|authorization)\\s*[:=]\\s*[^\\s,&;]+")) { it.groupValues[1]+"=[已隐藏]" }
        .replace(Regex("(?i)[0-9a-f]{32,}"), "[已隐藏]")
        .replace(Regex("(?i)(latitude|longitude|lat|lon|lng)[\"']?\\s*[:=]\\s*-?\\d+(?:\\.\\d+)?")) { it.groupValues[1]+"=[已隐藏]" }
    fun format(version: String, process: String, thread: String, time: String, error: Throwable): String {
        val text=StringBuilder("独练异常诊断\n版本：$version\n时间：$time\n进程：$process\n线程：$thread\n")
        val seen=Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
        var cause: Throwable?=error
        var count=0
        while(cause!=null && count++<8 && seen.add(cause) && text.length<MAX_CHARS) {
            text.append("\n").append(cause.javaClass.name).append(": ").append(cause.message?.take(800).orEmpty()).append('\n')
            cause.stackTrace.take(64).forEach { text.append("  at ").append(it.toString()).append('\n') }
            cause=cause.cause
        }
        return sanitize(text.toString()).take(MAX_CHARS)
    }
}
