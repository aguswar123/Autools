package com.chaptersplitter.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Outline icons (24x24, 2px round stroke). Same shapes as the Caption tools web page. */
private fun outline(name: String, vararg paths: String): ImageVector {
    val b = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
    for (p in paths) {
        b.addPath(
            pathData = addPathNodes(p),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        )
    }
    return b.build()
}

object AppIcons {
    val Back by lazy { outline("Back", "M19 12H5M11 6l-6 6 6 6") }
    val Chevron by lazy { outline("Chevron", "M9 6l6 6-6 6") }
    val More by lazy { outline("More", "M12 5h.01M12 12h.01M12 19h.01") }
    val Moon by lazy { outline("Moon", "M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z") }
    val Sun by lazy {
        outline(
            "Sun",
            "M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8z",
            "M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4"
        )
    }
    val Scissors by lazy {
        outline(
            "Scissors",
            "M6 9a3 3 0 1 0 0-6 3 3 0 0 0 0 6z",
            "M6 21a3 3 0 1 0 0-6 3 3 0 0 0 0 6z",
            "M20 4L8.12 15.88M14.47 14.48L20 20M8.12 8.12L12 12"
        )
    }
    val Match by lazy { outline("Match", "M7 7h12M16 3l4 4-4 4M17 17H5M8 13l-4 4 4 4") }
    val Batch by lazy { outline("Batch", "M12 2l9 5-9 5-9-5 9-5zM3 12l9 5 9-5M3 17l9 5 9-5") }
    val Translate by lazy {
        outline("Translate", "M4 5h9M8.5 3v2M6 5c0 4 3 7 6 8M12 5c0 4-3 7-7 8M13 21l4-10 4 10M14.5 17.5h5")
    }
    val Audio by lazy {
        outline(
            "Audio",
            "M3 18v-6a9 9 0 0 1 18 0v6",
            "M21 19a2 2 0 0 1-2 2h-1a2 2 0 0 1-2-2v-3a2 2 0 0 1 2-2h3z",
            "M3 19a2 2 0 0 0 2 2h1a2 2 0 0 0 2-2v-3a2 2 0 0 0-2-2H3z"
        )
    }
    val Folder by lazy { outline("Folder", "M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z") }
    val Check by lazy { outline("Check", "M20 6L9 17l-5-5") }
    val Alert by lazy {
        outline("Alert", "M12 9v4M12 17h.01M10.3 3.9L1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0z")
    }
    val Log by lazy { outline("Log", "M8 6h13M8 12h13M8 18h13M3 6h.01M3 12h.01M3 18h.01") }
    val Copy by lazy {
        outline("Copy", "M9 9h11a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H9a2 2 0 0 1-2-2v-9a2 2 0 0 1 2-2z", "M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1")
    }
    val Share by lazy { outline("Share", "M4 12v8a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-8M16 6l-4-4-4 4M12 2v13") }
    val Close by lazy { outline("Close", "M18 6L6 18M6 6l12 12") }
    val Tune by lazy { outline("Tune", "M4 6h10M18 6h2M4 12h4M12 12h8M4 18h12M20 18h0M14 4v4M8 10v4M16 16v4") }
}
