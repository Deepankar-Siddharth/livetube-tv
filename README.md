# LiveTube TV

LiveTube TV is a native Android TV application for browsing and watching official YouTube live channels. It is backed by a GitHub-hosted channel catalogue, a static administration portal, a deterministic M3U generator, and signed GitHub releases.

The repository is intentionally split into four independently usable pieces:

```text
 data/channels.json ──> Android TV app (offline cache + background refresh)
        │
        └──────────────> scripts/build_m3u.py ──> data/playlist.m3u8

 portal/ ──> GitHub Contents API ──> data/channels.json
 app/    ──> NewPipeExtractor ──> Media3 ──> Android TV
 GitHub Actions ──> validation, M3U, dependency updates, signing, releases
```

## Repository layout

| Path | Purpose |
| --- | --- |
| `app/` | Kotlin, Jetpack Compose, Media3, NewPipeExtractor, and Android TV resources |
| `data/channels.json` | Single source of truth for channel metadata |
| `data/playlist.m3u8` | Deterministic generated playlist |
| `data/build-metadata.json` | Machine-readable app/dependency/release metadata |
| `portal/` | Responsive static administration portal for desktop and mobile browsers |
| `scripts/validate_channels.py` | Strict catalogue validator used locally and in CI |
| `scripts/build_m3u.py` | Validating, deterministic M3U generator |
| `scripts/update_dependency.py` | Safe, compatibility-windowed NewPipeExtractor updater |
| `.github/workflows/` | Validation, playlist, Android build, release, and dependency automation |
| `app/src/test/` | JVM tests for parsing, URL generation, versions, releases, and recovery |

## Architecture and startup behaviour

The Android application is offline-first:

1. `ChannelRepository` reads the last atomically cached `channels.json` immediately; schema-v1 caches are normalized to schema v2 in memory.
2. If no cache exists, the bundled validated schema-v2 catalogue is rendered.
3. The UI composes and displays the guide without waiting for network I/O.
4. `RemoteConfigRepository` fetches the configured raw GitHub URL in the background.
5. The response is parsed and validated before `data_version` is compared.
6. Only a successfully validated document is written to the cache and published to the UI.
7. A network, HTTP, rate-limit, or JSON failure leaves the previous document untouched.

Selecting a channel follows this path:

```text
Channel
  -> YouTubeExtractor
  -> current channel live tab
  -> fresh StreamInfo
  -> ExtractionResult
  -> PlaybackController
  -> PlayerManager / Media3
```

`channels.json` stores only canonical URLs such as:

```text
https://www.youtube.com/@mirrornow/live
```

NewPipeExtractor resolves the current broadcast and returns a short-lived HLS/DASH URL in memory. Temporary URLs are never written to the catalogue or cache.

## Requirements

### Android build

- JDK 17 (the Gradle toolchain used by CI)
- Android SDK Platform 36 and Build Tools 35.0.0 or newer
- Android Studio Ladybug or a compatible command-line SDK
- Internet access during the first Gradle dependency resolution

The project currently uses the following compatible stable toolchain:

- Android Gradle Plugin `8.13.2`
- Gradle `8.13`
- Kotlin `2.2.21`
- Compose BOM `2025.10.01` (the newest line compatible with AGP 8.13/API 36)
- Media3 `1.11.1`
- NewPipeExtractor `v0.26.5`

The Compose BOM can be advanced with AGP 9.1+ and `compileSdk = 37` after the build and test matrix is verified.

### Python and portal

- Python 3.10 or newer (CI uses 3.12)
- A modern browser for the portal
- A GitHub fine-grained personal access token for writes

## Local Android build

Clone the repository and build a debug APK:

```bash
git clone <repository-url>
cd livetube-tv
./gradlew assembleDebug
```

Windows PowerShell:

```powershell
git clone <repository-url>
cd livetube-tv
.\gradlew.bat assembleDebug
```

The APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

The app ships with a bundled catalogue, so it can render its initial guide without a GitHub configuration. To enable remote channel refresh and release checking, pass the repository coordinates at build time:

```bash
./gradlew assembleDebug \
  -Pgithub.owner=YOUR_GITHUB_OWNER \
  -Pgithub.repository=livetube-tv \
  -Pgithub.branch=main
```

PowerShell:

```powershell
.\gradlew.bat assembleDebug `
  -Pgithub.owner=YOUR_GITHUB_OWNER `
  -Pgithub.repository=livetube-tv `
  -Pgithub.branch=main
```

The Gradle properties are deliberately empty in source control. CI supplies the real values. A release should always be built with the repository coordinates that will host its releases.

## Testing and static validation

Run the Android unit tests:

```bash
./gradlew testDebugUnitTest
```

Windows:

```powershell
.\gradlew.bat testDebugUnitTest
```

Run the Python catalogue and playlist tests:

```bash
python scripts/validate_channels.py data/channels.json
python scripts/build_m3u.py --check
python -m unittest discover -s tests -v
```

Build a debug APK and run lint:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

The tests cover:

- valid and malformed channel JSON;
- missing fields and duplicate IDs/order;
- schema-v2 parsing plus schema-v1 migration;
- category/subcategory filtering, disabled-channel filtering, deterministic ordering, and local Favorites add/remove persistence;
- `@handle` to canonical live URL generation;
- semantic version ordering (`1.0.9 < 1.0.10`);
- stable release selection and APK asset selection;
- the bounded `1, 2, 4, 8, 15, 30` second recovery schedule;
- deterministic M3U output and stale-playlist detection.

## Channel catalogue schema

`data/channels.json` is a versioned object. Every channel has exactly these fields:

```json
{
  "id": "aaj_tak",
  "name": "Aaj Tak",
  "category": "News",
  "subcategory": "Hindi News",
  "language": "Hindi",
  "region": "National",
  "logo": "https://example.com/logo.png",
  "youtube_handle": "@aajtak",
  "live_url": "https://www.youtube.com/@aajtak/live",
  "enabled": true,
  "sort_order": 1
}
```

The validator enforces:

- UTF-8 JSON without duplicate object keys or a BOM;
- `schema_version = 2` and a positive `data_version` (legacy schema v1 is accepted and normalized);
- a real UTC `updated_at` timestamp;
- required classification fields and no unknown channel fields;
- unique IDs, handles, canonical URLs, and sort orders;
- the exact TV top-level category and subcategory hierarchy;
- boolean `enabled` values and positive integer sort orders;
- HTTPS logo URLs;
- exact canonical YouTube live URLs.

The only source-backed top-level categories are `News`, `Regional`, `Devotional`, `Kids & Family`, `Knowledge`, `Music & Entertainment`, and `Sports & Live`. `Favorites` is a virtual first section backed by device-local DataStore IDs and is never written to `channels.json`. Channel cards are ordered by `sort_order` and then channel name; disabled channels remain in administration data but never appear in the app or generated M3U.

To add a channel, edit `data/channels.json` or use the portal, then run:

```bash
python scripts/validate_channels.py data/channels.json
python scripts/build_m3u.py
```

The generated playlist contains enabled channels sorted by `sort_order`. A canonical YouTube `/live` page is not itself an HLS manifest. The playlist is therefore intended for clients that can resolve YouTube channel/live URLs; it must not be described as a direct HLS playlist.

## GitHub Pages administration portal

The portal is a static site in `portal/`. It has no build step and no server-side credential.

### Deploy with GitHub Pages

1. Push the repository to GitHub.
2. Open **Settings → Pages**.
3. Under **Build and deployment**, choose **Deploy from a branch**.
4. Select the default branch and the `/portal` folder.
5. Open the generated `https://<owner>.github.io/<repo>/` URL.

If the Pages site is served from the repository root instead, publish or redirect to `portal/index.html`; the portal is intentionally kept separate from the Android source.

### Token setup

The portal supports a personal GitHub token:

1. Create a **fine-grained personal access token**.
2. Limit it to the one repository that contains `data/channels.json`.
3. Grant **Contents: Read and write** and **Metadata: Read** only.
4. Do not grant workflow, administration, organization, or broad repository scopes.
5. Enter owner, repository, branch, and token in the portal.
6. Use **Forget token** when finished.

The token is stored only in that browser's `localStorage`. It is sent only as an `Authorization` header to `https://api.github.com`; it is never placed in a URL, exported JSON, HTML, or Git commit. A public read can work without a token. A token is required for a GitHub write.

The portal supports:

- loading the committed catalogue through the GitHub Contents API;
- dashboard totals for total, enabled, disabled, categories, and last update;
- add, edit, delete, enable/disable, and reorder operations;
- schema-v2 category, subcategory, language, and region fields with strict hierarchy validation;
- automatic canonical URL generation from `@handle` or an exact YouTube live URL;
- local draft editing and token-free JSON export/import;
- validation before every save;
- a fetched blob SHA conflict guard, so a newer repository version is never silently overwritten.

A conflict leaves the local draft intact. Export it, fetch the latest repository version, reconcile the changes, and save again.

## GitHub Actions

### `validate.yml`

Runs strict schema validation, playlist freshness checks, Python unit tests, and Python compilation. It fails on malformed or invalid channel data.

### `generate-m3u.yml`

Runs when `data/channels.json` or the generator changes. It validates, regenerates `data/playlist.m3u8`, and commits only when the generated bytes differ. The generated playlist is not included in the push trigger, preventing an automatic commit loop. Pull requests must contain an already-current playlist.

### `build-app.yml`

- pull requests and manual dispatches run unit tests, lint, and a debug build;
- version tags such as `v1.0.0` run the release job;
- the release job requires the signing secrets, builds and tests the release variant, verifies the APK signature and package version, creates/updates a GitHub Release, and publishes conflict-safe metadata;
- no signing credential is stored in the repository.

A manual workflow run creates a CI artifact/debug build. To publish a production release, create a matching semantic-version tag after reviewing the commit:

```bash
git tag v1.0.0
git push origin v1.0.0
```

### `dependency-update.yml`

The scheduled/manual dependency job:

1. reads the current NewPipeExtractor version from Gradle;
2. queries the authoritative `TeamNewPipe/NewPipeExtractor` GitHub releases API;
3. ignores drafts and prereleases;
4. rejects releases marked breaking and stays within the current major/minor compatibility window;
5. stops successfully when there is no newer compatible stable release;
6. updates the Gradle property/fallback and `data/build-metadata.json`;
7. bumps the app patch version deterministically;
8. validates the catalogue and runs unit tests, lint, and a signed release build;
9. inspects the APK signature and package version;
10. commits, tags, signs, and publishes only after the build succeeds.

If compilation, tests, signing, or APK verification fails, the job stops before publishing a production release. A maintainer must review and explicitly change the compatibility policy before crossing a NewPipeExtractor major/minor line.

The application and dependency versions are independent:

```text
LiveTube TV:       1.0.0
NewPipeExtractor:  0.26.5
```

A dependency-only update changes the former, never the latter.

## Release signing

Configure these GitHub Actions secrets in the repository or a protected `release` environment:

```text
ANDROID_KEYSTORE_BASE64
ANDROID_KEYSTORE_PASSWORD
ANDROID_KEY_ALIAS
ANDROID_KEY_PASSWORD
```

Create a keystore outside the repository and encode it without committing it:

```bash
keytool -genkeypair -v \
  -keystore "$HOME/livetube-release.jks" \
  -alias livetube-tv \
  -keyalg RSA -keysize 4096 -validity 10000
base64 -w 0 "$HOME/livetube-release.jks"
```

On macOS use `base64 < "$HOME/livetube-release.jks"` instead of `base64 -w 0`. Put the resulting text only in `ANDROID_KEYSTORE_BASE64`. The workflow decodes it into the runner's temporary directory, passes the path/passwords to Gradle through environment variables, verifies the resulting APK, and deletes the temporary file.

Never commit `.jks`, `.keystore`, `keystore.properties`, passwords, tokens, or `local.properties`.

## Android TV installation

Enable developer and USB/network debugging on the test TV, connect ADB, and install the debug build:

```bash
adb connect <tv-ip>:5555
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

For a signed release APK, transfer it manually or install it with ADB after verifying its signing certificate. Android TV systems may require the user to enable installation from the file manager or package installer. The in-app updater intentionally opens the system package installer; it does not claim silent installation.

The update client:

- queries stable GitHub releases in the background at every app start;
- ignores drafts and prereleases;
- compares semantic application versions;
- accepts only `.apk` release assets;
- downloads only from GitHub asset hosts;
- verifies the package name, newer version code, and signing certificate;
- uses Android's `FileProvider` and the system installer;
- handles the unknown-app-source permission flow.

GitHub or network failure never prevents cached channel viewing.

## TV remote controls

The playback view is clean fullscreen video. The guide is a compact two-line overlay at the
bottom of the screen: line 1 is categories, line 2 is channels.

- Fullscreen: any D-pad direction or OK opens the bottom guide.
- Guide Up/Down: move between the category row and the channel row.
- Guide Left/Right: move within the focused row; the row scrolls horizontally and shows edge
  indicators when more items exist.
- Category OK: select the focused category; the channel row refreshes immediately.
- Channel OK: start or switch playback to that channel and close the guide.
- Channel OK (hold): open the channel actions (Play and Add/Remove Favorite).
- Filter action (end of the category row): pick a subcategory for the channel row; the action
  stays highlighted while a filter is active and the choice is remembered between openings.
- About action (end of the category row): open the About screen.
- Guide Back: close the guide, or the open dialog, first.
- Fullscreen Back: show an exit confirmation when no guide/dialog is open.
- Five seconds without interaction: close the guide and return to video; playback continues
  while the guide is open.
- Playback problems raise a small focused status card with Retry and About; About and retry
  actions are focusable with a remote.

The video surface remains behind the guide, and Media3 reports buffering, adaptive-resolution
availability, and live state without inventing latency measurements.

## About screen

The About action opens a user-facing About screen built from the branding in
`app/src/main/res/`. It deliberately shows no build, dependency, extraction, playback, or
cache internals.

- **Home**: LiveTube TV logo, "Watch Live TV on Android TV", plus four rows — Download Latest
  Version, Developer, Open Source, and App Information — and a CLOSE button.
- **Download Latest Version**: shows the current version, reuses the existing update check
  result to offer an in-app update when a newer stable release exists, and links to the GitHub
  Releases page.
- **Developer**: developer name, short description, GitHub profile, and the LiveTube TV
  repository.
- **Open Source**: opens the LiveTube TV repository.
- **App Information**: app version (read from `BuildConfig.VERSION_NAME`, build-type suffixes
  are never shown), source code, license, developer, and repository.

D-pad Up/Down moves between rows, OK selects, and Back returns from a sub page to About and
closes About from the home page. External links are opened through
`util/ExternalLinks.kt`, which reports failure instead of crashing when no browser is present.

## Troubleshooting

### The initial app has no remote updates

The repository coordinates are intentionally empty in source. Build with `-Pgithub.owner`, `-Pgithub.repository`, and `-Pgithub.branch`, or add them to a secure CI configuration. The bundled catalogue and local cache still work without them.

### GitHub API returns 403

This can be an unauthenticated rate limit or repository permission issue. The app keeps cached data. For the portal, use a narrowly scoped fine-grained token with Contents read/write. For CI, verify repository permissions and the release environment.

### A channel is unavailable

YouTube channels go offline, change handles, or apply geographic restrictions. The app reports a friendly retry state and schedules bounded recovery. It never stores a temporary media URL as channel data.

### Gradle cannot resolve NewPipeExtractor

JitPack must be reachable. Check:

```bash
./gradlew dependencies --configuration debugRuntimeClasspath
```

The dependency is declared through JitPack in `settings.gradle.kts` and the ProGuard rules retain NewPipe's Rhino support classes.

### Release signing is skipped or fails

Confirm all four signing secrets exist, the base64 decodes to a keystore, and the alias/passwords match. A release build without the environment signing values may produce an unsigned artifact; release CI intentionally fails rather than publishing one.

## Security and privacy

- No GitHub token, API key, password, or signing key belongs in source control.
- The portal is static and has no server-side secret.
- The Android app does not require a GitHub token to read public channel data.
- HTTPS is enforced for logos, catalogue downloads, GitHub API calls, and release assets.
- APK installation verifies package identity, version, and signing certificate.
- Pull-request workflows are read-only and do not receive release secrets.
- NewPipeExtractor is used only to resolve authorized public YouTube live streams; users remain responsible for complying with YouTube's terms, applicable law, and channel rights.

## License

This repository is released under the GNU General Public License v3.0 or later. See [`LICENSE`](LICENSE). NewPipeExtractor is a separate GPL-3.0-or-later project; review its license and notices when distributing a built APK.
