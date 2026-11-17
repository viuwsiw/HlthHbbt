package main

import (
	"database/sql"
	"io"
	"net/http"
	"strconv"
	"strings"
	"time"
	"unicode/utf8"
)

// Мессенджер 1.2: групповые чаты (до 5 участников), «прочитано»/«прослушано»,
// удаление диалога у себя и у всех, фото профиля.

const MaxGroupMembers = 5
const MaxAvatarBytes = 700 * 1024

func migrateChat2(db *sql.DB) error {
	if err := ensureColumn(db, "messages", "group_id", "INTEGER NOT NULL DEFAULT 0"); err != nil {
		return err
	}
	_, err := db.Exec(`
CREATE TABLE IF NOT EXISTS chat_groups (
	id         INTEGER PRIMARY KEY AUTOINCREMENT,
	name       TEXT NOT NULL,
	owner_id   INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
	created_at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS group_members (
	group_id INTEGER NOT NULL REFERENCES chat_groups(id) ON DELETE CASCADE,
	user_id  INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
	PRIMARY KEY (group_id, user_id)
);
CREATE INDEX IF NOT EXISTS idx_gm_user ON group_members(user_id);
CREATE TABLE IF NOT EXISTS group_cts (
	message_id INTEGER NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
	user_id    INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
	ct         TEXT NOT NULL,
	PRIMARY KEY (message_id, user_id)
);
CREATE INDEX IF NOT EXISTS idx_gct_user ON group_cts(user_id, message_id);
CREATE TABLE IF NOT EXISTS reads (
	user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
	conv    TEXT NOT NULL,
	up_to   INTEGER NOT NULL,
	PRIMARY KEY (user_id, conv)
);
CREATE INDEX IF NOT EXISTS idx_reads_conv ON reads(conv);
CREATE TABLE IF NOT EXISTS listened (
	message_id INTEGER NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
	user_id    INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
	PRIMARY KEY (message_id, user_id)
);
CREATE TABLE IF NOT EXISTS clears (
	user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
	conv    TEXT NOT NULL,
	up_to   INTEGER NOT NULL,
	PRIMARY KEY (user_id, conv)
);
CREATE TABLE IF NOT EXISTS avatars (
	user_id    INTEGER PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
	data       BLOB NOT NULL,
	updated_at INTEGER NOT NULL
);
`)
	return err
}

func gconv(id int64) string { return "g:" + strconv.FormatInt(id, 10) }

func (s *Server) isMember(gid, uid int64) bool {
	var n int
	_ = s.db.QueryRow(`SELECT COUNT(*) FROM group_members WHERE group_id=? AND user_id=?`, gid, uid).Scan(&n)
	return n > 0
}

func (s *Server) memberIDs(gid int64) []int64 {
	rows, err := s.db.Query(`SELECT user_id FROM group_members WHERE group_id=?`, gid)
	if err != nil {
		return nil
	}
	defer rows.Close()
	var ids []int64
	for rows.Next() {
		var id int64
		_ = rows.Scan(&id)
		ids = append(ids, id)
	}
	return ids
}

func (s *Server) notifyGroup(gid int64) {
	for _, id := range s.memberIDs(gid) {
		s.hub.send(id, map[string]any{"type": "group", "id": gid})
	}
}

// ---------- группы ----------

type groupView struct {
	ID      int64            `json:"id"`
	Name    string           `json:"name"`
	Owner   string           `json:"owner"`
	Members []map[string]any `json:"members"`
}

func (s *Server) loadGroup(gid int64) (*groupView, bool) {
	var g groupView
	err := s.db.QueryRow(`SELECT g.id, g.name, u.username FROM chat_groups g JOIN users u ON u.id=g.owner_id WHERE g.id=?`, gid).Scan(&g.ID, &g.Name, &g.Owner)
	if err != nil {
		return nil, false
	}
	rows, err := s.db.Query(`SELECT u.username, u.display_name, u.avatar, u.public_key FROM group_members m JOIN users u ON u.id=m.user_id WHERE m.group_id=? ORDER BY u.username`, gid)
	if err == nil {
		defer rows.Close()
		for rows.Next() {
			var un, dn, av, key string
			_ = rows.Scan(&un, &dn, &av, &key)
			g.Members = append(g.Members, map[string]any{"username": un, "displayName": dn, "avatar": av, "publicKey": key})
		}
	}
	return &g, true
}

func (s *Server) listGroups(w http.ResponseWriter, r *http.Request, u ctxUser) {
	rows, err := s.db.Query(`SELECT group_id FROM group_members WHERE user_id=?`, u.ID)
	if err != nil {
		fail(w, 500, "internal", "Ошибка сервера")
		return
	}
	var ids []int64
	for rows.Next() {
		var id int64
		_ = rows.Scan(&id)
		ids = append(ids, id)
	}
	rows.Close()
	list := []*groupView{}
	for _, id := range ids {
		if g, ok := s.loadGroup(id); ok {
			list = append(list, g)
		}
	}
	writeJSON(w, 200, map[string]any{"groups": list})
}

func cleanGroupName(n string) (string, bool) {
	n = strings.TrimSpace(n)
	return n, n != "" && utf8.RuneCountInString(n) <= 40
}

func (s *Server) createGroup(w http.ResponseWriter, r *http.Request, u ctxUser) {
	var req struct {
		Name    string   `json:"name"`
		Members []string `json:"members"`
	}
	if !readJSON(w, r, &req) {
		return
	}
	name, ok := cleanGroupName(req.Name)
	if !ok {
		fail(w, 400, "bad_name", "Название — от 1 до 40 символов")
		return
	}
	ids := map[int64]bool{u.ID: true}
	for _, m := range req.Members {
		var id int64
		if err := s.db.QueryRow(`SELECT id FROM users WHERE username=?`, strings.ToLower(strings.TrimSpace(m))).Scan(&id); err != nil {
			fail(w, 404, "not_found", "Пользователь @"+m+" не найден")
			return
		}
		ids[id] = true
	}
	if len(ids) < 2 {
		fail(w, 400, "too_few", "Добавьте хотя бы одного участника")
		return
	}
	if len(ids) > MaxGroupMembers {
		fail(w, 400, "too_many", "В группе не больше 5 участников")
		return
	}
	res, err := s.db.Exec(`INSERT INTO chat_groups(name, owner_id, created_at) VALUES(?,?,?)`, name, u.ID, time.Now().UnixMilli())
	if err != nil {
		fail(w, 500, "internal", "Ошибка сервера")
		return
	}
	gid, _ := res.LastInsertId()
	for id := range ids {
		_, _ = s.db.Exec(`INSERT OR IGNORE INTO group_members(group_id, user_id) VALUES(?,?)`, gid, id)
	}
	s.notifyGroup(gid)
	g, _ := s.loadGroup(gid)
	writeJSON(w, 200, g)
}

func (s *Server) groupID(w http.ResponseWriter, r *http.Request, u ctxUser) (int64, bool) {
	gid, err := strconv.ParseInt(r.PathValue("id"), 10, 64)
	if err != nil || !s.isMember(gid, u.ID) {
		fail(w, 404, "not_found", "Группа не найдена")
		return 0, false
	}
	return gid, true
}

func (s *Server) isOwner(gid, uid int64) bool {
	var owner int64
	_ = s.db.QueryRow(`SELECT owner_id FROM chat_groups WHERE id=?`, gid).Scan(&owner)
	return owner == uid
}

func (s *Server) updateGroup(w http.ResponseWriter, r *http.Request, u ctxUser) {
	gid, ok := s.groupID(w, r, u)
	if !ok {
		return
	}
	var req struct {
		Name string `json:"name"`
	}
	if !readJSON(w, r, &req) {
		return
	}
	name, ok := cleanGroupName(req.Name)
	if !ok {
		fail(w, 400, "bad_name", "Название — от 1 до 40 символов")
		return
	}
	_, _ = s.db.Exec(`UPDATE chat_groups SET name=? WHERE id=?`, name, gid)
	s.notifyGroup(gid)
	writeJSON(w, 200, map[string]any{"ok": true})
}

func (s *Server) addMember(w http.ResponseWriter, r *http.Request, u ctxUser) {
	gid, ok := s.groupID(w, r, u)
	if !ok {
		return
	}
	if !s.isOwner(gid, u.ID) {
		fail(w, 403, "forbidden", "Добавлять участников может только создатель группы")
		return
	}
	var req struct {
		Username string `json:"username"`
	}
	if !readJSON(w, r, &req) {
		return
	}
	id, ok := s.idByUsername(w, req.Username)
	if !ok {
		return
	}
	if len(s.memberIDs(gid)) >= MaxGroupMembers && !s.isMember(gid, id) {
		fail(w, 400, "too_many", "В группе не больше 5 участников")
		return
	}
	_, _ = s.db.Exec(`INSERT OR IGNORE INTO group_members(group_id, user_id) VALUES(?,?)`, gid, id)
	s.notifyGroup(gid)
	writeJSON(w, 200, map[string]any{"ok": true})
}

func (s *Server) removeMember(w http.ResponseWriter, r *http.Request, u ctxUser) {
	gid, ok := s.groupID(w, r, u)
	if !ok {
		return
	}
	var id int64
	if err := s.db.QueryRow(`SELECT id FROM users WHERE username=?`, strings.ToLower(r.PathValue("username"))).Scan(&id); err != nil {
		fail(w, 404, "not_found", "Пользователь не найден")
		return
	}
	if id != u.ID && !s.isOwner(gid, u.ID) {
		fail(w, 403, "forbidden", "Удалять участников может только создатель группы")
		return
	}
	members := s.memberIDs(gid)
	_, _ = s.db.Exec(`DELETE FROM group_members WHERE group_id=? AND user_id=?`, gid, id)
	for _, m := range members {
		s.hub.send(m, map[string]any{"type": "group", "id": gid})
	}
	// Создатель вышел — группа передаётся следующему участнику, пустая группа удаляется.
	if s.isOwner(gid, id) {
		left := s.memberIDs(gid)
		if len(left) == 0 {
			_, _ = s.db.Exec(`DELETE FROM chat_groups WHERE id=?`, gid)
		} else {
			_, _ = s.db.Exec(`UPDATE chat_groups SET owner_id=? WHERE id=?`, left[0], gid)
		}
	}
	writeJSON(w, 200, map[string]any{"ok": true})
}

type groupMsgReq struct {
	ClientID string            `json:"clientId"`
	Cts      map[string]string `json:"cts"`
	MediaIDs []string          `json:"mediaIds"`
}

func (s *Server) sendGroupMessage(w http.ResponseWriter, r *http.Request, u ctxUser) {
	gid, ok := s.groupID(w, r, u)
	if !ok {
		return
	}
	var req groupMsgReq
	if !readJSON(w, r, &req) {
		return
	}
	if len(req.ClientID) == 0 || len(req.ClientID) > 64 || len(req.MediaIDs) > 10 {
		fail(w, 400, "bad_message", "Некорректное сообщение")
		return
	}
	if !s.spam.Allow("msg:" + strconv.FormatInt(u.ID, 10)) {
		fail(w, 429, "spam", "Слишком часто: не больше 2 сообщений или файлов за 5 секунд")
		return
	}
	var sent int
	_ = s.db.QueryRow(`SELECT msg_count FROM users WHERE id=?`, u.ID).Scan(&sent)
	if sent >= MaxMessagesPerUser {
		fail(w, 403, "messages_limit", "Достигнут лимит: 999 999 сообщений на аккаунт")
		return
	}
	type rcpt struct {
		id   int64
		name string
		ct   string
	}
	var rcpts []rcpt
	for _, mid := range s.memberIDs(gid) {
		var name string
		_ = s.db.QueryRow(`SELECT username FROM users WHERE id=?`, mid).Scan(&name)
		ct := req.Cts[name]
		if ct == "" || len(ct) > 96*1024 {
			fail(w, 409, "members_changed", "Состав группы изменился — обновите чат")
			return
		}
		rcpts = append(rcpts, rcpt{mid, name, ct})
	}
	now := time.Now().UnixMilli()
	tx, err := s.db.Begin()
	if err != nil {
		fail(w, 500, "internal", "Ошибка сервера")
		return
	}
	defer tx.Rollback()
	res, err := tx.Exec(`INSERT INTO messages(sender_id, recipient_id, ct_recipient, ct_sender, client_id, created_at, group_id) VALUES(?,?,?,?,?,?,?)
		ON CONFLICT(sender_id, client_id) DO NOTHING`, u.ID, u.ID, "", req.Cts[u.Username], req.ClientID, now, gid)
	if err != nil {
		fail(w, 500, "internal", "Ошибка сервера")
		return
	}
	id, _ := res.LastInsertId()
	if n, _ := res.RowsAffected(); n == 0 {
		_ = tx.QueryRow(`SELECT id, created_at FROM messages WHERE sender_id=? AND client_id=?`, u.ID, req.ClientID).Scan(&id, &now)
		_ = tx.Commit()
		writeJSON(w, 200, map[string]any{"id": id, "createdAt": now})
		return
	}
	for _, rc := range rcpts {
		_, _ = tx.Exec(`INSERT OR REPLACE INTO group_cts(message_id, user_id, ct) VALUES(?,?,?)`, id, rc.id, rc.ct)
		for _, m := range req.MediaIDs {
			_, _ = tx.Exec(`INSERT OR IGNORE INTO media_access(media_id, user_id) SELECT id, ? FROM media WHERE id=? AND owner_id=?`, rc.id, m, u.ID)
		}
	}
	_, _ = tx.Exec(`UPDATE users SET msg_count=msg_count+1 WHERE id=?`, u.ID)
	if err := tx.Commit(); err != nil {
		fail(w, 500, "internal", "Ошибка сервера")
		return
	}
	for _, rc := range rcpts {
		s.hub.send(rc.id, map[string]any{"type": "message", "message": message{id, u.Username, gconv(gid), rc.ct, req.ClientID, now, gid}})
	}
	writeJSON(w, 200, map[string]any{"id": id, "createdAt": now})
}

// ---------- прочитано / прослушано ----------

// convFor — ключ диалога с точки зрения пользователя: логин собеседника или "g:<id>".
func (s *Server) postRead(w http.ResponseWriter, r *http.Request, u ctxUser) {
	var req struct {
		Conv string `json:"conv"`
		UpTo int64  `json:"upTo"`
	}
	if !readJSON(w, r, &req) {
		return
	}
	conv := strings.ToLower(req.Conv)
	if strings.HasPrefix(conv, "g:") {
		gid, _ := strconv.ParseInt(conv[2:], 10, 64)
		if !s.isMember(gid, u.ID) {
			fail(w, 404, "not_found", "Группа не найдена")
			return
		}
		_, _ = s.db.Exec(`INSERT INTO reads(user_id, conv, up_to) VALUES(?,?,?) ON CONFLICT(user_id, conv) DO UPDATE SET up_to=MAX(up_to, excluded.up_to)`, u.ID, conv, req.UpTo)
		for _, m := range s.memberIDs(gid) {
			if m != u.ID {
				s.hub.send(m, map[string]any{"type": "read", "conv": conv, "by": u.Username, "upTo": req.UpTo})
			}
		}
	} else {
		var peer int64
		if err := s.db.QueryRow(`SELECT id FROM users WHERE username=?`, conv).Scan(&peer); err != nil {
			fail(w, 404, "not_found", "Пользователь не найден")
			return
		}
		_, _ = s.db.Exec(`INSERT INTO reads(user_id, conv, up_to) VALUES(?,?,?) ON CONFLICT(user_id, conv) DO UPDATE SET up_to=MAX(up_to, excluded.up_to)`, u.ID, conv, req.UpTo)
		if peer != u.ID {
			s.hub.send(peer, map[string]any{"type": "read", "conv": u.Username, "by": u.Username, "upTo": req.UpTo})
		}
	}
	writeJSON(w, 200, map[string]any{"ok": true})
}

func (s *Server) postListened(w http.ResponseWriter, r *http.Request, u ctxUser) {
	id, _ := strconv.ParseInt(r.PathValue("id"), 10, 64)
	var sender, recipient, gid int64
	if err := s.db.QueryRow(`SELECT sender_id, recipient_id, group_id FROM messages WHERE id=?`, id).Scan(&sender, &recipient, &gid); err != nil {
		fail(w, 404, "not_found", "Сообщение не найдено")
		return
	}
	allowed := recipient == u.ID
	if gid > 0 {
		var n int
		_ = s.db.QueryRow(`SELECT COUNT(*) FROM group_cts WHERE message_id=? AND user_id=?`, id, u.ID).Scan(&n)
		allowed = n > 0
	}
	if !allowed || sender == u.ID {
		writeJSON(w, 200, map[string]any{"ok": true})
		return
	}
	_, _ = s.db.Exec(`INSERT OR IGNORE INTO listened(message_id, user_id) VALUES(?,?)`, id, u.ID)
	s.hub.send(sender, map[string]any{"type": "listened", "id": id, "by": u.Username})
	writeJSON(w, 200, map[string]any{"ok": true})
}

// Квитанции для моих сообщений: кто до какого сообщения прочитал и какие голосовые прослушаны.
func (s *Server) getReceipts(w http.ResponseWriter, r *http.Request, u ctxUser) {
	reads := []map[string]any{}
	rows, err := s.db.Query(`SELECT ru.username, r.conv, r.up_to FROM reads r JOIN users ru ON ru.id=r.user_id
		WHERE r.conv=? OR r.conv IN (SELECT 'g:' || group_id FROM group_members WHERE user_id=?)`, u.Username, u.ID)
	if err == nil {
		for rows.Next() {
			var by, conv string
			var upTo int64
			_ = rows.Scan(&by, &conv, &upTo)
			if by == u.Username {
				continue
			}
			if !strings.HasPrefix(conv, "g:") {
				conv = by
			}
			reads = append(reads, map[string]any{"conv": conv, "by": by, "upTo": upTo})
		}
		rows.Close()
	}
	listened := []int64{}
	rows, err = s.db.Query(`SELECT DISTINCT l.message_id FROM listened l JOIN messages m ON m.id=l.message_id WHERE m.sender_id=? ORDER BY l.message_id DESC LIMIT 3000`, u.ID)
	if err == nil {
		for rows.Next() {
			var id int64
			_ = rows.Scan(&id)
			listened = append(listened, id)
		}
		rows.Close()
	}
	writeJSON(w, 200, map[string]any{"reads": reads, "listened": listened})
}

// ---------- удаление диалога ----------

func (s *Server) clearConversation(w http.ResponseWriter, r *http.Request, u ctxUser) {
	conv := strings.ToLower(r.PathValue("conv"))
	all := r.URL.Query().Get("all") == "1"
	var maxID int64
	if strings.HasPrefix(conv, "g:") {
		gid, _ := strconv.ParseInt(conv[2:], 10, 64)
		if !s.isMember(gid, u.ID) {
			fail(w, 404, "not_found", "Группа не найдена")
			return
		}
		_ = s.db.QueryRow(`SELECT COALESCE(MAX(id),0) FROM messages WHERE group_id=?`, gid).Scan(&maxID)
		if all {
			if !s.isOwner(gid, u.ID) {
				fail(w, 403, "forbidden", "Очистить группу у всех может только создатель")
				return
			}
			_, _ = s.db.Exec(`DELETE FROM messages WHERE group_id=?`, gid)
			for _, m := range s.memberIDs(gid) {
				s.hub.send(m, map[string]any{"type": "cleared", "conv": conv, "upTo": maxID})
			}
		}
	} else {
		var peer int64
		if err := s.db.QueryRow(`SELECT id FROM users WHERE username=?`, conv).Scan(&peer); err != nil {
			fail(w, 404, "not_found", "Пользователь не найден")
			return
		}
		_ = s.db.QueryRow(`SELECT COALESCE(MAX(id),0) FROM messages WHERE group_id=0 AND ((sender_id=? AND recipient_id=?) OR (sender_id=? AND recipient_id=?))`, u.ID, peer, peer, u.ID).Scan(&maxID)
		if all {
			_, _ = s.db.Exec(`DELETE FROM messages WHERE group_id=0 AND ((sender_id=? AND recipient_id=?) OR (sender_id=? AND recipient_id=?))`, u.ID, peer, peer, u.ID)
			if peer != u.ID {
				s.hub.send(peer, map[string]any{"type": "cleared", "conv": u.Username, "upTo": maxID})
			}
		}
	}
	_, _ = s.db.Exec(`INSERT INTO clears(user_id, conv, up_to) VALUES(?,?,?) ON CONFLICT(user_id, conv) DO UPDATE SET up_to=MAX(up_to, excluded.up_to)`, u.ID, conv, maxID)
	s.hub.send(u.ID, map[string]any{"type": "cleared", "conv": conv, "upTo": maxID})
	writeJSON(w, 200, map[string]any{"ok": true, "upTo": maxID})
}

func (s *Server) clearsFor(uid int64) map[string]int64 {
	out := map[string]int64{}
	rows, err := s.db.Query(`SELECT conv, up_to FROM clears WHERE user_id=?`, uid)
	if err != nil {
		return out
	}
	defer rows.Close()
	for rows.Next() {
		var c string
		var v int64
		_ = rows.Scan(&c, &v)
		out[c] = v
	}
	return out
}

// ---------- фото профиля ----------

func (s *Server) putAvatar(w http.ResponseWriter, r *http.Request, u ctxUser) {
	data, err := io.ReadAll(http.MaxBytesReader(w, r.Body, MaxAvatarBytes))
	if err != nil || len(data) < 100 {
		fail(w, 413, "too_large", "Фото — до 700 КБ")
		return
	}
	if !(data[0] == 0xFF && data[1] == 0xD8) && !strings.HasPrefix(string(data[:8]), "\x89PNG") {
		fail(w, 400, "bad_image", "Нужно фото JPEG или PNG")
		return
	}
	now := time.Now().UnixMilli()
	_, _ = s.db.Exec(`INSERT INTO avatars(user_id, data, updated_at) VALUES(?,?,?) ON CONFLICT(user_id) DO UPDATE SET data=excluded.data, updated_at=excluded.updated_at`, u.ID, data, now)
	avatar := "photo:" + strconv.FormatInt(now, 10)
	_, _ = s.db.Exec(`UPDATE users SET avatar=? WHERE id=?`, avatar, u.ID)
	writeJSON(w, 200, map[string]any{"avatar": avatar})
}

func (s *Server) deleteAvatar(w http.ResponseWriter, r *http.Request, u ctxUser) {
	_, _ = s.db.Exec(`DELETE FROM avatars WHERE user_id=?`, u.ID)
	_, _ = s.db.Exec(`UPDATE users SET avatar='' WHERE id=? AND avatar LIKE 'photo:%'`, u.ID)
	writeJSON(w, 200, map[string]any{"ok": true})
}

func (s *Server) getAvatar(w http.ResponseWriter, r *http.Request, u ctxUser) {
	var data []byte
	err := s.db.QueryRow(`SELECT a.data FROM avatars a JOIN users us ON us.id=a.user_id WHERE us.username=?`, strings.ToLower(r.PathValue("username"))).Scan(&data)
	if err != nil {
		http.NotFound(w, r)
		return
	}
	ct := "image/jpeg"
	if len(data) > 4 && data[0] == 0x89 {
		ct = "image/png"
	}
	w.Header().Set("Content-Type", ct)
	w.Header().Set("Cache-Control", "private, max-age=86400")
	_, _ = w.Write(data)
}

// currentPhotoAvatar — сохраняет «photo:…», если клиент прислал "photo" (у него загружено фото).
func (s *Server) resolveAvatar(uid int64, requested string) string {
	if requested == "photo" || strings.HasPrefix(requested, "photo:") {
		var ts int64
		if err := s.db.QueryRow(`SELECT updated_at FROM avatars WHERE user_id=?`, uid).Scan(&ts); err == nil {
			return "photo:" + strconv.FormatInt(ts, 10)
		}
		return ""
	}
	if strings.HasPrefix(requested, "preset:") {
		_, _ = s.db.Exec(`DELETE FROM avatars WHERE user_id=?`, uid)
	}
	return requested
}
