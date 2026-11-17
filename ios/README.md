# HLTH HBTy для iOS

Нативное приложение на SwiftUI (iOS 17+, на iOS 26 — системное Liquid Glass).
Тот же сервер и протокол, что у Android: аккаунт, облачная копия и сквозное шифрование совместимы.

## Сборка IPA
Собирается в GitHub Actions (`.github/workflows/ios.yml`) на macOS при каждом пуше в `ios/`.
Готовый файл коммитится в `dist/ios/HLTH-HBTy-<версия>-ios.ipa`.

Локально на Mac: `brew install xcodegen && cd ios && xcodegen generate && open HLTH.xcodeproj`.

## Установка на iPhone
IPA не подписан сертификатом Apple — его подписывает инструмент установки вашим Apple ID:
- **Sideloadly** (Windows/macOS) или **AltStore**: выберите IPA, войдите Apple ID, установите.
  С бесплатным Apple ID приложение нужно переподписывать раз в 7 дней.
- **TrollStore** (если поддерживается вашей версией iOS) — без ограничений.
После установки: Настройки → Основные → VPN и управление устройством → доверять разработчику.
