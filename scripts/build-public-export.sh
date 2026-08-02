#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

OUT="${1:-$ROOT/target/public-export}"
ALLOWLIST="${PUBLIC_EXPORT_ALLOWLIST:-$ROOT/config/public-export-allowlist.txt}"
MODE="${PUBLIC_EXPORT_MODE:-review}"

case "$MODE" in
  review|publish) ;;
  *)
    echo "PUBLIC_EXPORT_MODE must be review or publish, got: $MODE" >&2
    exit 2
    ;;
esac

[[ -f "$ALLOWLIST" ]] || {
  echo "Public export allowlist not found: $ALLOWLIST" >&2
  exit 1
}
if ! git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  echo "Public export must be generated from a Git checkout" >&2
  exit 1
fi
if [[ -n "$(git status --porcelain=v1 --untracked-files=all)" ]]; then
  git status --short
  echo "Public export refused: working tree is not clean" >&2
  exit 1
fi

rm -rf "$OUT"
mkdir -p "$OUT"

file_list="$(mktemp)"
trap 'rm -f "$file_list"' EXIT

while IFS= read -r entry || [[ -n "$entry" ]]; do
  entry="${entry%$'\r'}"
  [[ -z "$entry" || "$entry" == \#* ]] && continue
  matches="$(git ls-files -- "$entry")"
  if [[ -z "$matches" ]]; then
    echo "Allowlist entry did not resolve to a tracked file: $entry" >&2
    exit 1
  fi
  printf '%s\n' "$matches" >> "$file_list"
done < "$ALLOWLIST"

sort -u "$file_list" -o "$file_list"

while IFS= read -r path; do
  [[ -n "$path" ]] || continue
  mkdir -p "$OUT/$(dirname "$path")"
  cp -- "$ROOT/$path" "$OUT/$path"
done < "$file_list"

[[ -s "$OUT/docs/public/README.md" ]] || {
  echo "Public end-user README was not exported" >&2
  exit 1
}
cp -- "$OUT/docs/public/README.md" "$OUT/README.md"
rm -f "$OUT/docs/public/README.md"
rmdir "$OUT/docs/public" 2>/dev/null || true

source_commit="$(git rev-parse HEAD)"
source_branch="${PUBLIC_EXPORT_SOURCE_BRANCH:-$(git symbolic-ref --quiet --short HEAD || printf 'detached')}"
policy_sha="$(sha256sum "$ALLOWLIST" | awk '{print $1}')"
created_utc="$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
plugin_version="$(sed -n 's/.*public static final String VERSION = "\([^"]*\)".*/\1/p' src/main/java/se/anders/tunerstudio/aetuner/AeTunerPlugin.java)"

if [[ "$MODE" == "publish" ]]; then
  status_text="publication-approved sanitized source export; target branch metadata determines stable or candidate status"
else
  status_text="review export; source publication is licensed but this artifact has not itself been promoted"
fi

cat > "$OUT/PUBLIC_PROVENANCE.md" <<PROVENANCE
# Public export provenance

- Project: AE Tuner (EPICEFI)
- Plugin version: \`$plugin_version\`
- Private authority: \`PJawZK/AE-Tuner-EPICEFI-\`
- Sanitized downstream target: \`PJawZK/AE-Tuner-EPICEFI_Public\`
- Source branch: \`$source_branch\`
- Source commit: \`$source_commit\`
- Export policy SHA-256: \`$policy_sha\`
- Generated UTC: \`$created_utc\`
- Export mode: \`$MODE\`
- Status: $status_text

The AE Tuner source is licensed under Apache-2.0. The TunerStudio Plugin API binary is intentionally excluded and remains governed by its own upstream terms.
PROVENANCE

bash scripts/check-public-export.sh "$OUT"

file_count="$(find "$OUT" -type f | wc -l | tr -d ' ')"
export_digest="$(find "$OUT" -type f -print0 \
  | sort -z \
  | xargs -0 sha256sum \
  | sha256sum \
  | awk '{print $1}')"

printf 'Public export created\n'
printf '  mode:          %s\n' "$MODE"
printf '  source branch: %s\n' "$source_branch"
printf '  source commit: %s\n' "$source_commit"
printf '  plugin:        %s\n' "$plugin_version"
printf '  files:         %s\n' "$file_count"
printf '  tree digest:   %s\n' "$export_digest"
printf '  directory:     %s\n' "$OUT"
