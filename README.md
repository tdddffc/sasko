# Weather

A standalone weather app extracted and rebuilt from the weather features of your
"Morning Digest" app — same Kotlin + Jetpack Compose stack, just weather, nothing else.

## What's in the app

**Main screen — 2 sections, exactly as requested:**
1. **Today** — live current conditions (temperature, feels like, humidity, wind,
   pressure, sunrise/sunset, today's high/low), cross-checked across up to 3
   independent weather sources. Shows e.g. "2/3 sources agree" so you can see how
   confident the reading is.
2. **Next 3 Days** — a tappable strip. Tapping it opens a full month view: the
   first ~16 days are a real forecast (from Open-Meteo), the rest of the month is
   a clearly-labeled historical outlook (average of the same calendar date over
   the last 3 years) — no provider forecasts a full month out for real, so this is
   flagged in the UI rather than presented as if it were.

**Settings:**
- Country dropdown → City field with live autocomplete suggestions as you type
  (powered by Open-Meteo's free geocoding search).
- Temperature unit toggle (°C / °F).
- Optional OpenWeather API key field — adding your own free key turns on a 3rd
  data source for the consensus check (not required; the app works fully without it).
- Custom weather alert rules (temperature above/below, UV index, wind speed, rain
  probability, thunderstorm, snow), each with your own threshold, a forecast
  horizon, and a lead-time — the same rule engine logic from the original app,
  ported to run on Open-Meteo's free data so no paid API key is needed.

## Weather sources used

| Source | Needs API key? | Used for |
|---|---|---|
| [Open-Meteo](https://open-meteo.com) | No | Primary: current conditions, hourly (for alerts), 16-day forecast, historical archive (for the month outlook), city geocoding |
| [wttr.in](https://wttr.in) | No | 2nd source for the current-conditions consensus check |
| [OpenWeather](https://openweathermap.org) | Optional, user-supplied | 3rd source for the consensus check, if you add a key in Settings |

## Building the APK

### Option A — GitHub Actions (matches your existing workflow)
Push this project to a GitHub repo. The included workflow
(`.github/workflows/build-apk.yml`) builds a debug APK automatically on every
push to `main`/`master`, and you can also trigger it manually from the Actions
tab. Download the APK from the workflow run's **Artifacts** section.

### Option B — Android Studio
Open the project folder in Android Studio (Ladybug or newer), let it sync
Gradle, then **Build > Build Bundle(s) / APK(s) > Build APK(s)**.

## Notes
- Default starting location is set to Vikersund, Norway — change it in Settings
  the first time you open the app.
- The month outlook is genuinely just an outlook, not a forecast — weather this
  far out can't be predicted accurately by anyone, so it's built from historical
  averages and marked as such in the UI.
