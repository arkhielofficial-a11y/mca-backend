CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS users (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  username VARCHAR(32) NOT NULL,
  username_key VARCHAR(32) NOT NULL UNIQUE,
  password_hash TEXT NOT NULL,
  account_type VARCHAR(16) NOT NULL DEFAULT 'offline'
    CHECK (account_type IN ('online', 'offline')),
  microsoft_subject TEXT,
  elo INTEGER NOT NULL DEFAULT 1000 CHECK (elo >= 0),
  wins INTEGER NOT NULL DEFAULT 0 CHECK (wins >= 0),
  losses INTEGER NOT NULL DEFAULT 0 CHECK (losses >= 0),
  placement_matches INTEGER NOT NULL DEFAULT 0 CHECK (placement_matches >= 0),
  consecutive_forfeits INTEGER NOT NULL DEFAULT 0 CHECK (consecutive_forfeits >= 0),
  queue_banned_until TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT users_username_format CHECK (username_key = lower(username_key))
);

CREATE TABLE IF NOT EXISTS sessions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token_hash CHAR(64) NOT NULL UNIQUE,
  expires_at TIMESTAMPTZ NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS sessions_user_id_idx ON sessions(user_id);
CREATE INDEX IF NOT EXISTS sessions_expires_at_idx ON sessions(expires_at);

CREATE TABLE IF NOT EXISTS matches (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  status VARCHAR(16) NOT NULL CHECK (status IN ('queued', 'running', 'finished', 'cancelled')),
  seed VARCHAR(20) NOT NULL,
  seed_category VARCHAR(32),
  result_reason VARCHAR(16),
  winner_id UUID REFERENCES users(id),
  started_at TIMESTAMPTZ,
  finished_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS match_players (
  match_id UUID NOT NULL REFERENCES matches(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES users(id),
  finish_time_ms INTEGER CHECK (finish_time_ms IS NULL OR finish_time_ms >= 0),
  forfeited BOOLEAN NOT NULL DEFAULT FALSE,
  elo_before INTEGER NOT NULL,
  elo_after INTEGER,
  position SMALLINT CHECK (position IN (1, 2)),
  PRIMARY KEY (match_id, user_id),
  UNIQUE (match_id, position)
);
CREATE INDEX IF NOT EXISTS match_players_user_id_idx ON match_players(user_id);
CREATE INDEX IF NOT EXISTS matches_status_created_idx ON matches(status, created_at);

ALTER TABLE users ADD COLUMN IF NOT EXISTS account_type VARCHAR(16) NOT NULL DEFAULT 'offline';
ALTER TABLE users ADD COLUMN IF NOT EXISTS microsoft_subject TEXT;
ALTER TABLE users ADD COLUMN IF NOT EXISTS placement_matches INTEGER NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN IF NOT EXISTS consecutive_forfeits INTEGER NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN IF NOT EXISTS queue_banned_until TIMESTAMPTZ;
ALTER TABLE matches ADD COLUMN IF NOT EXISTS seed_category VARCHAR(32);
ALTER TABLE matches ADD COLUMN IF NOT EXISTS result_reason VARCHAR(16);
ALTER TABLE match_players ADD COLUMN IF NOT EXISTS forfeited BOOLEAN NOT NULL DEFAULT FALSE;
CREATE UNIQUE INDEX IF NOT EXISTS users_microsoft_subject_idx
  ON users(microsoft_subject) WHERE microsoft_subject IS NOT NULL;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'users_account_type_check'
  ) THEN
    ALTER TABLE users ADD CONSTRAINT users_account_type_check
      CHECK (account_type IN ('online', 'offline')) NOT VALID;
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'users_placement_matches_check'
  ) THEN
    ALTER TABLE users ADD CONSTRAINT users_placement_matches_check
      CHECK (placement_matches >= 0) NOT VALID;
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'users_consecutive_forfeits_check'
  ) THEN
    ALTER TABLE users ADD CONSTRAINT users_consecutive_forfeits_check
      CHECK (consecutive_forfeits >= 0) NOT VALID;
  END IF;
END;
$$;
ALTER TABLE users VALIDATE CONSTRAINT users_account_type_check;
ALTER TABLE users VALIDATE CONSTRAINT users_placement_matches_check;
ALTER TABLE users VALIDATE CONSTRAINT users_consecutive_forfeits_check;

CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = NOW();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS users_set_updated_at ON users;
CREATE TRIGGER users_set_updated_at
BEFORE UPDATE ON users
FOR EACH ROW EXECUTE FUNCTION set_updated_at();
