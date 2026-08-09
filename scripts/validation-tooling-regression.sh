#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CHECKER="$ROOT/scripts/check-conflict-markers.sh"
FIXTURE="$(mktemp -d)"
trap 'rm -rf "$FIXTURE"' EXIT

git init -q "$FIXTURE"
git -C "$FIXTURE" config user.name "Validation Regression"
git -C "$FIXTURE" config user.email "validation-regression@example.invalid"
printf 'target/\ndist/\n' > "$FIXTURE/.gitignore"
printf 'clean tracked content\n' > "$FIXTURE/tracked.txt"
git -C "$FIXTURE" add .gitignore tracked.txt
git -C "$FIXTURE" commit -q -m fixture

mkdir -p "$FIXTURE/target" "$FIXTURE/dist"
printf '============================================================\n' \
  > "$FIXTURE/target/synthetic-map-predict-report.txt"
printf '======= decorative generated separator =======\n' \
  > "$FIXTURE/dist/generated-report.txt"

bash "$CHECKER" "$FIXTURE"

for marker in '<<<<<<< HEAD' '=======' '>>>>>>> branch'; do
  printf '%s\n' "$marker" > "$FIXTURE/tracked.txt"
  if bash "$CHECKER" "$FIXTURE" >/dev/null 2>&1; then
    echo "Tracked conflict marker was not detected: $marker" >&2
    exit 1
  fi
done

printf 'clean tracked content\n' > "$FIXTURE/tracked.txt"
bash "$CHECKER" "$FIXTURE"
