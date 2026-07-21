# Google Play Store Listing — AI Space Cleaner

Copy/paste-ready text plus the forms you'll fill in Play Console. Character limits noted.

---

## App title  (max 30 chars)

```
AI Space Cleaner
```
*(16 chars)*

## Short description  (max 80 chars)

```
On-device AI photo cleanup + duplicates. 100% offline. No ads, no tracking.
```
*(74 chars)*

## Full description  (max 4000 chars)

```
AI Space Cleaner is a storage cleaner and file manager built around one principle: your files
are nobody's business but yours. It works 100% offline — there is literally no internet
permission in the app, so nothing can ever leave your phone. No ads. No tracking. No accounts.
Even the AI runs entirely on your device.

ON-DEVICE AI PHOTO CLEANUP
A neural network built into the app (no cloud, no upload) looks at your photos and finds the
junk you'd want gone:
• Blurry and low-quality shots
• Near-identical / burst photos — it keeps the best one and flags the rest
• Screenshots and memes
You review everything before a single file is deleted. The AI never sends your photos anywhere.

WHAT ELSE IT FINDS
• Photos — duplicates and burst shots, with side-by-side compare
• Videos — large and redundant clips
• Audio — music and voice notes
• Documents — PDFs, ZIPs, archives, filterable by type
• Downloads & useless files — leftover APKs, ZIPs, GIFs, temp and log junk
• Largest files — the biggest space hogs across your storage
• Old & unused — your oldest files, surfaced for review
• Duplicate files — exact copies across phone storage AND chat apps (e.g. the same photo in
  your gallery and in WhatsApp), found by comparing real file content
• Leftover folders — empty/orphaned folders from uninstalled apps
• App storage — see every app by size and uninstall in a tap

POWERFUL TOOLS
• Smart Scan — scan every category at once
• Quick Clean — one tap to review and clear safe junk; you choose which categories in Settings
• Social Media Cleaner — clear WhatsApp, Telegram, Messenger, Viber and Signal media
• Scan any folder — point it at any folder (including an SD card) and clean it
• Global search, PDF and media previews, sort and filter, multi-select

SAFE BY DESIGN
• Everything you delete goes to a recoverable Trash for about 30 days — photos and videos to
  your device Trash, other files to the app's own trash — so nothing is lost by accident
• Clear confirmation before anything is removed

PRIVATE BY DESIGN
• No internet permission — verifiable, not just a promise
• No ads, no analytics, no third-party tracking
• Everything, including the AI, runs on your device

A one-time purchase. No subscriptions, no in-app purchases, no surprises.
```

---

## Store settings

- **App category:** Tools
- **Tags:** cleaner, file manager, storage, duplicate finder, AI
- **Content rating:** Everyone (complete the IARC questionnaire — no objectionable content)
- **Price:** **Paid — $1.99 USD** (the standard Play price point for "~$2"; you can set exactly
  $2.00 under Monetization → Products if you prefer the round number). Set this BEFORE the first
  release. Requires a Play payments/merchant profile.
- **Contains ads:** No
- **In-app purchases:** No

## Privacy policy URL

Already hosted on your public repo:
`https://github.com/VarunKumarMup/SpaceClean/blob/main/store/PRIVACY_POLICY.md`

## Data safety form (Play Console → App content → Data safety)

- **Does your app collect or share any user data?** → **No.**
- **Is all data encrypted in transit?** → N/A (no data leaves the device).
- **Do you provide a way to request data deletion?** → N/A (no data collected).
Yields the strong "No data collected / No data shared" badge. The on-device AI does **not**
change this answer — no photo or inference data ever leaves the phone.

## Permission declarations (Play Console → App content)

**All files access (MANAGE_EXTERNAL_STORAGE):**
```
AI Space Cleaner is a file manager and storage cleaner. Core functionality requires broad
access to the user's files to scan, preview, and delete photos, videos, documents, downloads,
duplicates, and leftover folders across the device. Scoped storage / MediaStore alone cannot
enumerate and remove arbitrary user files (e.g. duplicate files in chat-app folders), which is
the app's primary purpose. No file data is transmitted off the device — the app has no internet
permission, and its AI photo analysis runs entirely on-device.
```

**Query all packages (QUERY_ALL_PACKAGES):**
```
The App Storage feature lists the user's installed apps with their sizes so the user can review
and uninstall space-consuming apps. This requires enumerating installed packages. The list is
displayed only on-device and is never transmitted.
```

---

## Graphics

| Asset | Spec | Status |
|---|---|---|
| App icon | 512×512 PNG | ✅ `store/icon-512.png` |
| Feature graphic | 1024×500 PNG | ✅ `store/feature-graphic-1024x500.png` |
| Phone screenshots | min 2 (recommend 4–8), ≥320px | ⏳ Capture on device — include the AI Photo Cleanup screen |

Take screenshots from the **release** build so they reflect the final UI, and be sure one shows
the **AI Photo Cleanup** screen (it's the headline feature and justifies the "AI" name).

---

## Pre-launch checklist

- [ ] Privacy policy URL added in Console (GitHub URL above)
- [ ] Play Console account ($25) + **payments/merchant profile** (required for paid apps)
- [ ] New app created — title **AI Space Cleaner**, **Paid**
- [ ] Upload `app-release.aab` (accept Google-managed Play App Signing)
- [ ] Data safety = "No data collected"
- [ ] All-files-access + Query-all-packages declarations submitted (+ demo video ready)
- [ ] Content rating → Everyone
- [ ] Price $1.99 (or $2.00) + all countries selected
- [ ] Title, descriptions, icon, feature graphic, screenshots uploaded
- [ ] **Smoke-test the signed release on a real device** — scan, AI Photo Cleanup, a
      delete-to-Trash + restore, an uninstall, PDF previews
- [ ] Internal testing first, then promote to Production
```
