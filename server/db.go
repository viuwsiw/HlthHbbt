package main

import (
	"database/sql"
	"strings"
)

// ensureColumn добавляет колонку, если её ещё нет (миграции существующих баз).
func ensureColumn(db *sql.DB, table, column, ddl string) error {
	rows, err := db.Query(`PRAGMA table_info(` + table + `)`)
	if err != nil {
		return err
	}
	defer rows.Close()
	for rows.Next() {
		var cid, notnull, pk int
		var name, ctype string
		var dflt sql.NullString
		if err := rows.Scan(&cid, &name, &ctype, &notnull, &dflt, &pk); err != nil {
			return err
		}
		if strings.EqualFold(name, column) {
			return nil
		}
	}
	_, err = db.Exec(`ALTER TABLE ` + table + ` ADD COLUMN ` + column + ` ` + ddl)
	return err
}

func migrate(db *sql.DB) error {
	if err := migrateBase(db); err != nil {
		return err
	}
	// Статусы: ручной (ONLINE/DND) и время последней активности в мессенджере.
	if err := ensureColumn(db, "users", "status", "TEXT NOT NULL DEFAULT 'ONLINE'"); err != nil {
		return err
	}
	if err := ensureColumn(db, "users", "last_active", "INTEGER NOT NULL DEFAULT 0"); err != nil {
		return err
	}
	// Лимиты: число отправленных сообщений и суточная квота медиа.
	for _, c := range [][2]string{
		{"msg_count", "INTEGER NOT NULL DEFAULT 0"},
		{"quota_start", "INTEGER NOT NULL DEFAULT 0"},
		{"quota_used", "INTEGER NOT NULL DEFAULT 0"},
		{"device_id", "TEXT NOT NULL DEFAULT ''"},
	} {
		if err := ensureColumn(db, "users", c[0], c[1]); err != nil {
			return err
		}
	}
	_, err := db.Exec(`
CREATE TABLE IF NOT EXISTS media (
	id         TEXT PRIMARY KEY,
	owner_id   INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
	size       INTEGER NOT NULL,
	created_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_media_created ON media(created_at);
CREATE TABLE IF NOT EXISTS media_access (
	media_id TEXT NOT NULL REFERENCES media(id) ON DELETE CASCADE,
	user_id  INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
	PRIMARY KEY (media_id, user_id)
);
CREATE TABLE IF NOT EXISTS registrations (
	device_id  TEXT NOT NULL,
	ip         TEXT NOT NULL,
	created_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_reg_device ON registrations(device_id);
`)
	return err
}

func migrateBase(db *sql.DB) error {
	_, err := db.Exec(`
CREATE TABLE IF NOT EXISTS users (
	id            INTEGER PRIMARY KEY AUTOINCREMENT,
	username      TEXT NOT NULL UNIQUE,
	display_name  TEXT NOT NULL,
	pass_hash     TEXT NOT NULL,
	avatar        TEXT NOT NULL DEFAULT '',
	bio           TEXT NOT NULL DEFAULT '',
	discoverable  INTEGER NOT NULL DEFAULT 1,
	profile_json  TEXT NOT NULL DEFAULT '{}',
	public_key    TEXT NOT NULL DEFAULT '',
	created_at    INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS sessions (
	token_hash TEXT PRIMARY KEY,
	user_id    INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
	created_at INTEGER NOT NULL,
	last_seen  INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_sessions_user ON sessions(user_id);
CREATE TABLE IF NOT EXISTS contacts (
	owner_id   INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
	contact_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
	created_at INTEGER NOT NULL,
	PRIMARY KEY (owner_id, contact_id)
);
CREATE TABLE IF NOT EXISTS messages (
	id           INTEGER PRIMARY KEY AUTOINCREMENT,
	sender_id    INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
	recipient_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
	ct_recipient TEXT NOT NULL,
	ct_sender    TEXT NOT NULL,
	client_id    TEXT NOT NULL,
	created_at   INTEGER NOT NULL,
	UNIQUE (sender_id, client_id)
);
CREATE INDEX IF NOT EXISTS idx_msg_recipient ON messages(recipient_id, id);
CREATE INDEX IF NOT EXISTS idx_msg_sender ON messages(sender_id, id);
`)
	return err
}
