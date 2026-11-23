# Regain

A calm, dark, iOS-styled Android app that helps you use your phone less. It's gentle by default and firm when it needs to be.

Everything stays on the phone: the app has **no internet permission**.

## Features

**Limits**
- A daily time budget per app. Tightening a limit applies now; loosening it (raising or removing) applies **tomorrow**, so a limit can't be dodged the moment it bites.
- **Time's up** gate with a big, rewarding "Close" button and a quiet "Extend".
- **Extensions:** 5, 10 or 15 minutes, unlocked by **5 mental-math questions** (20 s each; a wrong answer or timeout gives a new question). Maximum 2 per app and 4 per day. The second extension is harder, and night mode makes it harder again. A typing challenge is available as an alternative.
- **Emergency unlock:** once a week, hold for a full minute to get 15 minutes.

**Friction and nudges**
- **Pause and ask why:** a breathing orb plus "Why are you opening it?" before a limited app opens.
- **Growing delay:** 3 → 5 → 10 → 20 → 30 s per open each day, doubled at night.
- **Fade:** the screen slowly dims over the last 5 minutes of a limit.
- **Doomscroll detector:** a buzz and a nudge after 3 minutes of nonstop scrolling.
- **Unlock counter:** a small "Unlock #23 today" pill each time you unlock.
- **Morning gate**, **night mode** and **downtime** schedules.

**Focus**
- An animated aurora gradient, a ring timer, presets, and strict mode.
- **Flip to start:** place the phone face down to begin.
- Picking the phone up mid-session gives a sad buzz and shows the time left.

**Insights**
- **Bloom:** a living flower whose petals grow with your streak and fill out on good days.
- A 7-day chart with a goal line, "in real terms" equivalents (movies, books, walks), the reasons you opened apps, and your peak hour.

## Installing (for testers)

1. Download the latest `Regain-vX.Y.Z.apk` from **Releases** and open it to install.
2. Open Regain and follow the setup steps:
   - **Usage access:** switch Regain on.
   - **Accessibility:** if Android says *"Restricted setting"*, open **App info → ⋮ → Allow restricted settings**, then turn the service on.
   - **Realme / ColorOS:** **App info → Battery → Allow background activity**, and lock Regain in Recents.

## Battery and memory design

- The engine is **event driven**: it wakes only when the app on screen changes, and never polls.
- Each session sets **one timer** for when its time runs out, and everything stops while the screen is off.
- Screen time is read from Android's own usage log, not tracked a second time.
- Scroll events are subscribed to only while a limited app is in front.
- Overlays are plain Views, and the APK is about 2 MB with R8.

## Building

```bash
./gradlew assembleDebug      # debug build
./gradlew assembleRelease    # needs keystore.properties + release.jks (git-ignored)
```

To publish a release, push a tag (`git tag v0.1.0 && git push --tags`). CI builds a signed APK and attaches it to a GitHub Release. This needs two repository secrets:
- `KEYSTORE_BASE64`: the base64 of `release.jks`
- `KEYSTORE_PASSWORD`

Tech: Kotlin, Jetpack Compose (custom iOS-style components, no Material), AccessibilityService, UsageStatsManager. minSdk 28, targetSdk 35.
