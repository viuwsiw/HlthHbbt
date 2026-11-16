#!/usr/bin/env bash
# Собирает APK, кладёт его в dist/ и обновляет update.json —
# установленные приложения найдут новую версию в GitHub.
# Использование: scripts/publish-update.sh "Что нового: строка 1\nстрока 2"
set -euo pipefail
cd "$(dirname "$0")/.."

./gradlew assembleDebug -q
APK=app/build/outputs/apk/debug/app-debug.apk
CODE=$(grep -oP 'versionCode = \K[0-9]+' app/build.gradle.kts)
NAME=$(grep -oP 'versionName = "\K[^"]+' app/build.gradle.kts)
cp "$APK" dist/HLTH-HBTy.apk
SIZE=$(stat -c %s dist/HLTH-HBTy.apk)

python3 - "$CODE" "$NAME" "$SIZE" "${1:-}" <<'PY'
import json, sys
code, name, size, notes = sys.argv[1:5]
json.dump({
    "versionCode": int(code),
    "versionName": name,
    "apkUrl": "https://raw.githubusercontent.com/viuwsiw/HlthHbbt/HEAD/dist/HLTH-HBTy.apk",
    "size": int(size),
    "changelog": notes.replace("\\n", "\n"),
}, open("update.json", "w", encoding="utf-8"), ensure_ascii=False, indent=2)
PY
echo "Готово: $NAME ($CODE). Закоммитьте dist/HLTH-HBTy.apk и update.json."
