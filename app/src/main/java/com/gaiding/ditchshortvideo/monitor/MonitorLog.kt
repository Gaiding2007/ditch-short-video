package com.gaiding.ditchshortvideo.monitor

import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.ArrayDeque

/**
 * Tiny in-memory event log for field diagnostics.
 *
 * It deliberately records only page/state metadata: app label, activity,
 * detection reason and timer transitions. It never stores visible page text.
 * The log is lost when the service process dies, which is fine: the useful
 * cases are "it stopped counting while the service was still connected".
 */
object MonitorLog {
    private const val MAX_ENTRIES = 80
    private val formatter = DateTimeFormatter.ofPattern("HH:mm:ss")
    private val entries = ArrayDeque<String>()

    @Synchronized
    fun add(message: String) {
        val line = "${LocalTime.now().format(formatter)} $message"
        entries.addLast(line)
        while (entries.size > MAX_ENTRIES) entries.removeFirst()
    }

    @Synchronized
    fun dump(): String = if (entries.isEmpty()) "无" else entries.joinToString("\n")

    @Synchronized
    fun clear() {
        entries.clear()
    }
}
