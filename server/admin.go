package main

import (
	"crypto/rand"
	"encoding/base64"
	"net/http"
	"os"
	"path/filepath"
	"runtime"
	"strconv"
	"strings"
	"syscall"
	"time"

	"golang.org/x/crypto/bcrypt"
)

var startedAt = time.Now()

func (s *Server) isAdmin(u ctxUser) bool {
	for _, a := range strings.Split(env("ADMIN_USERS", "developer"), ",") {
		if strings.TrimSpace(a) == u.Username {
			return true
		}
	}
	return false
}

func (s *Server) admin(h func(http.ResponseWriter, *http.Request, ctxUser)) http.HandlerFunc {
	return s.auth(func(w http.ResponseWriter, r *http.Request, u ctxUser) {
		if !s.isAdmin(u) {
			fail(w, 403, "forbidden", "Нет доступа")
			return
		}
		h(w, r, u)
	})
}

// Зарезервированные логины нельзя зарегистрировать через API (developer создаётся на сервере).
func reservedUsername(u string) bool {
	for _, a := range strings.Split(env("ADMIN_USERS", "developer"), ",") {
		if strings.TrimSpace(a) == u {
			return true
		}
	}
	return u == "admin" || u == "root" || u == "support" || u == "hlthhbbty"
}

func (s *Server) adminStats(w http.ResponseWriter, r *http.Request, u ctxUser) {
	count := func(q string) int64 {
		var n int64
		_ = s.db.QueryRow(q).Scan(&n)
		return n
	}
	var ms runtime.MemStats
	runtime.ReadMemStats(&ms)
	var fs syscall.Statfs_t
	_ = syscall.Statfs(s.mediaDir, &fs)
	dbSize := fileSize(env("DB_PATH", "data/hlth.db")) + fileSize(env("DB_PATH", "data/hlth.db")+"-wal")
	mediaSize := count(`SELECT COALESCE(SUM(size),0) FROM media`)
	memTotal, memAvail := meminfo()
	writeJSON(w, 200, map[string]any{
		"uptimeSec":     int64(time.Since(startedAt).Seconds()),
		"goVersion":     runtime.Version(),
		"users":         count(`SELECT COUNT(*) FROM users`),
		"usersToday":    count(`SELECT COUNT(*) FROM users WHERE created_at > strftime('%s','now') - 86400`),
		"messages":      count(`SELECT COUNT(*) FROM messages`),
		"messagesToday": count(`SELECT COUNT(*) FROM messages WHERE created_at > (strftime('%s','now') - 86400) * 1000`),
		"mediaFiles":    count(`SELECT COUNT(*) FROM media`),
		"mediaBytes":    mediaSize,
		"backups":       count(`SELECT COUNT(*) FROM backups`),
		"sessions":      count(`SELECT COUNT(*) FROM sessions`),
		"online":        s.hub.onlineCount(),
		"dbBytes":       dbSize,
		"diskTotal":     int64(fs.Blocks) * int64(fs.Bsize),
		"diskFree":      int64(fs.Bavail) * int64(fs.Bsize),
		"procMemBytes":  ms.Sys,
		"heapBytes":     ms.HeapAlloc,
		"goroutines":    runtime.NumGoroutine(),
		"sysMemTotal":   memTotal,
		"sysMemAvail":   memAvail,
		"load":          loadavg(),
		"cpus":          runtime.NumCPU(),
		"limits": map[string]any{
			"accountsPerDevice": s.maxAccountsDevice, "accountsTotal": s.maxAccountsTotal,
			"mediaTtlDays": s.mediaTTLDays, "messagesPer5s": 2,
		},
	})
}

func (s *Server) adminLogs(w http.ResponseWriter, r *http.Request, u ctxUser) {
	n, _ := strconv.Atoi(r.URL.Query().Get("n"))
	if n <= 0 || n > 500 {
		n = 200
	}
	writeJSON(w, 200, map[string]any{"lines": s.logs.Tail(n)})
}

// adminWipe — экстренный сброс ВСЕХ данных сервера (аккаунты, сообщения, медиа, копии).
func (s *Server) adminWipe(w http.ResponseWriter, r *http.Request, u ctxUser) {
	var req struct {
		Confirm string `json:"confirm"`
	}
	if !readJSON(w, r, &req) {
		return
	}
	if req.Confirm != "СБРОСИТЬ ВСЁ" {
		fail(w, 400, "confirm", "Нужно подтверждение")
		return
	}
	s.logf("ADMIN WIPE by @%s", u.Username)
	for _, q := range []string{
		`DELETE FROM media_access`, `DELETE FROM media`, `DELETE FROM group_cts`, `DELETE FROM listened`, `DELETE FROM reads`, `DELETE FROM clears`, `DELETE FROM group_members`, `DELETE FROM chat_groups`, `DELETE FROM avatars`, `DELETE FROM messages`, `DELETE FROM contacts`,
		`DELETE FROM backups`, `DELETE FROM registrations`, `DELETE FROM sessions`,
		`DELETE FROM users WHERE username NOT IN ('` + strings.ReplaceAll(env("ADMIN_USERS", "developer"), ",", "','") + `')`,
	} {
		_, _ = s.db.Exec(q)
	}
	entries, _ := os.ReadDir(s.mediaDir)
	for _, e := range entries {
		_ = os.Remove(filepath.Join(s.mediaDir, e.Name()))
	}
	_, _ = s.db.Exec(`VACUUM`)
	s.hub.closeAll()
	writeJSON(w, 200, map[string]any{"ok": true})
}

// createAdmin — CLI: создать аккаунт администратора со случайным паролем (вызывает install.sh).
func createAdmin(s *Server) {
	name := strings.TrimSpace(strings.Split(env("ADMIN_USERS", "developer"), ",")[0])
	var id int64
	if s.db.QueryRow(`SELECT id FROM users WHERE username=?`, name).Scan(&id) == nil {
		println("Аккаунт @" + name + " уже существует")
		return
	}
	raw := make([]byte, 12)
	_, _ = rand.Read(raw)
	password := base64.RawURLEncoding.EncodeToString(raw)
	hash, _ := bcrypt.GenerateFromPassword([]byte(password), 11)
	_, err := s.db.Exec(`INSERT INTO users(username, display_name, pass_hash, created_at, device_id) VALUES(?,?,?,?,?)`,
		name, "Developer", string(hash), time.Now().Unix(), "server")
	if err != nil {
		println("Ошибка: " + err.Error())
		os.Exit(1)
	}
	println("Создан аккаунт администратора:")
	println("    логин:  " + name)
	println("    пароль: " + password)
	println("Войдите им в приложении и смените пароль (Профиль → Сменить пароль).")
}

func fileSize(p string) int64 {
	if st, err := os.Stat(p); err == nil {
		return st.Size()
	}
	return 0
}

func meminfo() (total, avail int64) {
	b, err := os.ReadFile("/proc/meminfo")
	if err != nil {
		return 0, 0
	}
	for _, line := range strings.Split(string(b), "\n") {
		f := strings.Fields(line)
		if len(f) < 2 {
			continue
		}
		v, _ := strconv.ParseInt(f[1], 10, 64)
		switch f[0] {
		case "MemTotal:":
			total = v * 1024
		case "MemAvailable:":
			avail = v * 1024
		}
	}
	return
}

func loadavg() []float64 {
	b, err := os.ReadFile("/proc/loadavg")
	if err != nil {
		return nil
	}
	f := strings.Fields(string(b))
	out := []float64{}
	for i := 0; i < 3 && i < len(f); i++ {
		v, _ := strconv.ParseFloat(f[i], 64)
		out = append(out, v)
	}
	return out
}
