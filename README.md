# AuTools

Android app with a small menu of tools for audiobooks and subtitles. Material 3 look (light / dark),
works offline. Pick a tool on the home screen:

| Tool | What it does |
|---|---|
| **Chapter Splitter** | Splits an audiobook (`.m4b .m4a .mp3 .ogg .opus`) into one file per chapter **without re-encoding** (stream copy): fast, no quality loss. Time-remaining estimates and a built-in log. |
| **Caption Match** | Fixes a caption file (`.srt .vtt .lrc`) with the text of an EPUB chapter. Timestamps stay as they are. Optional "Unbreakable text" joins broken sentences. |
| **Batch Match** | Matches many caption files to their chapters at once and saves them as a zip. |
| **Translate Captions** | Copies the lines out for translation, takes the translated lines back and rebuilds the file with the original timestamps. No EPUB needed. |

## Download
Grab `ChapterSplitter.apk` from the **Releases** page (or the latest **Actions** run, under Artifacts).
One universal APK for arm64, 32-bit ARM and x86 devices (Android 8.0+).

## Build
Pushing to `main` builds automatically with GitHub Actions. Locally: `gradle assembleDebug`.

## Project layout
- `MainActivity.kt` – app shell, navigation, theme choice
- `HomeScreen.kt`, `Tools.kt` – the tool menu (add a tool = add an entry in `Tools.kt`)
- `SplitterScreen.kt`, `SplitterState.kt`, `SplitEngine.kt` – Chapter Splitter (UI, state, FFmpeg logic)
- `CaptionToolsScreen.kt` – shows the caption tools in a WebView and adds native file picking, saving and copy
- `assets/caption_tools.html` – the caption tools themselves (one self-contained page, no internet needed)
- `Theme.kt`, `Icons.kt` – Material 3 colours and icons

## Credits / License
App code: MIT (see LICENSE). Uses FFmpegKit (LGPL-3.0).
