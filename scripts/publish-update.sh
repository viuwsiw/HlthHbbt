#!/usr/bin/env bash
# Публикация обновления для автообновления в приложении.
#   1) собирает release APK и кладёт его в dist/HLTH-HBTy-<versionCode>.apk;
#   2) коммитит APK отдельным коммитом;
#   3) пишет update.json со ссылкой на APK по хешу этого коммита (неизменяемая ссылка —
#      CDN GitHub не может отдать по ней старый файл) и коммитит его.
# После запуска остаётся сделать git push.
# Использование:
#   scripts/publish-update.sh "Что нового"          — релиз (update.json, канал для всех)
#   scripts/publish-update.sh --beta "Что нового"   — бета (update-beta.json, только бета-канал)
set -euo pipefail
cd "$(dirname "$0")/.."

MANIFEST=update.json
KIND="релиз"
if [ "${1:-}" = "--beta" ]; then
  MANIFEST=update-beta.json
  KIND="бета"
  shift
fi

TRAILER=$'\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\nClaude-Session: https://claude.ai/code/session_01WQ7917RUREKS86ExXNkarV'

./gradlew assembleRelease -q
APK=app/build/outputs/apk/release/app-release.apk
CODE=$(grep -oP 'versionCode = \K[0-9]+' app/build.gradle.kts)
NAME=$(grep -oP 'versionName = "\K[^"]+' app/build.gradle.kts)

# Оставляем только APK, на которые ссылается другой канал; свой старый APK удаляем.
OTHER=$([ "$MANIFEST" = update.json ] && echo update-beta.json || echo update.json)
KEEP=$(python3 -c "import json,sys,os;f='$OTHER';print(os.path.basename(json.load(open(f))['apkUrl']) if os.path.exists(f) else '')" 2>/dev/null || true)
mkdir -p dist
for f in dist/*.apk; do
  [ -e "$f" ] || continue
  [ "$(basename "$f")" = "$KEEP" ] && continue
  git rm -q --ignore-unmatch "$f" >/dev/null 2>&1 || true
  rm -f "$f"
done
OUT="dist/HLTH-HBTy-$CODE.apk"
cp "$APK" "$OUT"
SIZE=$(stat -c %s "$OUT")

git add -A dist
git commit -q -m "dist: APK $NAME ($CODE, $KIND)$TRAILER"
SHA=$(git rev-parse HEAD)

python3 - "$CODE" "$NAME" "$SIZE" "$SHA" "$OUT" "${1:-}" "$MANIFEST" <<'PY'
import json, sys
code, name, size, sha, out, notes, manifest = sys.argv[1:8]
json.dump({
    "versionCode": int(code),
    "versionName": name,
    "apkUrl": f"https://raw.githubusercontent.com/viuwsiw/HlthHbbt/{sha}/{out}",
    "size": int(size),
    "changelog": notes.replace("\\n", "\n"),
}, open(manifest, "w", encoding="utf-8"), ensure_ascii=False, indent=2)
PY
git add "$MANIFEST"
git commit -q -m "Publish $KIND $NAME ($CODE)$TRAILER"
echo "Готово ($KIND): $NAME ($CODE) → $OUT @ ${SHA:0:7}, $MANIFEST. Осталось: git push."
