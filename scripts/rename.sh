#!/usr/bin/env bash
set -euo pipefail

NAME="$1"
SLUG=$(echo "$NAME" | tr '[:upper:]' '[:lower:]' | tr -cd '[:alnum:]-')

if [ -z "$SLUG" ]; then
  echo "invalid name: $NAME" >&2
  exit 1
fi

ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT"

echo "renaming template to '${SLUG}'"

files=$(git ls-files | grep -Ev '^(LICENSE|scripts/rename.sh)$')

for f in $files; do
  case "$f" in
    *.png|*.ico|*.icns|*.jpg) continue ;;
  esac
  perl -pi -e "s/\bapp\.(shared|client|ui|api|web|mobile)\b/${SLUG}.\1/g" "$f"
  perl -pi -e "s/\bapp\/(shared|client|ui|api|web|mobile)\b/${SLUG}\/\1/g" "$f"
  perl -pi -e "s/\bapp-(api|web|mobile|desktop)\b/${SLUG}-\1/g" "$f"
  perl -pi -e "s/com\.example\.app/com.example.${SLUG}/g" "$f"
done

for app in shared client ui; do
  if [ -d "packages/${app}/src/app" ]; then
    git mv "packages/${app}/src/app" "packages/${app}/src/${SLUG}" 2>/dev/null || mv "packages/${app}/src/app" "packages/${app}/src/${SLUG}"
  fi
done

for app in api web mobile; do
  if [ -d "apps/${app}/src/app" ]; then
    git mv "apps/${app}/src/app" "apps/${app}/src/${SLUG}" 2>/dev/null || mv "apps/${app}/src/app" "apps/${app}/src/${SLUG}"
  fi
done

if [ -d "apps/api/test/app" ]; then
  mv "apps/api/test/app" "apps/api/test/${SLUG}"
fi

echo "done. review with: git diff --stat"
