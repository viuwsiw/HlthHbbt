package main

import (
	"net/http"
	"net/url"
	"os"
	"strings"
)

// VPN: Xray (VLESS + WebSocket) за Caddy на том же 443 порту и домене.
// Ключ выдаётся вошедшим пользователям (VPN_USERS: "*" — всем, или список логинов через запятую).
func (s *Server) getVPN(w http.ResponseWriter, r *http.Request, u ctxUser) {
	uuid := os.Getenv("VPN_UUID")
	path := os.Getenv("VPN_PATH")
	domain := os.Getenv("DOMAIN")
	if uuid == "" || path == "" || domain == "" {
		writeJSON(w, 200, map[string]any{"enabled": false, "message": "VPN на сервере не настроен — обновите сервер командой установки"})
		return
	}
	allowed := env("VPN_USERS", "*")
	ok := allowed == "*"
	for _, n := range strings.Split(allowed, ",") {
		if strings.TrimSpace(strings.ToLower(n)) == u.Username {
			ok = true
		}
	}
	if !ok {
		writeJSON(w, 200, map[string]any{"enabled": false, "message": "VPN доступен только пользователям, которых добавил администратор"})
		return
	}
	q := url.Values{}
	q.Set("encryption", "none")
	q.Set("security", "tls")
	q.Set("sni", domain)
	q.Set("type", "ws")
	q.Set("host", domain)
	q.Set("path", path)
	key := "vless://" + uuid + "@" + domain + ":443?" + q.Encode() + "#" + url.PathEscape("HLTH VPN")
	writeJSON(w, 200, map[string]any{"enabled": true, "key": key, "host": domain, "port": 443, "path": path})
}
