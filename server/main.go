// HLTH HBTy messenger server: accounts, profiles with privacy, user search,
// end-to-end encrypted messages (server stores ciphertext only), WebSocket push.
package main

import (
	"context"
	"database/sql"
	"log"
	"net/http"
	"os"
	"os/signal"
	"strconv"
	"syscall"
	"time"

	_ "modernc.org/sqlite"
)

func main() {
	addr := env("ADDR", ":8080")
	dbPath := env("DB_PATH", "data/hlth.db")
	if err := os.MkdirAll(dirOf(dbPath), 0o700); err != nil {
		log.Fatal(err)
	}
	db, err := sql.Open("sqlite", dbPath+"?_pragma=journal_mode(WAL)&_pragma=foreign_keys(1)&_pragma=busy_timeout(5000)")
	if err != nil {
		log.Fatal(err)
	}
	db.SetMaxOpenConns(1) // SQLite: один писатель — без гонок
	if err := migrate(db); err != nil {
		log.Fatal(err)
	}
	s := &Server{
		db: db, hub: newHub(), limiter: newLimiter(20, time.Minute),
		mediaDir:          env("MEDIA_DIR", dirOf(dbPath)+"/media"),
		maxAccountsDevice: envInt("MAX_ACCOUNTS_PER_DEVICE", 15),
		maxAccountsTotal:  envInt("MAX_ACCOUNTS_TOTAL", 0), // 0 — без общего предела
		mediaTTLDays:      envInt("MEDIA_TTL_DAYS", 30),
	}
	if err := os.MkdirAll(s.mediaDir, 0o700); err != nil {
		log.Fatal(err)
	}
	go s.mediaJanitor()
	srv := &http.Server{Addr: addr, Handler: s.routes(), ReadHeaderTimeout: 10 * time.Second}

	go func() {
		log.Printf("listening on %s", addr)
		if err := srv.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			log.Fatal(err)
		}
	}()
	stop := make(chan os.Signal, 1)
	signal.Notify(stop, syscall.SIGINT, syscall.SIGTERM)
	<-stop
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	_ = srv.Shutdown(ctx)
}

func env(k, def string) string {
	if v := os.Getenv(k); v != "" {
		return v
	}
	return def
}

func envInt(k string, def int) int {
	if v, err := strconv.Atoi(os.Getenv(k)); err == nil {
		return v
	}
	return def
}

func dirOf(p string) string {
	for i := len(p) - 1; i >= 0; i-- {
		if p[i] == '/' {
			return p[:i]
		}
	}
	return "."
}
