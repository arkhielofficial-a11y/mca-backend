import { withTransaction } from "./db.js";
import {
  generateMinecraftSeed,
  selectOfficialSeedType
} from "./seedGenerator.js";

export class MatchmakingQueue {
  constructor({
    range = 100,
    expansionPerSecond = 2,
    maximumRange = 250
  } = {}) {
    this.range = range;
    this.expansionPerSecond = expansionPerSecond;
    this.maximumRange = maximumRange;
    this.entries = new Map();
  }

  add(user) {
    this.entries.set(user.id, { user, joinedAt: Date.now() });
  }

  remove(userId) {
    this.entries.delete(userId);
  }

  get(userId) {
    return this.entries.get(userId);
  }

  getSnapshot() {
    return [...this.entries.values()];
  }

  getAllowedRange(entry, now = Date.now()) {
    const waitedSeconds = (now - entry.joinedAt) / 1000;
    return Math.min(
      this.maximumRange,
      this.range + Math.floor(waitedSeconds * this.expansionPerSecond)
    );
  }

  findPair(now = Date.now()) {
    const entries = this.getSnapshot();
    for (let i = 0; i < entries.length; i++) {
      for (let j = i + 1; j < entries.length; j++) {
        const a = entries[i];
        const b = entries[j];
        const ratingDelta = Math.abs(a.user.elo - b.user.elo);
        if (
          ratingDelta <= this.getAllowedRange(a, now) &&
          ratingDelta <= this.getAllowedRange(b, now)
        ) {
          return [a.user, b.user];
        }
      }
    }
    return null;
  }

  async createMatch(players) {
    if (
      !Array.isArray(players) ||
      players.length !== 2 ||
      players[0].id === players[1].id
    ) {
      throw new Error("A match requires exactly two players");
    }

    // Take the players out of the queue before the first await. Otherwise the
    // 500 ms queue loop can pair them again while this transaction is running
    // and create duplicate matches.
    const queuedEntries = players
      .map((player) => this.entries.get(player.id))
      .filter(Boolean);
    for (const player of players) this.entries.delete(player.id);

    let match;
    try {
      match = await this.insertMatch(players);
    } catch (error) {
      // Put the players back so a failed insert does not silently drop them.
      for (const entry of queuedEntries) this.entries.set(entry.user.id, entry);
      throw error;
    }
    return match;
  }

  async insertMatch(players) {
    const match = await withTransaction(async (client) => {
      const lockedPlayers = await client.query(
        `SELECT id, elo FROM users
         WHERE id = ANY($1::uuid[])
         ORDER BY id
         FOR UPDATE`,
        [players.map((player) => player.id)]
      );
      if (lockedPlayers.rowCount !== 2) {
        throw new Error("Both matchmaking users must still exist");
      }

      const existingMatch = await client.query(
        `SELECT 1
         FROM match_players mp
         JOIN matches m ON m.id = mp.match_id
         WHERE mp.user_id = ANY($1::uuid[])
           AND m.status IN ('queued', 'running')
         LIMIT 1`,
        [players.map((player) => player.id)]
      );
      if (existingMatch.rowCount > 0) {
        const error = new Error("A matchmaking user already has an active match");
        error.code = "MRA_ACTIVE_MATCH";
        throw error;
      }

      const seed = generateMinecraftSeed();
      const lowestElo = Math.min(...lockedPlayers.rows.map((player) => player.elo));
      const seedCategory = selectOfficialSeedType(lowestElo);
      const created = await client.query(
        `INSERT INTO matches (status, seed, seed_category, started_at)
         VALUES ('running', $1, $2, NOW())
         RETURNING id, seed, seed_category, started_at`,
        [seed, seedCategory]
      );
      const row = created.rows[0];
      for (let position = 0; position < 2; position++) {
        const player = lockedPlayers.rows.find(
          (lockedPlayer) => lockedPlayer.id === players[position].id
        );
        await client.query(
          `INSERT INTO match_players
           (match_id, user_id, elo_before, position)
           VALUES ($1, $2, $3, $4)`,
          [row.id, player.id, player.elo, position + 1]
        );
      }
      return row;
    });

    return match;
  }
}
