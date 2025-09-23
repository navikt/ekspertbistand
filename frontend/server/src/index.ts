import express from "express";
import type { ApiHealth } from "shared";

const app = express();
const port = process.env.PORT || 4000;

app.get("/api/health", (_req, res) => {
  const health: ApiHealth = { status: "ok" };
  res.json(health);
});

app.listen(port, () => {
  console.log(`🚀 BFF running on http://localhost:${port}`);
});
