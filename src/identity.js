import crypto from "node:crypto";
import bcrypt from "bcryptjs";
import { pool, withTransaction } from "./db.js";
import { config } from "./config.js";

export function normalizeUsername(username) {
  if (typeof username !== "string") {
    throw new Error("Username is required");
  }
  const normalized = username.trim();
  if (!/^[a-zA-Z0-9_]{3,32}$/.test(normalized)) {
    throw new Error("Username must be 3-32 characters using letters, numbers, or underscore");
  }
  return normalized;
}

export function validatePassword(password) {
  if (typeof password !== "string" || password.length < 8 || password.length > 128) {
    throw new Error("Password must be 8-128 characters");
  }
}

export function hashToken(token) {
  return crypto.createHash("sha256").update(token).digest("hex");
}

async function createSession(userId) {
  const token = crypto.randomBytes(32).toString("base64url");
  const expiresAt = new Date(Date.now() + config.sessionTtlDays * 86_400_000);
  await pool.query(
    "INSERT INTO sessions (user_id, token_hash, expires_at) VALUES ($1, $2, $3)",
    [userId, hashToken(token), expiresAt]
  );
  return { token, expiresAt };
}

export async function register(usernameInput, password, accountType = "offline") {
  const username = normalizeUsername(usernameInput);
  validatePassword(password);
  if (accountType !== "offline") {
    throw new Error("Verified Microsoft authentication is not configured");
  }
  const usernameKey = username.toLowerCase();
  const passwordHash = await bcrypt.hash(password, 12);

  const user = await withTransaction(async (client) => {
    const created = await client.query(
      `INSERT INTO users (username, username_key, password_hash, account_type)
       VALUES ($1, $2, $3, 'offline')
       ON CONFLICT (username_key) DO NOTHING
       RETURNING id, username, elo, account_type`,
      [username, usernameKey, passwordHash]
    );
    if (created.rowCount !== 1) {
      throw new Error("Username is already registered");
    }
    return created.rows[0];
  });

  const session = await createSession(user.id);
  return { user, ...session };
}

export async function login(usernameInput, password) {
  const username = normalizeUsername(usernameInput);
  validatePassword(password);
  const found = await pool.query(
    "SELECT id, username, elo, account_type, password_hash FROM users WHERE username_key = $1",
    [username.toLowerCase()]
  );
  if (found.rowCount !== 1 || !(await bcrypt.compare(password, found.rows[0].password_hash))) {
    throw new Error("Invalid username or password");
  }
  const { password_hash, ...user } = found.rows[0];
  const session = await createSession(user.id);
  return { user, ...session };
}

export async function authenticateToken(token) {
  if (!token || typeof token !== "string") return null;
  const result = await pool.query(
    `SELECT u.id, u.username, u.elo
     FROM sessions s
     JOIN users u ON u.id = s.user_id
     WHERE s.token_hash = $1 AND s.expires_at > NOW()`,
    [hashToken(token)]
  );
  return result.rows[0] || null;
}
