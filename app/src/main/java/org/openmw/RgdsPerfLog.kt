package org.openmw

import android.os.Environment
import android.util.Log
import java.io.File
import kotlinx.coroutines.InternalCoroutinesApi

/**
 * RG DS: frame statistics in logcat, for measuring performance changes on the device.
 *
 * Off unless the file `OpenMW-DS/config/rgds-perf-log` exists on shared storage, so normal play pays
 * nothing. When on, every [PERIOD_MS] it writes one `RGDSPerf` line: the delivered frame rate and the
 * engine's WORK time per frame (measured before the frame cap sleeps, so it shows headroom under the
 * 30 fps cap), both over the frames of the last period, plus the busiest threads' share of a core.
 *
 * Read it with `adb logcat -s RGDSPerf`.
 */
@OptIn(InternalCoroutinesApi::class)
object RgdsPerfLog {
    private const val TAG = "RGDSPerf"
    private const val PERIOD_MS = 2000L

    @Volatile private var started = false

    fun startIfEnabled() {
        if (started) return
        val flag = File(Environment.getExternalStorageDirectory(), "OpenMW-DS/config/rgds-perf-log")
        if (!flag.exists()) return
        started = true
        Thread({ loop() }, "RgdsPerfLog").apply {
            isDaemon = true
            priority = Thread.MIN_PRIORITY
            start()
        }
        Log.i(TAG, "frame statistics on (${flag.path})")
    }

    private fun loop() {
        var lastThreads = readThreads()
        var lastWall = System.nanoTime()
        while (true) {
            try {
                Thread.sleep(PERIOD_MS)
            } catch (_: InterruptedException) {
                return
            }
            val raw = try {
                EngineActivity.getCompanionFrameTimes()
            } catch (_: Throwable) {
                null
            }
            val now = System.nanoTime()
            val threads = readThreads()
            val wallTicks = (now - lastWall) / 1e9 * CLOCK_TICKS
            val busy = threads.mapNotNull { (tid, t) ->
                val before = lastThreads[tid] ?: return@mapNotNull null
                val used = t.second - before.second
                if (used <= 0) null else t.first to (100.0 * used / wallTicks)
            }.sortedByDescending { it.second }.take(6)
            lastThreads = threads
            lastWall = now

            if (raw == null || raw.isEmpty()) continue
            val n = raw[0].toInt()
            if (n <= 0 || raw.size < 1 + 2 * n) continue
            // Newest frames first, back to one period.
            val work = ArrayList<Float>()
            var span = 0f
            var maxDelivered = 0f
            for (i in n - 1 downTo 0) {
                val delivered = raw[1 + n + i]
                span += delivered
                work.add(raw[1 + i])
                if (delivered > maxDelivered) maxDelivered = delivered
                if (span >= PERIOD_MS) break
            }
            if (work.isEmpty() || span <= 0f) continue
            val sorted = work.sorted()
            fun pct(p: Double) = sorted[((sorted.size - 1) * p).toInt()]
            val fps = 1000f * work.size / span
            val slow = work.count { it > 33.4f }
            val line = String.format(
                java.util.Locale.ROOT,
                "fps=%.1f frames=%d work avg=%.1f p50=%.1f p90=%.1f p99=%.1f max=%.1f over33=%d delivered max=%.1f | %s",
                fps, work.size, work.average(), pct(0.5), pct(0.9), pct(0.99), sorted.last(), slow, maxDelivered,
                busy.joinToString(" ") { String.format(java.util.Locale.ROOT, "%s=%.0f%%", it.first, it.second) }
            )
            Log.i(TAG, line)
        }
    }

    private const val CLOCK_TICKS = 100.0

    /** tid -> (name, utime + stime in clock ticks). */
    private fun readThreads(): Map<Int, Pair<String, Long>> {
        val out = HashMap<Int, Pair<String, Long>>()
        val tasks = File("/proc/self/task").listFiles() ?: return out
        for (task in tasks) {
            val tid = task.name.toIntOrNull() ?: continue
            val stat = try {
                File(task, "stat").readText()
            } catch (_: Throwable) {
                continue
            }
            val open = stat.indexOf('(')
            val close = stat.lastIndexOf(')')
            if (open < 0 || close < open) continue
            val name = stat.substring(open + 1, close).replace(' ', '_')
            val fields = stat.substring(close + 2).split(' ')
            // After the name: state is field 0, utime is field 11, stime field 12.
            if (fields.size < 13) continue
            val ticks = (fields[11].toLongOrNull() ?: 0L) + (fields[12].toLongOrNull() ?: 0L)
            out[tid] = "$name/$tid" to ticks
        }
        return out
    }
}
