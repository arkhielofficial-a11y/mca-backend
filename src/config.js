import "dotenv/config";

export const config = {
  port: Number(process.env.PORT || 8080),
  databaseUrl: process.env.DATABASE_URL,
  directDatabaseUrl: process.env.DIRECT_DATABASE_URL || process.env.DATABASE_URL,
  sessionTtlDays: Number(process.env.SESSION_TTL_DAYS || 30),
  apiBaseUrl: process.env.API_BASE_URL ||
    process.env.PUBLIC_BASE_URL ||
    `http://localhost:${process.env.PORT || 8080}`,
  realtimePublicUrl: process.env.REALTIME_PUBLIC_URL || "",
  websiteBaseUrl: process.env.WEBSITE_BASE_URL || ""
};

if (!config.databaseUrl) {
  throw new Error("DATABASE_URL is required");
}
