import test, { after } from "node:test";
import assert from "node:assert/strict";
import http from "node:http";

process.env.DATABASE_URL = "postgres://test:test@127.0.0.1:1/test";
const [{ createHttpApp }, { pool }] = await Promise.all([
  import("../src/http.js"),
  import("../src/db.js")
]);

const server = http.createServer(createHttpApp());
await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
const address = server.address();
const baseUrl = `http://127.0.0.1:${address.port}`;

after(async () => {
  await new Promise((resolve) => server.close(resolve));
  await pool.end();
});

test("liveness does not depend on database availability", async () => {
  const response = await fetch(`${baseUrl}/health/live`);
  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), { status: "alive" });
});
