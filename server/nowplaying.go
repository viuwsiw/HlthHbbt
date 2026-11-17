package main

import (
	"net/http"
	"sync"
	"unicode/utf8"
)

// Что сейчас играет у пользователя (только в памяти, не сохраняется). Видимость — по его настройке.
type nowPlaying struct {
	Title  string `json:"title"`
	Artist string `json:"artist"`
	App    string `json:"app"`
	Vis    string `json:"-"`
}

var (
	npMu  sync.Mutex
	npMap = map[int64]nowPlaying{}
)

func (s *Server) putNowPlaying(w http.ResponseWriter, r *http.Request, u ctxUser) {
	var req struct {
		Title  string `json:"title"`
		Artist string `json:"artist"`
		App    string `json:"app"`
		Vis    string `json:"vis"`
	}
	if !readJSON(w, r, &req) {
		return
	}
	trim := func(s string, n int) string {
		if utf8.RuneCountInString(s) > n {
			return string([]rune(s)[:n])
		}
		return s
	}
	npMu.Lock()
	if req.Title == "" || (req.Vis != VisAll && req.Vis != VisContacts) {
		delete(npMap, u.ID)
	} else {
		npMap[u.ID] = nowPlaying{trim(req.Title, 120), trim(req.Artist, 120), trim(req.App, 40), req.Vis}
	}
	npMu.Unlock()
	s.broadcastPresence(u.ID, u.Username)
	writeJSON(w, 200, map[string]any{"ok": true})
}

// nowPlayingFor — что играет у owner глазами viewer (с учётом видимости).
func (s *Server) nowPlayingFor(owner, viewer int64) *nowPlaying {
	npMu.Lock()
	np, ok := npMap[owner]
	npMu.Unlock()
	if !ok {
		return nil
	}
	if owner == viewer || np.Vis == VisAll || (np.Vis == VisContacts && s.isContact(owner, viewer)) {
		return &np
	}
	return nil
}
