# What's New — Ocean Facts

Release notes for **Ocean Facts** (`com.pixel.oceanfacts`).

Current version in source: **1.0** (versionCode 1) — see `app/build.gradle.kts`.

> **How this file is maintained**
> - The top section is always the **next** release: work that is merged but not yet live on Google Play.
> - When a version ships, its heading changes from `Unreleased` to `Shipped — YYYY-MM-DD` and a fresh `Unreleased` section is opened above it.
> - The **Play Store copy** block is the ready-to-paste text for the Play Console "What's new" field (500 character limit).
> - In-app release notes live in `app/src/main/java/com/pixel/oceanfacts/ui/WhatsNewSheet.kt` (the `Release` object). Keep the pending section here and that object in step.

---

## 1.0 — Unreleased

**Headline:** Eleven kilometres of water, one layer at a time

- **Thirty animated facts**, each hand-drawn as a scene rather than an illustration, playing on a uniform 15-second loop.
- **The water is the fact's own depth.** Every fact carries the depth it happens at, and the sea behind its scene is coloured from that number: sunlit turquoise at the surface, indigo through the twilight zone, and true black in the trenches. Light shafts fade out by 220 m, marine snow thickens as they go, and bioluminescence only appears once the sun has stopped competing with it.
- **Four zones, in the order you would meet them going down.** The Sunlight Zone (0–200 m), the Twilight Zone (200–1,000 m), the Midnight Zone (1,000–4,000 m) and the Trenches (4,000–11,000 m), six facts each.
- **Deep Dive.** Six standalone facts that belong to the whole ocean rather than one layer of it — the Titanic, the water cycle, the tides, the Amazon, the garbage patch and the global conveyor.
- **The Descent.** A fly-through from the surface to the Challenger Deep on a true depth scale, with sixteen markers along the way. The water darkens as the camera falls, so the screen is genuinely black by the time the Titanic goes past. Tap to hold, drag to go back up.
- **Today's quiz.** Ten multiple-choice questions, the same for everyone, worked out from the date alone. Drawn from a bank of sixty written against the facts the app ships — at least one for every fact. Wrong answers show the right one straight away, and the round ends with every fact you missed, ready to open and read again.
- **A streak to keep.** Watching a fact or finishing the daily round both count, so there is only ever one streak and one number to care about. Shown on the home screen, in the quiz and in the You tab, with the last seven days as dots and your longest run beside it.
- **Restore a broken streak.** Miss a single day on a run of three or more and you can get it back, that day only, by watching a short video. Capped at once a fortnight. If no video will load, the streak is simply given back.
- **A 50/50 on today's quiz.** Once a round, rule out two wrong answers. Never on practice rounds.
- **A daily reminder.** Switch it on in the You tab and pick a time; the day's fact arrives as a notification. When a live streak has not been kept yet it points at the quiz instead. Off by default, and the permission is only ever asked for on the tap that turns it on.
- **Search.** Find any fact by name, by what it is, by its category or zone, or by a number buried in its blurb or stats — "glow", "pressure", "1912".
- **Your progress shows.** Seen facts are ticked in every list, each zone card carries a progress bar, and each category shows how far through it you are.
- **Share a fact as a picture.** The Learn more sheet renders the fact's own scene as an image card — water colour and all — and hands it to the share sheet, with a link that opens straight back to that fact.
- **Sources.** Every fact links out to NOAA, Woods Hole, the Smithsonian, USGS, Seabed 2030 or Wikipedia, opened in-app with a way out to the browser.
- **A privacy choice for ads**, where the law gives you one, changeable at any time from the You tab.
- Anyone who buys **Remove ads** gets both the streak repair and the 50/50 outright, with no video.

### Before shipping

- Replace Google's public test AdMob unit ids in `AdManager.kt` and the app id in `AndroidManifest.xml` with the real ones.
- Create the `ocean_remove_ads` in-app product in the Play Console.
- Drop `app/google-services.json` in to switch Firebase Analytics on; without it nothing leaves the device.
- Regenerate the density-specific launcher bitmaps in `res/mipmap-*` (Android Studio, Image Asset) — only the adaptive icon has been drawn, so API 24–25 still falls back to the template art.
- Check the anchored banner on the Descent tab against a live creative: the slot is a large adaptive one, and Google's fixed-size test banners letterbox themselves inside it.

### Play Store copy

```
Eleven kilometres of water, one layer at a time.

Thirty animated facts about the sea, each one playing in water coloured for the depth it actually happens at — sunlit turquoise at the surface, black in the trenches.

• Four zones, from the sunlight down to the hadal trenches
• The Descent — surface to Challenger Deep, on a true depth scale
• Ten quiz questions a day, and a streak to keep
• Search every fact by name, subject or number
• Share any fact as a picture of its own scene
```
