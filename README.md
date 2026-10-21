# Jelly Cinema TV

The Jelly Cinema home screen as a real Android TV / Google TV app: big banner with logos, ratings
and muted trailers, Theater Mode, Top 10 Movies / Shows with Netflix-style numbers, weekend family
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

## Build

Requires JDK 21 and the Android SDK (platform 37). The signing key is **not** in this repository;
keep `jellycinema-tv.jks` and its password safe — every update must be signed with the same key.

```bash
./gradlew :app:assembleRelease \
  -Pjc.versionCode=106 -Pjc.versionName=2.0.6 \
  -Pkeystore.file=/path/to/jellycinema-tv.jks -Pkeystore.password=... \
  -Psigning.key.alias=jellycinema -Psigning.key.password=...
```

Output: `app/build/outputs/apk/release/jellycinema-tv-release.apk` (application id `app.jellycinema.tv`).

## Updates

The app checks `<server>/web/hub/jellycinema-tv.json` (`{"versionCode":..,"versionName":..,"apk":"jellycinema-tv.zip","md5":..}`)
on Home and from **Settings → Check for updates**, and installs the newer APK from the same folder.

## Trailers

Local trailer files play first (the app's own player). Otherwise the official YouTube embedded
player (android-youtube-player, Apache-2.0) plays the title's YouTube trailer, muted.
