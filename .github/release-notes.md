## One UI settings fix

PriceRadar 1.5.3 replaces the ineffective preview toggle with a substantially rebuilt Samsung-style settings experience.

### What changed

- One UI mode is enabled by default on fresh installs.
- Settings now use a large Samsung-style title, grouped dark cards, compact dividers, rounded icon tiles, One UI-like spacing, switches and hierarchy.
- A seven-colour accent picker changes icons, switches and selected controls.
- The version is correctly shown as 1.5.3 without the old “design preview” suffix.
- Existing watchlist, alerts, price history and retailer features remain available.

Download **PriceRadar-1.5.3.apk** below. The signing key is unchanged, so it can be installed over the previous build. Android version code: 12. Android 8+ is supported.

Validation: unit tests, release lint, optimized APK build, and Android 12 startup smoke test. The APK checksum is provided in **SHA256SUMS.txt**. This is a development-signed sideload build, not a Play Store release.

This is a custom Compose interface inspired by Samsung settings, not Samsung firmware or an official One UI/SESL distribution. Retailer integrations for eMAG, Vexio, and Altex remain experimental and require physical-device testing.

