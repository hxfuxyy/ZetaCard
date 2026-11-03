# Contributing to ZetaCard

Thank you for helping improve ZetaCard. This guide explains how to set up the project and what a good contribution looks like.

## Prerequisites

- JDK 17 or newer (CI uses Temurin 21)
- Android SDK with platform 37 (`sdkmanager "platforms;android-37.0"`)
- A rooted test device with LSPosed or Vector and Google Wallet for testing hook changes

## Local setup

```sh
git clone https://github.com/hxfuxyy/ZetaCard.git
cd ZetaCard
./gradlew assembleDebug
```

Point Android Studio at the repository root, or create `local.properties` with `sdk.dir=/path/to/Android/sdk`.

## Common commands

| Task | Command |
|---|---|
| Unit tests | `./gradlew testDebugUnitTest` |
| Android lint | `./gradlew lintDebug` |
| Kotlin style | `ktlint "app/src/**/*.kt"` (ktlint 1.7.1, rules in `.editorconfig`) |
| Debug APK | `./gradlew assembleDebug` |

CI runs all of these on every pull request.

## Project layout

| Path | Contents |
|---|---|
| `core/` | Pure JVM logic without Android dependencies: IDs, labels, registry format, crop geometry, limits, log redaction. Covered by unit tests. |
| `wallet/` | The LSPosed entry point (`WalletHook`) and hook discovery that run inside Google Wallet. |
| `root/` | All root shell access. Commands are fixed strings plus validated card IDs. |
| `data/`, `image/`, `logging/` | Storage, LSPosed sync, image decoding and rendering, diagnostic logs. |
| `ui/` | Compose screens, components, theme and the `MainViewModel`. |

## Coding conventions

- Kotlin follows `.editorconfig` and must pass ktlint. Java follows the existing style in the module.
- Keep logic that can run on the JVM in `core/` and add tests for it.
- Do not add source-code comments that restate the code.
- Keep root commands in `root/`, never build them from untrusted input, and validate card IDs with `CardIds.isValid`.
- Route log messages through `DiagnosticsLog` or the Wallet event log so they are redacted. Never log payment data, account identifiers, tokens or full URLs.
- Code that runs inside Wallet must never block its main thread and must catch its own failures.
- Do not add dependencies without a clear need.

## Pull requests

1. Create a branch from `main`.
2. Keep each pull request focused on one change and update `CHANGELOG.md` under **Unreleased**.
3. Make sure tests, lint and ktlint pass.
4. Describe how you tested, including Wallet version and device for hook changes.

## Reporting issues

Use the [issue forms](https://github.com/hxfuxyy/ZetaCard/issues/new/choose). For bugs, export logs from **⋮ → View logs → Export** and attach them. Review the file first; it contains your device model, Android and Wallet versions. Report security problems privately as described in [SECURITY.md](SECURITY.md).
