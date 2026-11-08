export function calculateExpectedScore(ratingA, ratingB) {
  return 1 / (1 + 10 ** ((ratingB - ratingA) / 400));
}

export function calculateEloChanges(ratingA, ratingB, scoreA, kFactorA = 32, kFactorB = 32) {
  if (![0, 0.5, 1].includes(scoreA)) {
    throw new Error("ELO score must be 0, 0.5, or 1");
  }
  if (![ratingA, ratingB, kFactorA, kFactorB].every((value) =>
    Number.isInteger(value) && value >= 0
  )) {
    throw new Error("ELO ratings and K-factors must be non-negative integers");
  }
  const expectedA = calculateExpectedScore(ratingA, ratingB);
  const expectedB = 1 - expectedA;
  const scoreB = 1 - scoreA;
  return {
    ratingA: Math.max(0, Math.round(ratingA + kFactorA * (scoreA - expectedA))),
    ratingB: Math.max(0, Math.round(ratingB + kFactorB * (scoreB - expectedB)))
  };
}

export function getKFactor(rating) {
  if (rating < 1200) return 40;
  if (rating < 1800) return 32;
  if (rating < 2400) return 24;
  return 16;
}
