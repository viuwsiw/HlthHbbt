package main

import (
	"io"
	"net/http"
	"strconv"
	"time"
)

// Облачная копия: зашифрована на устройстве ключом из пароля пользователя — сервер хранит
// непрозрачный блоб и не может его прочитать.
const MaxBackupBytes = 8 << 20

func (s *Server) putBackup(w http.ResponseWriter, r *http.Request, u ctxUser) {
	data, err := io.ReadAll(http.MaxBytesReader(w, r.Body, MaxBackupBytes))
	if err != nil {
		fail(w, 413, "too_large", "Резервная копия больше 8 МБ")
		return
	}
	if len(data) < 16 {
		fail(w, 400, "bad_backup", "Пустая копия")
		return
	}
	now := time.Now().Unix()
	_, err = s.db.Exec(`INSERT INTO backups(user_id, data, updated_at) VALUES(?,?,?)
		ON CONFLICT(user_id) DO UPDATE SET data=excluded.data, updated_at=excluded.updated_at`, u.ID, data, now)
	if err != nil {
		fail(w, 500, "internal", "Ошибка сервера")
		return
	}
	writeJSON(w, 200, map[string]any{"ok": true, "updatedAt": now})
}

func (s *Server) getBackup(w http.ResponseWriter, r *http.Request, u ctxUser) {
	var data []byte
	var updated int64
	if s.db.QueryRow(`SELECT data, updated_at FROM backups WHERE user_id=?`, u.ID).Scan(&data, &updated) != nil {
		fail(w, 404, "no_backup", "Копии ещё нет")
		return
	}
	w.Header().Set("Content-Type", "application/octet-stream")
	w.Header().Set("X-Updated-At", strconv.FormatInt(updated, 10))
	_, _ = w.Write(data)
}
