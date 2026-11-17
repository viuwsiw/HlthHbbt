package main

import (
	"context"
	"net/http"
	"sync"
	"time"

	"github.com/coder/websocket"
	"github.com/coder/websocket/wsjson"
)

// Hub рассылает события (новые сообщения) всем открытым WebSocket пользователя.
type Hub struct {
	mu    sync.Mutex
	conns map[int64]map[*client]struct{}
}

type client struct {
	out chan any
	c   *websocket.Conn
}

func newHub() *Hub { return &Hub{conns: map[int64]map[*client]struct{}{}} }

func (h *Hub) add(uid int64, c *client) {
	h.mu.Lock()
	defer h.mu.Unlock()
	if h.conns[uid] == nil {
		h.conns[uid] = map[*client]struct{}{}
	}
	h.conns[uid][c] = struct{}{}
}

func (h *Hub) remove(uid int64, c *client) {
	h.mu.Lock()
	defer h.mu.Unlock()
	delete(h.conns[uid], c)
	if len(h.conns[uid]) == 0 {
		delete(h.conns, uid)
	}
}

func (h *Hub) send(uid int64, ev any) {
	h.mu.Lock()
	defer h.mu.Unlock()
	for c := range h.conns[uid] {
		select {
		case c.out <- ev:
		default: // медленный клиент — пропустит, догонит через GET /messages
		}
	}
}

func (h *Hub) closeUser(uid int64) {
	h.mu.Lock()
	defer h.mu.Unlock()
	for c := range h.conns[uid] {
		_ = c.c.Close(websocket.StatusPolicyViolation, "account deleted")
	}
}

func (s *Server) ws(w http.ResponseWriter, r *http.Request) {
	u, ok := s.userByToken(bearer(r))
	if !ok {
		fail(w, 401, "unauthorized", "Нужно войти заново")
		return
	}
	conn, err := websocket.Accept(w, r, nil)
	if err != nil {
		return
	}
	cl := &client{out: make(chan any, 64), c: conn}
	s.hub.add(u.ID, cl)
	defer s.hub.remove(u.ID, cl)
	ctx := conn.CloseRead(r.Context()) // входящие не нужны, только ping/close
	ping := time.NewTicker(25 * time.Second)
	defer ping.Stop()
	for {
		select {
		case <-ctx.Done():
			return
		case ev := <-cl.out:
			wctx, cancel := context.WithTimeout(ctx, 10*time.Second)
			err := wsjson.Write(wctx, conn, ev)
			cancel()
			if err != nil {
				return
			}
		case <-ping.C:
			pctx, cancel := context.WithTimeout(ctx, 10*time.Second)
			err := conn.Ping(pctx)
			cancel()
			if err != nil {
				return
			}
		}
	}
}
