# Internal Testing → Production — Step-by-step (SpaceClean)

Internal testing lets you install the real signed build via Play before going public. Do this
**first** — it's the safest way to catch R8/runtime issues on actual devices.

## 0. One-time account setup
- [ ] Create a **Google Play Console** account ($25 one-time) → https://play.google.com/console
- [ ] Accept the Developer Distribution Agreement
- [ ] (Recommended) Keep **Play App Signing** enabled — Google holds the app signing key; your
      `spaceclean-upload.jks` is only the *upload* key (resettable if lost).

## 1. Create the app
- [ ] Console → **Create app**
- [ ] App name: **SpaceClean**, Default language: English, Type: **App**, **Paid**
- [ ] Confirm declarations (developer program policies, US export laws)

## 2. Build the upload artifact
```bash
./gradlew :app:bundleRelease
```
- [ ] Upload `app/build/outputs/bundle/release/app-release.aab`

## 3. Internal testing release
- [ ] Console → **Testing → Internal testing → Create new release**
- [ ] Upload the `.aab`
- [ ] Release name auto-fills (e.g. `1 (1.0)`); add brief release notes
- [ ] Add testers: create an email list (your own + a few accounts) under **Testers**
- [ ] **Save → Review release → Start rollout to Internal testing**
- [ ] Share the **opt-in URL** with testers; install from Play on a real device

## 4. Smoke-test on device (critical — R8 can change runtime behavior)
- [ ] App launches; grant **All files access** when prompted
- [ ] **Smart Scan All** populates tiles
- [ ] Open a file list → previews load (incl. **PDF** thumbnails)
- [ ] Delete a few photos → confirm they land in **Trash** (check Google Photos/Files), restorable
- [ ] **App Storage** → open an app's action sheet → **Uninstall** works and list refreshes
- [ ] **Duplicates** scan finds copies; deleting redundant copies works
- [ ] **Settings → Theme**: switch between Cyber / Midnight / Ocean / Emerald / **Light** and
      confirm the whole app recolors and the choice survives an app restart
- [ ] Rotate / background-restore a screen or two (no crashes)

## 5. Complete required store content (App content section)
- [ ] **Privacy policy URL** (host `PRIVACY_POLICY.md`)
- [ ] **Terms/EULA** — fill the `[...]` placeholders in `TERMS_OF_USE.md` (effective date,
      governing-law jurisdiction, privacy-policy URL), host it, and link it in the listing
      description. (A EULA isn't strictly required by Play, but strongly recommended for a
      paid app that deletes files — it carries the no-warranty & data-loss liability limits.)
- [ ] **Data safety** → "No data collected / No data shared"
- [ ] **App access** → all features available without login
- [ ] **Content rating** questionnaire → Everyone
- [ ] **Target audience** → not directed at children (general audience)
- [ ] **Permissions declaration** → submit the MANAGE_EXTERNAL_STORAGE + QUERY_ALL_PACKAGES
      justifications from `PLAY_STORE_LISTING.md`
- [ ] **Ads** → No

## 6. Store listing
- [ ] Title, short + full description (from `PLAY_STORE_LISTING.md`)
- [ ] App icon (export `icon-512.svg` → 512×512 PNG)
- [ ] Feature graphic (export `feature-graphic-1024x500.svg` → 1024×500 PNG)
- [ ] 2–8 phone screenshots (see `SCREENSHOT_CAPTIONS.md`)
- [ ] Category: **Tools**; contact email

## 7. Pricing & countries
- [ ] **Monetization → set up paid** (add a merchant/payments profile if first time)
- [ ] Set price (suggested $1.99–$2.99) and choose countries

## 8. Promote to Production
- [ ] After internal testing passes, **Production → Create release** with the same `.aab`
      (or promote the internal release)
- [ ] Submit for review (first review can take a few days; All-files-access apps get extra scrutiny)

## Gotchas specific to this app
- **All files access review is strict.** Expect questions; the declaration text is written to
  pre-empt them. Be ready to record a short screen capture showing why the app needs it.
- **No INTERNET permission** is a plus for review and a selling point — mention it.
- If review pushes back on `QUERY_ALL_PACKAGES`, the App Storage feature is the justification.
