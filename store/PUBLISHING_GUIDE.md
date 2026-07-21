# SpaceClean — Complete Publishing Guide

Everything needed to go from the built app to a live Google Play listing, written for a
first-time publisher. Do the sections in order.

---

## 0. Before you touch the Play Console

- [ ] **Decide the app name** (see "App name & the 'AI' question" below).
- [ ] **Decide Free vs Paid** (see "Pricing" below). This changes whether you need a
      payments profile.
- [ ] **Check the name isn't taken** — search the Play Store for your chosen name. If an
      existing app owns it (especially a trademarked one), pick another to avoid a takedown.
- [ ] Have the two hosted legal URLs ready (already live on your public GitHub):
  - Privacy Policy: `https://github.com/VarunKumarMup/SpaceClean/blob/main/store/PRIVACY_POLICY.md`
  - Terms/EULA: `https://github.com/VarunKumarMup/SpaceClean/blob/main/store/TERMS_OF_USE.md`

---

## 1. Create your Google Play developer account  *(only you can do this)*

Account creation and the registration fee require your identity and payment details, so this
step must be done by you personally.

1. Go to https://play.google.com/console and sign in with the Google account you want to own
   the app.
2. Choose account type **Individual** (simplest for a solo developer).
3. Pay the **one-time $25** registration fee.
4. Complete **identity verification** (Google now requires a legal name + address, sometimes
   a government ID and a D-U-N-S-style check). **This can take a few days — start it early.**
5. Accept the Developer Distribution Agreement.

> If you chose **Paid** (step 0), you must also set up a **Payments/merchant profile**
> (Console → Setup → Payments profile) with tax and bank info before you can publish a paid
> app. If you chose **Free**, you can skip this entirely.

---

## 2. Create the app

Console → **Create app**:
- App name: your chosen title (≤ 30 chars)
- Default language: English (United States)
- App or game: **App**
- Free or paid: your choice from step 0
- Tick the two declarations (developer program policies, US export laws)

---

## 3. Build & upload the app bundle

The signed bundle is already produced by:

```bash
./gradlew :app:bundleRelease
```

Upload `app/build/outputs/bundle/release/app-release.aab`.

Keep **Play App Signing** ON (default) — Google holds the real signing key; your
`spaceclean-upload.jks` is only the upload key and can be reset if lost. **Never commit the
keystore** (already git-ignored).

---

## 4. Internal testing — do this FIRST  *(this is the "how" you asked for)*

Internal testing installs the real, signed app on your own phone through Play, so you catch
any release-build (R8) issues before the public sees them. It is the safest first step.

1. Console → left menu → **Testing → Internal testing**.
2. Click **Create new release**.
3. **Upload** the `.aab` (or reuse the one from step 3).
4. Release name auto-fills (e.g. `1 (1.0)`); type a line of release notes ("Initial test").
5. Click **Next / Save**, then **Review release**, then **Start rollout to Internal testing**.
6. Go to the **Testers** tab → create an email list → add your own Google account email
   (and any friends). Save.
7. Copy the **"Copy link"** opt-in URL at the bottom, open it **on your phone**, tap
   **Become a tester**, then **Download it on Google Play**. The app installs from Play like
   any normal app.
8. **Smoke-test on the phone** (see checklist in section 8). Fix anything, upload a new
   `.aab`, repeat.

Internal testing has **no review delay** and up to 100 testers — you can iterate freely here.

---

## 5. Fill in "App content" (required forms)

Console → **Policy → App content**. Complete each:

- [ ] **Privacy policy** → paste the Privacy Policy GitHub URL above.
- [ ] **App access** → "All functionality is available without special access" (no login).
- [ ] **Ads** → **No, my app does not contain ads.**
- [ ] **Content rating** → fill the questionnaire honestly → it will come back **Everyone**.
- [ ] **Target audience and content** → select adult/teen age groups; **do NOT** target
      children (the app reads all files and lists installed apps, which is incompatible with
      the Families program).
- [ ] **Data safety** → declare **No data collected** and **No data shared**. (True — the app
      has no internet permission.) Mark data handled on-device only.
- [ ] **Government apps / Financial features / Health** → No to all.

---

## 6. Permissions declarations (the main review gate)

Console → **App content → Sensitive app permissions** (and the bundle's flagged permissions).
You must justify these; the wording is in `PLAY_STORE_LISTING.md`:

- [ ] **All files access (`MANAGE_EXTERNAL_STORAGE`)** — declare the core use case:
      "SpaceClean is a file manager and storage cleaner that must scan, preview, and delete
      files of all types across the device (duplicates, large files, documents, downloads,
      leftover folders) — functionality that cannot be achieved with scoped MediaStore/SAF
      access alone." **Record a ~30-second screen capture** of the app scanning and cleaning;
      reviewers of all-files-access apps usually want to see it.
- [ ] **`QUERY_ALL_PACKAGES`** — "Used by the App Storage manager to list installed apps and
      their sizes so the user can review and uninstall them. The list is shown only on-device."

> **Honest expectation:** all-files-access apps get **extra review scrutiny and can take
> longer (days to a week+)**, and a first submission is sometimes bounced with a request for
> more justification. That is normal for this category — resubmit with the demo video.

---

## 7. Store listing & graphics

Console → **Grow → Store presence → Main store listing**:

- [ ] Title, short description, full description — copy from `PLAY_STORE_LISTING.md`.
- [ ] **App icon** → upload `store/icon-512.png` (512×512).
- [ ] **Feature graphic** → upload `store/feature-graphic-1024x500.png` (1024×500).
- [ ] **Phone screenshots** (2–8) → take real screenshots on your phone; captions are in
      `SCREENSHOT_CAPTIONS.md`.
- [ ] Category: **Tools**. Add a support email.

---

## 8. On-device smoke test checklist (run during internal testing)

- [ ] App launches; grant **All files access** when prompted.
- [ ] **Smart Scan** fills all tiles.
- [ ] Open Videos → select one → **Delete** → confirm → it disappears with sound.
- [ ] Open **Largest / Documents** → delete a file → it disappears (no permanent-delete
      surprise) → appears in **Recently Deleted** → **Restore** puts it back.
- [ ] Delete photos → they show in **Recently Deleted** immediately → restorable.
- [ ] **Quick Clean** → the **All / None** toggle selects & clears everything → delete works.
- [ ] Settings → **Quick Clean auto-selects** toggles persist across an app restart.
- [ ] App Storage → uninstall an app works and the list refreshes.
- [ ] Rotate / background-restore a couple of screens (no crashes).

---

## 9. Promote to Production

After internal testing passes: **Production → Create release** → reuse the same `.aab` (or
"promote" the internal release) → set pricing & countries (select **all countries** for the
widest reach) → **Review release** → **Start rollout to Production** → submit for review.

First production review can take a few days (longer with all-files access).

---

## App name & the "AI" question

Naming the app "AI ..." when it contains no AI/ML is a **Google Play Misrepresentation policy
violation** and risks rejection or later removal — and "AI Cleaner" is a heavily
spam-flagged, clone-saturated category. The app's matching (perceptual + content hashing) is
clever but algorithmic, not AI. Safer, still-marketable directions:

- **Smart Space Cleaner — Offline** (keyword-rich, honest)
- **SpaceClean** (keep it)
- **Cleanup: Space & Duplicates**

If you genuinely want "AI" in the name, the honest path is to add a real on-device ML feature
first (e.g. an on-device model for blurry/screenshot/meme classification) — a separate build.

## Pricing

Your goal was "most downloads as fast as possible." That points to **Free** — a paid app
typically gets 10–50× fewer installs, and free also removes the payments-profile setup.
Recommended launch: **Free**, optionally adding a paid "Pro" upgrade later once you have
users. If you want revenue from day one instead, **$0.99–$1.99** is the usual floor, at a
large cost to install numbers.

---

## Legal readiness — is anything going to cause you problems?

**Short answer: the paperwork is complete and nothing here is a legal landmine, provided the
name is clear of trademarks.** Details:

| Item | Status |
|---|---|
| Privacy Policy (required) | ✅ Written, accurate, hosted publicly |
| Terms of Use / EULA (recommended for a delete app) | ✅ Written, US governing law, data-loss liability limited |
| Truthful claims (no fake "boost"/junk inflation) | ✅ Reclaimable numbers are real; RAM/battery are read-only info |
| No data collection / no internet permission | ✅ Verified in the merged manifest |
| Third-party code licenses (AndroidX, Compose, Media3) | ✅ All Apache-2.0, compatible |
| Content rating / target audience / data-safety forms | ⏳ You fill these in Console (answers above) |
| All-files-access justification | ⏳ Legitimate use case, but expect scrutiny + demo video |
| **App name trademark check** | ⚠️ **You must verify** the final name isn't an existing/registered app or trademark |

**The two things to watch, neither of which is a personal legal risk if handled:**
1. **All-files access review** — bureaucratic, not legal. Your use case qualifies; just be
   ready with the demo video and expect possible back-and-forth.
2. **Name collision** — do a Play Store + quick trademark search on your final name before
   submitting. This is the only item that could turn into a legal complaint, and it's fully
   avoidable by choosing a clear name.

Everything you can be sued or removed over — false privacy claims, hidden data collection,
misleading performance claims, missing EULA for a file-deleting app — has been addressed.
