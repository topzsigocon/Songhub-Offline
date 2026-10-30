# 🎤 Songhub Offline Edition

**100% OFFLINE MIDI Karaoke Player para sa Android.**
Walang internet, walang Firebase, walang ads. Lahat ng kanta ay galing sa SD Card mo.

| | |
|---|---|
| Language | Kotlin + AndroidX |
| Min SDK | 21 (Android 5.0) |
| Target SDK | 30 (Android 11) |
| Build | Gradle 8.7, AGP 8.2.2, JDK 17 |
| Internet permission | **WALA** |

---

## ✨ Features

- **Auto-scan** ng `.mid`, `.midi`, `.kar`, `.rmi` pagbukas ng app
  - `/<storage>/Songhub/` at `/<storage>/Songhub/MIDI/`
  - `/<storage>/SDCard/Songhub/MIDI/`
  - Kung walang Songhub folder → ini-scan ang buong SD Card
- **Built-in Folder Picker** – pumili ng kahit anong folder sa SD Card o Internal Storage (☰ → *Pumili ng Folder*)
- **Built-in karaoke lyrics** mula mismo sa MIDI file
  - Lyric Meta Event `FF 05`
  - `.KAR` (Soft Karaoke) text events `FF 01` – `\` paragraph, `/` bagong linya, `@T` title
- **Real-time na highlight** ng lyrics, syllable by syllable, may countdown bago kumanta
- **Key (Transpose)** −12 hanggang +12 (hindi naaapektuhan ang drums) – naaalala per song
- **Tempo** 50% – 150%
- **Volume** slider
- **Song List + Search** (title, song code, folder)
- **Favorites** at **Playlists**
- **Next / Previous / Auto-next**
- Pwedeng buksan ang `.mid` file mula sa ibang File Manager app
- Dark mode

### Song code
Kung ang filename ay nagsisimula sa numero, gagamitin iyon bilang song code:
`10234 - My Way.mid` → code **10234**, title **My Way**

---

## 📱 Paano i-build gamit ang ANDROID PHONE lang (GitHub)

### Option A – Pinakamadali: i-upload ang ZIP (walang kailangang i-extract)

1. Buksan ang **github.com** sa Chrome → mag-login.
2. **+** (taas) → **New repository** → pangalan: `SonghubOffline` → **Create repository**.
3. I-tap ang **"uploading an existing file"** → piliin ang `SonghubOffline.zip` → **Commit changes**.
4. Sa repo, **Add file → Create new file**.
   - Sa pangalan, i-type mismo: `.github/workflows/android.yml`
   - I-paste ang laman ng `android.yml` (kasama sa download) → **Commit changes**.
5. Pumunta sa tab na **Actions** → hintayin ang ✅ (mga 3–5 minuto).
6. Buksan ang run → sa baba, **Artifacts** → **SonghubOffline-debug-apk** → i-download.
7. I-extract ang zip sa phone → i-install ang `app-debug.apk`
   (payagan ang *Install unknown apps* kung hihingin).

> Ang workflow ay awtomatikong nag-a-unzip ng ZIP bago mag-build.

### Option B – Termux (kung gusto mo ng normal na git repo)

```bash
pkg install git unzip
termux-setup-storage
cd ~/storage/downloads && unzip SonghubOffline.zip && cd SonghubOffline
git init && git add . && git commit -m "Songhub Offline"
git branch -M main
git remote add origin https://github.com/<username>/SonghubOffline.git
git push -u origin main     # password = GitHub Personal Access Token
```

### Option C – Computer / Android Studio

```bash
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

---

## 🎵 Paano gamitin

1. Gumawa ng folder sa SD Card: **`Songhub/MIDI/`** at ilagay ang mga `.mid` / `.kar` file.
2. Buksan ang app → **Payagan ang Access**.
   Sa Android 11+: i-ON ang **"Allow access to manage all files"** → bumalik sa app.
3. Lalabas ang listahan. I-tap ang kanta para kumanta.
4. Ibang folder? ☰ menu → **Pumili ng Folder** → piliin ang SD Card → pumasok sa folder → **Piliin ang Folder na Ito**.

**Player controls:** Play/Pause, Prev, Next, Restart, ☆ Favorite,
**KEY −/+** (tap ang label = reset), **TEMPO −/+** (tap ang label = 100%), Volume.

---

## 🧠 Paano gumagana ang MIDI engine

```
.mid file ──► MidiFile.parse()  (pure Kotlin SMF parser)
                │
                ├─► LyricsExtractor  → FF 05 / KAR lyrics + oras (ms)
                │
                └─► MidiTransformer  → transpose notes, scale tempo
                          │
                          ▼
                 bagong .mid sa cache ──► Android MediaPlayer (built-in Sonivox synth)
```

Gumagamit ng **built-in MIDI synthesizer ng Android** – walang soundfont na kailangang i-download.
Kapag binago ang Key o Tempo, ginagawa ulit ang MIDI at itinutuloy sa parehong posisyon.

## 📂 Project structure

```
app/src/main/java/com/songhub/offline/
├── MainActivity.kt          Song list, search, tabs (Lahat / Favorites / Playlists)
├── PlayerActivity.kt        Karaoke player screen
├── FolderPickerActivity.kt  Built-in SD Card folder picker
├── PlaylistActivity.kt      Laman ng playlist
├── data/                    Song, Prefs (favorites/playlists), SongScanner
├── midi/                    MidiFile, TempoMap, KaraokeLyrics, MidiTransformer
├── player/KaraokePlayer.kt  MediaPlayer wrapper (key/tempo/volume)
├── ui/                      LyricsView, adapters
└── util/StorageUtils.kt     SD card detection + permissions
```

## ✅ Tests

```bash
./gradlew testDebugUnitTest
```
Tine-test ang MIDI parse/write, FF 05 lyrics, KAR lyrics, transpose (hindi kasama ang drums) at tempo.

## ⚠️ Notes

- Target SDK 30 (Android 11) gaya ng hiniling. Para sa sariling gamit / sideload – ang Google Play ay nangangailangan ng mas mataas na target SDK.
- `MANAGE_EXTERNAL_STORAGE` ang ginagamit sa Android 11+ para mabasa ang SD Card nang direkta.
- Ang tunog ay nakadepende sa built-in MIDI synth ng phone (pareho sa karamihan ng Android).
