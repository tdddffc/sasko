# Winter / Nordic alerts (all thresholds editable)

Settings -> Custom Weather Alerts -> "Winter & Nordic alerts". Each rule has a
switch; when on, tap its row to set your own number with the slider, then Save.

| Rule | Your value | Default |
|---|---|---|
| Black ice risk | wet weather at or below X °C (up to 4° colder), or wet air refreezing through 0 | 1 °C |
| Freezing rain | rain at or below X °C (freezing rain/drizzle codes always count) | 0 °C |
| Frost arriving | temp falls from above to X °C or below | 0 °C |
| Heavy snowfall | X cm of snow per hour | 2 cm/h |
| Snowstorm | snow with wind (or 70% of gusts) >= X km/h | 40 km/h |
| Wind gusts | gusts >= X km/h | 70 km/h |
| Wind chill | "feels like" <= X °C | -15 °C |
| Low visibility | visibility <= X m (100 m steps) | 1000 m |
| Rapid temperature drop | fall of X °C within 6h | 8 °C |

"Apply Norway winter preset" fills in sensible values. Data: Open-Meteo (free, no key).
The hourly worker now also remembers what it already notified, so the same hour isn't re-sent.

## Icons and 16-day screen
- Every weather icon is now an animated emoji (spinning sun, floating clouds, falling drops/flakes, flashing lightning, drifting fog). Stat icons (wind, UV, sunrise...) use matching emoji.
- "Next 16 days & month" screen: summary card (warmest/coldest/wettest/windiest day, frost nights, ice-risk days, snow days, totals), and tap any day for feels-like, precipitation mm, snowfall cm, wind and gusts, UV, sunrise/sunset and daylight length. Risky days carry tags: ❄️ frost, 🧊 ice risk, 🌨️ snow, 💨 strong wind, 🥶 wind chill.
