package com.confused.anikuta.error

import android.content.Context
import android.content.Intent
import android.os.Looper
import android.os.Process
import java.io.File
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale

/**
 * ROUND 180: THE BOOT WATCHDOG — the PM-6 diagnosis protocol's missing net.
 *
 * The crash handler catches EXCEPTIONS on any thread; a boot HANG (the main
 * thread stuck in a DB open, a lock wait, or the composition never starting)
 * produces NO error UI — the user sees a black screen and the logcat is out
 * of reach on a retail device. This watchdog converts the silent hang into
 * the exact report ErrorActivity already shows:
 *
 * • every boot stage stamps a marker — the timeline shows how far boot got;
 * • arming posts a background timer — if the FIRST FRAME never renders
 *   within the window, the MAIN THREAD's stack is captured AT THE MOMENT OF
 *   THE HANG (its deepest frame is the statement it is stuck on), the other
 *   threads' heads ride along, and the boot timeline lands in the crash
 *   file → ErrorActivity opens with it, copyable like any crash report.
 *
 * The disarmer is the first composition's [firstFrameRendered] — a hang
 * BEFORE it (the Application onCreate, the Koin graph, the DB open, the
 * activity's setContent) is exactly what this net is for.
 */
object BootWatchdog {

    private val t0 = System.currentTimeMillis()
    private val markers = Collections.synchronizedList(mutableListOf<String>())
    @Volatile private var firstFrameSeen = false
    @Volatile private var armed = false

    /** Stamp a boot stage (cheap — safe from any thread). */
    fun mark(stage: String) {
        try {
            markers += "+${System.currentTimeMillis() - t0}ms  $stage"
        } catch (_: Exception) {
        }
    }

    /** The first composition applied — the watchdog disarms. */
    fun firstFrameRendered() {
        if (firstFrameSeen) return
        firstFrameSeen = true
        mark("first-frame rendered — disarmed")
    }

    /**
     * Arm the hang timer. Called once in Application.onCreate — the window
     * covers the whole boot (the Koin graph, the DB open, the activity, the
     * first frame). A device that takes longer than the window on a SLOW but
     * healthy boot shows the report too — that report is still the evidence
     * (the timeline says where the time went).
     */
    fun arm(context: Context, timeoutMs: Long = 12_000L) {
        if (armed) return
        armed = true
        mark("watchdog armed (${timeoutMs}ms window)")
        Thread {
            try {
                Thread.sleep(timeoutMs)
            } catch (_: InterruptedException) {
                return@Thread
            }
            if (firstFrameSeen) return@Thread
            val report = buildHangReport()
            try {
                File(context.filesDir, AnikutaCrashHandler.CRASH_FILE).writeText(report)
            } catch (_: Exception) {
            }
            try {
                context.startActivity(
                    Intent(context, ErrorActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    },
                )
            } catch (_: Exception) {
            }
        }.apply { name = "boot-watchdog" }.start()
    }

    /** The hang report: the MAIN THREAD's stack + the other threads + the timeline. */
    private fun buildHangReport(): String {
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        val mainThread = try { Looper.getMainLooper().thread } catch (_: Exception) { null }
        return buildString {
            appendLine("=== ANIKUTA BOOT-HANG REPORT ===")
            appendLine("Time: $time")
            appendLine("Android API: ${android.os.Build.VERSION.SDK_INT} (${android.os.Build.VERSION.RELEASE})")
            appendLine("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
            appendLine("PID: ${Process.myPid()}")
            appendLine()
            appendLine("THE FIRST FRAME NEVER RENDERED — the app hung during boot.")
            appendLine("The MAIN THREAD's stack AT THE MOMENT OF CAPTURE follows —")
            appendLine("its deepest frame is the statement it is stuck on.")
            appendLine()
            if (mainThread != null) {
                appendLine("── MAIN THREAD (${mainThread.state}) ──")
                mainThread.stackTrace.forEach { appendLine("  at $it") }
                appendLine()
            } else {
                appendLine("── the main thread could not be resolved ──")
                appendLine()
            }
            // The other threads' heads — the lock holder usually shows here
            // (a background thread inside the DB open / a WAL checkpoint /
            // a migration while the main thread waits on the helper lock).
            var dumped = 0
            for ((thread, stack) in Thread.getAllStackTraces()) {
                if (thread == mainThread || stack.isEmpty()) continue
                if (dumped >= 24) {
                    appendLine("… (${dumped}+ threads)")
                    break
                }
                appendLine("── ${thread.name} (${thread.state}) ──")
                stack.take(10).forEach { appendLine("  at $it") }
                appendLine()
                dumped++
            }
            appendLine("── BOOT TIMELINE ──")
            synchronized(markers) { markers.forEach { appendLine("  $it") } }
        }
    }
}
