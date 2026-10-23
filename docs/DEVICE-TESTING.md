# Testing on real TV devices

Most people will run Jelly Cinema on an inexpensive streaming device, and those are much slower than a
developer's machine or an emulator. Before each release, try it on at least one ARM device with 2 GB of
memory or less. The app tones its effects down by itself on such devices (Settings → Jelly Cinema →
Effects: Automatic), and you can force either mode there to compare.

## Devices worth having

| Device | Why |
|---|---|
| Chromecast with Google TV (HD or 4K) | The most common Google TV device; 1.5–2 GB of memory |
| Walmart onn. 4K box / 4K Pro | Very common, inexpensive Google TV box |
| Amazon Fire TV Stick (4K or Lite) | Fire OS; install the Amazon build (`./gradlew :app:assembleAmazon`) |
| A TV with Google TV built in (e.g. a TCL or Hisense) | TV processors are often the slowest of all |

## What to check

1. **Start-up.** Close the app fully and open it: the intro plays smoothly to the end, then Home is ready
   with Play highlighted. Do it twice (the first start after an install or update is always slower).
2. **Remote response.** Right after Home appears, move around the banner buttons and the first rows: every
   press should move the highlight at once.
3. **Banner.** Leave Home alone for a few minutes: titles change, trailers play (muted) and the picture is
   sharp; Theater Mode takes over after the set time and the first key press brings everything back.
4. **Scrolling.** Hold Down through all the rows and back up: no stutter, posters load, the red outline
   follows the highlight.
5. **Playback.** Play a movie from a row with the remote's Play key and from the banner's Play button.
6. **Kids account.** Sign in as an account with a rating limit: bigger posters, no trailers, nothing
   above the limit anywhere.
7. **Server off.** Stop Jellyfin and open the app: the "Can't reach…" screen shows and retries; start
   Jellyfin again and the app continues on its own.
8. **Memory.** After 30 minutes on Home with Theater Mode and trailers, the app is still smooth. On a
   computer with adb: `adb shell dumpsys meminfo app.jellycinema.tv` (watch "TOTAL PSS").

## Fire TV notes

- Fire TV has no Google Play, so use the Amazon Appstore build. It doesn't update itself; the Appstore does.
- Voice search on Fire TV goes through Alexa, which only searches Amazon's own catalogue for most apps.
