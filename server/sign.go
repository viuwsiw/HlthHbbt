package main

import (
	"bytes"
	"context"
	"crypto/rand"
	"encoding/base64"
	"encoding/hex"
	"fmt"
	"html"
	"io"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
	"regexp"
	"strings"
	"sync"
	"time"
)

// Подпись обновлений iOS на сервере: пользователь загружает свой .p12 и .mobileprovision,
// сервер скачивает IPA из GitHub, подписывает его zsign и отдаёт по itms-services
// (установка «по воздуху» работает только по HTTPS — его даёт Caddy).

const signTTL = time.Hour

var (
	allowedIPAPrefixes = []string{
		"https://github.com/viuwsiw/HlthHbbt/raw/",
		"https://raw.githubusercontent.com/viuwsiw/HlthHbbt/",
	}
	bundleIDRe = regexp.MustCompile(`^[A-Za-z0-9.\-]{3,200}$`)
	versionRe  = regexp.MustCompile(`^[0-9A-Za-z.\-]{1,32}$`)
	signMu     sync.Mutex
)

func (s *Server) signDir(uid int64) string {
	return filepath.Join(filepath.Dir(s.mediaDir), "signing", fmt.Sprint(uid))
}
func (s *Server) otaDir() string { return filepath.Join(filepath.Dir(s.mediaDir), "ota") }

// plistString достаёт значение <key>k</key><string>v</string> из mobileprovision (внутри CMS лежит XML-plist).
func plistString(data []byte, key string) string {
	re := regexp.MustCompile(`<key>` + regexp.QuoteMeta(key) + `</key>\s*<string>([^<]*)</string>`)
	if m := re.FindSubmatch(data); m != nil {
		return html.UnescapeString(string(m[1]))
	}
	return ""
}

func plistDate(data []byte, key string) string {
	re := regexp.MustCompile(`<key>` + regexp.QuoteMeta(key) + `</key>\s*<date>([^<]*)</date>`)
	if m := re.FindSubmatch(data); m != nil {
		return string(m[1])
	}
	return ""
}

type profileInfo struct {
	Name     string `json:"name"`
	AppID    string `json:"appId"`
	Team     string `json:"team"`
	Expires  string `json:"expires"`
	Wildcard bool   `json:"wildcard"`
}

func parseProfile(data []byte) profileInfo {
	ent := data
	if i := bytes.Index(data, []byte("<key>Entitlements</key>")); i >= 0 {
		ent = data[i:]
	}
	appID := plistString(ent, "application-identifier")
	return profileInfo{
		Name: plistString(data, "Name"), AppID: appID, Team: plistString(data, "TeamName"),
		Expires: plistDate(data, "ExpirationDate"), Wildcard: strings.HasSuffix(appID, ".*") || strings.HasSuffix(appID, "*"),
	}
}

func (s *Server) getSigning(w http.ResponseWriter, r *http.Request, u ctxUser) {
	prov, err := os.ReadFile(filepath.Join(s.signDir(u.ID), "profile.mobileprovision"))
	if err != nil {
		writeJSON(w, 200, map[string]any{"configured": false})
		return
	}
	writeJSON(w, 200, map[string]any{"configured": true, "profile": parseProfile(prov)})
}

func (s *Server) putSigning(w http.ResponseWriter, r *http.Request, u ctxUser) {
	var req struct {
		P12       string `json:"p12"`
		Password  string `json:"password"`
		Provision string `json:"provision"`
	}
	if !readJSON(w, r, &req) {
		return
	}
	p12, err1 := base64.StdEncoding.DecodeString(req.P12)
	prov, err2 := base64.StdEncoding.DecodeString(req.Provision)
	if err1 != nil || err2 != nil || len(p12) == 0 || len(prov) == 0 {
		fail(w, 400, "bad_files", "Нужны файлы .p12 и .mobileprovision")
		return
	}
	info := parseProfile(prov)
	if info.AppID == "" {
		fail(w, 400, "bad_profile", "Это не профиль .mobileprovision")
		return
	}
	dir := s.signDir(u.ID)
	if err := os.MkdirAll(dir, 0o700); err != nil {
		fail(w, 500, "io", "Не удалось сохранить")
		return
	}
	_ = os.WriteFile(filepath.Join(dir, "cert.p12"), p12, 0o600)
	_ = os.WriteFile(filepath.Join(dir, "password"), []byte(req.Password), 0o600)
	_ = os.WriteFile(filepath.Join(dir, "profile.mobileprovision"), prov, 0o600)
	// Проверка: zsign умеет проверять сертификат и пароль.
	if out, err := exec.Command("zsign", "-C", "-k", filepath.Join(dir, "cert.p12"), "-p", req.Password).CombinedOutput(); err != nil {
		s.logf("zsign check %s: %v %s", u.Username, err, out)
		if strings.Contains(strings.ToLower(string(out)), "password") || strings.Contains(strings.ToLower(string(out)), "pkcs12") {
			_ = os.RemoveAll(dir)
			fail(w, 400, "bad_password", "Не удалось открыть .p12 — проверьте пароль")
			return
		}
	}
	writeJSON(w, 200, map[string]any{"configured": true, "profile": info})
}

func (s *Server) deleteSigning(w http.ResponseWriter, r *http.Request, u ctxUser) {
	_ = os.RemoveAll(s.signDir(u.ID))
	writeJSON(w, 200, map[string]any{"ok": true})
}

func randomToken() string {
	b := make([]byte, 16)
	_, _ = rand.Read(b)
	return hex.EncodeToString(b)
}

func (s *Server) signIPA(w http.ResponseWriter, r *http.Request, u ctxUser) {
	var req struct {
		IpaURL   string `json:"ipaUrl"`
		BundleID string `json:"bundleId"`
		Version  string `json:"version"`
		Title    string `json:"title"`
	}
	if !readJSON(w, r, &req) {
		return
	}
	ok := false
	for _, p := range allowedIPAPrefixes {
		if strings.HasPrefix(req.IpaURL, p) && !strings.Contains(req.IpaURL, "..") {
			ok = true
		}
	}
	if !ok || !bundleIDRe.MatchString(req.BundleID) || !versionRe.MatchString(req.Version) {
		fail(w, 400, "bad_request", "Некорректный запрос")
		return
	}
	dir := s.signDir(u.ID)
	pw, err := os.ReadFile(filepath.Join(dir, "password"))
	prov, err2 := os.ReadFile(filepath.Join(dir, "profile.mobileprovision"))
	if err != nil || err2 != nil {
		fail(w, 400, "no_signing", "Сначала загрузите .p12 и .mobileprovision")
		return
	}
	info := parseProfile(prov)

	if !signMu.TryLock() {
		fail(w, 429, "busy", "Сервер уже подписывает обновление — попробуйте через минуту")
		return
	}
	defer signMu.Unlock()

	ctx, cancel := context.WithTimeout(r.Context(), 4*time.Minute)
	defer cancel()
	work, err := os.MkdirTemp("", "sign-*")
	if err != nil {
		fail(w, 500, "io", "Нет места на сервере")
		return
	}
	defer os.RemoveAll(work)

	// Скачать IPA
	dreq, _ := http.NewRequestWithContext(ctx, "GET", req.IpaURL, nil)
	resp, err := http.DefaultClient.Do(dreq)
	if err != nil || resp.StatusCode != 200 {
		if resp != nil {
			resp.Body.Close()
		}
		fail(w, 502, "download", "Не удалось скачать обновление с GitHub")
		return
	}
	in := filepath.Join(work, "in.ipa")
	f, _ := os.Create(in)
	_, err = io.Copy(f, io.LimitReader(resp.Body, 300<<20))
	resp.Body.Close()
	f.Close()
	if err != nil {
		fail(w, 502, "download", "Не удалось скачать обновление")
		return
	}

	_ = os.MkdirAll(s.otaDir(), 0o700)
	s.cleanupOTA()
	token := randomToken()
	out := filepath.Join(s.otaDir(), token+".ipa")
	args := []string{"-q", "-k", filepath.Join(dir, "cert.p12"), "-p", string(pw), "-m", filepath.Join(dir, "profile.mobileprovision"), "-o", out}
	bundleID := req.BundleID
	if !info.Wildcard {
		// Профиль на одно приложение: идентификатор из профиля, расширения (Live Activity) убираем — для них нужен свой профиль.
		if i := strings.Index(info.AppID, "."); i >= 0 {
			bundleID = info.AppID[i+1:]
		}
		args = append(args, "-E")
	}
	args = append(args, "-b", bundleID, in)
	cmd := exec.CommandContext(ctx, "zsign", args...)
	if o, err := cmd.CombinedOutput(); err != nil {
		s.logf("zsign %s: %v %s", u.Username, err, o)
		fail(w, 500, "sign_failed", "Подпись не удалась: проверьте сертификат и профиль")
		return
	}

	base := "https://" + r.Host
	ipaURL := base + "/v1/ota/" + token + "/app.ipa"
	title := req.Title
	if title == "" {
		title = "HLTH HBTy"
	}
	manifest := fmt.Sprintf(`<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0"><dict><key>items</key><array><dict>
<key>assets</key><array><dict><key>kind</key><string>software-package</string><key>url</key><string>%s</string></dict></array>
<key>metadata</key><dict><key>bundle-identifier</key><string>%s</string><key>bundle-version</key><string>%s</string><key>kind</key><string>software</string><key>title</key><string>%s</string></dict>
</dict></array></dict></plist>`, html.EscapeString(ipaURL), html.EscapeString(bundleID), html.EscapeString(req.Version), html.EscapeString(title))
	_ = os.WriteFile(filepath.Join(s.otaDir(), token+".plist"), []byte(manifest), 0o600)
	manifestURL := base + "/v1/ota/" + token + "/manifest.plist"
	s.logf("signed iOS update %s for %s (%s)", req.Version, u.Username, bundleID)
	writeJSON(w, 200, map[string]any{
		"manifestUrl":       manifestURL,
		"installUrl":        "itms-services://?action=download-manifest&url=" + manifestURL,
		"bundleId":          bundleID,
		"extensionsRemoved": !info.Wildcard,
	})
}

var otaPathRe = regexp.MustCompile(`^[0-9a-f]{32}$`)

// Публичная раздача подписанного IPA и манифеста (без авторизации — iOS качает сама); живут час.
func (s *Server) ota(w http.ResponseWriter, r *http.Request) {
	token := r.PathValue("token")
	file := r.PathValue("file")
	if !otaPathRe.MatchString(token) {
		http.NotFound(w, r)
		return
	}
	var path, ctype string
	switch file {
	case "app.ipa":
		path, ctype = filepath.Join(s.otaDir(), token+".ipa"), "application/octet-stream"
	case "manifest.plist":
		path, ctype = filepath.Join(s.otaDir(), token+".plist"), "text/xml"
	default:
		http.NotFound(w, r)
		return
	}
	st, err := os.Stat(path)
	if err != nil || time.Since(st.ModTime()) > signTTL {
		http.NotFound(w, r)
		return
	}
	w.Header().Set("Content-Type", ctype)
	http.ServeFile(w, r, path)
}

func (s *Server) cleanupOTA() {
	entries, _ := os.ReadDir(s.otaDir())
	for _, e := range entries {
		if info, err := e.Info(); err == nil && time.Since(info.ModTime()) > signTTL {
			_ = os.Remove(filepath.Join(s.otaDir(), e.Name()))
		}
	}
}
