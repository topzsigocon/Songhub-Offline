# Songhub Offline Edition

A 100% offline Android MIDI Karaoke Player built with Kotlin, designed for Android 11+ with support for external SD card folder browsing and embedded lyric parsing.

## Features
- **100% Offline**: No internet required, no Firebase, no tracking telemetry, and zero ads.
- **Built-in Folder Picker**: Direct access via Storage Access Framework to point directly to your SD card path (`/Storage/Songhub/` or custom directories).
- **Embedded Karaoke Lyrics**: Automatically scans and parses standard MIDI Meta Event lyric blocks (`FF 05`).
- **GitHub Actions Ready**: Automated build workflows pre-configured.

## How to Build
1. Clone or download this project into Android Studio (Electric Eel / Flamingo or newer).
2. Sync project with Gradle files.
3. Build the debug APK via terminal:
   ```bash
   ./gradlew assembleDebug
