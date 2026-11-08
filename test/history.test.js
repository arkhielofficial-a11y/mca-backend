import test from "node:test";
import assert from "node:assert/strict";
import { mapHistoryRow, normalizeHistoryLimit, getMatchHistory } from "../src/history.js";

const row = {
  id: "m1",
  winner_id: "me",
  result_reason: null,
  seed_category: "village",
  finished_at: "2026-11-07T08:12:00.000Z",
  my_time_ms: 600000,
  elo_before: 1000,
  elo_after: 1020,
  opponent_time_ms: 650000,
  opponent_elo: 1010,
  opponent_name: "Rival"
};

test("maps a win from the player's point of view", () => {
  const entry = mapHistoryRow(row, "me");
  assert.equal(entry.won, true);
  assert.equal(entry.reason, "time");
  assert.equal(entry.eloChange, 20);
  assert.equal(entry.opponent, "Rival");
  assert.equal(entry.finishedAt, "2026-11-07T08:12:00.000Z");
});

test("maps a loss and keeps forfeit reason", () => {
  const entry = mapHistoryRow({ ...row, result_reason: "forfeit", elo_after: 980 }, "someone-else");
  assert.equal(entry.won, false);
  assert.equal(entry.reason, "forfeit");
  assert.equal(entry.eloChange, -20);
});

test("tolerates missing elo and times", () => {
  const entry = mapHistoryRow(
    { ...row, elo_after: null, my_time_ms: null, finished_at: null },
    "me"
  );
  assert.equal(entry.eloChange, null);
  assert.equal(entry.timeMs, null);
  assert.equal(entry.finishedAt, null);
});

test("clamps the history limit", () => {
  assert.equal(normalizeHistoryLimit(undefined), 25);
  assert.equal(normalizeHistoryLimit(0), 1);
  assert.equal(normalizeHistoryLimit(500), 50);
  assert.equal(normalizeHistoryLimit(10), 10);
});

test("queries with the user id and a clamped limit", async () => {
  let captured;
  const db = {
    query: async (sql, params) => {
      captured = { sql, params };
      return { rows: [row] };
    }
  };
  const entries = await getMatchHistory(db, "me", 999);
  assert.deepEqual(captured.params, ["me", 50]);
  assert.match(captured.sql, /m\.status = 'finished'/);
  assert.equal(entries.length, 1);
});
