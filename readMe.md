# Grand Theft Auto Style Dynamic Radio Player

An immersive Android radio player application built with **Kotlin**, **Jetpack Compose**, and **Android Media3 (ExoPlayer)**. This app replicates the classic atmosphere of radio stations found in games like *Grand Theft Auto*, seamlessly mixing music tracks, live DJ commentaries, station jingles, and commercial advertisements with dynamic audio transitions.

## 📻 Features
* **Dual-Deck Crossfading Architecture:** Utilizes two synchronized ExoPlayer instances (`playerA` and `playerB`) to create studio-grade crossfades. Music tracks smoothly fade in *over* the final seconds of a DJ's commentary, eliminating awkward silences.
* **"Live Broadcast" Simulation:** Emulates a live radio environment. When tuning into a station, the app skips a random number of queue items and drops the "needle" at a random timestamp of the current track, creating the illusion that the broadcast was already running in the background.
* **Analog Tuning & Static FX:** Plays an infinite white noise static loop when no station is selected. Switching stations triggers a randomized AM/FM frequency scanning sound effect, crossing over seamlessly into the new station.
* **Data-Driven Configuration:** Station metadata (Name and FM Frequency) is fully decoupled from the codebase and loaded dynamically via `station_info.json` files.
* **Independent Bag Randomization:** Music, DJs, jingles, and ads use isolated shuffle queues. No audio repeats within its category until the entire "bag" is emptied, and each category replenishes independently.
* **Universal Asset Scanner:** Automatically discovers and maps new radio stations simply by dropping a properly formatted folder into the `assets/` directory.
* **Advanced System Media & Bluetooth Integration:** * Natively routes audio to Bluetooth devices.
  * Auto-pauses when headphones/Bluetooth disconnect (`Audio Becoming Noisy`).
  * Custom Lockscreen/System Notification displaying station artwork, frequency, track title, and a controlled manual "Skip" button via a `ForwardingPlayer`.

---

## 🏗️ Architecture & Code Structure

The project follows clean architecture principles separating business logic from Android framework dependencies:

* **`models/`**: Pure data classes representing audio layers (`AudioType.kt`, `AudioTrack.kt`, `RadioStation.kt`).
* **`manager/`**: The core behavioral engine.
  * `RadioPlaybackManager.kt`: Builds the custom broadcast queue block-by-block, utilizing `peekNextTrack` to intelligently decide whether to trigger a crossfade or wait for an ad.
  * `RadioStationFactory.kt`: Scans asset subdirectories dynamically and parses `station_info.json` configs, building the station list at runtime.
* **`service/`**: Background execution layer.
  * `RadioMediaService.kt`: The "Stage Director". Manages the Dual-Deck (`activePlayer`), Coroutine volume monitors, tuning effects (`general/`), and overrides media session commands for manual skips and station switching.
* **`viewmodel/` & `ui/`**: Presentation layer.
  * `RadioViewModel.kt`: Acts as a remote control, binding to the background `MediaController`.
  * `PlayerScreen.kt`: Jetpack Compose layout featuring a dynamic station selector popup and responsive `ContentScale.Fit` branding.

---

## 📂 Assets Folder Setup & Requirements

To properly feed the factory and the tuning engine, the `assets` folder structure and file naming conventions must strictly follow these rules.

### 1. Directory Tree Structure
Place your files inside the `app/src/main/assets/` folder precisely as shown below:

    app/src/main/assets/
                  ├── general/                          <-- Global SFX Folder (Required)
                  │    ├── ruido.mp3                    <-- White noise static
                  │    ├── sintonizando1.mp3            <-- Tuning effect 1
                  │    └── sintonizando2.mp3            <-- Tuning effect 2
                  │
                  └── radio_los_santos/                 <-- Radio Station ID Folder (snake_case)
                      ├── logo.png                      <-- Station Square Album Art Logo
                      ├── station_info.json             <-- JSON Config for Name & Frequency
                      ├── music/                        <-- Place all song .mp3 files here
                      ├── dj_talks/                     <-- Place all DJ host commentary .mp3 files here
                      ├── jingles/                      <-- Place all short station id/vignette .mp3 files here
                      └── ads/                          <-- Place all commercial/advertisement .mp3 files here

### 2. Station Configuration (station_info.json)

Inside each station's root folder, create a station_info.json file. If omitted, the app will safely fallback to the formatted folder name and a default 89.9 FM frequency.
    
    {
      "name": "Radio Los Santos",
      "frequency": "106.1 FM"
    }

## 3. Crucial Naming Rules (snake_case)

Android uses an underlying Unix-based URI file scheme. Characters like #, ?, &, ', ", brackets (), or spaces are reserved characters and will break file streaming.

    DO NOT USE uppercase letters, spaces, or symbols (#, ( ), ", ', accents) in audio files or station folder names.

    ALWAYS USE lowercase letters, numbers, and underscores (_) to separate words.

Examples:

    ❌ Radio Los Santos/               ➡️  ✅ radio_los_santos/

    ❌ K-DST(Atmosphere) Night #1.mp3  ➡️  ✅ kdst_atmosphere_night_1.mp3

## 🔒 Version Control & Privacy (.gitignore)

Since full audio repositories can exceed GitHub's file storage limits and violate copyright regulations (DMCA), the audio files are explicitly excluded from being tracked by Git.

Ensure your root .gitignore contains the following rule:

    # Exclude raw radio assets but keep the repository structure intact
    /app/src/main/assets/*
    !/app/src/main/assets/README.md