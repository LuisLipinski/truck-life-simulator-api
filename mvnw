#!/bin/sh
# ----------------------------------------------------------------------------
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
# ----------------------------------------------------------------------------

# ----------------------------------------------------------------------------
# Apache Maven Wrapper startup batch script, version 3.3.4
#
# Optional ENV vars
# -----------------
#   JAVA_HOME - location of a JDK home dir, required when download maven via java source
#   MVNW_REPOURL - repo url base for downloading maven distribution
#   MVNW_USERNAME/MVNW_PASSWORD - user and password for downloading maven
#   MVNW_VERBOSE - true: enable verbose log; debug: trace the mvnw script; others: silence the output
# ----------------------------------------------------------------------------

set -euf
[ "${MVNW_VERBOSE-}" != debug ] || set -x

native_path() { printf %s\\n "$1"; }
case "$(uname)" in
CYGWIN* | MINGW*)
  [ -z "${JAVA_HOME-}" ] || JAVA_HOME="$(cygpath --unix "$JAVA_HOME")"
  native_path() { cygpath --path --windows "$1"; }
  ;;
esac

set_java_home() {
  if [ -n "${JAVA_HOME-}" ]; then
    if [ -x "$JAVA_HOME/jre/sh/java" ]; then
      JAVACMD="$JAVA_HOME/jre/sh/java"
      JAVACCMD="$JAVA_HOME/jre/sh/javac"
    else
      JAVACMD="$JAVA_HOME/bin/java"
      JAVACCMD="$JAVA_HOME/bin/javac"
      if [ ! -x "$JAVACMD" ] || [ ! -x "$JAVACCMD" ]; then
        echo "The JAVA_HOME environment variable is not defined correctly, so mvnw cannot run." >&2
        return 1
      fi
    fi
  else
    JAVACMD="$(command -v java 2>/dev/null || true)"
    JAVACCMD="$(command -v javac 2>/dev/null || true)"
    if [ ! -x "${JAVACMD-}" ] || [ ! -x "${JAVACCMD-}" ]; then
      echo "The java/javac command does not exist in PATH nor is JAVA_HOME set, so mvnw cannot run." >&2
      return 1
    fi
  fi
}

hash_string() {
  str="${1:-}" h=0
  while [ -n "$str" ]; do
    char="${str%"${str#?}"}"
    h=$(((h * 31 + $(LC_CTYPE=C printf %d "'$char")) % 4294967296))
    str="${str#?}"
  done
  printf %x\\n $h
}

verbose() { :; }
[ "${MVNW_VERBOSE-}" != true ] || verbose() { printf %s\\n "${1-}"; }
die() { printf %s\\n "$1" >&2; exit 1; }
trim() { printf "%s" "${1}" | tr -d '[:space:]'; }

scriptDir="$(dirname "$0")"
scriptName="$(basename "$0")"
while IFS="=" read -r key value; do
  case "${key-}" in
  distributionUrl) distributionUrl=$(trim "${value-}") ;;
  distributionSha256Sum) distributionSha256Sum=$(trim "${value-}") ;;
  esac
done <"$scriptDir/.mvn/wrapper/maven-wrapper.properties"
[ -n "${distributionUrl-}" ] || die "cannot read distributionUrl property"

MVN_CMD="mvn${scriptName#mvnw}"
_MVNW_REPO_PATTERN=/org/apache/maven/
[ -z "${MVNW_REPOURL-}" ] || distributionUrl="$MVNW_REPOURL$_MVNW_REPO_PATTERN${distributionUrl#*"$_MVNW_REPO_PATTERN"}"
distributionUrlName="${distributionUrl##*/}"
distributionUrlNameMain="${distributionUrlName%.*}"
distributionUrlNameMain="${distributionUrlNameMain%-bin}"
MAVEN_USER_HOME="${MAVEN_USER_HOME:-${HOME}/.m2}"
MAVEN_HOME="${MAVEN_USER_HOME}/wrapper/dists/${distributionUrlNameMain-}/$(hash_string "$distributionUrl")"

exec_maven() {
  unset MVNW_VERBOSE MVNW_USERNAME MVNW_PASSWORD MVNW_REPOURL || :
  exec "$MAVEN_HOME/bin/$MVN_CMD" "$@" || die "cannot exec Maven"
}

if [ -d "$MAVEN_HOME" ]; then exec_maven "$@"; fi
case "${distributionUrl-}" in *?-bin.zip) ;; *) die "distributionUrl is not valid" ;; esac
TMP_DOWNLOAD_DIR="$(mktemp -d)" || die "cannot create temp dir"
trap 'rm -rf -- "$TMP_DOWNLOAD_DIR"' HUP INT TERM EXIT
mkdir -p -- "${MAVEN_HOME%/*}"

if command -v curl >/dev/null; then
  curl --silent -f -L -o "$TMP_DOWNLOAD_DIR/$distributionUrlName" "$distributionUrl" || die "curl: Failed to fetch Maven"
elif command -v wget >/dev/null; then
  wget --quiet "$distributionUrl" -O "$TMP_DOWNLOAD_DIR/$distributionUrlName" || die "wget: Failed to fetch Maven"
elif set_java_home; then
  javaSource="$TMP_DOWNLOAD_DIR/Downloader.java"
  cat >"$javaSource" <<'END'
public class Downloader {
  public static void main(String[] args) throws Exception {
    java.nio.file.Files.copy(java.net.URI.create(args[0]).toURL().openStream(), java.nio.file.Paths.get(args[1]));
  }
}
END
  "$JAVACCMD" "$javaSource"
  "$JAVACMD" -cp "$TMP_DOWNLOAD_DIR" Downloader "$distributionUrl" "$TMP_DOWNLOAD_DIR/$distributionUrlName"
else
  die "No download mechanism available"
fi

if [ -n "${distributionSha256Sum-}" ]; then
  if command -v sha256sum >/dev/null; then
    echo "$distributionSha256Sum  $TMP_DOWNLOAD_DIR/$distributionUrlName" | sha256sum -c - >/dev/null || die "Maven distribution checksum validation failed"
  elif command -v shasum >/dev/null; then
    echo "$distributionSha256Sum  $TMP_DOWNLOAD_DIR/$distributionUrlName" | shasum -a 256 -c >/dev/null || die "Maven distribution checksum validation failed"
  else
    die "Checksum validation requested but sha256sum/shasum is unavailable"
  fi
fi

unzip -q "$TMP_DOWNLOAD_DIR/$distributionUrlName" -d "$TMP_DOWNLOAD_DIR" || die "failed to unzip Maven"
printf %s\\n "$distributionUrl" >"$TMP_DOWNLOAD_DIR/$distributionUrlNameMain/mvnw.url"
mv -- "$TMP_DOWNLOAD_DIR/$distributionUrlNameMain" "$MAVEN_HOME" || [ -d "$MAVEN_HOME" ] || die "failed to install Maven"
rm -rf -- "$TMP_DOWNLOAD_DIR"
trap - HUP INT TERM EXIT
exec_maven "$@"
