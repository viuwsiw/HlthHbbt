package main

import "database/sql"

func migrate(db *sql.DB) error {
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
