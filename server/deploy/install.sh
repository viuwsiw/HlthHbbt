#!/usr/bin/env bash
# Установка сервера мессенджера HLTH HBTy на чистый Ubuntu.
# Запуск на сервере (под root):
#   curl -fsSL https://raw.githubusercontent.com/viuwsiw/HlthHbbt/claude/quirky-goodall-is99zk/server/deploy/install.sh | bash
# Свой домен (необязательно):  ... | DOMAIN=chat.example.com bash
set -euo pipefail
REPO="https://github.com/viuwsiw/HlthHbbt.git"
BRANCH="claude/quirky-goodall-is99zk"
DIR=/opt/hlthhbbty

[ "$(id -u)" -eq 0 ] || { echo "Запустите от root (sudo -i)"; exit 1; }

echo "==> Пакеты"
export DEBIAN_FRONTEND=noninteractive
apt-get update -qq
apt-get install -y -qq git curl ca-certificates >/dev/null
if ! command -v docker >/dev/null; then
  echo "==> Docker"
  curl -fsSL https://get.docker.com | sh >/dev/null
fi

echo "==> Код сервера"
if [ -d "$DIR/.git" ]; then
  git -C "$DIR" fetch -q --depth 1 origin "$BRANCH" && git -C "$DIR" reset -q --hard FETCH_HEAD
else
  git clone -q --depth 1 --branch "$BRANCH" --filter=blob:none --sparse "$REPO" "$DIR"
  git -C "$DIR" sparse-checkout set server
fi

if [ -z "${DOMAIN:-}" ]; then
  IP=$(curl -4 -fsS https://api.ipify.org || curl -4 -fsS https://ifconfig.me)
  DOMAIN="$(echo "$IP" | tr . -).sslip.io"
fi
# VPN: ключ (UUID) и секретный путь создаются один раз и сохраняются между обновлениями.
VPNENV="$DIR/vpn.env"
if [ ! -f "$VPNENV" ]; then
  echo "VPN_UUID=$(cat /proc/sys/kernel/random/uuid)" > "$VPNENV"
  echo "VPN_PATH=/$(head -c 12 /dev/urandom | od -An -tx1 | tr -d ' \n')" >> "$VPNENV"
  echo "VPN_USERS=*" >> "$VPNENV"
fi
# shellcheck disable=SC1090
. "$VPNENV"
{
  echo "DOMAIN=$DOMAIN"
  echo "VPN_UUID=$VPN_UUID"
  echo "VPN_PATH=$VPN_PATH"
  echo "VPN_USERS=${VPN_USERS:-*}"
} > "$DIR/server/deploy/.env"
cat > "$DIR/server/deploy/xray.json" <<XRAY
{
  "log": { "loglevel": "warning" },
  "inbounds": [{
    "listen": "0.0.0.0", "port": 10000, "protocol": "vless",
    "settings": { "clients": [{ "id": "$VPN_UUID" }], "decryption": "none" },
    "streamSettings": { "network": "ws", "wsSettings": { "path": "$VPN_PATH" } },
    "sniffing": { "enabled": true, "destOverride": ["http", "tls"] }
  }],
  "outbounds": [{ "protocol": "freedom", "settings": { "domainStrategy": "UseIPv4" } }]
}
XRAY

if command -v ufw >/dev/null && ufw status | grep -q active; then
  ufw allow 80/tcp >/dev/null; ufw allow 443/tcp >/dev/null
fi

echo "==> Запуск"
cd "$DIR/server/deploy"
docker compose up -d --build

echo -n "==> Проверка HTTPS"
for i in $(seq 1 30); do
  if curl -fsS "https://$DOMAIN/v1/health" >/dev/null 2>&1; then echo " — ok"; break; fi
  echo -n "."; sleep 3
done
echo
echo
echo "==> Аккаунт администратора"
sleep 2
docker compose exec -T api /hlth-server create-admin || true
echo
echo "Готово. Адрес сервера для приложения:"
echo "    https://$DOMAIN"
echo
echo "VPN (VLESS + WebSocket + TLS): ключ выдаётся в приложении (iOS → Профиль → Настройки → VPN)."
echo "    Кому доступен: VPN_USERS в $VPNENV (* — всем; или логины через запятую)."
