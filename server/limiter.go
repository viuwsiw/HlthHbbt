package main

import (
	"sync"
	"time"
)

// Limiter — простое ограничение попыток входа/регистрации по IP.
type Limiter struct {
	mu     sync.Mutex
	max    int
	window time.Duration
	hits   map[string][]time.Time
}

func newLimiter(max int, window time.Duration) *Limiter {
	return &Limiter{max: max, window: window, hits: map[string][]time.Time{}}
}

func (l *Limiter) Allow(key string) bool {
	l.mu.Lock()
	defer l.mu.Unlock()
	now := time.Now()
	list := l.hits[key][:0]
	for _, t := range l.hits[key] {
		if now.Sub(t) < l.window {
			list = append(list, t)
		}
	}
	if len(list) >= l.max {
		l.hits[key] = list
		return false
	}
	l.hits[key] = append(list, now)
	return true
}
