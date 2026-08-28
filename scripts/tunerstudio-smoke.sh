#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TS_VERSION="${TUNERSTUDIO_VERSION:-3.3.01}"
TS_URL="${TUNERSTUDIO_URL:-https://www.efianalytics.com/TunerStudio/download/TunerStudioMS_v${TS_VERSION}.tar.gz}"
OUT="${SMOKE_OUT:-$ROOT/target/tunerstudio-smoke}"
WORK="${SMOKE_WORK:-${RUNNER_TEMP:-/tmp}/ae-tuner-tunerstudio-smoke}"
PLUGIN_JAR="${PLUGIN_JAR:-}"
DISPLAY_NUMBER="${DISPLAY_NUMBER:-99}"
DISPLAY=":${DISPLAY_NUMBER}"

rm -rf "$OUT" "$WORK"
mkdir -p "$OUT" "$WORK/extracted" "$WORK/home"

if [[ -z "$PLUGIN_JAR" ]]; then
  SOURCE_VERSION="$(sed -n 's/.*public static final String VERSION = "\([^"]*\)".*/\1/p' \
    "$ROOT/src/main/java/se/anders/tunerstudio/aetuner/AeTunerPlugin.java")"
  PLUGIN_JAR="$ROOT/dist/ae-tuner-epicefi-${SOURCE_VERSION}.jar"
fi

[[ -f "$PLUGIN_JAR" ]] || {
  echo "Plugin JAR not found: $PLUGIN_JAR" >&2
  exit 1
}

ARCHIVE="$WORK/TunerStudioMS_v${TS_VERSION}.tar.gz"
echo "Downloading official TunerStudio MS ${TS_VERSION} from EFI Analytics"
curl --fail --location --silent --show-error \
  --retry 4 --retry-delay 3 --retry-all-errors \
  --user-agent "AE-Tuner-EPICEFI-GitHub-Smoke-Test/1.0" \
  "$TS_URL" --output "$ARCHIVE"

sha256sum "$ARCHIVE" | tee "$OUT/tunerstudio-download.sha256"
tar -tzf "$ARCHIVE" > "$OUT/tunerstudio-archive-list.txt"
tar -xzf "$ARCHIVE" -C "$WORK/extracted"

TS_JAR="$(find "$WORK/extracted" -type f -name 'TunerStudioMS.jar' -print -quit)"
[[ -n "$TS_JAR" ]] || {
  echo "TunerStudioMS.jar was not found after extraction" >&2
  exit 1
}
TS_DIR="$(dirname "$TS_JAR")"
LAUNCHER="$(find "$TS_DIR" -maxdepth 1 -type f -name 'TunerStudio*.sh' -print -quit)"

{
  echo "TunerStudio version: $TS_VERSION"
  echo "Download URL: $TS_URL"
  echo "TunerStudio directory: $TS_DIR"
  echo "TunerStudio JAR: $TS_JAR"
  echo "Launcher: ${LAUNCHER:-direct java fallback}"
  echo "Java: $(java -version 2>&1 | head -1)"
} | tee "$OUT/environment.txt"

find "$TS_DIR" -maxdepth 2 -printf '%y %P\n' | sort > "$OUT/tunerstudio-layout.txt"
if [[ -n "$LAUNCHER" ]]; then
  cp "$LAUNCHER" "$OUT/TunerStudio-launcher.sh"
fi
if [[ -f "$TS_DIR/plugins/readme.txt" ]]; then
  cp "$TS_DIR/plugins/readme.txt" "$OUT/plugins-readme.txt"
fi
if [[ -f "$TS_DIR/TunerStudio.properties" ]]; then
  cp "$TS_DIR/TunerStudio.properties" "$OUT/TunerStudio.properties"
fi
jar tf "$TS_JAR" | grep -i 'plugin' > "$OUT/tunerstudio-plugin-classes.txt" || true
(unzip -p "$TS_JAR" | strings | grep -Ei 'plugin|add or update|user properties|TunerStudioProjects') \
  > "$OUT/tunerstudio-plugin-strings.txt" || true

mkdir -p "$TS_DIR/plugins"
cp "$PLUGIN_JAR" "$TS_DIR/plugins/"
PLUGIN_INSTALLED="$TS_DIR/plugins/$(basename "$PLUGIN_JAR")"
sha256sum "$PLUGIN_INSTALLED" | tee "$OUT/plugin-installed.sha256"
unzip -p "$PLUGIN_INSTALLED" META-INF/MANIFEST.MF | tr -d '\r' > "$OUT/plugin-manifest.txt"
grep -q '^ApplicationPlugin: se.anders.tunerstudio.aetuner.AeTunerPlugin$' "$OUT/plugin-manifest.txt"

export HOME="$WORK/home"
export DISPLAY
mkdir -p "$HOME/TunerStudioProjects"

Xvfb "$DISPLAY" -screen 0 1440x900x24 > "$OUT/xvfb.log" 2>&1 &
XVFB_PID=$!
cleanup() {
  set +e
  if [[ -n "${TS_PID:-}" ]]; then kill -TERM "$TS_PID" 2>/dev/null || true; fi
  pkill -TERM -f 'TunerStudioMS\.jar' 2>/dev/null || true
  kill -TERM "$XVFB_PID" 2>/dev/null || true
}
trap cleanup EXIT
sleep 2

set +e
(
  cd "$TS_DIR"
  if [[ -n "$LAUNCHER" ]]; then
    bash "$LAUNCHER"
  else
    java -cp '.:./plugins/*:lib:./lib/*' -Djava.library.path=lib -jar TunerStudioMS.jar
  fi
) > "$OUT/tunerstudio-stdout.log" 2> "$OUT/tunerstudio-stderr.log" &
TS_PID=$!
set -e

JAVA_PID=""
for _ in $(seq 1 45); do
  JAVA_PID="$(pgrep -f 'TunerStudioMS\.jar' | head -1 || true)"
  if [[ -n "$JAVA_PID" ]]; then
    break
  fi
  if ! kill -0 "$TS_PID" 2>/dev/null; then
    break
  fi
  sleep 1
done

if [[ -z "$JAVA_PID" ]]; then
  echo "TunerStudio Java process did not remain running" >&2
  find "$HOME" -maxdepth 5 -type f -print > "$OUT/home-files.txt" || true
  exit 1
fi

echo "$JAVA_PID" > "$OUT/tunerstudio-java.pid"
sleep 12

jcmd "$JAVA_PID" VM.command_line > "$OUT/jcmd-command-line.txt" 2>&1 || true
jcmd "$JAVA_PID" VM.system_properties > "$OUT/jcmd-system-properties.txt" 2>&1 || true
jcmd "$JAVA_PID" GC.class_histogram > "$OUT/jcmd-class-histogram.txt" 2>&1 || true
jcmd "$JAVA_PID" Thread.print > "$OUT/jcmd-threads.txt" 2>&1 || true

import -display "$DISPLAY" -window root "$OUT/tunerstudio-screen.png" 2> "$OUT/screenshot-error.txt" || true
find "$HOME" -maxdepth 6 -type f -print | sort > "$OUT/home-files.txt" || true

DEBUG_LOG="$(find "$HOME" -type f -name 'TunerStudioAppDebug.txt' -print -quit || true)"
if [[ -n "$DEBUG_LOG" ]]; then
  cp "$DEBUG_LOG" "$OUT/TunerStudioAppDebug.txt"
fi

PLUGIN_LOADED=0
if grep -q 'se\.anders\.tunerstudio\.aetuner\.AeTunerPlugin' "$OUT/jcmd-class-histogram.txt" \
    && grep -q 'se\.anders\.tunerstudio\.aetuner\.AeTunerPanel' "$OUT/jcmd-class-histogram.txt"; then
  PLUGIN_LOADED=1
fi

PLUGIN_SUPPORT_STATUS="unknown"
PLUGIN_GATE="failed"
if [[ "$PLUGIN_LOADED" -eq 1 ]]; then
  PLUGIN_SUPPORT_STATUS="available"
  PLUGIN_GATE="passed"
elif grep -q 'Registration file not found' "$OUT/tunerstudio-stdout.log" \
    && ! find "$TS_DIR" -type f -name 'TunerStudioPlugin.jar' -print -quit | grep -q .; then
  PLUGIN_SUPPORT_STATUS="disabled_in_stock_unregistered_host"
  PLUGIN_GATE="not_exercised"
fi

{
  echo "Real-host launch gate: passed"
  echo "TunerStudio process PID: $JAVA_PID"
  echo "Plugin manifest/install gate: passed"
  echo "Application-plugin host support: $PLUGIN_SUPPORT_STATUS"
  echo "Plugin instantiation gate: $PLUGIN_GATE"
  echo "Plugin class loaded: $PLUGIN_LOADED"
  echo "Installed plugin: $PLUGIN_INSTALLED"
  echo "Debug log: ${DEBUG_LOG:-not found}"
} | tee "$OUT/result.txt"

if grep -R -E 'Exception|Error' "$OUT"/tunerstudio-*.log "$OUT"/TunerStudioAppDebug.txt 2>/dev/null \
    | grep -E 'se\.anders\.tunerstudio\.aetuner|AeTunerPlugin|AeTunerPanel' > "$OUT/plugin-errors.txt"; then
  echo "TunerStudio reported an AE Tuner plugin exception" >&2
  exit 1
fi

if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
  {
    echo "## TunerStudio host smoke"
    echo
    echo "- Real host launch: **passed**"
    echo "- Plugin JAR manifest/install: **passed**"
    echo "- Application-plugin host support: **${PLUGIN_SUPPORT_STATUS}**"
    echo "- Plugin instantiation: **${PLUGIN_GATE}**"
    echo "- TunerStudio: \`${TS_VERSION}\`"
  } >> "$GITHUB_STEP_SUMMARY"
fi

if [[ "$PLUGIN_LOADED" -eq 1 ]]; then
  echo "TunerStudio host smoke passed: AE Tuner was instantiated by the real host."
  exit 0
fi

if [[ "$PLUGIN_SUPPORT_STATUS" == "disabled_in_stock_unregistered_host" ]]; then
  echo "TunerStudio real-host launch passed. Application-plugin instantiation was not exercised because the stock unregistered package does not include/enable TunerStudioPlugin.jar."
  exit 0
fi

echo "TunerStudio appears capable of application plugins, but AE Tuner was not instantiated" >&2
exit 1
