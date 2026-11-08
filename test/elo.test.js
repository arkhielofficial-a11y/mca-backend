import test from "node:test";
import assert from "node:assert/strict";
import {
  calculateExpectedScore,
  calculateEloChanges,
  getKFactor
} from "../src/elo.js";

test("equal ratings have equal expected scores", () => {
  assert.equal(calculateExpectedScore(1000, 1000), 0.5);
});

test("higher rated winner gains fewer points than lower rated winner", () => {
  const favorite = calculateEloChanges(1600, 1400, 1, 32, 32);
  const underdog = calculateEloChanges(1400, 1600, 1, 32, 32);
  assert.ok(favorite.ratingA - 1600 < underdog.ratingA - 1400);
});

test("provides bracket-specific K factors", () => {
  assert.equal(getKFactor(1000), 40);
  assert.equal(getKFactor(1500), 32);
  assert.equal(getKFactor(2000), 24);
  assert.equal(getKFactor(2500), 16);
});
