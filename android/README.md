# LinguaLoop Android

Native Kotlin client for the LinguaLoop REST API — the exact same contract
the web client uses (see `../docs/porting.md` for the shared contract,
shared logic and per-platform reimplementations).

## Stack

Kotlin 2.1, Jetpack Compose + Material 3, Hilt, Retrofit +
kotlinx.serialization, Room, Coroutines/Flow, ExoPlayer (Media3),
WorkManager, `minSdk 26`.

## Features

- Login/register against `/api/auth/*`; JWT stored in
  `EncryptedSharedPreferences` (Keystore-backed AES256-GCM).
- Home: today's queue, streak, per-unit mastery ring.
- Lesson player: TRANSLATE (text), MULTIPLE_CHOICE (radio), LISTEN
  (ExoPlayer + captions toggle).
- Offline-first: lessons cached as JSON in Room, audio downloaded to
  app-private storage; attempts and completion queued in Room and flushed
  FIFO by a WorkManager worker on connectivity. Finishing an already-started
  lesson offline works (starting a new one requires connectivity — a server
  contract constraint, see porting.md).
- Honors the `hint_timing` variant delay from the session payload.
- Accessibility: content descriptions on icons, `liveRegion` semantics for
  answer feedback, Material 3 minimum interactive sizes (48dp).

## Build & test

```bash
export ANDROID_HOME=/path/to/sdk   # platform 35 + build-tools 34
./gradlew testDebugUnitTest                 # JVM: ViewModel (Turbine), API (MockWebServer), sync
./gradlew connectedDebugAndroidTest         # Compose E2E lesson completion (emulator/device)
./gradlew assembleDebug                     # app-debug.apk
```

The API base URL is `BuildConfig.API_BASE_URL`
(`app/build.gradle.kts`, default `http://10.0.2.2:8080` — the emulator alias
for the host machine where the API runs).
