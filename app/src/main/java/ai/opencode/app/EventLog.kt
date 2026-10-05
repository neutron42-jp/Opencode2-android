package ai.opencode.app

import java.io.File
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/** Timestamped in-memory + file log. Rotated every process start. */
object EventLog {
    private const val MAX_LINES = 400
    private const val CURRENT = "opencode-log-current.txt"
    private const val PREVIOUS = "opencode-log-previous.txt"

    private val mem = ArrayDeque<String>()
    private var dir: File? = null
    private var inited = false

    @Synchronized
    fun init(dir: File, version: String) {
        if (inited) return
        inited = true
        this.dir = dir
        File(dir, PREVIOUS).delete()
        File(dir, CURRENT).renameTo(File(dir, PREVIOUS))
        mem.clear()
        log("app", "start v$version")
    }

    @Synchronized
    fun log(tag: String, msg: String) {
        val ts = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
        val line = "$ts [$tag] $msg"
        if (mem.size >= MAX_LINES) mem.removeFirst()
        mem.addLast(line)
        runCatching {
            File(dir ?: return, CURRENT).appendText(line + "\n")
        }
    }

    @Synchronized
    fun previousText(): String {
        val d = dir ?: return mem.joinToString("\n")
        val prev = File(d, PREVIOUS)
        val cur = File(d, CURRENT)
        val src = if (prev.exists()) prev else cur
        return runCatching { src.readText() }.getOrDefault(mem.joinToString("\n"))
    }
}
