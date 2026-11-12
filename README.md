# PriceRadar

<p align="center">
  <strong>A native Android price tracker for Romanian retailers.</strong><br>
  Watch products from eMAG, Vexio, and Altex, understand real price changes, and get notified when it is actually worth buying.
</p>

<p align="center">
  <a href="https://github.com/Clysmic590/PriceRadar/releases/latest"><img alt="Latest release" src="https://img.shields.io/github/v/release/Clysmic590/PriceRadar?display_name=tag&sort=semver"></a>
  <a href="https://github.com/Clysmic590/PriceRadar/actions/workflows/android.yml"><img alt="Android build" src="https://github.com/Clysm590/PriceRadar/actions/workflows/android.yml/badge.svg"></a>
  <img alt="Android 8+" src="https://img.shields.io/badge/Android-8%2B-3DDC84?logo=android&logoColor=white">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?logo=kotlin&logoColor=white">
</p>

## What PriceRadar does

PriceRadar keeps price tracking local, simple, and transparent. Add an exact product link or use the in-app retailer browser, then let the app build a real history from successful checks on your device.

- Track products from **eMAG**, **Vexio**, and **Altex**
- Receive **price-drop notifications**
- Set **target-price alerts** and a minimum drop threshold
- View price history for **1 day, 7 days, 1 month, or 1 year**
- Detect visible **coupon and voucher text**
- Flag suspicious advertised discounts using the preceding 30 days of observations
- Pause, resume, search, filter, sort, share, and refresh tracked products
- Import retailer links from Android's **Share** sheet
- Export observed history as **CSV**
- Use an optimized Material 3 dark interface with transitions, loading skeletons, tactile feedback, and polished empty states

## PriceRadar 1.5.2

Version 1.5.2 is a bug-fix release that corrects the version shown inside the app and packages the latest interface improvements in a correctly versioned build.

It also includes a hidden **One UI 9-inspired mode**. This is a custom Jetpack Compose presentation inspired by Samsung's visual language; it is **not** Samsung One UI, official SESL9, or a Samsung framework port.

[Download PriceRadar 1.5.2](https://github.com/Clysmic590/PriceRadar/releases/tag/v1.5.2)

The release contains:

- `PriceRadar-1.5.2.apk`
- `SHA256SUMS.txt`

The APK is development-signed for sideloading and is not a Play Store release.

## How price tracking works

1. Add an exact HTTPS product page from a supported retailer.
2. PriceRadar extracts public structured data and visible product metadata.
3. Successful checks are stored locally in SQLite.
4. WorkManager performs approximate background checks, while manual refresh is available at any time.
5. Alerts are generated for qualifying drops and target-price crossings.

Failed checks never fabricate a price or overwrite the last successful observation. Price history begins when you start tracking; the app does not ship seeded or purchased historical-price data.

## Fake-discount detector

The detector flags an advertised reference price when it is more than 5% above the lowest price observed during the preceding 30 days, after at least seven distinct observed days exist.

This is an evidence signal—not a legal finding. Sparse history, product variants, marketplace sellers, personal offers, shipping, and checkout-only promotions can affect the result.

## Privacy

- No PriceRadar account
- No backend service
- No analytics SDK
- No API credentials required
- Watchlist and history stay on the device
- Retailer pages are contacted only for searches, browsing, and price checks initiated by the app

The embedded retailer browser runs each store's own JavaScript and may use more resources than native screens.

## Install

1. Open the [latest release](https://github.com/Clysmic590/PriceRadar/releases/latest).
2. Download the APK and `SHA256SUMS.txt`.
3. Verify the checksum if desired.
4. Allow installation from your browser or file manager, then install the APK.

Android 8 or newer is supported. PriceRadar targets Android 17 / API 37.

### Verify SHA-256

Linux or macOS:

```sh
sha256sum -c SHA256SUMS.txt
```

Windows PowerShell:

```powershell
Get-FileHash .\PriceRadar-1.5.2.apk -Algorithm SHA256
```

For v1.5.2, the expected APK SHA-256 is:

```text
4a52919900e66d36b43c1761ae262b6d00cde540f71aec13fd5915c7f1e0d4f9
```

## Build from source

Requirements:

- JDK 17
- Android SDK platform 37
- Android build tools 37.0.0

Linux or macOS:

```sh
./gradlew testDebugUnitTest lintRelease assembleRelease
```

Windows:

```powershell
.\gradlew.bat testDebugUnitTest lintRelease assembleRelease
```

Set `ANDROID_HOME`, or create an untracked `local.properties` containing `sdk.dir`.

GitHub Actions performs unit tests, release lint, an optimized R8/resource-shrunk APK build, and an Android 12 startup smoke test for tagged releases. Verified releases receive both the APK and its generated checksum.

## Retailer integration status

All retailer integrations remain experimental and require physical-device testing. Retailers can change markup, offers, redirects, JavaScript behavior, or anti-bot rules without notice.

| Retailer | Implemented | Still requires testing |
| --- | --- | --- |
| eMAG | Public JSON-LD/product metadata extraction, reference-price and coupon selectors | Marketplace seller identity, variants, availability, redirects, coupon accuracy, and anti-bot behavior |
| Vexio | Public structured-data extraction and store-browser fallback | Live page access, stock/variant changes, price formatting, promotions, and request behavior |
| Altex | Public structured-data extraction and store-browser fallback | JavaScript-rendered offers, variants, availability, promotions, and request behavior |

Parsers have synthetic-fixture tests, but that does not prove live retailer compatibility. PriceRadar does not authenticate, bypass CAPTCHA, or claim an official partnership with any retailer.

Notifications and background scheduling also need broader testing on physical Android 17 devices, including denied notification permission, reboot, Doze, process death, and target crossings. WorkManager scheduling is best effort rather than real time.

## Project structure

- `MainActivity.kt` — Compose screens and navigation
- `Repository.kt` — persistent application state
- `RetailerClient.kt` — retailer requests and public-data extraction
- `Models.kt` — price-history and discount rules
- `Alerts.kt` — notifications and background work
- `.github/workflows/android.yml` — verification and release pipeline

## Disclaimer

PriceRadar is an independent project. eMAG, Vexio, Altex, Samsung, and One UI are trademarks of their respective owners. Prices, coupons, stock, and retailer terms should always be confirmed on the retailer's checkout page.
