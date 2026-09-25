#!/usr/bin/env bash
set -euo pipefail

# Keep Android builds reproducible on hosts where Java is not on PATH.
# Run via `sh tools/gradle-java17.sh` or `bash tools/gradle-java17.sh`;
# the script does not need to be executable.
if [[ -z "${JAVA_HOME:-}" ]]; then
  if [[ -x "$HOME/Android/jdk17/bin/java" ]]; then
    JAVA_HOME="$HOME/Android/jdk17"
  elif [[ -x "/usr/lib/jvm/java-17-openjdk/bin/java" ]]; then
    JAVA_HOME="/usr/lib/jvm/java-17-openjdk"
  else
    printf '%s\n' "JAVA_HOME is not set and no known JDK 17 installation was found" >&2
    exit 1
  fi
  export JAVA_HOME
fi

exec "$(cd -- "$(dirname -- "$0")/.." && pwd)/gradlew" "$@"
