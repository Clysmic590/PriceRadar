## One UI settings bug fixes and QoL

PriceRadar 1.5.4 fixes the first One UI settings release and makes the Samsung-style interface permanent.

### What changed

- One UI is now always enabled; the secret unlock and mode toggle are removed.
- The seven-colour palette now applies immediately and persists correctly using ARGB values.
- Enabled switches now use a visible accent-coloured track with a white thumb; disabled switches use Samsung-style dark tracks.
- All product and alert switches share the corrected appearance.
- Settings adds quick actions for checking all active products and testing notifications.
- The version is correctly shown as 1.5.4.
- Existing watchlist, alerts, price history and retailer features remain available.

Download **PriceRadar-1.5.4.apk** below. The signing key is unchanged, so it can be installed over the previous build. Android version code: 13. Android 8+ is supported.

Validation: unit tests, release lint, optimized APK build, and Android 12 startup smoke test. The APK checksum is provided in **SHA256SUMS.txt**. This is a development-signed sideload build, not a Play Store release.

This is a custom Compose interface inspired by Samsung settings, not Samsung firmware or an official One UI/SESL distribution. Retailer integrations for eMAG, Vexio, and Altex remain experimental and require physical-device testing.

