# Grand Theft Auto Style Dynamic Radio Player

An immersive Android radio player application built with **Kotlin**, **Jetpack Compose**, and **Android Media3 (ExoPlayer)**. This app replicates the classic atmosphere of radio stations found in games like *Grand Theft Auto*, seamlessly mixing music tracks, live DJ commentaries, station jingles, and commercial advertisements with dynamic audio transitions.

## 📻 Features
* **Dynamic Content Sequencing:** Implements a realistic radio broadcast algorithm instead of a static playlist.
* **Independent Bag Randomization:** Music, DJs, jingles, and ads use isolated shuffle queues. No audio repeats within its category until the entire "bag" is emptied, and each category replenishes independently.
* **Automatic Smooth Fade-Out:** A background Coroutine monitor tracks playback timing. When a music track reaches its final 5 seconds, the volume smoothly fades out to zero, instantly triggering the next radio segment block.
* **Universal Asset Scanner:** Automatically discovers and maps audio tracks directly from the Android `assets` directory at runtime.
* **System Media & Bluetooth Integration:** Utilizes Media3 `MediaSessionService` to natively route audio to Bluetooth devices, support hardware playback keys (Play/Pause/Skip), and display track metadata on lock screens or smartwatches.

---

## 🏗️ Architecture & Code Structure

The project follows clean architecture principles separating business logic from Android framework dependencies:

* **`models/`**: Pure data classes representing audio layers.
    * `AudioType.kt`: Enum categorizing tracks into `MUSIC`, `DJ_TALK`, `JINGLE`, or `AD`.
    * `AudioTrack.kt`: Contains metadata for individual audio files, tracking their relative asset paths.
    * `RadioStation.kt`: Holds categorized collections of all available tracks and branding configuration.
* **`manager/`**: The core behavioral engine.
    * `RadioPlaybackManager.kt`: The "Station Director". It builds the custom broadcast queue block-by-block using an advanced reabastecimento antecipado (eager replenishment) strategy to keep categories unique.
    * `RadioStationFactory.kt`: Scans asset subdirectories dynamically using Android's `AssetManager`, mapping physical filenames directly into structured data.
* **`service/`**: Background execution layer.
    * `RadioMediaService.kt`: Integrates the `ExoPlayer` instance within a `MediaSessionService`. Houses the `CoroutineScope` volume monitor that continuously checks remaining playback time to mathematically calculate and apply the 5-second fade-out curve.
* **`viewmodel/` & `ui/`**: Presentation layer.
    * `RadioViewModel.kt`: Acts as a remote control, binding to the background `MediaController` on the Main Thread and converting media state flows safely.
    * `PlayerScreen.kt`: Jetpack Compose layout rendered with full-screen `ContentScale.Fit` responsive sizing to correctly display the active station's square brand logo directly from the assets stream.

---

## 📂 Assets Folder Setup & Requirements

To prevent application crashes or unreadable files by the Android OS URI parser, the `assets` folder structure and file naming conventions must strictly follow specific rules.

### 1. Directory Tree Structure
Place your files inside the `app/src/main/assets/` folder precisely as shown below:

    app/src/main/assets/
                    └── K-DST/                          <-- Radio Station ID Folder
                        ├── logo.png                    <-- Station Square Album Art Logo
                        ├── music/                      <-- Place all song .mp3 files here
                        ├── dj_talks/                   <-- Place all DJ host commentary .mp3 files here
                        ├── jingles/                    <-- Place all short station station id/vignette .mp3 files here
                        └── ads/                        <-- Place all commercial/advertisement .mp3 files here

### 2. Crucial Naming Rules (snake_case)

Android uses an underlying Unix-based URI file scheme. Caracters like #, ?, &, ', ", brackets (), or spaces are reserved characters and will break file streaming, causing the app to crash or skip tracks silently.

    DO NOT USE uppercase letters, spaces, or symbols (#, ( ), ", ', acentos).

    ALWAYS USE lowercase letters, numbers, and underscores (_) to separate words.

Examples:

    ❌ K-DST(Atmosphere) Night #1.mp3  ➡️  ✅ kdst_atmosphere_night_1.mp3

    ❌ Comercial "Pibwasser" 2026.mp3 ➡️  ✅ comercial_pibwasser_2026.mp3

    ❌ Radio Rock Vignette!.mp3        ➡️  ✅ radio_rock_vignette.mp3

🔒 Version Control & Privacy (.gitignore)

Since full audio repositories can exceed GitHub's file storage limits and violate copyright regulations (DMCA), the audio files are explicitly excluded from being tracked by Git.

Ensure your root .gitignore contains the following rule:

    # Exclude raw radio assets but keep the repository structure intact
    /app/src/main/assets/*
    !/app/src/main/assets/README.md

