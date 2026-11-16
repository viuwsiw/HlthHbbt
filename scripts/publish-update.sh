#!/usr/bin/env bash
# Публикация обновления для автообновления в приложении.
#   1) собирает release APK и кладёт его в dist/HLTH-HBTy-<versionCode>.apk;
#   2) коммитит APK отдельным коммитом;
#   3) пишет update.json со ссылкой на APK по хешу этого коммита (неизменяемая ссылка —
#      CDN GitHub не может отдать по ней старый файл) и коммитит его.
# После запуска остаётся сделать git push.
# Использование: scripts/publish-update.sh "Что нового: строка 1\nстрока 2"
set -euo pipefail
cd "$(dirname "$0")/.."

TRAILER=$'\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\nClaude-Session: https://claude.ai/code/session_01WQ7917RUREKS86ExXNkarV'

./gradlew assembleRelease -q
APK=app/build/outputs/apk/release/app-release.apk
CODE=$(grep -oP 'versionCode = \K[0-9]+' app/build.gradle.kts)
NAME=$(grep -oP 'versionName = "\K[^"]+' app/build.gradle.kts)

git rm -q --ignore-unmatch 'dist/*.apk' >/dev/null 2>&1 || true
mkdir -p dist
rm -f dist/*.apk
OUT="dist/HLTH-HBTy-$CODE.apk"
cp "$APK" "$OUT"
SIZE=$(stat -c %s "$OUT")

git add -A dist
git commit -q -m "dist: APK $NAME ($CODE)$TRAILER"
SHA=$(git rev-parse HEAD)

python3 - "$CODE" "$NAME" "$SIZE" "$SHA" "$OUT" "${1:-}" <<'PY'
import json, sys
code, name, size, sha, out, notes = sys.argv[1:7]
json.dump({
    "versionCode": int(code),
    "versionName": name,
    "apkUrl": f"https://raw.githubusercontent.com/viuwsiw/HlthHbbt/{sha}/{out}",
    "size": int(size),
    "changelog": notes.replace("\\n", "\n"),
}, open("update.json", "w", encoding="utf-8"), ensure_ascii=False, indent=2)
PY
git add update.json
git commit -q -m "Publish update $NAME ($CODE)$TRAILER"
echo "Готово: $NAME ($CODE) → $OUT @ ${SHA:0:7}. Осталось: git push."
