# ZetaCard

ZetaCard is an original Android LSPosed module for giving Google Wallet payment cards custom artwork. Its English-only Material 3 manager shows the original card artwork beside your design. You can pick a photo, crop it to card proportions or stretch it to fit, rename the local card entry, or restore the original appearance.

The module changes artwork in the Wallet user interface. It does not edit payment credentials, NFC services, or Google Play Services.

## Screenshots

| Your cards | Crop editor | In Google Wallet |
|:---:|:---:|:---:|
| <img src="docs/screenshots/cards.png" alt="ZetaCard card list showing original and custom artwork" width="240"> | <img src="docs/screenshots/design.png" alt="ZetaCard crop editor with a photo fitted to the card frame" width="240"> | <img src="docs/screenshots/wallet.png" alt="Custom artwork on a Google Wallet card" width="240"> |

## Requirements

- Android 10 or newer
- Google Wallet
- LSPosed / Vector with Modern API 102
- Root (KernelSU, Magisk, or similar), with root access granted to ZetaCard

## Setup

1. Install the APK.
2. Enable ZetaCard in LSPosed or Vector with `com.google.android.apps.walletnfcrel` in scope.
3. Open ZetaCard. The welcome screen checks root and LSPosed access; allow ZetaCard when your root manager (KernelSU, Magisk, or similar) asks.
4. Force stop and reopen Google Wallet and view your cards once.
5. Return to ZetaCard. Your cards appear with previews of their original artwork. Choose and crop an image for a card, then reopen Wallet.

## How it works

The LSPosed module runs inside Google Wallet, finds its card artwork hooks on its own and rescans automatically after Wallet updates. It writes the list of your cards into Wallet's private storage, and ZetaCard reads it back with root. Your artwork is stored on the device and shown in place of the original card art. **View logs**, at the bottom of the card list, shows events from ZetaCard and from the module inside Wallet.

## Build

Use JDK 17 or newer and Android SDK 37. The included Gradle wrapper downloads Gradle 9.7.1:

```sh
./gradlew assembleDebug
```

The APK is created at `app/build/outputs/apk/debug/app-debug.apk`.

GitHub Actions builds a debug APK on every push and publishes it as a preview in [Releases](https://github.com/hxfuxyy/ZetaCard/releases). To publish a versioned release, select **Actions → Build APK → Run workflow** and enter a version name and a higher Android version code. Push builds use version 1.4 and code 140; change the workflow defaults to change those values. GitHub Actions APKs are debug-signed on its runner, so APKs from separate runs may require uninstalling the previous build before installation. A stable signing key is needed for seamless upgrades between runs.

## Source and permissions

Copyright (c) 2026 hxfuxyy. ZetaCard is released under the [ZetaCard Source License 2.0](LICENSE). You may fork, modify, extend and build it and publish your own versions, as long as you keep the license and credit the original author: every fork must state clearly that it is based on ZetaCard by hxfuxyy and link to this repository. Selling ZetaCard or derivatives requires permission. Third-party dependencies keep their own licenses.

GitHub: https://github.com/hxfuxyy/ZetaCard
