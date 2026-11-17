package main

import (
	"crypto/rand"
	"crypto/sha256"
	"database/sql"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"errors"
	"net"
	"net/http"
	"regexp"
	"strconv"
	"strings"
	"time"
	"unicode/utf8"

	"golang.org/x/crypto/bcrypt"
)

type Server struct {
	db      *sql.DB
	hub     *Hub
	limiter *Limiter
}

var usernameRe = regexp.MustCompile(`^[a-z0-9_.]{3,24}$`)

// Видимость полей профиля.
const (
	VisAll      = "ALL"
	VisContacts = "CONTACTS"
	VisNobody   = "NOBODY"
)

type ctxUser struct {
	ID       int64
	Username string
}

func (s *Server) routes() http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("GET /v1/health", func(w http.ResponseWriter, r *http.Request) { writeJSON(w, 200, map[string]any{"ok": true}) })
	mux.HandleFunc("POST /v1/auth/register", s.limit(s.register))
	mux.HandleFunc("POST /v1/auth/login", s.limit(s.login))
	mux.HandleFunc("POST /v1/auth/logout", s.auth(s.logout))
	mux.HandleFunc("GET /v1/me", s.auth(s.me))
	mux.HandleFunc("DELETE /v1/me", s.auth(s.deleteMe))
	mux.HandleFunc("PUT /v1/me/profile", s.auth(s.putProfile))
	mux.HandleFunc("PUT /v1/me/key", s.auth(s.putKey))
	mux.HandleFunc("PUT /v1/me/status", s.auth(s.putStatus))
	mux.HandleFunc("PUT /v1/me/presence", s.auth(s.putPresence))
	mux.HandleFunc("GET /v1/presence", s.auth(s.getPresence))
	mux.HandleFunc("GET /v1/users/search", s.auth(s.search))
	mux.HandleFunc("GET /v1/users/{username}", s.auth(s.userProfile))
	mux.HandleFunc("GET /v1/contacts", s.auth(s.listContacts))
	mux.HandleFunc("PUT /v1/contacts/{username}", s.auth(s.addContact))
	mux.HandleFunc("DELETE /v1/contacts/{username}", s.auth(s.removeContact))
	mux.HandleFunc("POST /v1/messages", s.auth(s.sendMessage))
	mux.HandleFunc("GET /v1/messages", s.auth(s.listMessages))
	mux.HandleFunc("GET /v1/ws", s.ws)
	return withLimits(mux)
}

// ---------- auth ----------

type authReq struct {
	Username    string `json:"username"`
	Password    string `json:"password"`
	DisplayName string `json:"displayName"`
}

func (s *Server) register(w http.ResponseWriter, r *http.Request) {
	var req authReq
	if !readJSON(w, r, &req) {
		return
	}
	req.Username = strings.ToLower(strings.TrimSpace(req.Username))
	req.DisplayName = strings.TrimSpace(req.DisplayName)
	if !usernameRe.MatchString(req.Username) {
		fail(w, 400, "bad_username", "Логин: 3–24 символа, латиница, цифры, _ и .")
		return
	}
	if utf8.RuneCountInString(req.Password) < 6 {
		fail(w, 400, "bad_password", "Пароль — минимум 6 символов")
		return
	}
	if req.DisplayName == "" || utf8.RuneCountInString(req.DisplayName) > 40 {
		req.DisplayName = req.Username
	}
	hash, err := bcrypt.GenerateFromPassword([]byte(req.Password), 11)
	if err != nil {
		fail(w, 500, "internal", "Ошибка сервера")
		return
	}
	res, err := s.db.Exec(`INSERT INTO users(username, display_name, pass_hash, created_at) VALUES(?,?,?,?)`,
		req.Username, req.DisplayName, string(hash), time.Now().Unix())
	if err != nil {
		if strings.Contains(err.Error(), "UNIQUE") {
			fail(w, 409, "username_taken", "Логин уже занят")
			return
		}
		fail(w, 500, "internal", "Ошибка сервера")
		return
	}
	id, _ := res.LastInsertId()
	s.issueToken(w, id, req.Username)
}

func (s *Server) login(w http.ResponseWriter, r *http.Request) {
	var req authReq
	if !readJSON(w, r, &req) {
		return
	}
	req.Username = strings.ToLower(strings.TrimSpace(req.Username))
	var id int64
	var hash string
	err := s.db.QueryRow(`SELECT id, pass_hash FROM users WHERE username=?`, req.Username).Scan(&id, &hash)
	if err != nil || bcrypt.CompareHashAndPassword([]byte(hash), []byte(req.Password)) != nil {
		fail(w, 401, "bad_credentials", "Неверный логин или пароль")
		return
	}
	s.issueToken(w, id, req.Username)
}

func (s *Server) issueToken(w http.ResponseWriter, id int64, username string) {
	raw := make([]byte, 32)
	_, _ = rand.Read(raw)
	token := base64.RawURLEncoding.EncodeToString(raw)
	now := time.Now().Unix()
	if _, err := s.db.Exec(`INSERT INTO sessions(token_hash, user_id, created_at, last_seen) VALUES(?,?,?,?)`, hashToken(token), id, now, now); err != nil {
		fail(w, 500, "internal", "Ошибка сервера")
		return
	}
	writeJSON(w, 200, map[string]any{"token": token, "userId": id, "username": username})
}

func (s *Server) logout(w http.ResponseWriter, r *http.Request, u ctxUser) {
	_, _ = s.db.Exec(`DELETE FROM sessions WHERE token_hash=?`, hashToken(bearer(r)))
	writeJSON(w, 200, map[string]any{"ok": true})
}

func hashToken(t string) string {
	h := sha256.Sum256([]byte(t))
	return hex.EncodeToString(h[:])
}

func bearer(r *http.Request) string {
	if h := r.Header.Get("Authorization"); strings.HasPrefix(h, "Bearer ") {
		return strings.TrimPrefix(h, "Bearer ")
	}
	return r.URL.Query().Get("token")
}

func (s *Server) userByToken(token string) (ctxUser, bool) {
	if token == "" {
		return ctxUser{}, false
	}
	var u ctxUser
	err := s.db.QueryRow(`SELECT u.id, u.username FROM sessions s JOIN users u ON u.id=s.user_id WHERE s.token_hash=?`, hashToken(token)).Scan(&u.ID, &u.Username)
	if err != nil {
		return ctxUser{}, false
	}
	_, _ = s.db.Exec(`UPDATE sessions SET last_seen=? WHERE token_hash=?`, time.Now().Unix(), hashToken(token))
	return u, true
}

func (s *Server) auth(h func(http.ResponseWriter, *http.Request, ctxUser)) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		u, ok := s.userByToken(bearer(r))
		if !ok {
			fail(w, 401, "unauthorized", "Нужно войти заново")
			return
		}
		h(w, r, u)
	}
}

func (s *Server) limit(h http.HandlerFunc) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		ip, _, _ := net.SplitHostPort(r.RemoteAddr)
		if fwd := r.Header.Get("X-Forwarded-For"); fwd != "" {
			ip = strings.TrimSpace(strings.Split(fwd, ",")[0])
		}
		if !s.limiter.Allow(ip) {
			fail(w, 429, "rate_limited", "Слишком много попыток, подождите минуту")
			return
		}
		h(w, r)
	}
}

// ---------- profile ----------

type field struct {
	Vis   string          `json:"vis"`
	Value json.RawMessage `json:"value"`
}

type profileReq struct {
	DisplayName  string           `json:"displayName"`
	Avatar       string           `json:"avatar"`
	Bio          string           `json:"bio"`
	Discoverable *bool            `json:"discoverable"`
	Fields       map[string]field `json:"fields"`
}

func (s *Server) me(w http.ResponseWriter, r *http.Request, u ctxUser) {
	p, err := s.loadProfile(u.ID, u.ID)
	if err != nil {
		fail(w, 404, "not_found", "Пользователь не найден")
		return
	}
	writeJSON(w, 200, p)
}

func (s *Server) putProfile(w http.ResponseWriter, r *http.Request, u ctxUser) {
	var req profileReq
	if !readJSON(w, r, &req) {
		return
	}
	name := strings.TrimSpace(req.DisplayName)
	if name == "" || utf8.RuneCountInString(name) > 40 {
		fail(w, 400, "bad_name", "Имя — от 1 до 40 символов")
		return
	}
	if utf8.RuneCountInString(req.Bio) > 160 {
		fail(w, 400, "bad_bio", "О себе — до 160 символов")
		return
	}
	if len(req.Avatar) > 64 { // только встроенные аватары: "preset:N"
		req.Avatar = ""
	}
	fields := map[string]field{}
	for k, f := range req.Fields {
		if len(k) > 32 || len(f.Value) > 8192 {
			continue
		}
		if f.Vis != VisAll && f.Vis != VisContacts {
			continue // NOBODY — не храним вовсе
		}
		fields[k] = f
	}
	if len(fields) > 16 {
		fail(w, 400, "too_many_fields", "Слишком много полей")
		return
	}
	pj, _ := json.Marshal(fields)
	disc := 1
	if req.Discoverable != nil && !*req.Discoverable {
		disc = 0
	}
	_, err := s.db.Exec(`UPDATE users SET display_name=?, avatar=?, bio=?, discoverable=?, profile_json=? WHERE id=?`,
		name, req.Avatar, strings.TrimSpace(req.Bio), disc, string(pj), u.ID)
	if err != nil {
		fail(w, 500, "internal", "Ошибка сервера")
		return
	}
	writeJSON(w, 200, map[string]any{"ok": true})
}

func (s *Server) putKey(w http.ResponseWriter, r *http.Request, u ctxUser) {
	var req struct {
		PublicKey string `json:"publicKey"`
	}
	if !readJSON(w, r, &req) {
		return
	}
	if len(req.PublicKey) < 32 || len(req.PublicKey) > 4096 {
		fail(w, 400, "bad_key", "Некорректный ключ")
		return
	}
	_, _ = s.db.Exec(`UPDATE users SET public_key=? WHERE id=?`, req.PublicKey, u.ID)
	writeJSON(w, 200, map[string]any{"ok": true})
}

func (s *Server) deleteMe(w http.ResponseWriter, r *http.Request, u ctxUser) {
	_, _ = s.db.Exec(`DELETE FROM users WHERE id=?`, u.ID)
	s.hub.closeUser(u.ID)
	writeJSON(w, 200, map[string]any{"ok": true})
}

func (s *Server) isContact(owner, viewer int64) bool {
	var x int
	return s.db.QueryRow(`SELECT 1 FROM contacts WHERE owner_id=? AND contact_id=?`, owner, viewer).Scan(&x) == nil
}

// loadProfile отдаёт профиль owner глазами viewer: поля фильтруются по видимости.
func (s *Server) loadProfile(owner, viewer int64) (map[string]any, error) {
	var username, name, avatar, bio, pj, key string
	var disc int
	var created int64
	err := s.db.QueryRow(`SELECT username, display_name, avatar, bio, discoverable, profile_json, public_key, created_at FROM users WHERE id=?`, owner).
		Scan(&username, &name, &avatar, &bio, &disc, &pj, &key, &created)
	if err != nil {
		return nil, err
	}
	var fields map[string]field
	_ = json.Unmarshal([]byte(pj), &fields)
	self := owner == viewer
	contact := !self && s.isContact(owner, viewer)
	visible := map[string]any{}
	for k, f := range fields {
		if self || f.Vis == VisAll || (f.Vis == VisContacts && contact) {
			if self {
				visible[k] = f
			} else {
				visible[k] = f.Value
			}
		}
	}
	st, seen := s.presenceOf(owner)
	res := map[string]any{
		"status": st, "lastSeen": seen,
		"userId": owner, "username": username, "displayName": name, "avatar": avatar, "bio": bio,
		"createdAt": created, "fields": visible, "hasKey": key != "",
		"isContact": !self && s.isContact(viewer, owner), "isSelf": self,
	}
	if self {
		res["discoverable"] = disc == 1
		res["manualStatus"] = s.manualStatus(owner)
	}
	if key != "" {
		res["publicKey"] = key
	}
	return res, nil
}

func (s *Server) userProfile(w http.ResponseWriter, r *http.Request, u ctxUser) {
	var id int64
	if err := s.db.QueryRow(`SELECT id FROM users WHERE username=?`, strings.ToLower(r.PathValue("username"))).Scan(&id); err != nil {
		fail(w, 404, "not_found", "Пользователь не найден")
		return
	}
	p, err := s.loadProfile(id, u.ID)
	if err != nil {
		fail(w, 404, "not_found", "Пользователь не найден")
		return
	}
	writeJSON(w, 200, p)
}

func (s *Server) search(w http.ResponseWriter, r *http.Request, u ctxUser) {
	q := strings.ToLower(strings.TrimSpace(r.URL.Query().Get("q")))
	q = strings.TrimPrefix(q, "@")
	if len(q) < 2 {
		writeJSON(w, 200, map[string]any{"users": []any{}})
		return
	}
	esc := strings.NewReplacer(`\`, `\\`, `%`, `\%`, `_`, `\_`).Replace(q)
	rows, err := s.db.Query(`SELECT id, username, display_name, avatar FROM users
		WHERE discoverable=1 AND id<>? AND (username LIKE ? ESCAPE '\' OR lower(display_name) LIKE ? ESCAPE '\')
		ORDER BY (username=?) DESC, length(username) LIMIT 20`, u.ID, esc+"%", "%"+esc+"%", q)
	if err != nil {
		fail(w, 500, "internal", "Ошибка сервера")
		return
	}
	defer rows.Close()
	type row struct {
		id         int64
		un, dn, av string
	}
	var found []row
	for rows.Next() {
		var r row
		_ = rows.Scan(&r.id, &r.un, &r.dn, &r.av)
		found = append(found, r)
	}
	rows.Close()
	users := []map[string]any{}
	for _, r := range found {
		st, seen := s.presenceOf(r.id)
		users = append(users, map[string]any{"username": r.un, "displayName": r.dn, "avatar": r.av, "status": st, "lastSeen": seen})
	}
	writeJSON(w, 200, map[string]any{"users": users})
}

// ---------- contacts ----------

func (s *Server) listContacts(w http.ResponseWriter, r *http.Request, u ctxUser) {
	rows, err := s.db.Query(`SELECT u.username, u.display_name, u.avatar FROM contacts c JOIN users u ON u.id=c.contact_id WHERE c.owner_id=? ORDER BY u.display_name`, u.ID)
	if err != nil {
		fail(w, 500, "internal", "Ошибка сервера")
		return
	}
	defer rows.Close()
	list := []map[string]any{}
	for rows.Next() {
		var un, dn, av string
		_ = rows.Scan(&un, &dn, &av)
		list = append(list, map[string]any{"username": un, "displayName": dn, "avatar": av})
	}
	writeJSON(w, 200, map[string]any{"contacts": list})
}

func (s *Server) addContact(w http.ResponseWriter, r *http.Request, u ctxUser) {
	id, ok := s.idByUsername(w, r.PathValue("username"))
	if !ok {
		return
	}
	if id == u.ID {
		fail(w, 400, "self", "Нельзя добавить себя")
		return
	}
	_, _ = s.db.Exec(`INSERT OR IGNORE INTO contacts(owner_id, contact_id, created_at) VALUES(?,?,?)`, u.ID, id, time.Now().Unix())
	writeJSON(w, 200, map[string]any{"ok": true})
}

func (s *Server) removeContact(w http.ResponseWriter, r *http.Request, u ctxUser) {
	id, ok := s.idByUsername(w, r.PathValue("username"))
	if !ok {
		return
	}
	_, _ = s.db.Exec(`DELETE FROM contacts WHERE owner_id=? AND contact_id=?`, u.ID, id)
	writeJSON(w, 200, map[string]any{"ok": true})
}

func (s *Server) idByUsername(w http.ResponseWriter, username string) (int64, bool) {
	var id int64
	if err := s.db.QueryRow(`SELECT id FROM users WHERE username=?`, strings.ToLower(username)).Scan(&id); err != nil {
		fail(w, 404, "not_found", "Пользователь не найден")
		return 0, false
	}
	return id, true
}

// ---------- messages (только шифротекст) ----------

type msgReq struct {
	To          string `json:"to"`
	CtRecipient string `json:"ctRecipient"`
	CtSender    string `json:"ctSender"`
	ClientID    string `json:"clientId"`
}

type message struct {
	ID          int64  `json:"id"`
	From        string `json:"from"`
	To          string `json:"to"`
	Ciphertext  string `json:"ciphertext"` // тот вариант, который может расшифровать запрашивающий
	ClientID    string `json:"clientId"`
	CreatedAt   int64  `json:"createdAt"`
}

func (s *Server) sendMessage(w http.ResponseWriter, r *http.Request, u ctxUser) {
	var req msgReq
	if !readJSON(w, r, &req) {
		return
	}
	if len(req.CtRecipient) == 0 || len(req.CtRecipient) > 64*1024 || len(req.CtSender) > 64*1024 || len(req.ClientID) == 0 || len(req.ClientID) > 64 {
		fail(w, 400, "bad_message", "Некорректное сообщение")
		return
	}
	to, ok := s.idByUsername(w, req.To)
	if !ok {
		return
	}
	now := time.Now().UnixMilli()
	res, err := s.db.Exec(`INSERT INTO messages(sender_id, recipient_id, ct_recipient, ct_sender, client_id, created_at) VALUES(?,?,?,?,?,?)
		ON CONFLICT(sender_id, client_id) DO NOTHING`, u.ID, to, req.CtRecipient, req.CtSender, req.ClientID, now)
	if err != nil {
		fail(w, 500, "internal", "Ошибка сервера")
		return
	}
	id, _ := res.LastInsertId()
	if n, _ := res.RowsAffected(); n == 0 { // повторная отправка — возвращаем существующее
		_ = s.db.QueryRow(`SELECT id, created_at FROM messages WHERE sender_id=? AND client_id=?`, u.ID, req.ClientID).Scan(&id, &now)
	}
	toName := strings.ToLower(req.To)
	s.hub.send(to, map[string]any{"type": "message", "message": message{id, u.Username, toName, req.CtRecipient, req.ClientID, now}})
	s.hub.send(u.ID, map[string]any{"type": "message", "message": message{id, u.Username, toName, req.CtSender, req.ClientID, now}})
	writeJSON(w, 200, map[string]any{"id": id, "createdAt": now})
}

func (s *Server) listMessages(w http.ResponseWriter, r *http.Request, u ctxUser) {
	since, _ := strconv.ParseInt(r.URL.Query().Get("since"), 10, 64)
	rows, err := s.db.Query(`SELECT m.id, su.username, ru.username,
			CASE WHEN m.recipient_id=? THEN m.ct_recipient ELSE m.ct_sender END,
			m.client_id, m.created_at
		FROM messages m JOIN users su ON su.id=m.sender_id JOIN users ru ON ru.id=m.recipient_id
		WHERE (m.recipient_id=? OR m.sender_id=?) AND m.id>? ORDER BY m.id LIMIT 500`, u.ID, u.ID, u.ID, since)
	if err != nil {
		fail(w, 500, "internal", "Ошибка сервера")
		return
	}
	defer rows.Close()
	list := []message{}
	for rows.Next() {
		var m message
		_ = rows.Scan(&m.ID, &m.From, &m.To, &m.Ciphertext, &m.ClientID, &m.CreatedAt)
		list = append(list, m)
	}
	writeJSON(w, 200, map[string]any{"messages": list})
}

// ---------- helpers ----------

func withLimits(h http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		r.Body = http.MaxBytesReader(w, r.Body, 256*1024)
		w.Header().Set("Cache-Control", "no-store")
		h.ServeHTTP(w, r)
	})
}

func readJSON(w http.ResponseWriter, r *http.Request, v any) bool {
	dec := json.NewDecoder(r.Body)
	if err := dec.Decode(v); err != nil {
		var mbe *http.MaxBytesError
		if errors.As(err, &mbe) {
			fail(w, 413, "too_large", "Слишком большой запрос")
		} else {
			fail(w, 400, "bad_json", "Некорректный запрос")
		}
		return false
	}
	return true
}

func writeJSON(w http.ResponseWriter, code int, v any) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.WriteHeader(code)
	_ = json.NewEncoder(w).Encode(v)
}

func fail(w http.ResponseWriter, code int, errCode, msg string) {
	writeJSON(w, code, map[string]any{"error": errCode, "message": msg})
}


// ---------- статусы ----------

const (
	StatusOnline = "ONLINE" // в сети (вручную, пока пользователь в мессенджере)
	StatusDND    = "DND"    // не беспокоить (вручную)
	StatusAway   = "AWAY"   // вне чата (автоматически)
)

// activeWindow — сколько секунд после последнего сигнала «в мессенджере» считаем пользователя в сети.
const activeWindow = 90

func (s *Server) manualStatus(id int64) string {
	var st string
	if s.db.QueryRow(`SELECT status FROM users WHERE id=?`, id).Scan(&st) != nil || st == "" {
		return StatusOnline
	}
	return st
}

// presenceOf — статус, который видят другие: DND (вручную) > ONLINE (активен в мессенджере) > AWAY.
func (s *Server) presenceOf(id int64) (string, int64) {
	var st string
	var last int64
	if s.db.QueryRow(`SELECT status, last_active FROM users WHERE id=?`, id).Scan(&st, &last) != nil {
		return StatusAway, 0
	}
	if st == StatusDND {
		return StatusDND, last
	}
	if s.hub.connected(id) && s.hub.isActive(id) && time.Now().Unix()-last <= activeWindow {
		return StatusOnline, last
	}
	return StatusAway, last
}

func (s *Server) putStatus(w http.ResponseWriter, r *http.Request, u ctxUser) {
	var req struct {
		Status string `json:"status"`
	}
	if !readJSON(w, r, &req) {
		return
	}
	if req.Status != StatusOnline && req.Status != StatusDND {
		fail(w, 400, "bad_status", "Статус: ONLINE или DND")
		return
	}
	_, _ = s.db.Exec(`UPDATE users SET status=? WHERE id=?`, req.Status, u.ID)
	s.broadcastPresence(u.ID, u.Username)
	writeJSON(w, 200, map[string]any{"ok": true})
}

// putPresence — клиент сообщает, открыт ли сейчас мессенджер (иначе — «вне чата»).
func (s *Server) putPresence(w http.ResponseWriter, r *http.Request, u ctxUser) {
	var req struct {
		Active bool `json:"active"`
	}
	if !readJSON(w, r, &req) {
		return
	}
	s.hub.setActive(u.ID, req.Active)
	_, _ = s.db.Exec(`UPDATE users SET last_active=? WHERE id=?`, time.Now().Unix(), u.ID)
	s.broadcastPresence(u.ID, u.Username)
	writeJSON(w, 200, map[string]any{"ok": true})
}

// getPresence?u=a,b,c — статусы нескольких пользователей разом (для списка чатов).
func (s *Server) getPresence(w http.ResponseWriter, r *http.Request, u ctxUser) {
	res := map[string]any{}
	for i, name := range strings.Split(r.URL.Query().Get("u"), ",") {
		if i >= 100 {
			break
		}
		name = strings.ToLower(strings.TrimSpace(name))
		var id int64
		if name == "" || s.db.QueryRow(`SELECT id FROM users WHERE username=?`, name).Scan(&id) != nil {
			continue
		}
		st, seen := s.presenceOf(id)
		res[name] = map[string]any{"status": st, "lastSeen": seen}
	}
	writeJSON(w, 200, map[string]any{"presence": res})
}

// broadcastPresence — разослать новый статус собеседникам и тем, у кого пользователь в контактах.
func (s *Server) broadcastPresence(id int64, username string) {
	st, seen := s.presenceOf(id)
	ev := map[string]any{"type": "presence", "username": username, "status": st, "lastSeen": seen}
	rows, err := s.db.Query(`
		SELECT DISTINCT CASE WHEN sender_id=? THEN recipient_id ELSE sender_id END FROM messages WHERE sender_id=? OR recipient_id=?
		UNION SELECT owner_id FROM contacts WHERE contact_id=?`, id, id, id, id)
	if err != nil {
		return
	}
	var peers []int64
	for rows.Next() {
		var p int64
		_ = rows.Scan(&p)
		peers = append(peers, p)
	}
	rows.Close()
	for _, p := range peers {
		s.hub.send(p, ev)
	}
}
