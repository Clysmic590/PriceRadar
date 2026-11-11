Search by product name, choose a store, and track without copying a link.

- eMAG, Vexio and Altex filters, store-labeled search previews, and store search shortcuts on tracked listings.
- In-app store browsing with **Track this product** when native results cannot be extracted; **Open store / buy** opens the selected store externally.
- Smoother tab/detail transitions and lighter press/refresh animations.
- Reduce motion defaults on for Android low-RAM devices and can be changed in Settings.
- Database loading/filtering moved off the UI thread, fewer database reads, cached chart geometry and independently serialized refreshes.
- Optimized release APK with code/resource shrinking, approximately 1.5 MB.

Download **PriceRadar-1.3.0.apk** below. Android 8 or newer is required. This uses the same public development signing key as 1.1–1.2, so installing over those versions can retain your watchlist/history. Do not uninstall first. The 1.0 signing key is different and may require uninstalling, which deletes local data.

Search previews never become price-history observations. Cross-store results can differ in model, seller, variant and stock. Public native search extraction remains experimental: current checks returned HTTP 410 for eMAG and no readable product listings for Vexio/Altex; the store-browser fallback is available. Actual device performance and store-browser behavior still need phone testing. No lag-free guarantee is claimed.

Validation: 17 unit tests, release lint, optimized APK build and signing-certificate checks. SHA256SUMS.txt contains the APK checksum. This is a development-signed sideload build, not a Play Store release.
