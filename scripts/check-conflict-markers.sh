#!/usr/bin/env bash
set -euo pipefail

ROOT="${1:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
cd "$ROOT"

matches=""
if matches="$(git grep -n -E '^(<<<<<<<|=======|>>>>>>>)' -- .)"; then
  printf '%s\n' "$matches"
  echo "Merge conflict markers found" >&2
  exit 1
else
  status=$?
  if (( status != 1 )); then
    echo "Tracked-file conflict-marker scan failed" >&2
    exit "$status"
  fi
fi
