# AI Space Cleaner — Complete Publishing Guide

Everything needed to go from the built app to a live Google Play listing, written for a
first-time publisher. Do the sections in order.

---

## ⚡ FASTEST PATH TO LIVE (read this first)

**Honest timeline: ~2.5–3 weeks for a new personal account.** The app is done; the delay is
entirely Google's gates. Three things set the clock, so start all three TODAY and run
everything else in parallel:

1. **Identity verification** — start the moment you create the account (can take days).
2. **The 12-tester / 14-day closed test** — Google requires new *personal* developer accounts
   to run a **Closed testing** track with **at least 12 testers who opt in, for 14 continuous
   days**, before you may even apply for production. **This is the long pole.** Upload the AAB
   to Closed testing and send the opt-in link to 12+ people (friends/family Google accounts —
   they just tap the link, install once, and stay opted in) on **day one**.
3. **Payments/merchant profile** — required because the app is Paid; tax + bank verification
   also takes days.

While those clocks run (days 1–14), complete the store listing, data-safety form, content
rating, target audience, permission declarations, and screenshots — none of it blocks the
14-day timer. On/after day 14, with 12 testers still opted in, **apply for production access**,
promote the build to Production, and submit. Then Google's review (a few days, longer because
of all-files access) is the last wait.

> **The single biggest speed lever:** start the Closed test with 12 testers on the first day.
> Every day you delay recruiting testers is a day added to the end. Do NOT remove testers or
> pause the track during the 14 days, or the counter can reset.
>
> *(Want it even faster / fewer hoops? Launching **Free** removes the payments-profile step and
> a paid app's extra friction — but you chose Paid, so the payments profile stays on the
> critical path.)*

---

## 0. Before you touch the Play Console

**Decisions locked in:** app name **"AI Space Cleaner"**, **Paid at $1.99 USD** (the standard
"~$2" price point — set exactly $2.00 if you prefer). The name is honest because the app has a
real, central on-device AI feature (AI Photo Cleanup).

- [ ] **Check the name isn't taken** — search the Play Store for "AI Space Cleaner". "AI ...
      Cleaner" is a crowded space; if an existing app (especially a trademarked one) owns the
      exact name, tweak it (e.g. add a word) to avoid a takedown.
- [ ] Because it's **Paid**, set up the payments/merchant profile early (see step 1).
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

> **This app is Paid, so you MUST set up a Payments/merchant profile** (Console → Setup →
> Payments profile) with tax and bank info before you can publish. This is often the slowest
> part (tax forms + bank verification) — start it right after account setup.

---

## 2. Create the app

Console → **Create app**:
- App name: **AI Space Cleaner**
- Default language: English (United States)
- App or game: **App**
- Free or paid: **Paid**
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

## App name — "AI Space Cleaner" (resolved)

This name is now **honest and policy-safe** because the app ships a real, central on-device AI
feature — **AI Photo Cleanup**, a bundled neural network (TensorFlow Lite) that finds blurry,
near-duplicate, and screenshot photos entirely on-device. Make sure a screenshot of that screen
is in your listing so the "AI" claim is visibly backed by the product (Google's reviewers check
that "AI" in a title corresponds to a genuine feature). Keep the AI feature working in every
release so the name stays truthful.

## Pricing — Paid $1.99 (resolved)

Set to **$1.99 USD** (the conventional "~$2" price; set exactly $2.00 under Monetization if you
want the round number). Reality check so you're not surprised: a paid app gets far fewer installs
than a free one, and Google requires a payments/merchant profile before you can publish. If
downloads stall, the usual playbook is to switch to **Free with a paid "Pro" upgrade** later —
say the word and I'll add the in-app-purchase plumbing.

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
