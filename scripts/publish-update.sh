#!/usr/bin/env bash
# Собирает APK, кладёт его в dist/ и обновляет update.json —
# установленные приложения найдут новую версию в GitHub.
# Использование: scripts/publish-update.sh "Что нового: строка 1\nстрока 2"
set -euo pipefail
cd "$(dirname "$0")/.."

./gradlew assembleRelease -q
APK=app/build/outputs/apk/release/app-release.apk
CODE=$(grep -oP 'versionCode = \K[0-9]+' app/build.gradle.kts)
NAME=$(grep -oP 'versionName = "\K[^"]+' app/build.gradle.kts)
# У каждой версии своё имя файла: новую ссылку CDN GitHub не мог закэшировать заранее.
git rm -q --ignore-unmatch dist/*.apk 2>/dev/null || true
rm -f dist/*.apk
OUT="dist/HLTH-HBTy-$CODE.apk"
cp "$APK" "$OUT"
SIZE=$(stat -c %s "$OUT")

python3 - "$CODE" "$NAME" "$SIZE" "${1:-}" <<'PY'
import json, sys
code, name, size, notes = sys.argv[1:5]
json.dump({
    "versionCode": int(code),
    "versionName": name,
    "apkUrl": "https://raw.githubusercontent.com/viuwsiw/HlthHbbt/HEAD/dist/HLTH-HBTy-" + code + ".apk",
    "size": int(size),
    "changelog": notes.replace("\\n", "\n"),
}, open("update.json", "w", encoding="utf-8"), ensure_ascii=False, indent=2)
PY
echo "Готово: $NAME ($CODE) → $OUT. Закоммитьте dist/ и update.json."
