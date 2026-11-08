import { WebSocketServer, WebSocket } from "ws";
import { authenticateToken } from "./identity.js";
import { MatchmakingQueue } from "./matchmaking.js";
import { pool, withTransaction } from "./db.js";
import { calculateEloChanges, getKFactor } from "./elo.js";
import { getMatchHistory } from "./history.js";

export function attachWebSocketServer(httpServer) {
  const wss = new WebSocketServer({ server: httpServer, maxPayload: 16 * 1024 });
  const queue = new MatchmakingQueue();
  const connections = new Map();
  const activeMatches = new Map();
  const matchStates = new Map();

  function send(socket, type, payload = {}) {
    if (socket && socket.readyState === WebSocket.OPEN) {
      socket.send(JSON.stringify({ type, ...payload }));
    }
  }

  function parseMessage(raw) {
    try {
      const value = JSON.parse(raw.toString());
      if (typeof value.type !== "string") throw new Error();
      return value;
    } catch {
      return null;
    }
  }

  async function processQueue() {
    const pair = queue.findPair();
    if (!pair) return;
    // Remove stale entries before creating a match. A socket may have closed
    // between queue.findPair() and this asynchronous database transaction.
    for (const player of pair) {
      const socket = connections.get(player.id);
      if (!socket || socket.readyState !== WebSocket.OPEN) {
        queue.remove(player.id);
        return;
      }
    }
    try {
      const match = await queue.createMatch(pair);
      const opponentIds = [pair[1], pair[0]];
      matchStates.set(match.id, { ready: new Set(), countdownSent: false });

      for (let i = 0; i < pair.length; i++) {
        const player = pair[i];
        const socket = connections.get(player.id);
        if (!socket) continue;
        activeMatches.set(player.id, { matchId: match.id, opponentId: opponentIds[i].id });
        send(socket, "match.found", {
          matchId: match.id,
          seed: match.seed,
          seedCategory: match.seed_category,
          opponent: opponentIds[i]
        });
      }
    } catch (error) {
      if (error.code === "MRA_ACTIVE_MATCH") {
        for (const player of pair) {
          queue.remove(player.id);
          send(connections.get(player.id), "error", {
            message: "A player already has an active match"
          });
        }
        return;
      }
      console.error("create match failed:", error);
    }
  }

  wss.on("connection", async (socket, request) => {
    try {
      const url = new URL(request.url, "http://localhost");
      const token = url.searchParams.get("token");
      const user = await authenticateToken(token);
      if (!user) {
        socket.close(4001, "Unauthorized");
        return;
      }

      socket.user = user;
      socket.alive = true;
      socket.on("pong", () => { socket.alive = true; });
      socket.on("error", (error) => {
        console.error(JSON.stringify({
          event: "websocket_client_error",
          code: error.code || "UNKNOWN"
        }));
      });
      connections.set(user.id, socket);

      send(socket, "session.ready", { user });

      socket.on("message", async (raw) => {
        try {
      const message = parseMessage(raw);
      if (!message) return send(socket, "error", { message: "Invalid message" });

      if (message.type === "queue.join") {
        if (activeMatches.has(user.id)) {
          return send(socket, "error", { message: "You already have an active match" });
        }
        const existingEntry = queue.get(user.id);
        if (existingEntry) {
          return send(socket, "queue.joined", { rating: existingEntry.user.elo });
        }
        // Reload the rating from the database: the copy taken at connection time
        // goes stale after a finished match, which would corrupt later Elo math.
        const fresh = await pool.query(
          "SELECT id, username, elo, queue_banned_until FROM users WHERE id = $1",
          [user.id]
        );
        const current = fresh.rows[0] || user;
        if (current.queue_banned_until && new Date(current.queue_banned_until).getTime() > Date.now()) {
          return send(socket, "error", {
            message: "Queue access is temporarily suspended after repeated forfeits"
          });
        }
        socket.user = current;
        queue.add(current);
        send(socket, "queue.joined", { rating: current.elo });
      } else if (message.type === "leaderboard.get") {
        const limit = Number.isInteger(message.limit)
          ? Math.min(100, Math.max(1, message.limit))
          : 50;
        const result = await pool.query(
          `SELECT username, elo, wins, losses,
                  ROW_NUMBER() OVER (
                    ORDER BY elo DESC, wins DESC, username_key ASC
                  )::int AS rank
           FROM users
           ORDER BY elo DESC, wins DESC, username_key ASC
           LIMIT $1`,
          [limit]
        );
        send(socket, "leaderboard.list", { entries: result.rows });
      } else if (message.type === "profile.get") {
        const result = await pool.query(
          `SELECT u.username, u.elo, u.wins, u.losses,
                  (SELECT COUNT(*)::int + 1
                     FROM users ranked
                    WHERE ranked.elo > u.elo
                       OR (ranked.elo = u.elo AND ranked.wins > u.wins)
                       OR (ranked.elo = u.elo AND ranked.wins = u.wins
                           AND ranked.username_key < u.username_key)) AS rank
           FROM users u WHERE u.id = $1`,
          [user.id]
        );
        send(socket, "profile.data", { profile: result.rows[0] || null });
      } else if (message.type === "match.history") {
        const entries = await getMatchHistory(pool, user.id, message.limit);
        send(socket, "match.history.list", { entries });
      } else if (message.type === "queue.leave") {
        queue.remove(user.id);
        send(socket, "queue.left");
      } else if (message.type === "match.ready") {
        const active = activeMatches.get(user.id);
        if (!active) return send(socket, "error", { message: "No active match" });
        const state = matchStates.get(active.matchId);
        if (!state || state.countdownSent) return;
        state.ready.add(user.id);
        send(socket, "match.ready.ack", { matchId: active.matchId });
        if (state.ready.size === 2) {
          state.countdownSent = true;
          for (const playerId of [user.id, active.opponentId]) {
            const playerSocket = connections.get(playerId);
            if (playerSocket) {
              send(playerSocket, "match.countdown", {
                matchId: active.matchId,
                countdownSeconds: 3
              });
            }
          }
        }
      } else if (message.type === "match.forfeit") {
        await forfeitMatch(user.id, activeMatches.get(user.id), socket);
      } else if (message.type === "match.split") {
        const active = activeMatches.get(user.id);
        if (!active) return;
        if (
          typeof message.split !== "string" ||
          !Number.isInteger(message.splitTimeMs) ||
          message.splitTimeMs < 0 ||
          message.splitTimeMs > 86_400_000
        ) {
          return send(socket, "error", { message: "Invalid split packet" });
        }
        const opponent = connections.get(active.opponentId);
        if (opponent) {
          send(opponent, "opponent.split", {
            matchId: active.matchId,
            split: message.split,
            splitName: String(message.splitName || ""),
            splitTimeMs: message.splitTimeMs
          });
        }
      } else if (message.type === "match.finish") {
        await finishMatch(user.id, activeMatches.get(user.id), message.finishTimeMs, socket);
      }
        } catch (error) {
          console.error(JSON.stringify({
            event: "websocket_message_failed",
            code: error?.code || "UNKNOWN"
          }));
          send(socket, "error", { message: "The server could not process that message" });
        }
      });

      socket.on("close", () => {
        if (connections.get(user.id) === socket) {
          connections.delete(user.id);
          queue.remove(user.id);
        }
      });
    } catch (error) {
      console.error(JSON.stringify({
        event: "websocket_connection_failed",
        code: error?.code || "UNKNOWN"
      }));
      if (socket.readyState === WebSocket.OPEN) {
        socket.close(1011, "Could not initialize session");
      } else {
        socket.terminate();
      }
    }
  });

  async function forfeitMatch(userId, active, socket) {
    if (!active) return send(socket, "error", { message: "No active match" });

    await withTransaction(async (client) => {
      const matchResult = await client.query(
        `SELECT m.status, m.started_at,
                mp.user_id, mp.elo_before, u.username, u.elo AS current_elo
         FROM matches m
         JOIN match_players mp ON mp.match_id = m.id
         JOIN users u ON u.id = mp.user_id
         WHERE m.id = $1
         FOR UPDATE`,
        [active.matchId]
      );
      const matchRow = matchResult.rows[0];
      if (!matchRow || matchRow.status !== "running") {
        return send(socket, "error", { message: "Match is not running" });
      }

      const loser = matchResult.rows.find((row) => row.user_id === userId);
      const winner = matchResult.rows.find((row) => row.user_id !== userId);
      if (!loser || !winner) {
        return send(socket, "error", { message: "Invalid forfeit match state" });
      }

      const elapsedSeconds = Math.max(
        0,
        (Date.now() - new Date(matchRow.started_at).getTime()) / 1000
      );
      const minimumSeconds = loser.current_elo >= 1500 ? 210 : 270;
      if (elapsedSeconds < minimumSeconds) {
        return send(socket, "error", {
          message: `Forfeit becomes available after ${Math.ceil(minimumSeconds / 60)} minutes`
        });
      }

      const changes = calculateEloChanges(
        winner.elo_before,
        loser.elo_before,
        1,
        getKFactor(winner.elo_before),
        getKFactor(loser.elo_before)
      );

      await client.query(
        `UPDATE matches
         SET status = 'finished', winner_id = $1, result_reason = 'forfeit',
             finished_at = NOW()
         WHERE id = $2`,
        [winner.user_id, active.matchId]
      );
      await client.query(
        `UPDATE match_players SET forfeited = TRUE
         WHERE match_id = $1 AND user_id = $2`,
        [active.matchId, loser.user_id]
      );
      await client.query("UPDATE users SET elo = $1 WHERE id = $2",
        [changes.ratingA, winner.user_id]);
      await client.query("UPDATE users SET elo = $1 WHERE id = $2",
        [changes.ratingB, loser.user_id]);
      await client.query(
        "UPDATE users SET wins = wins + 1 WHERE id = $1",
        [winner.user_id]
      );
      await client.query(
        `UPDATE users
         SET losses = losses + 1,
             consecutive_forfeits = consecutive_forfeits + 1
         WHERE id = $1`,
        [loser.user_id]
      );

      const loserStats = await client.query(
        `SELECT consecutive_forfeits
         FROM users WHERE id = $1`,
        [loser.user_id]
      );
      const consecutiveForfeits = loserStats.rows[0]?.consecutive_forfeits || 0;
      if (consecutiveForfeits >= 3) {
        const banMinutes = 3 * Math.pow(2, consecutiveForfeits - 3);
        await client.query(
          `UPDATE users
           SET queue_banned_until = NOW() + ($1 || ' minutes')::INTERVAL
           WHERE id = $2`,
          [banMinutes.toString(), loser.user_id]
        );
      }

      await client.query(
        `UPDATE match_players SET elo_after = $1
         WHERE match_id = $2 AND user_id = $3`,
        [changes.ratingA, active.matchId, winner.user_id]
      );
      await client.query(
        `UPDATE match_players SET elo_after = $1
         WHERE match_id = $2 AND user_id = $3`,
        [changes.ratingB, active.matchId, loser.user_id]
      );

      for (const playerId of [winner.user_id, loser.user_id]) {
        const playerSocket = connections.get(playerId);
        if (playerSocket) {
          send(playerSocket, "match.finished", {
            matchId: active.matchId,
            winnerId: winner.user_id,
            resultReason: "forfeit",
            eloAfter: playerId === winner.user_id ? changes.ratingA : changes.ratingB
          });
        }
        activeMatches.delete(playerId);
        matchStates.delete(active.matchId);
      }
    });
  }

  async function finishMatch(userId, active, finishTimeMs, socket) {
    if (!active) return send(socket, "error", { message: "No active match" });
    if (!Number.isInteger(finishTimeMs) || finishTimeMs < 0 || finishTimeMs > 86_400_000) {
      return send(socket, "error", { message: "Invalid finish time" });
    }

    await withTransaction(async (client) => {
      const matchResult = await client.query(
        `SELECT m.status, mp.user_id, mp.finish_time_ms
         FROM matches m
         JOIN match_players mp ON mp.match_id = m.id
         WHERE m.id = $1
         FOR UPDATE`,
        [active.matchId]
      );
      const matchRow = matchResult.rows.find((row) => row.status);
      if (!matchRow || matchRow.status !== "running") return;

      await client.query(
        `UPDATE match_players SET finish_time_ms = $1
         WHERE match_id = $2 AND user_id = $3`,
        [finishTimeMs, active.matchId, userId]
      );

      const players = await client.query(
        `SELECT mp.user_id, mp.finish_time_ms, mp.elo_before, u.username
         FROM match_players mp JOIN users u ON u.id = mp.user_id
         WHERE mp.match_id = $1 ORDER BY mp.position`,
        [active.matchId]
      );
      const finished = players.rows.filter((player) => player.finish_time_ms !== null);
      if (finished.length !== players.rows.length) return;

      const winner = [...players.rows].sort((a, b) => a.finish_time_ms - b.finish_time_ms)[0];
      const loser = players.rows.find((player) => player.user_id !== winner.user_id);
      const changes = calculateEloChanges(
        winner.elo_before,
        loser.elo_before,
        1,
        getKFactor(winner.elo_before),
        getKFactor(loser.elo_before)
      );

      await client.query(
        `UPDATE matches SET status = 'finished', winner_id = $1, finished_at = NOW()
         WHERE id = $2`,
        [winner.user_id, active.matchId]
      );
      // Apply exact Elo values so concurrent match accounting never double-adds.
      await client.query(
        `UPDATE users SET elo = $1, consecutive_forfeits = 0 WHERE id = $2`,
        [changes.ratingA, winner.user_id]
      );
      await client.query(
        "UPDATE users SET elo = $1 WHERE id = $2",
        [changes.ratingB, loser.user_id]
      );
      await client.query(
        `UPDATE match_players SET elo_after = $1
         WHERE match_id = $2 AND user_id = $3`,
        [changes.ratingA, active.matchId, winner.user_id]
      );
      await client.query(
        `UPDATE match_players SET elo_after = $1
         WHERE match_id = $2 AND user_id = $3`,
        [changes.ratingB, active.matchId, loser.user_id]
      );
      await client.query(
        `UPDATE users SET wins = wins + 1 WHERE id = $1`,
        [winner.user_id]
      );
      await client.query(
        `UPDATE users SET losses = losses + 1 WHERE id = $1`,
        [loser.user_id]
      );

      for (const player of players.rows) {
        const playerSocket = connections.get(player.user_id);
        if (playerSocket) {
          send(playerSocket, "match.finished", {
            matchId: active.matchId,
            winnerId: winner.user_id,
            winnerTimeMs: winner.finish_time_ms,
            eloBefore: player.user_id === winner.user_id
              ? winner.elo_before
              : loser.elo_before,
            eloAfter: player.user_id === winner.user_id
              ? changes.ratingA
              : changes.ratingB,
            eloChange: player.user_id === winner.user_id
              ? changes.ratingA - winner.elo_before
              : changes.ratingB - loser.elo_before
          });
        }
        activeMatches.delete(player.user_id);
        matchStates.delete(active.matchId);
      }
    });
  }

  const heartbeat = setInterval(() => {
    for (const socket of wss.clients) {
      if (!socket.alive) {
        socket.terminate();
        continue;
      }
      socket.alive = false;
      socket.ping();
    }
  }, 30_000);
  heartbeat.unref();

  const queueLoop = setInterval(() => {
    processQueue().catch((error) => console.error("queue loop failed:", error));
  }, 500);
  queueLoop.unref();

  wss.on("close", () => {
    clearInterval(queueLoop);
    clearInterval(heartbeat);
  });

  return { wss, queue };
}
