package main

import (
	"crypto/rand"
	"encoding/hex"
	"io"
	"log"
	"net/http"
	"os"
	"path/filepath"
	"strconv"
	"time"
)

// uploadMedia принимает уже зашифрованный на устройстве файл (сервер не знает ключа).
// Лимиты: 30 МБ на файл, 300 МБ за 24 часа с момента первой выгрузки в окне.
func (s *Server) uploadMedia(w http.ResponseWriter, r *http.Request, u ctxUser) {
	size := r.ContentLength
	limit := int64(MaxMediaBytes + 64*1024) // запас на служебные байты шифрования
	if size <= 0 {
		fail(w, 411, "length_required", "Нужен размер файла")
		return
	}
	if size > limit {
		fail(w, 413, "file_too_large", "Файл больше 30 МБ")
		return
	}
	now := time.Now().Unix()
	var qStart, qUsed int64
	_ = s.db.QueryRow(`SELECT quota_start, quota_used FROM users WHERE id=?`, u.ID).Scan(&qStart, &qUsed)
	if now-qStart >= 86400 {
		qStart, qUsed = now, 0 // новое окно начинается с этой выгрузки
	}
	if qUsed+size > DailyMediaBytes {
		left := time.Duration(86400-(now-qStart)) * time.Second
		fail(w, 429, "daily_quota", "Лимит 300 МБ в сутки исчерпан. Обновится через "+left.Round(time.Minute).String())
		return
	}
	raw := make([]byte, 16)
	_, _ = rand.Read(raw)
	id := hex.EncodeToString(raw)
	path := filepath.Join(s.mediaDir, id)
	f, err := os.OpenFile(path, os.O_CREATE|os.O_EXCL|os.O_WRONLY, 0o600)
	if err != nil {
		fail(w, 500, "internal", "Ошибка сервера")
		return
	}
	n, err := io.Copy(f, http.MaxBytesReader(w, r.Body, limit))
	_ = f.Close()
	if err != nil || n != size {
		_ = os.Remove(path)
		fail(w, 400, "upload_failed", "Загрузка прервалась")
		return
	}
	if _, err := s.db.Exec(`INSERT INTO media(id, owner_id, size, created_at) VALUES(?,?,?,?)`, id, u.ID, n, now); err != nil {
		_ = os.Remove(path)
		fail(w, 500, "internal", "Ошибка сервера")
		return
	}
	_, _ = s.db.Exec(`INSERT INTO media_access(media_id, user_id) VALUES(?,?)`, id, u.ID)
	_, _ = s.db.Exec(`UPDATE users SET quota_start=?, quota_used=? WHERE id=?`, qStart, qUsed+n, u.ID)
	writeJSON(w, 200, map[string]any{"id": id, "size": n, "quotaUsed": qUsed + n})
}

func (s *Server) downloadMedia(w http.ResponseWriter, r *http.Request, u ctxUser) {
	id := r.PathValue("id")
	if len(id) != 32 {
		fail(w, 404, "not_found", "Файл не найден")
		return
	}
	var x int
	if s.db.QueryRow(`SELECT 1 FROM media_access WHERE media_id=? AND user_id=?`, id, u.ID).Scan(&x) != nil {
		fail(w, 404, "not_found", "Файл не найден или удалён")
		return
	}
	f, err := os.Open(filepath.Join(s.mediaDir, id))
	if err != nil {
		fail(w, 404, "not_found", "Файл удалён")
		return
	}
	defer f.Close()
	st, _ := f.Stat()
	w.Header().Set("Content-Type", "application/octet-stream")
	w.Header().Set("Content-Length", strconv.FormatInt(st.Size(), 10))
	_, _ = io.Copy(w, f)
}

// mediaJanitor раз в час удаляет файлы старше MEDIA_TTL_DAYS, чтобы диск не переполнялся.
func (s *Server) mediaJanitor() {
	for {
		if s.mediaTTLDays > 0 {
			cutoff := time.Now().Add(-time.Duration(s.mediaTTLDays) * 24 * time.Hour).Unix()
			rows, err := s.db.Query(`SELECT id FROM media WHERE created_at < ?`, cutoff)
			if err == nil {
				var ids []string
				for rows.Next() {
					var id string
					_ = rows.Scan(&id)
					ids = append(ids, id)
				}
				rows.Close()
				for _, id := range ids {
					_ = os.Remove(filepath.Join(s.mediaDir, id))
					_, _ = s.db.Exec(`DELETE FROM media WHERE id=?`, id)
				}
				if len(ids) > 0 {
					log.Printf("media janitor: removed %d files", len(ids))
				}
			}
		}
		time.Sleep(time.Hour)
	}
}
