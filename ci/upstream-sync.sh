#!/usr/bin/env bash
# Brings in the Jellyfin Android TV app's changes since the version Jelly Cinema is based on (UPSTREAM_BASE).
# Jelly Cinema started from one snapshot of Jellyfin's code, so Jellyfin's changes are applied as a patch
# (three-way, so they merge with Jelly Cinema's own changes). Run from the repository root:
#   ci/upstream-sync.sh            -> applies the changes and commits them, or lists the files that need a hand merge
# Exit codes: 0 = changes committed, 2 = nothing new, 3 = needs a hand merge (nothing committed).
set -euo pipefail
UPSTREAM_URL="${UPSTREAM_URL:-https://github.com/jellyfin/jellyfin-androidtv.git}"
BRANCH="${UPSTREAM_BRANCH:-master}"
git remote get-url jellyfin-upstream >/dev/null 2>&1 || git remote add jellyfin-upstream "$UPSTREAM_URL"
git fetch --quiet jellyfin-upstream "$BRANCH"
BASE="$(cat UPSTREAM_BASE)"
NEW="$(git rev-parse "jellyfin-upstream/$BRANCH")"
if [ "$BASE" = "$NEW" ]; then echo "Already up to date with Jellyfin ($NEW)."; exit 2; fi
COUNT="$(git rev-list --count "$BASE..$NEW")"
echo "Jellyfin has $COUNT new change(s) since ${BASE:0:7}:"
git log --oneline "$BASE..$NEW" | head -50
if ! git diff --binary "$BASE" "$NEW" | git apply --3way --index --whitespace=nowarn; then
  echo
  echo "These files need a hand merge (Jellyfin and Jelly Cinema both changed them):"
  git diff --name-only --diff-filter=U || true
  git reset --hard --quiet HEAD
  exit 3
fi
echo "$NEW" > UPSTREAM_BASE
git add UPSTREAM_BASE
git commit --quiet -m "Bring in Jellyfin Android TV changes up to ${NEW:0:7} ($COUNT change(s))

$(git log --format='- %s' "$BASE..$NEW" | head -40)"
echo "Committed: $(git log -1 --format=%s)"
