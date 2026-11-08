import crypto from "node:crypto";

export function generateMinecraftSeed() {
  const bytes = crypto.randomBytes(8);
  // Java Minecraft seeds are signed 64-bit values. Reinterpret bytes as signed
  // two's-complement so the exact same seed can be sent to Java clients.
  let seed = BigInt("0x" + bytes.toString("hex"));
  const signedLimit = 1n << 63n;
  if (seed >= signedLimit) seed -= 1n << 64n;
  return seed.toString();
}

export const OFFICIAL_SEED_DISTRIBUTION = Object.freeze({
  low: Object.freeze([
    ["Village", 55],
    ["Shipwreck", 15],
    ["Desert Temple", 30]
  ]),
  mid: Object.freeze([
    ["Village", 30],
    ["Shipwreck", 25],
    ["Desert Temple", 25],
    ["Ruined Portal", 20]
  ]),
  high: Object.freeze([
    ["Village", 20],
    ["Shipwreck", 20],
    ["Desert Temple", 20],
    ["Ruined Portal", 20],
    ["Buried Treasure", 20]
  ])
});

export function getOfficialSeedDistribution(lowestElo) {
  if (!Number.isInteger(lowestElo) || lowestElo < 0) {
    throw new Error("Lowest Elo must be a non-negative integer");
  }
  if (lowestElo < 600) return OFFICIAL_SEED_DISTRIBUTION.low;
  if (lowestElo < 1200) return OFFICIAL_SEED_DISTRIBUTION.mid;
  return OFFICIAL_SEED_DISTRIBUTION.high;
}

export function selectOfficialSeedType(lowestElo, randomValue = crypto.randomInt(100)) {
  if (!Number.isInteger(randomValue) || randomValue < 0 || randomValue >= 100) {
    throw new Error("Seed random value must be an integer from 0 through 99");
  }

  let cursor = 0;
  for (const [seedType, weight] of getOfficialSeedDistribution(lowestElo)) {
    cursor += weight;
    if (randomValue < cursor) return seedType;
  }
  throw new Error("Official seed distribution did not select a category");
}
