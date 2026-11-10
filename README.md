# PriceRadar

Native Kotlin / Jetpack Compose Android app with a Material 3 dark interface. Targets Android 17 (API 37); Android 8+ devices can install it.

## New in 1.1.6
- One UI-inspired dark appearance for every install, with rounded panels, blue accents and gentle navigation, refresh, watchlist and chart animations. The theme picker has been removed; existing theme preferences no longer apply.
- Notification status follows Android app/category settings; refresh feedback dismisses automatically (respecting accessibility timeout preferences).
- Search by product or retailer and sort by recently checked, price, name or proximity to target.
- Pause/resume individual products while keeping observations and alert settings.
- Refresh only the open product; the watchlist refresh checks all active products.
- Set a minimum drop threshold from 0–50% to reduce noisy alerts. Target crossings remain independent of this threshold.
- Tap a notification to open its product, including when the app is already running.
- Export all observed history as CSV through Android's document picker, with exact RON amounts and UTC timestamps. CSV is a history export, not a restorable backup.
- See target progress and refresh feedback in the interface.

Existing watchlists migrate automatically: absent pause and threshold fields default to active tracking and any-drop alerts. Live retailer limitations below still apply. New document-picker, notification-navigation and pause controls require device testing.

Starting with 1.1, debug builds use the repository's **public development-only signing key**, so future debug APK updates can retain app data. Never use this key for a production release. The original 1.0 APK used a temporary CI key: Android may require uninstalling that version before installing 1.1, which clears its local watchlist/history. Schema migration preserves data only when installing with a matching signing key.

## Features
- Track HTTPS product links from eMAG, Vexio and Altex, including Android Share-sheet import.
- Local SQLite persistence, manual refresh and approximate six-hour WorkManager checks.
- Price-drop notifications and configurable target-price crossing alerts. Enable notification permission in Settings.
- Observed price history: 1 day, 7 days, 30-day month and 365-day year, with charts and low/high values.
- Public coupon/voucher text detection. Codes and eligibility are not verified at checkout.
- Fake-discount heuristic: flags an advertised reference price over 5% above the lowest price observed in the preceding 30 days, after at least seven distinct observed days. This is an evidence signal, not a legal finding.

No fabricated history or seeded live prices. History accumulates on this device after successful checks. No backend, login or historical-price provider is included. Failed checks preserve previous observations and display an error.

## Build and download

Use JDK 17, Android SDK platform 37, and Gradle Wrapper:

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

On Windows use `gradlew.bat`. Set `ANDROID_HOME` or create an untracked `local.properties` with `sdk.dir`.

Every push, pull request or manual **Actions → Android debug APK → Run workflow** compiles, tests, lints and uploads **PriceRadar-debug**. Open a successful run and download its artifact ZIP; extract `app-debug.apk`. This is a debug-signed APK for sideloading, not a Play Store release. Artifacts expire after 30 days.

## Retailer integration status — live testing required

| Retailer | Implementation | Verification still required |
| --- | --- | --- |
| eMAG | Public JSON-LD / product metadata price extraction; reference-price and coupon selectors | Real product and marketplace offer identity, variant selection, anti-bot behavior, coupon accuracy, redirects, price currency and availability |
| Vexio | Same public structured-data extractor | Real pages, stock/variant changes, HTML price formatting, coupon/reference selectors and request behavior |
| Altex | Same public structured-data extractor | JavaScript-rendered offers, stock/variant changes, promotions, coupon/reference selectors and request behavior |

All three integrations are experimental and **have not passed end-to-end retailer validation**. Unit tests use synthetic HTML fixtures plus a minimal eMAG structured-offer fixture captured during development (product title shortened; descriptive content removed). They validate extraction logic only. Requests do not execute JavaScript, authenticate, follow redirects, or bypass CAPTCHA. A retailer can reject or change its HTML; the app reports failure rather than making up a price. No official retailer API partnership is claimed. Check retailer terms before broader use.

Development smoke check: a direct public request to a Vexio Sony WH1000XM5B product URL returned HTTP 403 on November 10, 2026. This confirms an access limitation in this environment, not successful price extraction. Other networks/devices can behave differently.

The eMAG sample `/pd/DFHWK8MBM/` returned HTTP 200 with a RON JSON-LD Offer compatible with the parser. This single-page smoke check does not verify marketplace variants, coupons or continued access. An Altex sample did not complete during the development request and remains unverified.

Notifications and background scheduling still need on-device Android 17 testing, including denied permission, reboot, Doze, process death, and target crossings. WorkManager is periodic and best effort, not real time. Shipping, personal offers and checkout-only discounts are excluded. Fake-discount results are limited by sparse local history and detected reference-price markup.

## Manual acceptance checks
1. Add one exact product URL per retailer; compare name, seller/variant, RON price and availability against its page.
2. Refresh, restart and rotate the app; verify persisted history/targets. Check each period, including empty and single-point charts.
3. Enable notifications and use the test button. Verify drops and target crossings using a controlled test fixture; ensure unchanged prices do not repeatedly alert.
4. Verify coupon text against actual checkout requirements and reference-price markup against retailer display.
5. Test offline, HTTP errors, redirects and challenge pages; ensure no new price point is inserted.
6. Collect seven observed days before assessing suspicious reference prices. Verify that old prices outside the 30-day window do not affect the result.
7. In Settings, allow notifications and verify the status changes to "Notifications enabled" and the button to "Notification settings". Send a test notification and verify the confirmation and delivered notification. Disable app notifications or the Price alerts category in Android settings, then return to the app and verify the status updates. Re-enable them and return again. On Android 13+, also deny the permission request and verify the button provides access to Android settings, including after repeated denial.
8. Verify the One UI-inspired dark appearance on a fresh install and an update from 1.1.5 (including devices that previously selected Original or ColorOS). Verify the theme picker is absent, text and controls remain readable, and the appearance survives restart/rotation. Confirm refresh feedback clears after about six seconds (or the Android accessibility timeout), and repeat a refresh. Check animations with Android animation scales enabled and disabled.


## Structure
`MainActivity.kt` contains Compose screens; `Repository.kt` owns persistent state; `RetailerClient.kt` extracts public listings; `Models.kt` contains price and discount rules; `Alerts.kt` owns notifications and scheduled work. No API credentials are required or committed.
