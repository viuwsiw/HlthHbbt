package main

import (
	"crypto/sha256"
	"crypto/subtle"
	"encoding/base64"
	"net/http"
	"strconv"
	"strings"
	"time"
	"unicode/utf8"

	"golang.org/x/crypto/bcrypt"
	"golang.org/x/crypto/pbkdf2"
)

// checkPassword поддерживает bcrypt и перенесённые с устройства хеши PBKDF2-HMAC-SHA256
// в формате pbkdf2$<итерации>$<соль base64>$<хеш base64>.
func checkPassword(stored, password string) bool {
	if strings.HasPrefix(stored, "pbkdf2$") {
		parts := strings.Split(stored, "$")
		if len(parts) != 4 {
			return false
		}
		iter, err := strconv.Atoi(parts[1])
		if err != nil || iter < 10_000 || iter > 1_000_000 {
			return false
		}
		salt, err1 := base64.StdEncoding.DecodeString(parts[2])
		want, err2 := base64.StdEncoding.DecodeString(parts[3])
		if err1 != nil || err2 != nil || len(want) == 0 {
			return false
		}
		got := pbkdf2.Key([]byte(password), salt, iter, len(want), sha256.New)
		return subtle.ConstantTimeCompare(got, want) == 1
	}
	return bcrypt.CompareHashAndPassword([]byte(stored), []byte(password)) == nil
}

type importReq struct {
	Username    string `json:"username"`
	DisplayName string `json:"displayName"`
	Hash        string `json:"hash"` // PBKDF2, base64
	Salt        string `json:"salt"` // base64
	Iterations  int    `json:"iterations"`
	DeviceID    string `json:"deviceId"`
}

// importAccount переносит аккаунт, созданный на устройстве до появления сервера.
// Пароль не передаётся — только его хеш; как и регистрация, работает лишь для свободного логина.
func (s *Server) importAccount(w http.ResponseWriter, r *http.Request) {
	var req importReq
	if !readJSON(w, r, &req) {
		return
	}
	req.Username = strings.ToLower(strings.TrimSpace(req.Username))
	if !usernameRe.MatchString(req.Username) {
		fail(w, 400, "bad_username", "Некорректный логин")
		return
	}
	if reservedUsername(req.Username) {
		fail(w, 409, "username_taken", "Логин уже занят на сервере")
		return
	}
	hash, err1 := base64.StdEncoding.DecodeString(req.Hash)
	salt, err2 := base64.StdEncoding.DecodeString(req.Salt)
	if err1 != nil || err2 != nil || len(hash) < 16 || len(salt) < 8 || req.Iterations < 10_000 || req.Iterations > 1_000_000 {
		fail(w, 400, "bad_hash", "Некорректные данные аккаунта")
		return
	}
	name := strings.TrimSpace(req.DisplayName)
	if name == "" || utf8.RuneCountInString(name) > 40 {
		name = req.Username
	}
	if !s.checkAccountLimit(w, req.DeviceID) {
		return
	}
	stored := "pbkdf2$" + strconv.Itoa(req.Iterations) + "$" + base64.StdEncoding.EncodeToString(salt) + "$" + base64.StdEncoding.EncodeToString(hash)
	res, err := s.db.Exec(`INSERT INTO users(username, display_name, pass_hash, created_at, device_id) VALUES(?,?,?,?,?)`,
		req.Username, name, stored, time.Now().Unix(), req.DeviceID)
	if err != nil {
		if strings.Contains(err.Error(), "UNIQUE") {
			fail(w, 409, "username_taken", "Логин уже занят на сервере")
			return
		}
		fail(w, 500, "internal", "Ошибка сервера")
		return
	}
	id, _ := res.LastInsertId()
	s.recordRegistration(r, req.DeviceID)
	s.issueToken(w, id, req.Username)
}

func (s *Server) changePassword(w http.ResponseWriter, r *http.Request, u ctxUser) {
	var req struct {
		Current string `json:"current"`
		New     string `json:"new"`
	}
	if !readJSON(w, r, &req) {
		return
	}
	var stored string
	_ = s.db.QueryRow(`SELECT pass_hash FROM users WHERE id=?`, u.ID).Scan(&stored)
	if !checkPassword(stored, req.Current) {
		fail(w, 401, "bad_credentials", "Текущий пароль неверный")
		return
	}
	if utf8.RuneCountInString(req.New) < 6 {
		fail(w, 400, "bad_password", "Пароль — минимум 6 символов")
		return
	}
	h, err := bcrypt.GenerateFromPassword([]byte(req.New), 11)
	if err != nil {
		fail(w, 500, "internal", "Ошибка сервера")
		return
	}
	_, _ = s.db.Exec(`UPDATE users SET pass_hash=? WHERE id=?`, string(h), u.ID)
	// Остальные сессии завершаем, текущую оставляем.
	_, _ = s.db.Exec(`DELETE FROM sessions WHERE user_id=? AND token_hash<>?`, u.ID, hashToken(bearer(r)))
	writeJSON(w, 200, map[string]any{"ok": true})
}

func (s *Server) limits(w http.ResponseWriter, r *http.Request, u ctxUser) {
	var sent int
	var qStart, qUsed int64
	_ = s.db.QueryRow(`SELECT msg_count, quota_start, quota_used FROM users WHERE id=?`, u.ID).Scan(&sent, &qStart, &qUsed)
	if time.Now().Unix()-qStart >= 86400 {
		qUsed, qStart = 0, 0
	}
	writeJSON(w, 200, map[string]any{
		"messagesSent": sent, "messagesMax": MaxMessagesPerUser, "messageChars": MaxMessageChars,
		"mediaFileMax": MaxMediaBytes, "mediaDailyMax": DailyMediaBytes, "mediaUsed": qUsed,
		"mediaWindowStart": qStart,
	})
}
