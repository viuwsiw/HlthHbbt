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
# Reality (самый быстрый режим): ключи x25519 и shortId — один раз.
if [ -z "${REALITY_PRIVATE:-}" ]; then
  KEYS=$(docker run --rm teddysun/xray:latest xray x25519 2>/dev/null || true)
  REALITY_PRIVATE=$(echo "$KEYS" | grep -i 'private' | head -1 | awk -F': *' '{print $2}' | tr -d ' \r')
  REALITY_PUBLIC=$(echo "$KEYS" | grep -iE 'public|password' | head -1 | awk -F': *' '{print $2}' | tr -d ' \r')
  REALITY_SID=$(head -c 8 /dev/urandom | od -An -tx1 | tr -d ' \n')
  if [ -n "$REALITY_PRIVATE" ] && [ -n "$REALITY_PUBLIC" ]; then
    { echo "REALITY_PRIVATE=$REALITY_PRIVATE"; echo "REALITY_PUBLIC=$REALITY_PUBLIC"; echo "REALITY_SID=$REALITY_SID"; } >> "$VPNENV"
  fi
fi
REALITY_SNI=${REALITY_SNI:-www.microsoft.com}
{
  echo "DOMAIN=$DOMAIN"
  echo "VPN_UUID=$VPN_UUID"
  echo "VPN_PATH=$VPN_PATH"
  echo "VPN_USERS=${VPN_USERS:-*}"
  echo "REALITY_PUBLIC=${REALITY_PUBLIC:-}"
  echo "REALITY_SID=${REALITY_SID:-}"
  echo "REALITY_SNI=$REALITY_SNI"
} > "$DIR/server/deploy/.env"
REALITY_INBOUND=""
if [ -n "${REALITY_PRIVATE:-}" ]; then
REALITY_INBOUND=$(cat <<RJSON
, {
    "listen": "0.0.0.0", "port": 8443, "protocol": "vless",
    "settings": { "clients": [{ "id": "$VPN_UUID", "flow": "xtls-rprx-vision" }], "decryption": "none" },
    "streamSettings": {
      "network": "tcp", "security": "reality",
      "sockopt": { "tcpFastOpen": true },
      "realitySettings": {
        "dest": "$REALITY_SNI:443", "serverNames": ["$REALITY_SNI"],
        "privateKey": "${REALITY_PRIVATE:-}", "shortIds": ["${REALITY_SID:-}"]
      }
    },
    "sniffing": { "enabled": true, "destOverride": ["http", "tls"], "routeOnly": true }
  }
RJSON
)
fi
cat > "$DIR/server/deploy/xray.json" <<XRAY
{
  "log": { "loglevel": "warning" },
  "inbounds": [{
    "listen": "0.0.0.0", "port": 10000, "protocol": "vless",
    "settings": { "clients": [{ "id": "$VPN_UUID" }], "decryption": "none" },
    "streamSettings": { "network": "ws", "wsSettings": { "path": "$VPN_PATH" } },
    "sniffing": { "enabled": true, "destOverride": ["http", "tls"], "routeOnly": true }
  }${REALITY_INBOUND}],
  "outbounds": [{ "protocol": "freedom", "settings": { "domainStrategy": "UseIPv4" } }]
}
XRAY

if command -v ufw >/dev/null && ufw status | grep -q active; then
  ufw allow 80/tcp >/dev/null; ufw allow 443/tcp >/dev/null; ufw allow 8443/tcp >/dev/null
fi

echo "==> Сеть: TCP BBR и большие буферы (скорость VPN на дальних каналах)"
cat > /etc/sysctl.d/99-hlth-net.conf <<SYSCTL
net.core.default_qdisc=fq
net.ipv4.tcp_congestion_control=bbr
net.core.rmem_max=67108864
net.core.wmem_max=67108864
net.ipv4.tcp_rmem=4096 131072 33554432
net.ipv4.tcp_wmem=4096 131072 33554432
net.ipv4.tcp_fastopen=3
net.ipv4.tcp_mtu_probing=1
net.ipv4.tcp_slow_start_after_idle=0
SYSCTL
modprobe tcp_bbr 2>/dev/null || true
sysctl -q --system >/dev/null 2>&1 || true
echo "    congestion control: $(sysctl -n net.ipv4.tcp_congestion_control)"

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
echo "VPN — ключи (вставьте в Streisand / Hiddify / V2Box / v2rayNG):"
P=$(printf '%s' "$VPN_PATH" | sed 's#/#%2F#g')
echo
echo "  Быстрый (Reality, порт 8443):"
if [ -n "${REALITY_PUBLIC:-}" ]; then
  echo "  vless://$VPN_UUID@$DOMAIN:8443?encryption=none&flow=xtls-rprx-vision&security=reality&sni=$REALITY_SNI&fp=chrome&pbk=$REALITY_PUBLIC&sid=$REALITY_SID&type=tcp#HLTH%20Reality"
else
  echo "  (не удалось создать ключи Reality — запустите установку ещё раз)"
fi
echo
echo "  Запасной (WebSocket + TLS, порт 443):"
echo "  vless://$VPN_UUID@$DOMAIN:443?encryption=none&security=tls&sni=$DOMAIN&type=ws&host=$DOMAIN&path=$P#HLTH%20VPN"
echo
echo "    Кому доступен: VPN_USERS в $VPNENV (* — всем; или логины через запятую)."
