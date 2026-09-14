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
  perl -pi -e "s/\bapp\.(shared|api-sdk|app|api|web|mobile)\b/${SLUG}.\1/g" "$f"
  perl -pi -e "s/\bapp\/(shared|api-sdk|app|api|web|mobile)\b/${SLUG}\/\1/g" "$f"
  perl -pi -e "s/\bapp-(api|web|mobile|desktop)\b/${SLUG}-\1/g" "$f"
  perl -pi -e "s/com\.example\.app/com.example.${SLUG}/g" "$f"
done

for dir in packages/*/src packages/*/test apps/*/src apps/*/test; do
  if [ -d "${dir}/app" ]; then
    git mv "${dir}/app" "${dir}/${SLUG}" 2>/dev/null || mv "${dir}/app" "${dir}/${SLUG}"
  fi
done

echo "done. review with: git diff --stat"
