# MorningSteps Kids

MorningSteps Kids is an offline routine organizer for children and parents. It shows morning, evening and school routines as a path of stepping stones from **Start** to **Done**. Children mark steps as done at their own pace. Parents edit routines, change the order of steps and can add an optional, gentle timer to a step.

There are no accounts, servers, ads, analytics, payments, notifications or network access. The app makes no health, medical, developmental or therapeutic claims.

## Features

- **Four starter routines**, seeded once on first launch and fully editable:
  - Morning
  - Evening
  - Before School
  - After School

  All starter timers are off.
- **Step Path main screen.** Large icon nodes alternate along a curved, dashed trail from Start to Done.
  - Each node shows its position, a checkmark when done, and a timer badge when a timer is set.
  - The suggested next step has a soft outline and the text "Up next", so progress doesn't depend on color alone.
  - The trail is filled up to each completed node.
- **Gentle progress.** Progress is shown as "2 of 4 steps done" with a progress bar. There are no points, streaks, rankings or warnings.
- **Order is only guidance.** A child can open, complete or undo any step, and there is no penalty for doing a later step first.
- **Next step** opens the first step that isn't done yet. The path never scrolls by itself after a completion.
- **Finishing a routine.** The Done node unlocks when every step is complete. A review screen saves the result, and saving is atomic and protected against duplicate taps. A quiet completion screen follows, with "Back to routines" and "Start again".
- **Optional step timers** (15 s to 30 min) with Start, Pause, Resume and Reset.
  - At zero the screen says "Timer finished. Take the time you need." The step is never completed automatically.
  - The screen doesn't flash and there is no alarm.
  - Only one timer runs at a time. Starting a second one offers to pause the first.
- **History** of the latest 100 finished routines, newest first. Each entry has a step snapshot that does not change when routines are edited or deleted.
- **Parents area**, opened by holding the labeled Parents button for 3 seconds. A short tap, or a screen reader, opens an accessible alternative: a simple grown-up question. This prevents accidental entry; it is not security. The area contains:
  - Routine management: create, edit, duplicate, delete and reorder
  - The routine editor: icons, instructions, timers, and drag-and-drop with accessible Move up / Move down
  - Sound and motion preferences
  - Delete history
  - Privacy information
  - Restore starter routines
  - Clear all local data
- **Routine editor** with an explicit Save/Cancel flow.
  - Each step has a visible drag handle. Other steps shift live as you drag, and the list scrolls automatically near the edges.
  - Step identifiers stay stable across reorders.
  - Move up, Move down, Edit and Remove are available both as menu items and as TalkBack custom actions.
  - Saving changes to a routine with unfinished progress asks for confirmation and restarts that session.
  - Leaving with unsaved changes asks whether to discard them.
- **Accessibility**
  - Child-facing text is at least 18 sp and scales with the system font size.
  - Touch targets are at least 48 dp.
  - Path nodes and icons have content descriptions, and the path is read in a logical top-to-bottom order.
  - Completion changes are announced through polite live regions.
  - The in-app "Reduce motion" switch and the system "Remove animations" setting are both respected.

## Architecture

The app is a single module, `:app`, written in Kotlin with Jetpack Compose and using manual dependency injection (`AppContainer`).

```
com.morningsteps.kids
├── data/local          Room entities, DAOs (interfaces), AppDatabase + one-time seed callback, TransactionRunner
├── data/repository    RoutineRepository (all mutations, transactional), SettingsRepository (DataStore), StarterSeed
├── domain/routines    Models, Progress, Reorder, validation (DraftValidator), starter routines, unique names
├── domain/timers      AppClock, TimerEngine (pure, monotonic), ChimePolicy
├── platform           AndroidClock (elapsedRealtime + boot counter), ChimePlayer (SoundPool)
└── ui                 AppRoot (Navigation Compose), ViewModelFactories
    ├── path           Step Path, routine picker, finish review, completion
    ├── step           Step detail + timer
    ├── history        History list + detail
    ├── parent         Parent gate, parent area, routine editor, drag-and-drop (Reorderable.kt), privacy
    ├── common         Shared composables and formatting
    └── theme          Palette, typography, routine colors and icons
```

- The UI follows the ViewModel → `StateFlow` → `collectAsStateWithLifecycle` pattern.
- Database work runs off the main thread, using Room's suspend functions and Flows.
- Timer math in `TimerEngine` is pure and separate from rendering, so it is unit-tested with a fake clock.
- Drag-and-drop is built only on Compose `pointerInput` and `LazyListState`; no extra library is needed.

### Dependencies (all declared directly in `gradle/libs.versions.toml`)

| Library | Version | Why |
|---|---|---|
| androidx.core:core-ktx | 1.16.0 | Kotlin extensions |
| androidx.activity:activity-compose | 1.10.1 | `setContent`, `enableEdgeToEdge`, `BackHandler` (predictive Back compatible) |
| Compose BOM | 2025.08.00 | ui, ui-graphics, foundation, animation, runtime, material3, tooling-preview (+ ui-tooling in debug only) |
| androidx.lifecycle (common, runtime-ktx, runtime-compose, viewmodel-ktx, viewmodel-compose) | 2.9.2 | ViewModel, lifecycle-aware collection, `repeatOnLifecycle` |
| androidx.navigation:navigation-compose | 2.9.3 | Navigation |
| androidx.room (runtime, ktx, compiler via KSP, Gradle plugin) | 2.7.2 | Local database + exported schemas |
| androidx.sqlite:sqlite | 2.5.0 (resolved to Room's version) | `SupportSQLiteDatabase` in the seed callback |
| androidx.datastore:datastore-preferences | 1.1.7 | Small settings |
| kotlinx-coroutines (core, android) | 1.10.2 | Coroutines |
| junit | 4.13.2 | Unit tests |

The app uses no networking libraries, Firebase, analytics or other SDKs.

## Toolchain

| Tool | Version |
|---|---|
| JDK | 17 (Temurin in CI) |
| Gradle (wrapper, committed) | 8.14.3 |
| Android Gradle Plugin | 8.13.0 |
| Kotlin + Compose compiler plugin | 2.2.10 |
| KSP | 2.2.10-2.0.2 |
| Build tools (CI) | 36.0.0 |
| **compileSdk / targetSdk / minSdk** | **36 / 36 / 26** |

Setup: install JDK 17 and the Android SDK with Platform 36, then point `local.properties` (`sdk.dir=…`) or `ANDROID_HOME` to the SDK.

### Android 16 (API 36) notes

- Edge-to-edge can no longer be opted out of. The app calls `enableEdgeToEdge()`, and every screen pads for system bars, display cutouts and the IME through `WindowInsets.safeDrawing`.
- Predictive Back is on by default (`enableOnBackInvokedCallback="true"`). Back is handled only through `BackHandler`/Navigation, never `onBackPressed`.
- On large screens, orientation and resizability restrictions are ignored. The app sets none: it rotates, resizes, and limits content width on tablets.
- The display is never kept awake.

## Build

```bash
./gradlew testDebugUnitTest        # unit tests
./gradlew lintRelease              # release lint (abortOnError)
./gradlew assembleDebug            # debug APK, no release credentials needed
./gradlew assembleRelease bundleRelease   # signed release APK + AAB (needs credentials, see below)
```

Outputs:

- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`. The application id is `com.morningsteps.kids.debug`, so it can be installed next to the release build.
- Release APK: `app/build/outputs/apk/release/app-release.apk`. Use it for local installation and verification only.
- Release AAB: `app/build/outputs/bundle/release/app-release.aab`. **Only the AAB is uploaded to Google Play.**

Room writes its schema to `app/schemas/com.morningsteps.kids.data.local.AppDatabase/1.json` during the first build. CI also uploads it in the `reports` artifact. **Commit that file.** Any future schema change must bump the version and add a `Migration` (or AutoMigration) to `AppDatabase.MIGRATIONS`; destructive migration is not enabled.

## Release signing (PKCS12)

`app/build.gradle.kts` defines `signingConfigs.release` with `storeType = "PKCS12"` and assigns it to the release build type.

- **Where credentials come from:** environment variables first, then an uncommitted `keystore.properties` in the project root.
- **Missing credentials:** any `assembleRelease` / `bundleRelease` / `packageRelease` / `installRelease` task fails at once. The build never falls back to debug signing. Debug builds work without credentials.

| Environment variable | keystore.properties key |
|---|---|
| `ANDROID_KEYSTORE_PATH` | `storeFile` |
| `ANDROID_KEYSTORE_PASSWORD` | `storePassword` |
| `ANDROID_KEY_ALIAS` | `keyAlias` |
| `ANDROID_KEY_PASSWORD` | `keyPassword` |

Required GitHub Secrets:

- `ANDROID_KEYSTORE_BASE64`: the `.p12` file, base64-encoded on one line
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

For PKCS12, the key password is the same as the store password.

`.gitignore` excludes `*.p12`, `*.jks`, `*.keystore` and `keystore.properties`. Passwords never appear in the Gradle scripts or in logs.

**Google Play App Signing.** The keystore above is the **upload key**. When you enroll in Play App Signing, Google generates and holds the **app signing key**, which signs what users install. You sign each AAB with the upload key, and Play checks it against the registered upload certificate.

- If the upload key is lost or leaked, you can request an upload-key reset in Play Console. The app signing key is not affected.
- Keep the `.p12` file and its password backed up offline.

## CI (GitHub Actions)

`.github/workflows/android.yml` runs on every push to `main`, on pull requests, and manually:

1. Installs JDK 17, Android SDK Platform 36 and build-tools 36.0.0, and uses the committed Gradle Wrapper.
2. Runs `testDebugUnitTest` and `lintRelease`, then uploads the reports and the Room schema.
3. Decodes the PKCS12 keystore into `$RUNNER_TEMP`.
4. Builds the signed release APK and AAB.
5. Checks the APK:
   - Runs `apksigner verify --print-certs`.
   - Fails on `CN=Android Debug`.
   - Fails if the signer's SHA-256 differs from the keystore certificate.
6. Checks the AAB:
   - Runs `jarsigner -verify`, which must report "jar verified" with no unsigned entries.
   - A self-signed upload certificate is accepted; only integrity and signer identity matter.
   - Uses `keytool -printcert -jarfile` to confirm the signer SHA-256 matches the keystore and isn't `CN=Android Debug`.
7. Checks permissions. `aapt2 dump permissions` on the release APK, plus the merged release manifest, must declare no permissions.
8. Inspects native libraries in the APK and AAB. If `.so` files ever appear, it checks `zipalign -P 16` and ELF `LOAD` alignment (16 KB or more).
9. Uploads the verified APK, AAB, certificate printouts, permission report and native-library report.
10. Deletes the decoded keystore (`if: always()`).

## R8 / resource shrinking

Release builds are **not minified by default**, so a signed, non-minified release can be verified first. After it passes:

1. Run the workflow manually with `enable_r8 = true`, or build locally with `./gradlew -PenableR8=true assembleRelease bundleRelease`.
2. Repeat installation and the core-flow checks.
3. Keep the `r8-mapping` artifact, which CI uploads for R8 builds.

No custom keep rules are needed: the app uses no reflection, and Room, DataStore and Compose ship their own consumer rules.

## Offline behavior, privacy, storage and backup

- The app works fully offline from the first launch. Every icon is a bundled vector drawable, and the chime is a bundled 0.9 s WAV file.
- Room stores routines, steps, sessions, timers and history. DataStore stores the selected routine, the sound preference (off by default) and the reduce-motion preference. Both live in app-private storage.
- Backup and transfer are disabled:
  - `android:allowBackup="false"`
  - `fullBackupContent` excludes every domain (Android 11 and lower)
  - `dataExtractionRules` excludes every domain for cloud backup and device transfer (Android 12 and higher)
- The manifest explicitly strips `INTERNET` and `ACCESS_NETWORK_STATE` (`tools:node="remove"`) in case a dependency adds them. There are no runtime permissions.
- The Privacy screen (Parents → Privacy) explains all of this to the parent.
- "Clear all local data" asks for confirmation, then wipes everything in one transaction, re-seeds the starter routines, and clears the preferences.

## Timer lifecycle and recovery

- Each timer is stored as a checkpoint: `remainingAtCheckpointMillis`, `checkpointElapsedRealtimeMillis` and `bootReference` (`Settings.Global.BOOT_COUNT`). A new checkpoint is persisted on start, pause, resume, reset and finish.
- Remaining time is always `remainingAtCheckpoint − (elapsedRealtime − checkpoint)`. No counter is decremented, and changes to the wall clock have no effect.
- No background work, foreground service, alarms, notifications or background audio are used. While the app is in the background, time keeps elapsing on the monotonic clock. When the app comes back, `recoverTimers()` runs in `onStart` and when the step screen resumes:
  - **Same boot:** the timer shows its true remaining time.
  - **Same boot, timer expired while away:** the timer is marked *finished* silently.
  - **Device rebooted, or the reference is invalid** (boot counter changed, or `elapsedRealtime` is lower than the checkpoint): the timer is restored as *paused* with its last persisted remaining time, and the screen says "The timer was paused because the device restarted."
- **Chime rules:**
  - The chime is optional and off by default.
  - It plays only if the expiry was observed live by the visible step screen. The previous tick must have shown time left and be at most 1.5 s old (`ChimePolicy`).
  - It is never played after returning from the background, and never in silent or vibrate mode.
  - It uses the sonification audio usage, so it follows the device volume.
- **Timers and completion stay independent:**
  - Completing a step early stops its timer.
  - Resetting a timer never undoes completion.
  - Starting a timer on a completed step requires undoing the step first.

## 16 KB page-size compatibility

The app has no native code (no NDK or CMake), and none of its declared dependencies (AndroidX, Compose, Room, DataStore, coroutines) ship `.so` files for Android.

- **Expected result:** no `lib/**.so` entries in the APK or AAB, so ELF alignment does not apply.
- **How it is checked:** the CI step "Inspect native libraries" lists every `.so` in the actual packaged APK and AAB and writes `native-libs.txt` into the release artifact. If a dependency ever adds native libraries, CI checks zipalign and ELF `LOAD` alignment and fails if either is under 16 KB.
- **Status:** pending until the first CI run confirms the list is empty. Even then, runtime behavior in a 16 KB environment has **not** been tested on a device or emulator. Targeting API 36 alone does not prove compatibility.

## Local verification with adb

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
adb logcat --pid=$(adb shell pidof -s com.morningsteps.kids) '*:W'
# full log for the app process:
adb logcat --pid=$(adb shell pidof -s com.morningsteps.kids)
adb shell dumpsys package com.morningsteps.kids | grep -i permission   # expect no requested permissions
```

Checklist for each device or emulator. Record the device, Android version, artifact (CI run and SHA-256) and the result:

- [ ] First launch in airplane mode; all four starter routines and their icons appear
- [ ] Complete and undo steps; force-stop and relaunch; progress is kept
- [ ] Switch between two routines with unfinished progress; each keeps its own progress
- [ ] Parent editing: rename, add, edit and remove steps; drag-and-drop with auto-scroll; Move up and Move down via the menu and via TalkBack actions
- [ ] Timer: start, background the app for 30 s, return; the remaining time is correct
- [ ] Timer expiry while in the background: finished state is shown with no sound on return (sound setting on)
- [ ] Reboot with a running timer: it is restored paused, with the restart note
- [ ] Finish a routine, edit it, then delete it; the history detail still shows the original snapshot
- [ ] Rotation, largest font size, keyboard in the editor fields, Android Back on every screen (including predictive back)
- [ ] Clear all local data; starter routines return and history is empty
- [ ] No permission prompts, no crashes in logcat, nothing depends on the network

## Verification status

| Check | Status |
|---|---|
| Domain and repository unit tests: 33 tests (timer pause, resume, reset and expiry; background and restart recovery; wall-clock immunity; chime policy; completion and undo; progress; reordering; validation; duplicate-finish prevention; history snapshots after edit and delete; session reset and history retention; one running timer; restore and clear-all) | **Passed.** Compiled with kotlinc 2.2.10 and run with JUnit 4.13.2 outside Gradle, against Room annotation stubs and in-memory fake DAOs. |
| `./gradlew testDebugUnitTest lintRelease` | **Pending.** Runs on the first CI run. |
| Signed release APK and AAB, signature, signer identity, permissions, native-library report | **Pending.** Runs on the first CI run with the secrets set. |
| Room schema JSON committed | **Pending.** Generated by the first build. |
| R8-enabled release | **Pending.** Do this after the non-minified release has been verified. |
| Device and emulator checklist above | **Pending.** Not performed yet. |
| 16 KB runtime check | **Pending.** Not performed yet. |

Nothing in this table is reported as passed unless it was actually run.
