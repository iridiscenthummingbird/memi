# Memi

A minimal Android to-do / notes app. Notes are shown as colourful "capsules" that flow like words in a sentence, and are always at hand: in the app, on a home-screen widget, and on the lock screen.

Built with Kotlin and Jetpack Compose (Material 3). The UI is in Ukrainian.

## Features

- **Capsule flow layout.** Notes wrap like text, and a long note splits across lines.
- **Quick actions.** Tap a note to mark it done. Long-press to enter arrange mode, where you can drag to reorder, tap to edit, or delete (with undo).
- **Pinning and colours.** Pinned notes stay at the top. Each note can use one of 4 colours taken from the current theme.
- **Home-screen widget** (Glance). Shows the same capsule layout, toggles notes in place, and has a "+" button for quick add.
- **Lock-screen notification.** An optional, silent, ongoing notification that lists your active notes.
- **Themes.** Material You dynamic colours (Android 12+) or a set of preset hues, with system, light or dark mode.
- **Launcher shortcut** to add a note straight away.
- **Offline and private.** Notes are stored locally as JSON. There is no account, network access or tracking.

## Requirements

- Android 8.0+ (minSdk 26), targets SDK 36
- JDK 17 to build

## Build

```bash
./gradlew assembleDebug        # debug APK → app/build/outputs/apk/debug/
./gradlew installDebug         # install on a connected device/emulator
./gradlew testDebugUnitTest    # JVM unit tests
./gradlew assembleRelease      # R8-minified release build
```

### Release signing

A release build is signed when `app/release.jks` exists and a store password is provided. Otherwise it is built unsigned. Set the values in `local.properties` (gitignored):

```properties
release.storePassword=...
release.keyPassword=...      # optional, defaults to the store password
release.keyAlias=...         # optional
release.storeFile=...        # optional, defaults to release.jks
```

You can also set them as environment variables (`RELEASE_STOREPASSWORD`, `RELEASE_KEYPASSWORD`, …).

## Project structure

```
app/src/main/java/app/memi/
├── NotesRepo.kt          # single source of truth, JSON persistence, list logic
├── Settings.kt           # theme / mode / lock-notification prefs
├── CapsuleFlow.kt        # pure capsule flow layout (shared by app + widget)
├── LockNotification.kt   # lock-screen notification
├── MainActivity.kt, App.kt, BootReceiver.kt
├── ui/                   # Compose screen, settings sheet, themes
└── widget/               # Glance home-screen widget
```

See [CLAUDE.md](CLAUDE.md) for a more detailed architecture overview.
