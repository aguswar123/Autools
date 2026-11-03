package com.chaptersplitter.app

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.layout.Spacer
import androidx.documentfile.provider.DocumentFile
import androidx.webkit.WebViewAssetLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/*
 * Caption tools (Match, Batch, Translate). The tool is a web page shipped inside the app
 * (assets/caption_tools.html) and shown in a WebView, so nothing is downloaded and it works offline.
 * This file adds what a web page cannot do by itself: native file picking, saving, copying,
 * the top bar and the back gesture.
 */

private const val HOST = "https://appassets.androidplatform.net"

private fun modeTitle(mode: String) = when (mode) {
    "batch" -> "Batch Match"
    "translate" -> "Translate Captions"
    else -> "Caption Match"
}

/** Things waiting for an Android result (save dialog / file chooser). */
private class Pending {
    var bytes: ByteArray? = null
    var chooser: ValueCallback<Array<Uri>>? = null
    var multiple = false
}

/** Functions the web page can call as AuBridge.xxx(). They run on a background thread. */
private class CaptionBridge(
    private val ctx: Context,
    private val modeChanged: (String) -> Unit,
    private val save: (String, ByteArray) -> Unit
) {
    private val main = Handler(Looper.getMainLooper())

    @JavascriptInterface fun onMode(m: String) { main.post { modeChanged(m) } }

    @JavascriptInterface fun saveText(name: String, text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        main.post { save(name, bytes) }
    }

    @JavascriptInterface fun saveBase64(name: String, data: String, @Suppress("UNUSED_PARAMETER") mime: String) {
        val bytes = Base64.decode(data, Base64.DEFAULT)
        main.post { save(name, bytes) }
    }

    @JavascriptInterface fun copyText(text: String) {
        main.post {
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("AuTools", text))
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptionToolsScreen(startMode: String, dark: Boolean, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val pending = remember { Pending() }
    var mode by remember { mutableStateOf(startMode) }
    var web by remember { mutableStateOf<WebView?>(null) }

    // Save dialog: the page hands us the bytes, the user picks where they go
    val saver = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val bytes = pending.bytes
        pending.bytes = null
        if (uri != null && bytes != null) {
            scope.launch {
                val msg = withContext(Dispatchers.IO) {
                    try {
                        ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
                        "Saved " + (DocumentFile.fromSingleUri(ctx, uri)?.name ?: "file")
                    } catch (e: Exception) {
                        "Could not save the file"
                    }
                }
                web?.evaluateJavascript("toast(" + JSONObject.quote(msg) + ")", null)
            }
        }
    }

    // File chooser for the page's "Open EPUB / Import captions" buttons
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val cb = pending.chooser
        pending.chooser = null
        val result: Array<Uri>? = when {
            uris.isEmpty() -> null
            pending.multiple -> uris.toTypedArray()
            else -> arrayOf(uris.first())
        }
        cb?.onReceiveValue(result)
    }

    val bridge = remember {
        CaptionBridge(
            ctx = ctx,
            modeChanged = { mode = it },
            save = { name, bytes -> pending.bytes = bytes; saver.launch(name) }
        )
    }
    val loader = remember {
        WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(ctx))
            .build()
    }

    // Back: close the page's settings sheet first, otherwise return to the menu
    fun handleBack() {
        val w = web
        if (w == null) { onBack(); return }
        w.evaluateJavascript("window.auBack ? window.auBack() : false") { r -> if (r != "true") onBack() }
    }
    BackHandler { handleBack() }

    DisposableEffect(Unit) {
        onDispose {
            pending.chooser?.onReceiveValue(null)
            pending.chooser = null
            web?.let { it.removeJavascriptInterface("AuBridge"); it.destroy() }
            web = null
        }
    }

    // Same colour as the page's bottom navigation, so it also fills the system gesture area
    val navStrip = if (dark) Color(0xFF1B2621) else Color(0xFFE7F0EB)
    val url = "$HOST/assets/caption_tools.html?embedded=1&m=$startMode&theme=${if (dark) "dark" else "light"}"

    Scaffold(
        containerColor = cs.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(modeTitle(mode)) },
                navigationIcon = {
                    IconButton(onClick = { handleBack() }) { Icon(AppIcons.Back, contentDescription = "Back to menu") }
                },
                actions = {
                    if (mode != "translate") {
                        IconButton(onClick = {
                            web?.evaluateJavascript("window.auToggleSettings && window.auToggleSettings()", null)
                        }) { Icon(AppIcons.Tune, contentDescription = "Match settings") }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background)
            )
        }
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(top = inner.calculateTopPadding()).imePadding()) {
            AndroidView(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                factory = { c ->
                    WebView(c).apply {
                        setBackgroundColor(cs.background.toArgb())
                        overScrollMode = View.OVER_SCROLL_NEVER
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = true
                        settings.setSupportZoom(false)
                        webViewClient = object : WebViewClient() {
                            override fun shouldInterceptRequest(
                                view: WebView, request: WebResourceRequest
                            ): WebResourceResponse? = loader.shouldInterceptRequest(request.url)

                            // The page never leaves the app
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onShowFileChooser(
                                view: WebView,
                                callback: ValueCallback<Array<Uri>>,
                                params: WebChromeClient.FileChooserParams
                            ): Boolean {
                                pending.chooser?.onReceiveValue(null)
                                pending.chooser = callback
                                pending.multiple = params.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE
                                picker.launch(arrayOf("*/*"))
                                return true
                            }
                        }
                        addJavascriptInterface(bridge, "AuBridge")
                        web = this
                        loadUrl(url)
                    }
                },
                update = { it.setBackgroundColor(cs.background.toArgb()) }
            )
            Spacer(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsBottomHeight(WindowInsets.navigationBars.exclude(WindowInsets.ime))
                    .background(navStrip)
            )
        }
    }
}
