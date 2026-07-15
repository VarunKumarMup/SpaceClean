# Google Play Store Listing — SpaceClean

Copy/paste-ready text plus the forms you'll fill in Play Console. Character limits noted.

---

## App title  (max 30 chars)

```
SpaceClean: Offline Cleaner
```
*(27 chars)*

## Short description  (max 80 chars)

```
Private, 100% offline storage cleaner. No ads, no internet, no tracking.
```
*(72 chars)*

## Full description  (max 4000 chars)

```
SpaceClean is a storage cleaner and file manager built around one principle: your files are
nobody's business but yours. It works 100% offline — there is literally no internet permission
in the app, so nothing can ever leave your phone. No ads. No tracking. No accounts.

Reclaim space in seconds with a clean, modern interface.

WHAT IT FINDS
• Photos — duplicate and burst shots, with side-by-side compare
• Videos — large and redundant clips
• Audio — music and voice notes
• Documents — PDFs, ZIPs, archives, and more, filterable by type
• Downloads — leftover APKs, ZIPs and junk
• Largest files — the biggest space hogs across your storage
• Old & unused — your oldest files, surfaced for review
• Duplicate files — exact copies across phone storage AND chat apps (e.g. the same photo in
  your gallery and in WhatsApp), found by comparing real file content
• Leftover folders — empty/orphaned folders from uninstalled apps
• App storage — see every app by size and uninstall in a tap

POWERFUL TOOLS
• Smart Scan All — scan every category at once
• Social Media Cleaner — clear WhatsApp, Telegram, Messenger, Viber and Signal media, grouped
  per app with previews
• Scan any folder — point it at any folder (including an SD card) and clean it
• Global search across everything you've scanned
• PDF and media previews, sort and filter, multi-select

SAFE BY DESIGN
• Deleted media goes to your device's Trash, recoverable for ~30 days
• Clear confirmation before anything is removed

PRIVATE BY DESIGN
• No internet permission — verifiable, not just a promise
• No ads, no analytics, no third-party code
• Everything runs on your device

A one-time purchase. No subscriptions, no in-app purchases, no surprises.
```

---

## Store settings

- **App category:** Tools
- **Tags:** cleaner, file manager, storage, duplicate finder
- **Content rating:** Everyone (complete the IARC questionnaire — no objectionable content)
- **Price:** Paid — suggested **$1.99–$2.99** (set under Monetization → set up before first release)
- **Contains ads:** No
- **In-app purchases:** No

## Privacy policy URL

You must host `PRIVACY_POLICY.md` at a public URL and paste it in Console. Easiest options:
- **GitHub Pages / Gist** (free) — paste the markdown into a public Gist and use its raw URL, or
  enable Pages on a repo.
- **Google Sites** (free) — paste the text into a one-page site.

## Data safety form (Play Console → App content → Data safety)

Answer:
- **Does your app collect or share any user data?** → **No.**
- **Is all data encrypted in transit?** → N/A (no data leaves the device).
- **Do you provide a way to request data deletion?** → N/A (no data collected).
This yields the strong "No data collected / No data shared" badge.

## Permission declarations (Play Console → App content)

You will be prompted to justify these. Suggested text:

**All files access (MANAGE_EXTERNAL_STORAGE):**
```
SpaceClean is a file manager and storage cleaner. Core functionality requires broad access to
the user's files to scan, preview, and delete photos, videos, documents, downloads, duplicates,
and leftover folders across the device. Scoped storage / MediaStore alone cannot enumerate and
remove arbitrary user files (e.g. duplicate files in chat-app folders), which is the app's
primary purpose. No file data is transmitted off the device — the app has no internet permission.
```

**Query all packages (QUERY_ALL_PACKAGES):**
```
The App Storage feature lists the user's installed apps with their sizes so the user can review
and uninstall space-consuming apps. This requires enumerating installed packages. The list is
displayed only on-device and is never transmitted.
```

---

## Graphics you still need to create

| Asset | Spec | Notes |
|---|---|---|
| App icon | 512×512 PNG (32-bit) | You have a launcher icon; export a 512px store version. |
| Feature graphic | 1024×500 PNG/JPG | Required. Aurora/glass theme + "100% Offline Cleaner". |
| Phone screenshots | min 2 (recommend 4–8), 16:9 or 9:16, ≥320px | Dashboard, a file list with previews, Duplicates, App storage. |
| (Optional) 7"/10" tablet shots | | Only if you target tablets. |

Tip: take screenshots from the **release** build on a device/emulator so they reflect the
final UI (aurora + glass).

---

## Pre-launch checklist

- [ ] Host privacy policy; add URL in Console
- [ ] Create a Play Console account ($25 one-time) and a new app
- [ ] Upload `app-release.aab` (Play App Signing: accept Google-managed signing)
- [ ] Complete Data safety = "No data collected"
- [ ] Submit All-files-access + Query-all-packages declarations
- [ ] Content rating questionnaire → Everyone
- [ ] Set countries + price (Paid)
- [ ] Add title, descriptions, graphics, screenshots
- [ ] **Smoke-test the signed release APK on a real device** (R8 can change runtime behavior):
      verify scanning, a delete-to-Trash, an uninstall, and PDF previews all work
- [ ] Roll out to Internal testing first, then Production
```
