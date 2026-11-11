Fixes the immediate startup crash in the optimized 1.3.0 APK.

R8 had removed the public no-argument constructor Room uses to initialize WorkManager's database. The constructor is now explicitly retained. Release publishing is gated on launching the actual optimized APK on an Android 12 tablet emulator, restarting it and rotating the display.

Download **PriceRadar-1.3.1.apk** below and install it over 1.3.0. The signing key is unchanged; uninstalling is not required. Android version code: 7. Android 8+ is supported.

Includes all 1.3 features: product-name search, eMAG/Vexio/Altex choices, store-browser tracking, smoother transitions, reduced motion and optimized rendering. Native search remains experimental and uses the store-browser fallback when results are unavailable.

Validation: unit tests, release lint, optimized APK build and Android 12 release-APK startup checks. APK checksum is in SHA256SUMS.txt. This is a development-signed sideload build, not a Play Store release.
