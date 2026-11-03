package com.chaptersplitter.app

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import com.arthenica.ffmpegkit.Chapter
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

// ------------------------------------------------------------------ Debug log

object DebugLog {
    private val sb = StringBuilder()
    @Synchronized fun add(s: String) {
        sb.append(s).append('\n')
        if (sb.length > 300_000) sb.delete(0, sb.length - 250_000)
    }
    @Synchronized fun clear() { sb.setLength(0) }
    @Synchronized fun text(): String = sb.toString()
}

fun fullDebugReport(): String {
    val ffv = try { FFmpegKitConfig.getVersion() } catch (t: Throwable) { "FAILED TO LOAD: $t\n${t.stackTraceToString().take(1500)}" }
    return buildString {
        appendLine("=== AuTools debug report ===")
        appendLine("Time: ${Date()}")
        appendLine("App: ${BuildConfigInfo.VERSION}")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        appendLine("ABIs: ${Build.SUPPORTED_ABIS.joinToString()}")
        appendLine("FFmpegKit: $ffv")
        appendLine("======================================")
        append(DebugLog.text().ifBlank { "(no split has been run yet)" })
    }
}

object BuildConfigInfo { const val VERSION = "1.8.0" }

// ------------------------------------------------------------------ Logic (port of the shell script)

fun displayName(ctx: Context, uri: Uri): String =
    ctx.contentResolver.query(uri, null, null, null, null)?.use {
        if (it.moveToFirst()) it.getString(it.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) else null
    } ?: "audiobook.m4b"

/** Same idea as clean_name() in the shell script */
fun cleanName(s: String): String {
    var r = s.replace(Regex("[\\u0000-\\u001f]"), "")
        .replace(Regex("[\\\\/:*?\"<>|]"), "_")
        .take(120).trim().trimEnd('.')
    if (r.isEmpty()) r = "Chapter"
    return r
}

fun fmtTime(sec: Double): String {
    if (sec.isNaN() || sec.isInfinite() || sec < 0) return "…"
    val t = sec.toLong()
    val h = t / 3600; val m = (t % 3600) / 60; val r = t % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, r) else "%d:%02d".format(m, r)
}

fun fileSize(ctx: Context, uri: Uri): Long =
    ctx.contentResolver.query(uri, null, null, null, null)?.use {
        if (it.moveToFirst()) {
            val i = it.getColumnIndex(OpenableColumns.SIZE)
            if (i >= 0 && !it.isNull(i)) it.getLong(i) else -1L
        } else -1L
    } ?: -1L

/** Copies with a big 1 MB buffer (the default 8 KB is very slow through Android's file layer). */
fun copyStream(ins: InputStream, out: OutputStream, onProgress: (Long) -> Unit) {
    val buf = ByteArray(1 shl 20)
    var done = 0L
    var last = 0L
    while (true) {
        val n = ins.read(buf)
        if (n < 0) break
        out.write(buf, 0, n)
        done += n
        val now = System.currentTimeMillis()
        if (now - last > 250) { onProgress(done); last = now }
    }
    onProgress(done)
}

fun chapterTitle(ch: Chapter, n: Int): String {
    var t = ch.tags?.optString("title", "").orEmpty()
    if (t.isBlank()) t = ch.tags?.optString("TITLE", "").orEmpty()
    return if (t.isBlank()) "Chapter $n" else t
}

fun chapterStart(ch: Chapter): Double = ch.startTime.toString().toDoubleOrNull() ?: 0.0
fun chapterEnd(ch: Chapter): Double = ch.endTime.toString().toDoubleOrNull() ?: 0.0

suspend fun splitBook(
    ctx: Context, input: Uri, fileName: String, outTree: Uri,
    log: (String) -> Unit, progress: (Float) -> Unit, status: (String) -> Unit
): Pair<Int, Int> = withContext(Dispatchers.IO) {
    DebugLog.clear()
    DebugLog.add("== Split started ==")
    DebugLog.add("File: $fileName\nUri: $input\nOutput tree: $outTree")
    val tAll = System.currentTimeMillis()

    val base = fileName.substringBeforeLast('.')
    val ext = fileName.substringAfterLast('.', "m4b")
    val noCover = ext.lowercase() in listOf("opus", "ogg")

    // ---- Step 1: one fast private copy of the book
    var localBook: File? = null
    val size = fileSize(ctx, input)
    val free = ctx.cacheDir.usableSpace
    val cand = File(ctx.cacheDir, "book.$ext")
    cand.delete()
    if (size > 0 && free > size + 200L * 1024 * 1024) {
        log("Preparing: copying the book to app storage…")
        val t0 = System.currentTimeMillis()
        try {
            val ins = ctx.contentResolver.openInputStream(input) ?: error("Cannot open the book")
            ins.use { i ->
                cand.outputStream().use { o ->
                    copyStream(i, o) { done ->
                        val el = (System.currentTimeMillis() - t0) / 1000.0
                        val frac = done.toDouble() / size
                        val eta = if (frac > 0.01) el * (1 - frac) / frac else Double.NaN
                        status("Preparing: copying book ${(frac * 100).toInt()}%\nAbout ${fmtTime(eta)} left")
                    }
                }
            }
            localBook = cand
            val ms = System.currentTimeMillis() - t0
            DebugLog.add("Local copy done: ${size / 1048576} MB in ${ms} ms")
        } catch (e: Exception) {
            cand.delete()
            DebugLog.add("Local copy FAILED:\n${e.stackTraceToString()}")
            log("Could not copy the book, using direct access (slower).")
        }
    } else {
        DebugLog.add("Skipping local copy: size=$size free=$free")
        log("Not enough free space for a local copy, using direct access (slower).")
    }
    fun src(): String = localBook?.absolutePath ?: FFmpegKitConfig.getSafParameterForRead(ctx, input)

    // ---- Step 2: read chapters
    val tProbe = System.currentTimeMillis()
    val probe = FFprobeKit.getMediaInformation(src())
    val info = probe.mediaInformation
    DebugLog.add("ffprobe took ${System.currentTimeMillis() - tProbe} ms")
    if (info == null) {
        DebugLog.add("ffprobe returned NO media info. ffprobe output:\n${probe.allLogsAsString}")
    } else {
        DebugLog.add("Format: ${info.format}, duration: ${info.duration}")
        DebugLog.add("Streams: " + info.streams.orEmpty().joinToString { "${it.type}/${it.codec}" })
    }
    val chapters = info?.chapters.orEmpty()
    if (chapters.isEmpty()) {
        log("No chapters found in this file.")
        DebugLog.add("No chapters found.")
        localBook?.delete()
        return@withContext 0 to 0
    }
    chapters.take(3).forEachIndexed { i, c ->
        DebugLog.add("Chapter ${i + 1}: start=${c.startTime} end=${c.endTime} tags=${c.tags}")
    }

    val total = chapters.size
    val pad = maxOf(2, total.toString().length)
    val totalDur = chapters.sumOf { chapterEnd(it) - chapterStart(it) }.coerceAtLeast(1.0)
    DebugLog.add("ext=$ext noCover=$noCover total=$total totalAudio=${fmtTime(totalDur)} usingLocalCopy=${localBook != null}")

    val root = DocumentFile.fromTreeUri(ctx, outTree) ?: error("Can't open output folder")
    val dirName = "${cleanName(base)} - Chapters"
    val dir = root.findFile(dirName) ?: root.createDirectory(dirName) ?: error("Can't create folder")
    log("$total chapters → $dirName")

    var ok = 0; var fail = 0
    var fastWorked = false

    // =====================================================================
    // FAST MODE: ONE ffmpeg run writes every chapter (the file is opened once)
    // =====================================================================
    val freeNow = ctx.cacheDir.usableSpace
    if (size > 0 && freeNow > size + 150L * 1048576) {
        log("Fast mode: cutting all $total chapters in one pass…")
        val outs = (0 until total).map { File(ctx.cacheDir, "out_${it + 1}.$ext") }
        outs.forEach { it.delete() }

        val args = mutableListOf("-hide_banner", "-loglevel", "warning", "-nostdin", "-y", "-i", src())
        chapters.forEachIndexed { idx, ch ->
            val n = idx + 1
            val st = chapterStart(ch)
            val dur = chapterEnd(ch) - st
            val meta = chapterTitle(ch, n).replace('"', '\'')
            args += listOf("-map", "0:a")
            if (!noCover) args += listOf("-map", "0:v?")
            args += listOf(
                "-map_chapters", "-1", "-map_metadata", "0", "-c", "copy",
                "-ss", "%.6f".format(Locale.US, st), "-t", "%.6f".format(Locale.US, dur),
                "-metadata", "title=$meta", "-metadata", "track=$n/$total",
                outs[idx].absolutePath
            )
        }
        DebugLog.add("FAST ffmpeg with ${args.size} args (first 40): ${args.take(40).joinToString(" ")}")

        val tFast0 = System.currentTimeMillis()
        val poller = launch {
            while (isActive) {
                val written = outs.sumOf { it.length() }
                val el = (System.currentTimeMillis() - tFast0) / 1000.0
                val frac = (written.toDouble() / size).coerceIn(0.0, 1.0)
                val eta = if (frac > 0.02) el * (1 - frac) / frac else Double.NaN
                val cur = outs.indexOfLast { it.length() > 0 }.coerceAtLeast(0)
                val rate = written / maxOf(el, 0.001)
                val expected = size * ((chapterEnd(chapters[cur]) - chapterStart(chapters[cur])) / totalDur)
                val chEta = if (rate > 1) ((expected - outs[cur].length()) / rate).coerceAtLeast(0.0) else Double.NaN
                val wholeEta = eta + size / 80_000_000.0 // + rough time for the saving step
                progress((frac * 0.7).toFloat())
                status(
                    "Cutting all chapters · ${(frac * 100).toInt()}%\n" +
                    "Chapter ${cur + 1}/$total: about ${fmtTime(chEta)} left\n" +
                    "Whole book: about ${fmtTime(wholeEta)} left"
                )
                delay(500)
            }
        }
        val session = FFmpegKit.executeWithArguments(args.toTypedArray())
        poller.cancel()
        val execMs = System.currentTimeMillis() - tFast0
        val tLog = System.currentTimeMillis()
        val out = (session.allLogsAsString ?: "").trim()
        DebugLog.add("FAST ffmpeg returnCode=${session.returnCode} execTime=${execMs} ms (log fetch ${System.currentTimeMillis() - tLog} ms)\n${out.takeLast(3000)}")

        if (ReturnCode.isSuccess(session.returnCode)) {
            fastWorked = true
            val tSave0 = System.currentTimeMillis()
            val totalBytes = outs.sumOf { it.length() }.coerceAtLeast(1L)
            var savedBytes = 0L
            for (idx in 0 until total) {
                val n = idx + 1
                val f = outs[idx]
                val title = chapterTitle(chapters[idx], n)
                val outName = "${n.toString().padStart(pad, '0')} - ${cleanName(title)}.$ext"
                val len = f.length()
                val t0 = System.currentTimeMillis()
                var good = false
                try {
                    if (len > 0) {
                        dir.findFile(outName)?.delete()
                        val target = dir.createFile("application/octet-stream", outName)
                        if (target != null) {
                            ctx.contentResolver.openOutputStream(target.uri)?.use { o ->
                                f.inputStream().use { ins ->
                                    copyStream(ins, o) { done ->
                                        val sd = savedBytes + done
                                        val el = (System.currentTimeMillis() - tSave0) / 1000.0
                                        val rate = maxOf(sd / maxOf(el, 0.001), 1.0)
                                        progress((0.7 + 0.3 * sd.toDouble() / totalBytes).toFloat())
                                        status(
                                            "Saving chapter $n/$total\n" +
                                            "This chapter: about ${fmtTime((len - done) / rate)} left\n" +
                                            "Whole book: about ${fmtTime((totalBytes - sd) / rate)} left"
                                        )
                                    }
                                }
                            }
                            good = true
                        } else DebugLog.add("[$n] createFile returned null for $outName")
                    } else DebugLog.add("[$n] output file is empty")
                } catch (e: Exception) {
                    DebugLog.add("[$n] save exception:\n${e.stackTraceToString()}")
                }
                savedBytes += len
                f.delete()
                if (good) { ok++; log("[$n/$total] $title") } else { fail++; log("[$n/$total] FAILED: $title") }
                DebugLog.add("[$n/$total] SAVE ${System.currentTimeMillis() - t0} ms size=${len / 1048576.0} MB")
            }
            DebugLog.add("Saving step took ${System.currentTimeMillis() - tSave0} ms")
        } else {
            log("Fast mode failed, switching to one-chapter-at-a-time mode.")
            outs.forEach { it.delete() }
        }
    } else {
        DebugLog.add("Fast mode skipped: free=$freeNow size=$size")
    }

    // =====================================================================
    // SLOW FALLBACK: one ffmpeg run per chapter
    // =====================================================================
    if (!fastWorked) {
        ok = 0; fail = 0
        val tmp = File(ctx.cacheDir, "chunk.$ext")
        var lastError = ""
        var failStreak = 0
        val statMs = AtomicLong(0)
        val tLoop = System.currentTimeMillis()
        var doneDur = 0.0
        var curDur = 1.0
        var chStart = tLoop
        var curN = 0
        var cutMsSum = 0L
        var copyMsSum = 0L
        fun wCut(): Double = if (cutMsSum + copyMsSum > 0) cutMsSum.toDouble() / (cutMsSum + copyMsSum) else 0.6

        fun publish(chapterFrac: Double, phase: String) {
            val now = System.currentTimeMillis()
            val f = chapterFrac.coerceIn(0.0, 1.0)
            val elapsedTotal = (now - tLoop) / 1000.0
            val chElapsed = (now - chStart) / 1000.0
            val processed = doneDur + f * curDur
            val chEta = if (f > 0.02) chElapsed * (1 - f) / f else Double.NaN
            val totEta = if (processed > 1.0 && elapsedTotal > 1.0) (totalDur - processed) * elapsedTotal / processed else Double.NaN
            progress((processed / totalDur).toFloat().coerceIn(0f, 1f))
            status("Chapter $curN/$total · $phase\nThis chapter: about ${fmtTime(chEta)} left\nWhole book: about ${fmtTime(totEta)} left")
        }

        FFmpegKitConfig.enableStatisticsCallback { st ->
            statMs.set(st.time.toLong())
            publish(wCut() * (statMs.get() / 1000.0 / curDur), "cutting")
        }

        for ((i, ch) in chapters.withIndex()) {
            if (failStreak >= 3) {
                log("Stopping after 3 failures in a row.")
                DebugLog.add("Stopped: 3 failures in a row.")
                break
            }
            val n = i + 1
            curN = n
            val start = chapterStart(ch)
            val dur = chapterEnd(ch) - start
            curDur = maxOf(dur, 0.001)
            chStart = System.currentTimeMillis()
            statMs.set(0)
            publish(0.0, "starting")

            val title = chapterTitle(ch, n)
            val outName = "${n.toString().padStart(pad, '0')} - ${cleanName(title)}.$ext"
            val meta = title.replace('"', '\'')

            fun run(withCover: Boolean): Boolean {
                lastError = ""
                tmp.delete()
                val args = mutableListOf(
                    "-hide_banner", "-loglevel", "warning", "-nostdin", "-y",
                    "-ss", "%.6f".format(Locale.US, start), "-i", src(),
                    "-t", "%.6f".format(Locale.US, dur), "-map", "0:a"
                )
                if (withCover) args += listOf("-map", "0:v?")
                args += listOf(
                    "-map_chapters", "-1", "-map_metadata", "0", "-c", "copy",
                    "-metadata", "title=$meta", "-metadata", "track=$n/$total", tmp.absolutePath
                )
                DebugLog.add("[$n] ffmpeg ${args.joinToString(" ")}")
                val tx = System.currentTimeMillis()
                val session = FFmpegKit.executeWithArguments(args.toTypedArray())
                val execMs = System.currentTimeMillis() - tx
                val okRun = ReturnCode.isSuccess(session.returnCode)
                val tl = System.currentTimeMillis()
                val out = (session.allLogsAsString ?: "").trim()
                DebugLog.add("[$n] → returnCode=${session.returnCode} (cover=$withCover) execTime=${execMs}ms logFetch=${System.currentTimeMillis() - tl}ms tmpSize=${tmp.length()}\n${out.takeLast(3000)}")
                if (!okRun) lastError = out.takeLast(400) + " [code ${session.returnCode}]"
                return okRun
            }

            val tCut0 = System.currentTimeMillis()
            var success = run(withCover = !noCover)
            if (!success && !noCover) success = run(withCover = false)
            val cutMs = System.currentTimeMillis() - tCut0

            var copyMs = 0L
            var bytes = 0L
            if (success) {
                val tCopy0 = System.currentTimeMillis()
                try {
                    bytes = tmp.length()
                    dir.findFile(outName)?.delete()
                    val target = dir.createFile("application/octet-stream", outName)
                    if (target != null) {
                        val w = wCut()
                        ctx.contentResolver.openOutputStream(target.uri)?.use { o ->
                            tmp.inputStream().use { ins ->
                                copyStream(ins, o) { done ->
                                    val cf = if (bytes > 0) done.toDouble() / bytes else 1.0
                                    publish(w + (1 - w) * cf, "saving")
                                }
                            }
                        }
                    } else {
                        success = false
                        lastError = "Could not create output file in chosen folder"
                        DebugLog.add("[$n] createFile returned null for $outName")
                    }
                } catch (e: Exception) {
                    success = false
                    lastError = "Copy failed: ${e.message}"
                    DebugLog.add("[$n] copy exception:\n${e.stackTraceToString()}")
                }
                copyMs = System.currentTimeMillis() - tCopy0
            }
            tmp.delete()

            if (success) { cutMsSum += cutMs; copyMsSum += copyMs }
            doneDur += curDur
            publish(1.0, "done")

            val elapsedTotal = (System.currentTimeMillis() - tLoop) / 1000.0
            val totEta = if (doneDur > 1.0) (totalDur - doneDur) * elapsedTotal / doneDur else Double.NaN
            DebugLog.add(
                "[$n/$total] TIMING cut=${cutMs}ms save=${copyMs}ms size=${bytes / 1048576.0}MB audio=${fmtTime(curDur)} " +
                "| elapsed=${fmtTime(elapsedTotal)} wholeBookETA=${fmtTime(totEta)}"
            )

            if (success) { ok++; failStreak = 0; log("[$n/$total] $title") }
            else {
                fail++; failStreak++
                log("[$n/$total] FAILED: $title")
                if (lastError.isNotEmpty()) log("   ↳ $lastError")
            }
        }
        FFmpegKitConfig.enableStatisticsCallback(null)
    }

    localBook?.delete()
    val totalSec = (System.currentTimeMillis() - tAll) / 1000.0
    DebugLog.add("Total time: ${fmtTime(totalSec)} (fastMode=$fastWorked)")
    progress(1f)
    status("Finished in ${fmtTime(totalSec)}")
    ok to fail
}
