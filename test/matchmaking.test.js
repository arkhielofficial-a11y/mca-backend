import test from "node:test";
import assert from "node:assert/strict";

process.env.DATABASE_URL = "postgres://test:test@127.0.0.1:5432/test";
const { MatchmakingQueue } = await import("../src/matchmaking.js");

test("pairs players only within the allowed Elo range", () => {
  const queue = new MatchmakingQueue({ range: 100, expansionPerSecond: 0 });
  queue.add({ id: "a", elo: 1000 });
  queue.add({ id: "b", elo: 1100 });

  assert.equal(queue.findPair()[0].id, "a");
});

test("does not pair players outside the allowed Elo range", () => {
  const queue = new MatchmakingQueue({ range: 100, expansionPerSecond: 0 });
  queue.add({ id: "a", elo: 1000 });
  queue.add({ id: "b", elo: 1201 });
  assert.equal(queue.findPair(), null);
});

test("rejects pairing a player against themselves", async () => {
  const queue = new MatchmakingQueue();
  await assert.rejects(
    queue.createMatch([{ id: "same", elo: 1000 }, { id: "same", elo: 1000 }]),
    /exactly two players/
  );
});

test("search range expands and caps at the documented maximum", () => {
  const queue = new MatchmakingQueue({
    range: 100,
    expansionPerSecond: 20,
    maximumRange: 250
  });
  const entry = { joinedAt: Date.now() - 20_000 };
  assert.equal(queue.getAllowedRange(entry), 250);
});
