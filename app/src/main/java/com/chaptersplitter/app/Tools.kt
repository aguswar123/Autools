package com.chaptersplitter.app

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Every tool shown on the home menu. To add a new tool later: add an entry here,
 * then handle its id in App() (MainActivity.kt).
 *
 * captionMode is the mode the Caption tools web page opens in ("single", "batch", "translate").
 */
enum class Tool(
    val id: String,
    val title: String,
    val desc: String,
    val group: String,
    val icon: ImageVector,
    val captionMode: String? = null
) {
    SPLIT(
        "split", "Chapter Splitter",
        "Split an audiobook into one file per chapter. No re-encoding, no quality loss.",
        "Audiobooks", AppIcons.Scissors
    ),
    MATCH(
        "match", "Caption Match",
        "Fix a caption file with the text of an EPUB chapter. Timestamps stay as they are.",
        "Captions and EPUB", AppIcons.Match, "single"
    ),
    BATCH(
        "batch", "Batch Match",
        "Match many caption files to their chapters in one go and save them as a zip.",
        "Captions and EPUB", AppIcons.Batch, "batch"
    ),
    TRANSLATE(
        "translate", "Translate Captions",
        "Copy the lines for translation, paste them back and rebuild the file. No EPUB needed.",
        "Captions and EPUB", AppIcons.Translate, "translate"
    );

    companion object {
        fun fromId(id: String?): Tool? = entries.firstOrNull { it.id == id }
    }
}
