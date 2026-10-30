package net.nekomo.nex.log

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Rolling on-device log (filesDir/logs/nex.log). Never logs secrets: callers must not pass API keys or tokens. */
object NexLog {
    private const val MAX_BYTES = 512L * 1024
    private var file: File? = null
    @Volatile var debug: Boolean = false
    private val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    fun init(context: Context) {
        file = File(context.filesDir, "logs/nex.log").apply { parentFile?.mkdirs() }
    }

    fun d(tag: String, msg: String) {
        if (debug) write("D", tag, msg, null)
    }

    fun i(tag: String, msg: String) = write("I", tag, msg, null)
    fun w(tag: String, msg: String, t: Throwable? = null) = write("W", tag, msg, t)
    fun e(tag: String, msg: String, t: Throwable? = null) = write("E", tag, msg, t)

    @Synchronized
    private fun write(level: String, tag: String, msg: String, t: Throwable?) {
        when (level) {
            "E" -> Log.e(tag, msg, t)
            "W" -> Log.w(tag, msg, t)
            else -> if (debug) Log.d(tag, msg)
        }
        val f = file ?: return
        runCatching {
            if (f.length() > MAX_BYTES) {
                val old = File(f.parentFile, "nex.1.log")
                old.delete()
                f.renameTo(old)
            }
            f.appendText(buildString {
                append(fmt.format(Date())).append(' ').append(level).append('/').append(tag).append(": ").append(msg).append('\n')
                if (t != null) append(Log.getStackTraceString(t)).append('\n')
            })
        }
    }

    fun read(): String {
        val f = file ?: return ""
        val old = File(f.parentFile, "nex.1.log")
        return (if (old.exists()) old.readText() else "") + (if (f.exists()) f.readText() else "")
    }

    fun sizeBytes(): Long {
        val f = file ?: return 0
        return f.length() + File(f.parentFile, "nex.1.log").length()
    }

    @Synchronized
    fun clear() {
        file?.let { it.delete(); File(it.parentFile, "nex.1.log").delete() }
    }
}
