# Focus

A calm, dark, iOS-styled Android app that helps you use your phone less. It's gentle by default and firm when it needs to be.

Everything stays on the phone: the app has **no internet permission**. It also uses **no accessibility service**, so banking and UPI apps (Google Pay, PhonePe, Paytm) work normally alongside it.

## Features

**Limits**
- A daily time budget per app. Tightening a limit applies now; loosening it (raising or removing) applies **tomorrow**, so a limit can't be dodged the moment it bites.
- **Time's up** gate with a big, rewarding "Close" button and a quiet "Extend".
- **Extensions:** 5, 10 or 15 minutes, unlocked by **5 mental-math questions** (20 s each; a wrong answer or timeout gives a new question). Maximum 2 per app and 4 per day. The second extension is harder, and night mode makes it harder again. A typing challenge is available as an alternative.
- **Emergency unlock:** once a week, hold for a full minute to get 15 minutes.

**Groups and weekends**
- **App groups:** one shared budget across several apps, e.g. "Social · 1h".
- **Weekend limits:** the same as weekdays, 1.5×, or 2× on Saturday and Sunday.
- **Earn your scroll:** every 5 minutes of completed focus earns 1 minute (up to 20 a day), spendable on the time's-up screen without math.

**Accountability buddy**
- One short SMS to a trusted number when you use an emergency unlock, end focus early, or loosen or remove a limit. Changing the number notifies the old one.
- The default number comes from the `BUDDY_PHONE` build secret, so it's never stored in the repo.

**Friction and nudges**
- **Pause and ask why:** a breathing orb plus "Why are you opening it?" before a limited app opens.
- **Growing delay:** 3 → 5 → 10 → 20 → 30 s per open each day, doubled at night.
- **Fade:** the screen slowly dims over the last 5 minutes of a limit.
- **Unlock counter:** a small "Unlock #23 today" pill each time you unlock.
- **Morning gate**, **night mode** and **downtime** schedules, plus a gentle **wind-down reminder** at bedtime.
- **Drain colour** (optional): the screen turns gray in the last 5 minutes. This needs a one-time `adb shell pm grant com.regain.app android.permission.WRITE_SECURE_SETTINGS`.

**Focus**
- An animated aurora gradient, a ring timer, presets, and strict mode.
- **Flip to start:** place the phone face down to begin.
- Picking the phone up mid-session gives a sad buzz and shows the time left.
- Blocks limited apps, plus the Google app and Chrome, for the whole session.

**Insights**
- **Bloom:** a living flower whose petals grow with your streak and fill out on good days. It wilts when you go over your goal and recovers the next day.
- **Week in review:** animated story slides on Sundays and Mondays, and any time from Insights.
- **Home-screen widget:** your Bloom, today's time against your goal, and the limit closest to running out.
- A 7-day chart with a goal line, "in real terms" equivalents (movies, books, walks), the reasons you opened apps, and your peak hour.

## Installing (for testers)

1. Download the latest `Focus-vX.Y.Z.apk` from **Releases** and open it to install.
2. Open Focus and follow the setup steps:
   - **Usage access:** switch Focus on.
   - **Display over other apps:** switch Focus on.
   - **Realme / ColorOS:** **App info → Battery usage → Allow background activity** and **Allow auto launch**, and lock Focus in Recents.

Updating from v0.2.x: open Focus once after installing. It asks for "Display over other apps", and the old accessibility service disappears by itself.

## Battery and memory design

- The engine is a small foreground service. **Only while the screen is on**, it reads the newest entries of Android's usage log every 0.75 s to see which app is in front: about 0.1 s of CPU per minute. With the screen off it does nothing.
- Each session sets **one timer** for when its time runs out.
- Screen time is read from Android's own usage log, not tracked a second time.
- Gates open over other apps behind a black cover, so the app disappears at once. If Android ever refuses to open the gate, the cover asks for one tap instead.
- Overlays are plain Views, and the APK is under 3 MB with R8.

## Building

```bash
./gradlew assembleDebug      # debug build
./gradlew assembleRelease    # needs keystore.properties + release.jks (git-ignored)
```

To publish a release, push a tag (`git tag v0.1.0 && git push --tags`). CI builds a signed APK and attaches it to a GitHub Release. This needs these repository secrets:
- `KEYSTORE_BASE64`: the base64 of `release.jks`
- `KEYSTORE_PASSWORD`
- `BUDDY_PHONE` (optional): the default accountability-buddy number, e.g. `+91XXXXXXXXXX`

Typeface: Inter (SIL OFL, see `third_party/Inter-OFL.txt`).

Tech: Kotlin, Jetpack Compose (custom iOS-style components, no Material), a foreground service with UsageStatsManager, and overlay windows. minSdk 28, targetSdk 35.
