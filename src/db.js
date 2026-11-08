import pg from "pg";
import { config } from "./config.js";

const { Pool } = pg;

export const pool = new Pool({
  connectionString: config.databaseUrl,
  ssl: process.env.NODE_ENV === "production" || config.databaseUrl.includes("neon.tech")
    ? { rejectUnauthorized: true }
    : false,
  max: 4,
  min: 0,
  idleTimeoutMillis: 30_000,
  connectionTimeoutMillis: 10_000,
  query_timeout: 15_000,
  statement_timeout: 15_000
});

pool.on("error", (error) => {
  console.error(JSON.stringify({
    event: "database_idle_client_error",
    code: error.code || "UNKNOWN"
  }));
});

export async function withTransaction(work) {
  const client = await pool.connect();
  try {
    await client.query("BEGIN");
    const result = await work(client);
    await client.query("COMMIT");
    return result;
  } catch (error) {
    try {
      await client.query("ROLLBACK");
    } catch (rollbackError) {
      console.error(JSON.stringify({
        event: "database_rollback_failed",
        code: rollbackError.code || "UNKNOWN"
      }));
    }
    throw error;
  } finally {
    client.release();
  }
}
