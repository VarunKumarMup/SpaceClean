# AI Space Cleaner 🧹

A premium, **100% offline** Android storage cleaner with **on-device AI**. No internet
permission, no ads, no analytics, no accounts — your files and scan results never leave your
device, because there is no network code to send them. Even the AI runs entirely on the phone.

*(Project/package codename: `spaceclean` / `io.github.varunkumar.spaceclean`.)*

## Features

- **AI Photo Cleanup (on-device)** — a bundled neural network (TensorFlow Lite) finds blurry,
  near-duplicate, and screenshot photos with zero network access; you review before deleting.

- **Smart Scan** — 12 categories scanned in one tap: photos, videos, apps, documents,
  audio, downloads, chat media, leftover folders, useless files, largest, old & unused,
  and exact duplicates.
- **Quick Clean with review** — auto-selects only safe junk (similar-photo extras,
  duplicate copies, leftover folders, download junk, useless files) and shows a full
  preview before anything is deleted. Which categories get auto-selected is
  user-configurable in Settings; documents are never auto-selected by default.
- **Similar-photo detection** — perceptual hashing (dHash + Hamming-distance clustering),
  fully on-device.
- **True duplicate detection** — content hashing across phone storage and chat apps.
- **Recently Deleted** — deleted media goes to the Android system Trash and is restorable
  in-app for ~30 days.
- **Big-file suggestions** — really large files are surfaced for review but never
  pre-selected.
- **Chat media cleaner** — WhatsApp, Telegram, Messenger, Viber, Signal residue.
- **App storage manager** with in-app uninstall, targeted folder scan (SAF), global
  search, themes.

## Tech

- Kotlin + Jetpack Compose (Material 3), single-activity, `StateFlow` MVVM
- MediaStore APIs (`createTrashRequest`, trashed-item queries), scoped-storage aware
- minSdk 26 · targetSdk 36

## Build

```bash
./gradlew :app:assembleDebug        # debug APK
./gradlew :app:bundleRelease        # Play Store bundle (needs your own keystore)
```

Release signing expects a `keystore.properties` in the project root (not committed):

```properties
storeFile=spaceclean-upload.jks
storePassword=...
keyAlias=...
keyPassword=...
```

## Store / legal

`store/` contains the Play listing copy, privacy policy, terms of use (EULA), icon and
feature-graphic sources, and an internal-testing checklist.

## License

**Source-available, no redistribution.** You're welcome to read the code, learn from it,
and build it for your own personal use — but redistributing the source or compiled builds
(including publishing to any app store) is not permitted. The only official distribution
channel is the developer's own Google Play listing. See [LICENSE](LICENSE) for the full terms.

© 2026 Varun Kumar Muppuri · varunkumarmuppuri@gmail.com
