import test from "node:test";
import assert from "node:assert/strict";
import {
  getOfficialSeedDistribution,
  selectOfficialSeedType
} from "../src/seedGenerator.js";

test("uses the documented low Elo seed distribution", () => {
  assert.deepEqual(
    getOfficialSeedDistribution(599),
    [["Village", 55], ["Shipwreck", 15], ["Desert Temple", 30]]
  );
});

test("uses the documented mid Elo seed distribution", () => {
  assert.deepEqual(
    getOfficialSeedDistribution(1199),
    [["Village", 30], ["Shipwreck", 25], ["Desert Temple", 25], ["Ruined Portal", 20]]
  );
});

test("uses the documented high Elo seed distribution", () => {
  assert.deepEqual(
    getOfficialSeedDistribution(1200),
    [["Village", 20], ["Shipwreck", 20], ["Desert Temple", 20], ["Ruined Portal", 20], ["Buried Treasure", 20]]
  );
});

test("selects categories according to weighted ranges", () => {
  assert.equal(selectOfficialSeedType(599, 54), "Village");
  assert.equal(selectOfficialSeedType(599, 55), "Shipwreck");
  assert.equal(selectOfficialSeedType(599, 99), "Desert Temple");
  assert.equal(selectOfficialSeedType(1200, 70), "Ruined Portal");
  assert.equal(selectOfficialSeedType(1200, 99), "Buried Treasure");
});
