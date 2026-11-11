# PriceRadar

Native Kotlin / Jetpack Compose Android app with a Material 3 dark interface. Targets Android 17 (API 37); Android 8+ devices can install it.

## Store website search only (unreleased)
Native product-result search and its parser/cache have been removed. Choose eMAG, Vexio or Altex and search its website in the store browser. Tracking the opened product, paste-link import and searching the saved watchlist remain available.

## Vexio fixes (unreleased)
- Recognize Vexio's category/brand/product-ID links as trackable products, retaining legacy category/product links and retailer/HTTPS validation.
- Vexio search returns a JavaScript shell. Selecting Vexio and searching now opens the store's interactive results directly; all-store search offers an explicit Open Vexio results action instead of claiming there are no matches.
- Refresh the browser's selected URL when navigation finishes and allow website subframes while restricting top-level navigation to the selected retailer.

## UI remake — 1.4.0-preview.1
- Completely rebuilt watchlist: large overview, dedicated search tile, store-labelled price cards, and filter/sort sheet.
- New pill navigation, grouped settings, redesigned product price header, and updated product search.
- Settings → Automatic refresh opens a 1/3/6/12/24-hour picker, with persistent scheduling and cancel support.
- Single-layer screen fades and ripple-only product presses reduce rendering work. Reduce motion remains available.
- APK uses version code 8 and version name 1.4.0-preview.1, so installing it is visibly distinguishable from the old preview. The app's Settings footer identifies the remake.
- Runtime smoke checks assert the new screen text, exercise schedule persistence, and capture watchlist/search/settings screenshots.

## New in 1.3.1
- Fixes an immediate optimized-build startup crash: Room/WorkManager reflectively constructs `WorkDatabase_Impl`, whose no-argument constructor R8 had removed. An explicit keep rule preserves it.
- Tagged releases now require an Android 12 tablet-emulator smoke test of the optimized APK (fresh launch, restart, main-screen rendering and rotation) before publication.

## New in 1.3.0: discovery and smoother rendering
- Find product searches by name, with eMAG, Vexio and Altex filters, store-labeled results, tracking and view/buy actions. Each tracked listing also has store search shortcuts.
- When native search results cannot be extracted, browse the selected store inside the app, open a product and tap Track this product. Paste link and Android Share-sheet import remain available.
- Search results are previews and never become history observations. Store results can differ in variant, seller and availability; cross-store identity matching is not claimed.
- Short directional transitions cover tabs and product details. Press/refresh effects update drawing layers; charts cache paths and cap geometry at 240 points while retaining bucket extremes and endpoints.
- Reduce motion is enabled by default on Android low-RAM devices and can be changed in Settings. Animations respect Android system animation settings.
- Release APKs use R8 code optimization and resource shrinking; CI now builds the optimized APK, retaining the same development signing key for updates.
- Startup database reads and watchlist filtering run off the UI thread. Saving one product no longer re-reads every product/history from SQLite. Refresh requests serialize independently of watchlist edits and re-check whether a product was paused/deleted before saving a response.

Native search availability was checked on November 11, 2026: the eMAG search request returned HTTP 410; Vexio and Altex returned HTML without usable product listings. No successful live native search extraction is claimed. The parser has synthetic-fixture tests; in-app store browsing and retailer behavior require device testing. Search requests are explicit, limited to three stores, time out after 12 seconds, cap responses at 2 MB and cache successful results briefly in memory. Only one store browser exists at a time and is destroyed when closed. It uses the store's own JavaScript and may be heavier than native screens.

## New in 1.2.0
- Animated tab transitions and springy product-card presses.
- Pull down on the watchlist to refresh active products; tab scroll positions are retained.
- Filter active/paused products, reached targets and failed checks alongside retailer/search filters.
- Clear search, reset filters, see matching counts and share a product through Android's share sheet.
- Refresh explains when there are no active products to check.

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
./gradlew testDebugUnitTest lintRelease assembleRelease
```

On Windows use `gradlew.bat`. Set `ANDROID_HOME` or create an untracked `local.properties` with `sdk.dir`.

Every push, pull request or manual **Actions → Android optimized APK → Run workflow** compiles, tests, lints and uploads **PriceRadar-APK**. Open a successful run and download its artifact ZIP; extract `app-release.apk`. This is a code/resource-shrunk release build signed with the repository's public development key for sideloading, not a Play Store release. Debug builds remain available with `./gradlew assembleDebug`. Artifacts expire after 30 days.

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

## 1.2.0 acceptance checks
- Pull down at the top of an empty, paused-only and populated watchlist. Verify appropriate feedback and only one refresh at a time.
- Combine status, retailer and search filters, then clear/reset them. Unknown prices must not count as reached targets.
- Scroll each tab, switch away/back, and verify position is retained. Check tab transitions and card presses with system animation scales disabled as well.
- Share a product and verify its name and exact URL; cancel the share sheet and confirm the app returns normally.

## Discovery/performance acceptance checks
- Search a name, a model with punctuation and a nonexistent product; inspect each store and its unavailable/empty state. Open a native result and verify exact variant, seller, price/currency and availability at the store.
- Browse each store in-app, navigate to a product, track without copying a URL, and use Open store / buy. Verify browsing stops at other sites and login/CAPTCHA/SSL errors are not bypassed. Back/results/close must free the browser; repeat on a low-memory device.
- Search rapidly, switch stores, leave during requests, rotate and retry offline. Confirm no stale results are applied to a new query and no search preview is inserted into history.
- Populate a large watchlist and dense history, cold-start, scroll/sort/filter, refresh while pausing/removing products, and open charts. Confirm settings/history survive restart and paused/deleted products are not restored by an in-flight refresh.
- Measure frame times and memory on an actual lower-end phone in both motion modes; responsiveness improvements are implemented but no lag-free claim or device benchmark has been established.
