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

ZetaCard is published to the [Xposed Modules Repository](https://modules.lsposed.org), which LSPosed shows in its module list. The module lives in `Xposed-Modules-Repo/io.github.hxfuxyy.zetacard`, and the **Publish to Xposed Modules Repository** workflow (`publish-xposed.yml`) keeps it up to date:

- On every stable release it downloads the signed APK from the GitHub Release, checks it against the published SHA-256 file and creates the release `<versionCode>-<versionName>` (for example `160-1.6`) with the same notes. Prereleases are skipped.
- It copies `xposed/README.md`, `xposed/SUMMARY`, `xposed/SOURCE_URL` and `xposed/SCOPE` into the module repository and keeps its description set to `ZetaCard`. Editing anything in `xposed/` on `main` syncs the module page automatically.
- It never rebuilds the APK, and re-running it does nothing when everything is already published.

### One-time setup

1. Submit the module: open an issue titled `[submission] io.github.hxfuxyy.zetacard` in [Xposed-Modules-Repo/submission](https://github.com/Xposed-Modules-Repo/submission). The bot creates `Xposed-Modules-Repo/io.github.hxfuxyy.zetacard` and invites you as an admin. Accept the invitation.
2. Create a token that can write to that repository:
   - Preferred: a fine-grained token (**Settings → Developer settings → Fine-grained tokens**) with resource owner **Xposed-Modules-Repo**, access to **only** `io.github.hxfuxyy.zetacard`, and permissions **Contents: Read and write** and **Administration: Read and write**. If the organization is not offered or the token stays pending, the organization does not allow fine-grained tokens; use the fallback.
   - Fallback: a classic token with only the `public_repo` scope. It can write to every public repository you have access to, so give it an expiry date and do not use it anywhere else.
3. Add it to `hxfuxyy/ZetaCard` as the repository secret `XPOSED_REPO_TOKEN`.
4. Run **Actions → Publish to Xposed Modules Repository → Run workflow** with an empty tag. It syncs the module page and publishes the latest stable release. After about five minutes the module appears on modules.lsposed.org and in LSPosed.

### Later

- Releases publish automatically after the Release workflow. If a run fails, fix the cause and run the workflow again with the tag, for example `v1.6`.
- To update only the module page, edit the files in `xposed/` and push to `main`, or run the workflow with **metadata only** checked.
- Renew the token before it expires; the workflow logs a notice and skips publishing when the secret is missing.

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
