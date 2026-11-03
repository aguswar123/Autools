package com.chaptersplitter.app

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Everything the Chapter Splitter screen shows. It lives above the screen (in App), so leaving
 * the screen, or going back to the menu, does not stop a split that is running.
 */
class SplitterState {
    var bookUri by mutableStateOf<Uri?>(null)
    var bookName by mutableStateOf("")
    var outUri by mutableStateOf<Uri?>(null)
    var outName by mutableStateOf("")
    var running by mutableStateOf(false)
    var progress by mutableFloatStateOf(0f)
    var status by mutableStateOf("")
    val log = mutableStateListOf<String>()
    var finished by mutableStateOf(false)
    var okCount by mutableIntStateOf(0)
    var failCount by mutableIntStateOf(0)

    val canStart: Boolean get() = !running && bookUri != null && outUri != null

    fun start(ctx: Context, scope: CoroutineScope) {
        val book = bookUri ?: return
        val out = outUri ?: return
        if (running) return
        val appCtx = ctx.applicationContext
        log.clear(); progress = 0f; status = ""; finished = false; okCount = 0; failCount = 0; running = true
        scope.launch {
            val (ok, fail) = try {
                splitBook(appCtx, book, bookName, out, { log.add(it) }, { progress = it }, { status = it })
            } catch (e: Throwable) {
                log.add("Error: ${e.javaClass.simpleName}: ${e.message}")
                DebugLog.add("EXCEPTION:\n${e.stackTraceToString()}")
                0 to 0
            }
            log.add("Done. $ok chapter files created, $fail failed.")
            DebugLog.add("== Done: $ok ok, $fail failed ==")
            okCount = ok; failCount = fail
            running = false
            finished = true
        }
    }
}
