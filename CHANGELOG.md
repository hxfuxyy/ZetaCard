# Changelog

All notable changes to ZetaCard are documented in this file. The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow `version.properties`.

## [Unreleased]

## [1.6]

### Added

- Six built-in Zeta designs (Violet, Midnight, Aurora, Ocean, Holo, Pearl) that fit the card exactly and apply with one tap ([#5](https://github.com/hxfuxyy/ZetaCard/issues/5), suggested by @bughaxx).
- Backup and restore of all designs, card names and caption settings in a single ZIP file. Restoring validates every entry before anything changes.
- Optional card name on the design, drawn in the top corner so it never covers the Zeta wordmark. The module inside Wallet and the app preview draw it with the same code, and turning it off keeps the original image untouched.
- Brightness and shade controls in the photo editor, saved exactly as previewed.
- 18 languages: English, Ukrainian, Russian, Spanish, Portuguese (Brazil), French, German, Italian, Polish, Turkish, Vietnamese, Indonesian, Japanese, Korean, Chinese (Simplified and Traditional), Hindi and Arabic, with right-to-left layout for Arabic.
- In-app language picker under **⋮ → Language**. On Android 13 and newer the choice also appears in the system's per-app language settings.
- Automatic card scan: when no cards are known yet (at most every six hours) or when pull to refresh finds nothing new, ZetaCard opens Google Wallet for a moment, waits until the cards are rendered and returns on its own. The scan uses a single root call, so root managers that show a toast per request stay quiet.
- New About screen with the animated Zeta card, version, author and quick links to the source code, releases, issue reporting and the license.
- Version badge next to the app name.
- Haptic feedback for taps, the preview switch, caption toggles, pull to refresh and for successful or failed actions. It follows the system touch feedback setting.
- Detailed diagnostics in **View logs** and the exported file: device and firmware, root manager, LSPosed framework and API, whether Wallet is in the module scope, Wallet version, per-card status and the Wallet hook cache.
- Crashes are recorded in the log with a readable stack trace, and logs are kept across app restarts.
- Setup guidance for root managers that unmount modules for Google Wallet ([#1](https://github.com/hxfuxyy/ZetaCard/pull/1), thanks to @Doffi4).
- Publishing to the Xposed Modules Repository: stable releases and the module page are kept in sync automatically.

### Changed

- Redesigned card editor: one large preview with an animated Original / Your design switch and a design picker sheet.
- Smoother animations throughout: staggered entrances, press feedback, animated status chips and counters, and a cross-fade from setup to the card list.
- Setup can no longer be skipped. Continue unlocks once root is granted, the LSPosed module is active and Google Wallet is in the module scope; the status updates live.
- Pull to refresh scans Google Wallet when no new cards are found and always reports the result.
- Messages appear as short Android toasts.
- The default card name and all messages follow the app language.
- New launcher icon based on the card-shaped Zeta logo, with a themed icon on Android 13 and newer.

### Fixed

- After the LSPosed service connected late, cards kept showing "waiting for LSPosed" until the app was reopened.
- Inside Wallet, a design that could not be read once stayed on the original artwork until Wallet restarted; it is now retried after a short pause.
- Ripple effects on rounded previews spilled outside the corners.

### Upgrade notes

- **From 1.5:** install 1.6 over the existing app. Your designs and settings are kept.
- **From 1.4 or older:** install 1.6 next to the old app and open it once with root; your designs are copied automatically. Then uninstall the old app and enable the new module in LSPosed.
- After installing, force stop Google Wallet and open it again.

## [1.5]

### Fixed

- Google Wallet could hang on its splash screen and fail to open while the module searched for artwork hooks. The search now runs in the background with a time limit, uses indexed lookups instead of a nested brute-force match, and records unsupported Wallet versions instead of rescanning on every launch.
- Large photos (up to 6000 px) were decoded at full size and could run out of memory. Images are now down-sampled to at most 3000 px before editing.
- Photos taken with the camera could appear rotated. Images are now decoded with `ImageDecoder`, which applies EXIF orientation.
- Root commands could block indefinitely when a command wrote to standard error. Commands now have timeouts and discard standard error.
- The welcome screen could be drawn under the status and navigation bars on Android 15, and status bar icons could be unreadable in light mode. The app now enables edge-to-edge with a day/night window theme.
- After a Wallet update, custom artwork for already known cards could be missing until the second launch. The module now preloads known cards from its saved card list.
- The saved card list inside Wallet only kept cards seen in the current session; previously discovered cards are now preserved.
- The crop editor opened below the zoom needed to fill the frame, so **Apply crop** stayed disabled until the photo was zoomed, and pinching out could disable it again. The editor now starts filled and never zooms out past the card frame.
- Artwork sync could corrupt the shared file when the app synced on startup while a new design was being saved. Syncs are now serialized, and unchanged artwork is no longer copied again on every app start.
- Inside Wallet, a missing or unreadable artwork file was reopened and decoded on every frame. Failures are now remembered per artwork revision.
- Two Wallet threads could write the saved card list at the same time.
- Reopening ZetaCard while a slow card import was still running could freeze the app on start.
- Pull-to-refresh did nothing visible while an automatic import was already running.
- A malformed character reference in the old app's preferences could stop the migration from ever completing.
- Migration from the old app could be marked finished even when a design failed to copy or its data could not be read. Each design now has its own result, migration only completes when every design is present, and the app asks you to keep the old version until then.
- The saved hook map stored only the renderer name and parameter count, so a different overload could be hooked on the next launch. The cache now stores the full signature (format v2; older caches trigger one rescan).
- When several card state wrappers or renderer overloads scored equally, only the first was hooked. All equally scored layouts in the same renderer class are now hooked.
- Root command timeouts did not apply while a command kept its output open, so a stuck command could block for much longer than allowed.
- The 45-second Wallet scan budget did not include loading Wallet's classes. It now covers the whole scan, and an unfinished scan is never cached.
- Writing the card list, encoding original-artwork previews and the first artwork decode happened on Wallet's UI path. They now run on a background thread, and known artwork is preloaded at start.
- The card status said "Custom artwork active" even when the design had not reached the LSPosed module. Cards now show whether a change is applied or still waiting for LSPosed.
- Root access used the primary user's storage paths. Wallet and old-app data are now located for the current user, and only that user's Wallet is restarted.
- Missing artwork previews were downloaded again on every return to the app, which could stall refreshes when offline. Failed previews are retried only on pull-to-refresh or the next app start.

### Changed

- The package name is now `io.github.hxfuxyy.zetacard`, matching the requirements of the Xposed Modules Repository. On first launch with root, designs and card names are copied from the old `dev.zxcwsurx.zetacard` app, and a reminder is shown until the old app is uninstalled.
- The manager app was split into separate UI, view model, data, image, root, logging and Wallet layers, using coroutines instead of manual threads.
- The repeated "ZetaCard · version" footer was removed. The rounded overflow menu holds the logs and an About dialog with the version and source link.
- New app icon based on the card-shaped logo, with a matching monochrome layer for themed icons on Android 13 and newer.
- The welcome screen opens with a new card-shaped logo in your theme colors, and lists both setup steps in one aligned panel with a clear status for each.
- Cards show a status chip, Change and Restore actions side by side, and a confirmation before restoring the original artwork.
- Artwork previews are decoded at display size instead of full resolution.
- Diagnostic logs redact URLs, email addresses, private storage paths and long digit sequences.
- The version is defined once in `version.properties`; the duplicated `version` entry was removed from `module.prop`.
- Production releases are built from the release variant, signed with a permanent key and published with SHA-256 checksums. Preview builds are uploaded as CI artifacts instead of public releases.

### Added

- Unit tests for crop geometry, image sampling, card IDs, card registry parsing, artwork URL validation, log redaction, hook discovery and module metadata.
- CI, release and CodeQL workflows, Dependabot, ktlint, and project documentation.
- A separate, re-runnable workflow publishes an existing release to the Xposed Modules Repository.

### Upgrade notes

- 1.5 installs next to the old app because the package name changed. Open it once with root to copy your designs, uninstall the old app, then enable the new module in LSPosed for Google Wallet.

## [1.4]

Releases up to and including 1.4 were published as debug-signed APKs by the previous workflow. See [GitHub Releases](https://github.com/hxfuxyy/ZetaCard/releases) for their notes.

[Unreleased]: https://github.com/hxfuxyy/ZetaCard/compare/v1.6...HEAD
[1.6]: https://github.com/hxfuxyy/ZetaCard/compare/v1.5...v1.6
[1.5]: https://github.com/hxfuxyy/ZetaCard/compare/v1.4...v1.5
[1.4]: https://github.com/hxfuxyy/ZetaCard/releases/tag/v1.4
