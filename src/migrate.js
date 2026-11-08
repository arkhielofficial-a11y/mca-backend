import fs from "node:fs/promises";
import pg from "pg";
import { config } from "./config.js";

const sql = await fs.readFile(new URL("../sql/schema.sql", import.meta.url), "utf8");
const { Pool } = pg;
const pool = new Pool({
  connectionString: config.directDatabaseUrl,
  ssl: process.env.NODE_ENV === "production" || config.directDatabaseUrl?.includes("neon.tech")
    ? { rejectUnauthorized: true }
    : false,
  max: 1,
  connectionTimeoutMillis: 15_000,
  query_timeout: 60_000,
  statement_timeout: 60_000
});

let client;
try {
  client = await pool.connect();
  await client.query("BEGIN");
  await client.query("SELECT pg_advisory_xact_lock(hashtext('mra-schema-migration'))");
  await client.query(sql);
  await client.query("COMMIT");
  console.log(JSON.stringify({ event: "migration_completed" }));
} catch (error) {
  if (client) {
    await client.query("ROLLBACK").catch((rollbackError) => {
      console.error(JSON.stringify({
        event: "migration_rollback_failed",
        code: rollbackError.code || "UNKNOWN"
      }));
    });
  }
  console.error(JSON.stringify({
    event: "migration_failed",
    code: error?.code || "UNKNOWN",
    message: "Database schema migration failed"
  }));
  process.exitCode = 1;
} finally {
  client?.release();
  await pool.end();
}
