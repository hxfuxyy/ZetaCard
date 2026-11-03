# Releasing ZetaCard

This guide is for the repository owner. It explains how builds are versioned, signed and published.

## Build types

| Build | Produced by | Signed with | Published as |
|---|---|---|---|
| Debug | `./gradlew assembleDebug` locally | Local debug key | Not published |
| Preview | CI on every push and pull request | CI runner debug key | Actions artifact, kept for 14 days |
| Production | Release workflow | Permanent release key | GitHub Release with SHA-256 checksum |

Preview APKs use a different debug key on every run, so they cannot update a production install.

## One-time signing setup

The release key identifies ZetaCard to Android. Every future release must be signed with the same key, otherwise users cannot update without uninstalling. Keep the keystore and passwords backed up offline. Losing them means every user has to reinstall.

1. Create the keystore on a trusted machine:

   ```sh
   keytool -genkeypair -v -keystore zetacard-release.jks -alias zetacard \
     -keyalg RSA -keysize 4096 -validity 10000 -storetype PKCS12
   ```

   With PKCS12 keystores the key password is the same as the store password.

2. Encode it for GitHub:

   ```sh
   base64 -w0 zetacard-release.jks > zetacard-release.jks.b64
   ```

3. In **Settings → Secrets and variables → Actions**, add these repository secrets:

   | Secret | Value |
   |---|---|
   | `ZETACARD_KEYSTORE_BASE64` | Contents of `zetacard-release.jks.b64` |
   | `ZETACARD_KEYSTORE_PASSWORD` | Keystore password |
   | `ZETACARD_KEY_ALIAS` | `zetacard` |
   | `ZETACARD_KEY_PASSWORD` | Key password |

4. Optionally pin the certificate so a wrong key can never be used. Print the fingerprint and add it as the repository variable `ZETACARD_CERT_SHA256`:

   ```sh
   keytool -list -v -keystore zetacard-release.jks -alias zetacard | grep SHA256
   ```

   Colons, spaces and letter case are ignored, so the value can be pasted straight from `keytool` or `apksigner verify --print-certs`.

5. Delete the `.b64` file. Never commit the keystore; `*.jks` and `keystore.properties` are ignored by Git.

### Local release builds

Create `keystore.properties` in the repository root:

```properties
storeFile=/absolute/path/zetacard-release.jks
storePassword=...
keyAlias=zetacard
keyPassword=...
```

Then run `./gradlew assembleRelease`. Environment variables `ZETACARD_KEYSTORE_FILE`, `ZETACARD_KEYSTORE_PASSWORD`, `ZETACARD_KEY_ALIAS` and `ZETACARD_KEY_PASSWORD` take precedence over the file. Without signing configuration, Gradle produces an unsigned release APK.

## Migration from 1.4 and earlier

Releases up to 1.4 used the package name `dev.zxcwsurx.zetacard` and were signed with whichever debug key the CI runner generated. From 1.5 the package is `io.github.hxfuxyy.zetacard` and every release uses the permanent key. Because the package changed, 1.5 installs next to the old app instead of replacing it. With root, the new app copies designs, previews and card names from the old one on first launch and shows a reminder to uninstall it. Never change the package name or the signing key again.

## Xposed Modules Repository

The [Xposed Modules Repository](https://modules.lsposed.org/submission) requires the repository name to be the package name, a non-empty repository description with the module name, and releases tagged `versionCode-versionName` with an APK asset.

1. Submit the module at <https://modules.lsposed.org/submission>. Once approved you get access to `Xposed-Modules-Repo/io.github.hxfuxyy.zetacard`.
2. Set that repository's description to `ZetaCard` and copy the README text into its `README.md`.
3. Create a fine-grained personal access token with **Contents: read and write** on that repository only, and add it to this repository as the secret `XPOSED_REPO_TOKEN`.

After every stable release, the release workflow starts **Publish to Xposed Modules Repository** (`publish-xposed.yml`). It downloads the APK already attached to the GitHub Release, checks it against the published SHA-256 file, and creates the release `<versionCode>-<versionName>` (for example `150-1.5`) in the module repository. If that step fails or the token was missing, add or fix the secret and run the workflow manually from **Actions** with the release tag, such as `v1.5`. It never rebuilds the APK and skips work that is already done.

## Publishing a release

1. Update `version.properties`: raise `versionCode` and set `versionName`. A name with a suffix such as `1.6-beta1` is published as a prerelease.
2. Move the **Unreleased** entries in `CHANGELOG.md` under a new `## [x.y]` heading.
3. Commit, then either push a tag or run the workflow manually:

   ```sh
   git tag v1.5
   git push origin v1.5
   ```

   The tag `150-1.5` (versionCode-versionName, the format used by the Xposed Modules Repository) is also accepted. Running **Actions → Release → Run workflow** creates the tag `v<versionName>` on the selected commit.

The workflow refuses to run if the tag does not match `version.properties`, if the release already exists, or if signing secrets are missing. It runs unit tests, builds the release variant, verifies alignment and the signature with `apksigner`, and publishes `ZetaCard-<version>.apk` with its `.sha256` file and notes taken from `CHANGELOG.md`.

## Old releases

The workflow never edits or deletes existing releases. Suggested manual cleanup of the old `preview-*` releases:

1. Keep `v1.4` and earlier versioned releases for history, and add a note that they are debug-signed.
2. Delete `preview-*` releases and their tags from the Releases page once 1.5 is published, since preview builds are now Actions artifacts.

## Repository settings

- Description: "Customize Google Wallet card artwork with a modern Material 3 LSPosed module for Android."
- Topics: `android`, `kotlin`, `lsposed`, `xposed-module`, `google-wallet`, `material3`, `root`, `android-customization`.
- Enable private vulnerability reporting under **Settings → Code security**.
- Protect `main`: require pull requests and the **CI / Lint, test and build** check, and block force pushes.
