# Screenshot Captions — SpaceClean

Play allows 2–8 phone screenshots. Capture them from the **signed release build** so they
match the final UI (aurora + glass). Order matters — the first 2–3 are what most users see.

Recommended set (in order), with a short overlay caption you can add in any image editor:

| # | Screen to capture | Caption (overlay) |
|---|---|---|
| 1 | **Dashboard** (after Smart Scan All, tiles showing sizes) | "Reclaim space in seconds" |
| 2 | **Duplicates** screen (a group expanded, keeper marked) | "Find duplicate files — even across WhatsApp" |
| 3 | **A file list** with photo/PDF previews + sort/filter chips | "Real previews. Sort, filter, multi-select." |
| 4 | **App Storage** (apps by size, action sheet open) | "See every app by size — uninstall in a tap" |
| 5 | **Social Media Cleaner** (per-app groups) | "Clear WhatsApp, Telegram & more media" |
| 6 | **Settings** showing the privacy hero + theme picker | "100% offline. Pick your theme." |
| 7 | *(optional)* **Largest / Old files** list | "Hunt down your biggest space hogs" |
| 8 | *(optional)* Delete confirm dialog (Trash messaging) | "Safe by design — recoverable for 30 days" |

## Caption style tips
- Keep each overlay to **≤ 6 words**, high contrast, near the top or bottom third.
- Use a consistent font/color across all shots (match the cyan/white theme).
- Don't cover the actual UI content with the caption.
- Avoid claims you can't back up ("fastest", "best") — Play dislikes superlatives.

## How to capture
- Run the release build on a phone or emulator (1080×1920 or your device's resolution).
- Populate data first: run **Smart Scan All** so tiles/lists show real content.
- Use the device screenshot (Power+VolDown) or `adb exec-out screencap -p > shot.png`.
- Phone screenshots must be 16:9 or 9:16, min dimension ≥ 320 px, max ≥ 3840 px.

## Theme idea
Take shot #6 in **Light** theme and the rest in **Cyber/Ocean** — it shows off the new theme
switcher and signals polish.
