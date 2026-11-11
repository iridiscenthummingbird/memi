# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

MyNotes (`ua.mynotes`) — a single-module Android to-do/notes app in Kotlin + Jetpack Compose (Material 3), with a Glance home-screen widget and a persistent lock-screen notification. minSdk 26, target/compile SDK 36, JVM 17. No DI, no ViewModels, no database. User-facing strings are Ukrainian and are mostly hardcoded in Kotlin (not only in `strings.xml`).

## Workflow

Do not use git worktrees in this project — work directly in the main checkout (owner's preference).

## Commands

```bash
./gradlew assembleDebug                 # debug APK → app/build/outputs/apk/debug/
./gradlew installDebug                  # install on connected device/emulator
./gradlew assembleRelease               # needs app/release.jks (gitignored, not in repo)
./gradlew testDebugUnitTest             # JVM unit tests
./gradlew testDebugUnitTest --tests "ua.mynotes.CapsuleFlowTest.capsuleIsCutAtLineEndAndContinues"
```

No lint/format tooling is configured beyond `kotlin.code.style=official`. The only tests are pure-JVM tests of the capsule layout (`app/src/test`); there are no instrumented tests.

## Architecture

**Global singletons as state.** `NotesRepo` and `Settings` are Kotlin `object`s exposing `StateFlow`s. They must be `init(context)`-ed before use, and every entry point does so itself (`App`, `MainActivity`, `BootReceiver`, `NotesWidget.provideGlance`) because the widget/boot receiver can run without the Activity. `init` is idempotent.

**NotesRepo is the single source of truth** for the app, the widget and the notification. Notes persist as JSON in `filesDir/notes.json` via `AtomicFile` (hand-written `org.json` serialization; new fields must be read with `opt*` defaults for backward compatibility). Every mutation goes through `update {}`, which re-sorts (active before done → pinned first → manual `order` → `created`), saves, then calls `onChanged()`. `onChanged()` refreshes `LockNotification` and calls `NotesWidget().updateAll()` — so any new mutation path or setting that affects visuals must end up calling `NotesRepo.onChanged()` (as `Settings.setTheme/setMode` do), otherwise the widget/notification go stale. Note text is single-line (newlines are replaced with spaces).

**Capsule flow layout (`CapsuleFlow.kt`).** Notes render as "capsules" that flow like words in text; a capsule that doesn't fit the rest of a line is split, and the continuation starts the next line (`CapsuleSeg.first/last` decide which ends are rounded). `flowCapsules()` is pure and measurement-agnostic (takes a `measure` lambda), and is shared by both the Compose screen (`ui/NotesScreen.kt`) and the Glance widget (`widget/NotesWidget.kt`). The widget passes `maxPerLine` because Glance rows can hold at most 10 children. Changes to this algorithm affect both surfaces — run `CapsuleFlowTest`.

**Theming is duplicated across Compose and Glance.** `ui/Themes.kt` defines the theme list (index 0 = Material You/dynamic colours on API 31+, others = a single hue → generated M3 scheme) and `notePalette()` (4 vivid note colours derived from the primary). The app uses it via `NotesTheme`/`noteColors()`; the widget builds its own `ColorProviders`/palette from the same functions with `DayNightColor`, honouring `Settings.mode` (system/light/dark). Keep both mappings in sync when changing colours. `Note.color` is an index into this palette (`NOTE_COLORS = 4`).

**Entry points into the app.**
- `MainActivity` (`singleTask`) handles action `ua.mynotes.ADD` (from the widget "+" and the launcher shortcut in `res/xml/shortcuts.xml`) by bumping a counter that focuses the input field in `NotesScreen`.
- Widget taps on a note run `ToggleNoteAction` (Glance `ActionCallback`) directly against `NotesRepo`.
- `BootReceiver` re-posts the lock-screen notification after boot/app update.

**Lock-screen notification (`LockNotification.kt`).** Silent ongoing InboxStyle notification listing active notes (max 7 lines). Shown only if enabled (pref `lock_notification` in the shared `settings` prefs), there are active notes, and `POST_NOTIFICATIONS` is granted; otherwise it is cancelled. `MainActivity.onResume` re-triggers `onChanged()` to pick up permission changes.

**UI (`ui/NotesScreen.kt`).** Single screen: capsule flow + input bar + settings sheet + edit sheet. Tap toggles done; long press enters "arrange mode" (delete crosses, drag & drop reorder → `NotesRepo.reorder`, tap to edit). Deletions use snackbar undo via `NotesRepo.restore`.
