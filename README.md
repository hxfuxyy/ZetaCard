# ZetaCard

ZetaCard is an original Android LSPosed module for giving Google Wallet payment cards custom artwork. Its English-only Material 3 manager shows the original card artwork beside your design. You can pick a photo, crop it to card proportions or stretch it to fit, rename the local card entry, or restore the original appearance.

The module changes artwork in the Wallet user interface. It does not edit payment credentials, NFC services, or Google Play Services.

## Screenshots

| Your cards | Design preview | In Google Wallet |
|:---:|:---:|:---:|
| <img src="docs/screenshots/cards.jpg" alt="ZetaCard card list showing original artwork" width="240"> | <img src="docs/screenshots/design.jpg" alt="ZetaCard custom design preview" width="240"> | <img src="docs/screenshots/wallet.png" alt="Custom artwork in Google Wallet with the profile photo blurred" width="240"> |

## Requirements

- Android 10 or newer
- Google Wallet
- LSPosed / Vector with Modern API 102

## Build

Use JDK 17 or newer and Android SDK 37. The included Gradle wrapper downloads Gradle 9.7.1:

```sh
./gradlew assembleDebug
```

The APK is created at `app/build/outputs/apk/debug/app-debug.apk`.

GitHub Actions builds a debug APK on every push and publishes it as a preview in [Releases](https://github.com/hxfuxyy/ZetaCard/releases). To publish a versioned release, select **Actions → Build APK → Run workflow** and enter a version name and a higher Android version code. Push builds use version 1.0 and code 100; change the workflow defaults to change those values. GitHub Actions APKs are debug-signed on its runner, so APKs from separate runs may require uninstalling the previous build before installation. A stable signing key is needed for seamless upgrades between runs.

## Setup

1. Install the APK.
2. Enable ZetaCard in LSPosed or Vector with `com.google.android.apps.walletnfcrel` in scope.
3. Force stop and reopen Google Wallet, then browse your payment cards. ZetaCard saves a local preview of their original artwork.
4. Open ZetaCard, choose and crop an image for a discovered card, then reopen Wallet.

Images, including original previews, are saved in ZetaCard's private app storage. The module derives a local SHA-256 ID from the card artwork URL. The URL is never saved by ZetaCard. The selected art remains visible with Wallet's masked card digits over it.

## Compatibility

Verified on OnePlus Ace 6T, Android 16, Vector 1.2, and Google Wallet `26.38.985592722`. Wallet is obfuscated and changes often. ZetaCard finds the card image renderer by method shape at runtime. If Wallet changes that shape, the module leaves Wallet's original artwork visible until ZetaCard is updated. NFC payments were not exercised as part of visual testing.

## Source and permissions

Copyright (c) 2026 hxfuxyy. All rights reserved. The [ZetaCard Source License](LICENSE) allows viewing and forking on GitHub and personal use of official APKs. It does not grant permission to reuse, modify, build, or redistribute the original source code. Third-party dependencies keep their own licenses.

GitHub: https://github.com/hxfuxyy/ZetaCard
