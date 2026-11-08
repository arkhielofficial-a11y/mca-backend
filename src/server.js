import http from "node:http";
import { createHttpApp } from "./http.js";
import { attachWebSocketServer } from "./websocket.js";
import { config } from "./config.js";
import { pool } from "./db.js";

const app = createHttpApp();
const server = http.createServer(app);
const { wss } = attachWebSocketServer(server);

server.listen(config.port, "0.0.0.0", () => {
  console.log(JSON.stringify({
    event: "server_started",
    port: config.port
  }));
  pool.query("SELECT 1").then(() => {
    console.log(JSON.stringify({ event: "database_connected" }));
  }).catch((error) => {
    console.error(JSON.stringify({
      event: "database_initial_connection_failed",
      code: error?.code || "UNKNOWN"
    }));
  });
});

let shuttingDown = false;
async function shutdown(signal) {
  if (shuttingDown) return;
  shuttingDown = true;
  console.log(JSON.stringify({ event: "server_shutdown", signal }));

  const forceExit = setTimeout(() => process.exit(1), 10_000);
  forceExit.unref();
  for (const socket of wss.clients) {
    socket.close(1001, "Server shutting down");
  }

  await Promise.all([
    new Promise((resolve) => server.close(resolve)),
    new Promise((resolve) => wss.close(resolve))
  ]);
  clearTimeout(forceExit);
  await pool.end();
  process.exit(0);
}
process.on("SIGTERM", () => shutdown("SIGTERM"));
process.on("SIGINT", () => shutdown("SIGINT"));
