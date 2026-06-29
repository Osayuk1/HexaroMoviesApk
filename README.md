# Hexaro Movies

Hexaro Movies is an Android streaming aggregator built with modern Kotlin and Jetpack Compose. It is designed to deliver a native, ad-free streaming experience with offline supports, customizable theme engines, and high-performance local caching.

![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white) ![Jetpack Compose](https://img.shields.io/badge/Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white) ![Android](https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white) ![SQLite](https://img.shields.io/badge/SQLite-07405e?style=for-the-badge&logo=sqlite&logoColor=white) ![Gradle](https://img.shields.io/badge/Gradle-02303A?style=for-the-badge&logo=gradle&logoColor=white)

---

## Key Capabilities

* **Ad-Free Media Aggregator**: Structured embedded player framework supporting major streaming sources (vidsrc, multiembed, 2embed) with responsive playback controls, automated season/episode progression tracking, and swipe-to-gesture (brightness/volume/speed) controls.
* **Offline Storage Engine**: Support for direct episode and film streaming downloads to a dedicated Offline Library tab for offline playback.
* **Intelligent Progress Persistence**: Automated real-time local logging of watching sessions with a dedicated "Currently Watching" dashboard on the home navigation.
* **Robust Local Synchronization**: Complete offline-first Room state system supporting:
    * **Watchlist**: Track future interest with instant status toggles.
    * **Progress History**: Track individual playback milestones and episode numbers.
    * **Favorites**: Dedicated curation for favorite films and television shows.
* **Custom Color Themes**: Theme customization engine including multiple custom themes: Cosmic Purple (Default), Emerald Ocean, Crimson Shadow, Sapphire Star, and Sunset Gold.
* **Adaptive Android Layout**: Edge-to-edge system bar layout, fully optimizing padding against display notches and device aspect ratios.

## Architecture & Technology Stack

* **UI Layer**: Built entirely in Jetpack Compose utilizing standard Material Design 3 guidelines.
* **Software Design Pattern**: Model-View-ViewModel (MVVM) coupled with standard unidirectional state flow via Coroutines `StateFlow` and `SharedFlow`.
* **Local Persistence**: SQLite database wrapped in modern Room persistence library with customized type converters for complex schema indexing.
* **Concurrency**: Pure Kotlin Coroutines for asynchronous network pooling and background download queues.
* **Image Delivery**: Coroutine-assisted caching and rendering via the Coil framework.

## Setup & Build

1. **Clone the repository:**
   ```bash
   git clone https://github.com/Osayuk1/HexaroMoviesApk.git
   cd HexaroMoviesApk
   ```

2. **Configure Environment Variables:**
   Rename `.env.example` in the root folder to `.env` and assign your standard API key values:
   ```env
   TMDB_API_KEY=your_tmdb_api_key
   ```

3. **Assemble Debug Build:**
   ```bash
   ./gradlew assembleDebug
   ```
