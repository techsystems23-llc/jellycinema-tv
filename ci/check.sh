#!/usr/bin/env bash
# The full check: a throwaway Jellyfin server with sample movies and restricted accounts, the unit tests
# with the privacy check, and an unsigned build. Used by GitHub Actions (build, release and Jellyfin
# update workflows). Needs Docker, ffmpeg, Python 3 and the Android SDK.
set -euo pipefail
MEDIA="${RUNNER_TEMP:-/tmp}/jc-privacy-media"
mkdir -p "$MEDIA"
docker rm -f jc-privacy-jellyfin >/dev/null 2>&1 || true
docker run -d --name jc-privacy-jellyfin -p 8096:8096 -v "$MEDIA:/media" jellyfin/jellyfin:latest >/dev/null
trap 'if [ $? -ne 0 ]; then docker logs jc-privacy-jellyfin 2>&1 | tail -100; fi' EXIT
for i in $(seq 90); do curl -fs http://localhost:8096/System/Info/Public >/dev/null && break; sleep 2; done
python3 ci/privacy/seed.py http://localhost:8096 "$MEDIA" /media
JC_PRIVACY_ACCOUNTS="$MEDIA/accounts.json" ./gradlew :app:testDebugUnitTest
./gradlew :app:assembleRelease
