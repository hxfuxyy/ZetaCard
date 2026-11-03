# Privacy

This document describes what data ZetaCard accesses, based on the source code in this repository.

## Summary

- No analytics, advertising, crash reporting or tracking libraries.
- No accounts and no data sent to the developer.
- One kind of network request: downloading original card artwork images from Google.

## Data accessed inside Google Wallet

The LSPosed module runs inside the Google Wallet process. It observes the image URLs Wallet uses for card artwork and, for older Wallet layouts, a short display label of the card. Labels that contain eight or more consecutive digits are discarded; labels Wallet itself shows, such as a card network with the last four digits, may be kept.

The module does not access card numbers, payment tokens, account details, transaction history or NFC payment data.

Each card is identified by the SHA-256 hash of its artwork URL. The module writes the following files into Wallet's private storage:

- `zetacard_cards.tsv`: card IDs, labels and artwork URLs
- `zetacard_stock_<id>.png`: previews of the original artwork
- `zetacard_events.log`: diagnostic events, redacted and limited to 64 KB
- `zetacard_hook_cache` preferences: the class layout detected for the installed Wallet version

## Data stored by the ZetaCard app

- Your chosen artwork, as PNG files in the app's private storage.
- Original artwork previews and the card list with names you can edit.
- A diagnostic log of app events, cleared every time the app is started fresh and limited to 32 KB.

Artwork is shared with the module through LSPosed's module storage so Wallet can display it. App backups are disabled (`allowBackup="false"`). Uninstalling ZetaCard removes its data.

## Network access

The `INTERNET` permission is used only to download the original artwork image for a card from an HTTPS URL on `googleusercontent.com`, so the app can show it next to your design. Other hosts, plain HTTP and redirects are refused, and downloads are limited to 2 MB. If the download fails, the preview captured inside Wallet is used instead.

## Root access

ZetaCard uses root to read the files listed above from Wallet's private storage and to stop the Wallet process so new artwork applies. While the previous app `dev.zxcwsurx.zetacard` is installed, it also uses root once to copy that app's artwork, previews and card names into its own storage. Commands use the data directories Android reports for the current user, validated card IDs and fixed file names, and only the current user's Wallet process is restarted.

## Photos

ZetaCard uses the Android photo picker and receives only the image you select. The image is cropped or resized on the device.

## Diagnostic logs

Logs stay on the device. They are shared only if you copy or export them. Messages are redacted to remove URLs, email addresses, private storage paths and long digit sequences. An exported log also includes your device manufacturer and model, Android version, firmware and build ID, and the installed ZetaCard and Google Wallet versions. Review an exported file before sharing it.

## Contact

Questions about privacy can be raised in [GitHub issues](https://github.com/hxfuxyy/ZetaCard/issues). Report security problems as described in [SECURITY.md](SECURITY.md).
