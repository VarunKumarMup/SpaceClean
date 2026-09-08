# Closed Testing — 12 testers, 14 days (do this the moment verification clears)

Google requires a new personal developer account to run a **Closed testing** track with **≥12
testers opted in for 14 continuous days** before you can apply for production. This is the long
pole, so start it the same day verification completes.

## Prep you can do NOW (while waiting on verification)

- [ ] **Line up 12+ people** with **Android** phones (friends/family). Make a list of their
      **Gmail addresses** — those are what you add as testers. Text them now so they're ready.
- [ ] **Take screenshots** on your working phone (you can do this today):
      - Dashboard (with the AI Photo Cleanup card + scan gauge)
      - **AI Photo Cleanup results screen** ← important, this justifies the "AI" name to review
      - A file list with thumbnails (e.g. Photos or Largest)
      - Duplicates or Quick Clean review screen
      - Recently Deleted (Trash)
      Take 4–8. Any modern phone screenshot size is fine (≥320px).

## Day verification clears — start the track

1. Play Console → **Testing → Closed testing → Create track** (or use the default "Alpha").
2. **Create release** → upload `app/build/outputs/bundle/release/app-release.aab`.
3. Add release notes ("Initial closed test"), Save → Review → **Start rollout**.
4. **Testers** tab → create an email list → paste your 12+ Gmail addresses → Save.
5. Copy the **opt-in link** and send it to your testers with the message below.
6. Confirm each tester actually opts in and installs (they must, and stay opted in 14 days).

## Tester message (copy/paste, drop in the opt-in link)

> Hi! I just built an Android app — **AI Space Cleaner**, a private, 100% offline storage
> cleaner with on-device AI. I need a few testers before Google will let me launch it. Could you
> help? Takes 2 minutes on your Android phone:
>
> 1. Open this link on your phone: **[PASTE OPT-IN LINK]**
> 2. Tap **Become a tester**, then **Download it on Google Play** and install.
> 3. That's it — just keep it installed for two weeks. You don't have to use it.
>
> Thanks so much, this genuinely helps me get it published! 🙏

## During the 14 days — don't reset the clock

- **Don't remove testers or pause/stop the track** — the 14-day counter can reset.
- Keep at least 12 testers opted in the whole time.
- Meanwhile, finish the store listing, data-safety, content-rating, target-audience, permission
  declarations, screenshots, and pricing (see `PUBLISHING_GUIDE.md` §5–7) — none of it blocks
  the timer.

## After 14 continuous days

Play Console will show you're eligible → **apply for production access** → promote the build to
**Production** → submit for review. (Review takes a few days; longer because of all-files access
— have the ~30-sec demo video ready.)
