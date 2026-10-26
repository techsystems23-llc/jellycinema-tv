# Jelly Cinema TV

The Jelly Cinema home screen as a real Android TV / Google TV app: big banner with logos, ratings
and muted trailers, Theater Mode, Top 10 Movies / Shows with big ranked numbers, weekend family
Picks, seasonal effects (leaves and bats, snow, ...) and rows, "Because You Watched", genre,
Collections and Watch Again rows. It works on any Jellyfin server, with or without the Jelly
Cinema plugin, and only ever shows what the signed-in person may watch.

Based on the official Jellyfin Android TV app (see `README.jellyfin.md`), licensed **GPL-2.0**.

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

## Price and tips

- **Google Play version: $2.99**, a one-time convenience fee for installing and updating through Google
  Play. Same app, no extra features, no in-app purchases; the store description says it's free on GitHub.
- **GitHub and server versions: free.** Settings → Support the developer (also in About) shows a QR code
  for the tip page set with `-Pjc.supportUrl=https://...`; without it the item is hidden. GitHub releases
  use the repository variable `JC_SUPPORT_URL` (Settings → Secrets and variables → Actions → Variables).
- The Play and Amazon builds never show the tip page: stores don't allow outside payment links.

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
`PRIVACY.md` is the privacy policy (Google Play needs a web address for it: publish it, e.g. as a GitHub
page). Store screenshots still need making, from a demo library without copyrighted movie art.

## Licence

GPL-2.0, like the Jellyfin Android TV app it is based on (see `LICENSE` and `README.jellyfin.md`).
Anyone who receives the app is entitled to its complete source code under the same licence.
Jelly Cinema is not affiliated with or endorsed by the Jellyfin project. Don't use the Jellyfin
logo as the app's own icon or branding.

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

## Settings on the TV

**Settings → Jelly Cinema**: profile PIN, Kids' Home, effects (full / light), banner trailers, slide time,
Theater Mode delay, Top 10, Picks, holiday rows and the
holiday-library rows (with the share of holiday titles that earns a library its own row), how many Because You
Watched and genre rows, Collections and Watch Again, and the seasonal effects. Every option starts on
*Automatic*: the server's Jelly Cinema plugin settings when it has the plugin, otherwise the app's defaults.

## Languages

All Jelly Cinema text is in `app/src/main/res/values/jellycinema_strings.xml`, with Spanish, French and German in
`values-es`, `values-fr` and `values-de`. The app follows the TV's language.

## Profiles and kids

- **Profile PIN**: any profile can get a 4-digit PIN on this TV. It's asked when the app starts on that profile
  and when someone switches to it; the profile's Jellyfin password removes a forgotten PIN.
- **Kids' Home**: automatic for accounts with a rating limit: bigger posters and no trailers (Settings can
  turn it on or off for anyone).

## Google TV home screen and voice

The app fills the Google TV / Android TV home screen's Continue watching, and offers rows there (Next up,
latest movies and episodes, Top 10 Movies This Week and the holiday row in season), which people can add
with the home screen's Customize channels. "Hey Google, find Frozen on Jelly Cinema" opens Frozen directly
when exactly one title has that name, otherwise the search results.

## Trailers

Local trailer files play first (the app's own player). Otherwise the official YouTube embedded
player (android-youtube-player, Apache-2.0) plays the title's YouTube trailer, muted.

## Trademarks and warranty

Jellyfin is a trademark of the Jellyfin project and YouTube is a trademark of Google LLC; both names are
used only to describe compatibility. Jelly Cinema TV is not made or endorsed by either. The app is provided
"as is", without warranty of any kind (GNU GPL v2, sections 11 and 12). Third-party artwork and font
licences: see `NOTICE` and `licenses/`.
