# Building Jelly Cinema TV

For people who want to build the app themselves or work on the code. To just install it, see the [README](../README.md).

## Where the Jelly Cinema code is

- `app/src/main/java/org/jellyfin/androidtv/jellycinema/` — banner (`HeroBanner.kt`), state and Theater
  Mode (`JellyCinemaHeroState.kt`), rows and settings (`JellyCinemaRepository.kt`, `JellyCinemaRows.kt`),
  Top 10 cards (`Top10CardPresenter.kt`), seasonal effects (`SeasonalEffects.kt`), self-update
  (`UpdateChecker.kt`, `UpdateReceiver.kt`), Settings diagnostics (`JellyCinemaDiagnostics.kt`).
- Changed Jellyfin files: `ui/home/HomeFragment.kt`, `ui/home/HomeRowsFragment.kt`,
  `ui/browsing/MainActivity.kt`, `ui/presentation/CardPresenter.kt`, `CustomListRowPresenter.kt`,
  `ui/settings/screen/SettingsMainScreen.kt`, `di/AppModule.kt`, branding resources.
- Also: Settings → Jelly Cinema (`ui/settings/screen/SettingsJellyCinemaScreen.kt`, `jellycinema/JellyCinemaSettings.kt`),
  the start-up intro (`jellycinema/JellyCinemaIntro.kt`), the "can't reach the server" screen
  (`ui/startup/fragment/ServerUnreachableFragment.kt`) and crash reports that ask first (`telemetry/TelemetryService.kt`).

## Build

Requires JDK 21 and the Android SDK (platform 37). The signing key is **not** in this repository;
keep `jellycinema-tv.jks` and its password safe — every update must be signed with the same key.

```bash
./gradlew :app:assembleRelease \
  -Pjc.versionCode=106 -Pjc.versionName=2.0.6 \
  -Pkeystore.file=/path/to/jellycinema-tv.jks -Pkeystore.password=... \
  -Psigning.key.alias=jellycinema -Psigning.key.password=...
```

Output in `app/build/outputs/apk/release/`: `jellycinema-tv-universal-release.apk` (every device) plus smaller
`jellycinema-tv-<abi>-release.apk` files for `arm64-v8a`, `armeabi-v7a` (almost every TV) and `x86_64`.
Application id `com.techsystems23.jellycinema`. `./gradlew :app:assembleAmazon` makes the Amazon Appstore (Fire TV)
build, which, like the Play build, leaves updating to the store.

## Google Play build

Play doesn't allow apps to update themselves outside the store. `./gradlew :app:bundlePlay` (same signing
properties as above) makes a Play build with self-update turned off and without the install-apps
permission. `assembleRelease` stays the sideload build that updates itself from the server.

## Keeping up with Jellyfin

Jelly Cinema is built on the Jellyfin Android TV app, which gets fixes every few weeks. `UPSTREAM_BASE` holds
the Jellyfin commit the code currently matches. Once a month (and on demand from the Actions tab) the
**Jellyfin updates** workflow runs `ci/upstream-sync.sh`, which applies Jellyfin's changes since then, runs the
full check (`ci/check.sh`: unit tests, privacy check, build) and opens a pull request to review and merge. If
Jellyfin and Jelly Cinema changed the same lines, it opens an issue listing the files to merge by hand
instead. Run `ci/upstream-sync.sh` locally to do the same by hand.

## Devices

See `docs/DEVICE-TESTING.md` for the devices to test on and what to check before a release. On TV boxes
with little memory the app tones its effects down by itself (Settings → Jelly Cinema → Effects).

## Store listing

`fastlane/metadata/android/en-US/` holds the store title, descriptions, icon, feature graphic and TV banner.
`PRIVACY.md` is the privacy policy (the published copy is linked from the app's Settings → About → Privacy policy).
The store screenshots use public-domain films; `docs/screenshots/` has uncaptioned copies for this README.

## Updates

Sideloaded builds update themselves (the Play build is updated by the store). On Home and from
**Settings → Check for updates** the app looks, in order, at:

1. its own update address, built in with `-Pjc.updateUrl=https://…/jellycinema-tv.json`. Release builds made by
   GitHub Actions point at the newest GitHub release:
   `https://github.com/<owner>/<repo>/releases/latest/download/jellycinema-tv.json`. The repository has to be
   public for TVs to download from it.
2. the connected server's `<server>/web/hub/jellycinema-tv.json`, for server owners who host their own builds.

The update information is `{"versionCode":..,"versionName":..,"apk":"jellycinema-tv.apk","md5":..}`; `apk` is a
file next to the .json or a full `https://` address. The download is checked against the md5 before installing.

## Releases (GitHub Actions)

- **Every push / pull request** (`.github/workflows/jellycinema-ci.yaml`): unit tests, the privacy check (below)
  and an unsigned build.
- **A tag like `v2.3.0`** (`.github/workflows/jellycinema-release.yaml`): the same checks, then a signed sideload
  APK, the `jellycinema-tv.json` update information and the Play bundle, published as a GitHub release.
  Version code = major×10000 + minor×100 + patch (2.3.0 → 20300). Needs the repository secrets
  `JC_KEYSTORE_BASE64`, `JC_KEYSTORE_PASSWORD`, `JC_KEY_ALIAS`, `JC_KEY_PASSWORD`; the key itself is never in the
  repository.

## Privacy check

`app/src/test/kotlin/jellycinema/JellyCinemaPrivacyTest.kt` signs in to a throwaway Jellyfin server made by
`ci/privacy/seed.py` (sample movies with ratings and holiday tags in three libraries, an account limited to PG and
one limited to a single library, plus watch history so R-rated movies rank in the shared Top 10). It loads every
Jelly Cinema row and the banner, on every holiday's dates, and fails if any title is one the account couldn't open,
is above its rating limit or is outside its libraries. Run it locally:

```bash
python3 ci/privacy/seed.py http://localhost:8096 /tmp/jc-media          # on an empty test server
JC_PRIVACY_ACCOUNTS=/tmp/jc-media/accounts.json ./gradlew :app:testDebugUnitTest
```

## Languages

All Jelly Cinema text is in `app/src/main/res/values/jellycinema_strings.xml`, with Spanish, French and German in
`values-es`, `values-fr` and `values-de`. The app follows the TV's language.
