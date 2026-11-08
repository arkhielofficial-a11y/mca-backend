import express from "express";
import helmet from "helmet";
import { pool } from "./db.js";
import { register, login, authenticateToken } from "./identity.js";

function asyncHandler(handler) {
  return (req, res, next) => Promise.resolve(handler(req, res, next)).catch(next);
}

export function createHttpApp() {
  const app = express();
  app.disable("x-powered-by");
  app.use(helmet());
  app.use(express.json({ limit: "32kb" }));

  app.get("/health/live", (_req, res) => {
    res.status(200).json({ status: "alive" });
  });

  app.get("/health/ready", async (_req, res) => {
    try {
      await pool.query("SELECT 1");
      res.status(200).json({ status: "ready" });
    } catch (error) {
      console.error(JSON.stringify({
        event: "readiness_database_check_failed",
        code: error.code || "UNKNOWN"
      }));
      res.status(503).json({ status: "not_ready" });
    }
  });

  app.get("/health", async (_req, res) => {
    try {
      await pool.query("SELECT 1");
      res.status(200).json({ status: "ready" });
    } catch (error) {
      console.error(JSON.stringify({
        event: "health_database_check_failed",
        code: error.code || "UNKNOWN"
      }));
      res.status(503).json({ status: "not_ready" });
    }
  });

  app.post("/v1/auth/register", async (req, res) => {
    try {
      const result = await register(
        req.body?.username,
        req.body?.password,
        req.body?.accountType || "offline"
      );
      res.status(201).json({
        token: result.token,
        expiresAt: result.expiresAt,
        user: result.user
      });
    } catch (error) {
      if (error.code) {
        console.error(JSON.stringify({
          event: "registration_database_failed",
          code: error.code
        }));
        return res.status(503).json({ error: "Authentication service unavailable" });
      }
      res.status(400).json({ error: error.message });
    }
  });

  app.post("/v1/auth/login", async (req, res) => {
    try {
      const result = await login(req.body?.username, req.body?.password);
      res.json({ token: result.token, expiresAt: result.expiresAt, user: result.user });
    } catch (error) {
      if (error.code) {
        console.error(JSON.stringify({
          event: "login_database_failed",
          code: error.code
        }));
        return res.status(503).json({ error: "Authentication service unavailable" });
      }
      res.status(401).json({ error: error.message });
    }
  });

  app.get("/v1/leaderboard", asyncHandler(async (req, res) => {
    const limit = Math.min(100, Math.max(1, Number(req.query.limit || 50)));
    const result = await pool.query(
      `SELECT username, elo, wins, losses, created_at,
              ROW_NUMBER() OVER (
                ORDER BY elo DESC, wins DESC, username_key ASC
              )::int AS rank
       FROM users
       ORDER BY elo DESC, wins DESC, username_key ASC
       LIMIT $1`,
      [limit]
    );
    res.json({ leaderboard: result.rows });
  }));

  app.get("/v1/profile/me", async (req, res) => {
    try {
      const authorization = req.headers.authorization;
      if (typeof authorization !== "string" || !authorization.startsWith("Bearer ")) {
        return res.status(401).json({ error: "Authentication required" });
      }
      const user = await authenticateToken(authorization.slice("Bearer ".length));
      if (!user) return res.status(401).json({ error: "Invalid session" });

      const profile = await pool.query(
        `SELECT u.id, u.username, u.elo, u.wins, u.losses, u.created_at,
                (SELECT COUNT(*)::int + 1
                   FROM users ranked
                  WHERE ranked.elo > u.elo
                     OR (ranked.elo = u.elo AND ranked.wins > u.wins)
                     OR (ranked.elo = u.elo AND ranked.wins = u.wins
                         AND ranked.username_key < u.username_key)) AS rank
         FROM users u WHERE u.id = $1`,
        [user.id]
      );
      res.json({ profile: profile.rows[0] });
    } catch (error) {
      console.error(JSON.stringify({
        event: "profile_load_failed",
        code: error.code || "UNKNOWN"
      }));
      res.status(500).json({ error: "Could not load profile" });
    }
  });

  app.use((_req, res) => res.status(404).json({ error: "Not found" }));
  app.use((error, _req, res, _next) => {
    console.error(JSON.stringify({
      event: "http_request_failed",
      code: error.code || "UNKNOWN"
    }));
    res.status(500).json({ error: "Internal server error" });
  });
  return app;
}
