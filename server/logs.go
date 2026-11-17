package main

import (
	"fmt"
	"net/http"
	"strings"
	"sync"
	"time"
)

// RingLog хранит последние строки лога в памяти — для админ-панели.
type RingLog struct {
	mu    sync.Mutex
	lines []string
	max   int
	out   interface{ Write([]byte) (int, error) }
}

func newRingLog(max int, out interface{ Write([]byte) (int, error) }) *RingLog {
	return &RingLog{max: max, out: out}
}

func (r *RingLog) Write(p []byte) (int, error) {
	r.mu.Lock()
	for _, l := range strings.Split(strings.TrimRight(string(p), "\n"), "\n") {
		r.lines = append(r.lines, l)
	}
	if len(r.lines) > r.max {
		r.lines = r.lines[len(r.lines)-r.max:]
	}
	r.mu.Unlock()
	return r.out.Write(p)
}

func (r *RingLog) Tail(n int) []string {
	r.mu.Lock()
	defer r.mu.Unlock()
	if n > len(r.lines) {
		n = len(r.lines)
	}
	out := make([]string, n)
	copy(out, r.lines[len(r.lines)-n:])
	return out
}

type statusRecorder struct {
	http.ResponseWriter
	code int
}

func (s *statusRecorder) WriteHeader(c int) { s.code = c; s.ResponseWriter.WriteHeader(c) }

// Hijack нужен WebSocket.
func (s *statusRecorder) Unwrap() http.ResponseWriter { return s.ResponseWriter }

// requestLog пишет в лог метод, путь, код и время ответа (без тел запросов и токенов).
func requestLog(logf func(string, ...any), h http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path == "/v1/ws" || r.URL.Path == "/v1/health" {
			h.ServeHTTP(w, r)
			return
		}
		start := time.Now()
		rec := &statusRecorder{ResponseWriter: w, code: 200}
		h.ServeHTTP(rec, r)
		logf("%s %s → %d (%s)", r.Method, r.URL.Path, rec.code, time.Since(start).Round(time.Millisecond))
	})
}

var _ = fmt.Sprintf
