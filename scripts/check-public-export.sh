#!/usr/bin/env bash
set -euo pipefail

EXPORT_DIR="${1:-}"
if [[ -z "$EXPORT_DIR" || ! -d "$EXPORT_DIR" ]]; then
  echo "Usage: bash scripts/check-public-export.sh <export-directory>" >&2
  exit 2
fi

EXPORT_DIR="$(cd "$EXPORT_DIR" && pwd)"

if find "$EXPORT_DIR" -type l -print -quit | grep -q .; then
  echo "Public export contains a symbolic link" >&2
  find "$EXPORT_DIR" -type l -print >&2
  exit 1
fi

for forbidden in \
  'lib/TunerStudioPluginAPI.jar' \
  '.git' \
  'local-evidence' \
  'target' \
  'dist'
do
  if [[ -e "$EXPORT_DIR/$forbidden" ]]; then
    echo "Forbidden public-export path present: $forbidden" >&2
    exit 1
  fi
done

forbidden_file_regex='\.(msl|mlg|msq|csv|zip|mkv|mp4|mov|avi|pem|key|p12|jks)$|(^|/)\.env($|\.)|(^|/)(id_rsa|id_ed25519)$'
if find "$EXPORT_DIR" -type f -printf '%P\n' | grep -Ei "$forbidden_file_regex"; then
  echo "Public export contains a forbidden evidence, credential, archive, or media file" >&2
  exit 1
fi

required=(
  README.md
  LICENSE
  NOTICE
  THIRD_PARTY_NOTICES.md
  PUBLIC_PROVENANCE.md
  lib/README.md
  src/main/java/se/anders/tunerstudio/aetuner/AeTunerPlugin.java
)
for path in "${required[@]}"; do
  if [[ ! -s "$EXPORT_DIR/$path" ]]; then
    echo "Public export is missing required file: $path" >&2
    exit 1
  fi
done

if [[ -e "$EXPORT_DIR/docs/public/README.md" ]]; then
  echo "Public README source path was not promoted/removed" >&2
  exit 1
fi

if ! grep -q '^Apache License$' "$EXPORT_DIR/LICENSE" \
    || ! grep -q 'Version 2.0, January 2004' "$EXPORT_DIR/LICENSE"; then
  echo "LICENSE is not the expected Apache License 2.0 text" >&2
  exit 1
fi

if ! grep -q 'You do not need to compile or modify code to use the plugin' "$EXPORT_DIR/README.md"; then
  echo "Public README is not the approved end-user README" >&2
  exit 1
fi

if grep -q 'Private authority repository:' "$EXPORT_DIR/README.md" \
    || grep -q 'Run the handoff pre-flight' "$EXPORT_DIR/README.md"; then
  echo "Public README contains private/developer authority instructions" >&2
  exit 1
fi

content_pattern='(/home/[^[:space:]]+|[A-Za-z]:\\Users\\|BEGIN (RSA |OPENSSH |EC )?PRIVATE KEY|github_pat_[A-Za-z0-9_]+|ghp_[A-Za-z0-9]+|private-user-images\.githubusercontent\.com)'
content_match=0
while IFS= read -r -d '' file; do
  relative="${file#"$EXPORT_DIR"/}"
  # The checker contains the forbidden-pattern definitions themselves. Exclude
  # only this exact exported file from the content scan so the rules do not
  # self-match while every other exported file remains covered.
  if [[ "$relative" == "scripts/check-public-export.sh" ]]; then
    continue
  fi
  if grep -InE --binary-files=without-match "$content_pattern" "$file"; then
    content_match=1
  fi
done < <(find "$EXPORT_DIR" -type f -print0)

if (( content_match )); then
  echo "Public export contains a personal path, private key marker, token-shaped value, or private attachment URL" >&2
  exit 1
fi

if ! grep -q 'Apache-2.0' "$EXPORT_DIR/PUBLIC_PROVENANCE.md"; then
  echo "Public provenance does not record the approved source licence" >&2
  exit 1
fi

if ! grep -q 'TunerStudio Plugin API binary is intentionally excluded' "$EXPORT_DIR/PUBLIC_PROVENANCE.md"; then
  echo "Public provenance does not record the third-party dependency exclusion" >&2
  exit 1
fi

echo "Public export licence and leak checks passed: $EXPORT_DIR"
