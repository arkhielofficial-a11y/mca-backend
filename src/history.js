const DEFAULT_LIMIT = 25;
const MAX_LIMIT = 50;

export function normalizeHistoryLimit(value) {
  if (!Number.isInteger(value)) return DEFAULT_LIMIT;
  return Math.min(MAX_LIMIT, Math.max(1, value));
}

/**
 * Converts a joined match row into the packet shape the client expects,
 * from the point of view of `userId`.
 */
export function mapHistoryRow(row, userId) {
  const won = row.winner_id === userId;
  const hasElo = Number.isInteger(row.elo_before) && Number.isInteger(row.elo_after);
  return {
    matchId: row.id,
    won,
    reason: row.result_reason || "time",
    opponent: row.opponent_name,
    opponentElo: Number.isInteger(row.opponent_elo) ? row.opponent_elo : null,
    timeMs: Number.isInteger(row.my_time_ms) ? row.my_time_ms : null,
    opponentTimeMs: Number.isInteger(row.opponent_time_ms) ? row.opponent_time_ms : null,
    eloBefore: Number.isInteger(row.elo_before) ? row.elo_before : null,
    eloAfter: Number.isInteger(row.elo_after) ? row.elo_after : null,
    eloChange: hasElo ? row.elo_after - row.elo_before : null,
    seedCategory: row.seed_category || null,
    finishedAt: row.finished_at ? new Date(row.finished_at).toISOString() : null
  };
}

/** Most recent finished matches for a player, newest first. */
export async function getMatchHistory(db, userId, limit) {
  const result = await db.query(
    `SELECT m.id, m.result_reason, m.seed_category, m.winner_id, m.finished_at,
            me.finish_time_ms AS my_time_ms, me.elo_before, me.elo_after,
            opp.finish_time_ms AS opponent_time_ms, opp.elo_before AS opponent_elo,
            u.username AS opponent_name
       FROM match_players me
       JOIN matches m ON m.id = me.match_id
       JOIN match_players opp ON opp.match_id = m.id AND opp.user_id <> me.user_id
       JOIN users u ON u.id = opp.user_id
      WHERE me.user_id = $1 AND m.status = 'finished'
      ORDER BY m.finished_at DESC NULLS LAST
      LIMIT $2`,
    [userId, normalizeHistoryLimit(limit)]
  );
  return result.rows.map((row) => mapHistoryRow(row, userId));
}
