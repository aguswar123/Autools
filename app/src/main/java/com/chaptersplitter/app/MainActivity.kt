package com.chaptersplitter.app

import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { App() }
    }
}

private const val HOME = "home"

@Composable
fun App() {
    val ctx = LocalContext.current
    val activity = ctx as ComponentActivity
    val scope = rememberCoroutineScope()
    val prefs = remember { ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE) }
    val sysDark = isSystemInDarkTheme()

    var dark by remember { mutableStateOf(prefs.getBoolean("dark", sysDark)) }
    var route by rememberSaveable { mutableStateOf(HOME) }
    var lastTool by remember { mutableStateOf(prefs.getString("lastTool", null)) }
    val splitter = remember { SplitterState() }

    // Status and navigation bars follow the theme
    SideEffect {
        val tr = android.graphics.Color.TRANSPARENT
        val style = if (dark) SystemBarStyle.dark(tr) else SystemBarStyle.light(tr, tr)
        activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }
    // Keep the screen on only while a split is running
    val splitRunning = splitter.running   // read here so this block re-runs when it changes
    SideEffect {
        if (splitRunning) activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    fun open(t: Tool) {
        route = t.id
        lastTool = t.id
        prefs.edit().putString("lastTool", t.id).apply()
    }
    fun toggleTheme() {
        dark = !dark
        prefs.edit().putBoolean("dark", dark).apply()
    }

    // System back / back gesture inside a tool goes to the menu
    BackHandler(enabled = route != HOME) { route = HOME }

    AppTheme(dark) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            AnimatedContent(
                targetState = route,
                transitionSpec = {
                    if (targetState == HOME)
                        (slideInHorizontally { -it / 4 } + fadeIn()) togetherWith (slideOutHorizontally { it / 3 } + fadeOut())
                    else
                        (slideInHorizontally { it / 3 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 4 } + fadeOut())
                },
                label = "screen"
            ) { r ->
                val tool = Tool.fromId(r)
                when {
                    tool == null -> HomeScreen(
                        dark = dark,
                        lastTool = lastTool,
                        splitRunning = splitter.running,
                        splitProgress = splitter.progress,
                        onOpen = { open(it) },
                        onToggleTheme = { toggleTheme() }
                    )
                    tool == Tool.SPLIT -> SplitterScreen(
                        state = splitter,
                        scope = scope,
                        onBack = { route = HOME }
                    )
                    else -> CaptionToolsScreen(
                        startMode = tool.captionMode ?: "single",
                        dark = dark,
                        onBack = { route = HOME }
                    )
                }
            }
        }
    }
}
