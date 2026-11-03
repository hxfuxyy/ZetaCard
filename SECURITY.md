# Security policy

## Supported versions

Only the latest stable release receives security fixes.

| Version | Supported |
|---|---|
| Latest release | Yes |
| Older releases and preview builds | No |

## Reporting a vulnerability

Please do not open a public issue for security problems. Report them privately through [GitHub private vulnerability reporting](https://github.com/hxfuxyy/ZetaCard/security/advisories/new).

Include the ZetaCard and Google Wallet versions, your Android version and root solution, steps to reproduce, and the impact you observed. You will receive an acknowledgement when the report has been reviewed. Please allow reasonable time for a fix before disclosing details publicly.

## Scope

In scope:

- The ZetaCard manager app, including its root commands, file handling, image decoding and network access.
- The module code that runs inside Google Wallet.
- The build and release workflows in this repository.

Out of scope:

- Vulnerabilities in Google Wallet, Android, LSPosed, Vector, Magisk, KernelSU or other third-party software.
- Risks inherent to rooting a device or running Xposed modules.

## Limitations

ZetaCard runs code inside Google Wallet and uses root by design. It has not undergone an independent security audit, and no security certification is claimed. Install releases only from this repository and compare the APK with the published SHA-256 checksum and signing certificate.
