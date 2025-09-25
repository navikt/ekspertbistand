import express from "express";
import type { ApiHealth } from "shared";
import path from "path";
import fs from "fs";
import { fileURLToPath } from "url";

const app = express();
const port = process.env.PORT || 4000;
const basePath = process.env.BASE_PATH || "/";

const router = express.Router();

router.get("/api/health", (_req, res) => {
  const health: ApiHealth = { status: "ok" };
  res.json(health);
});

app.use(basePath, router);

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const explicitStatic = process.env.STATIC_DIR;
const candidates = [
  explicitStatic ? path.resolve(explicitStatic) : undefined,
  path.resolve(__dirname, "../client/dist"),
  path.resolve(__dirname, "../../client/dist"),
].filter(Boolean) as string[];

const staticDir = candidates.find((p) => fs.existsSync(p));
if (!staticDir) {
  throw new Error(`Client build not found. Tried: ${candidates.join(", ")}`);
}

const spa = express.Router();
spa.use(express.static(staticDir, { index: false }));
spa.get("*", (_req, res) => {
  res.sendFile(path.join(staticDir, "index.html"));
});

const mountAt = (bp: string) => {
  app.use(bp, router);
  app.use(bp, spa);
};

mountAt(basePath);
if (process.env.NODE_ENV !== "production" && basePath !== "/") {
  mountAt("/");
}

app.listen(port, () => {
  console.log(`🚀 BFF running on http://localhost:${port} (basePath: ${basePath})`);
});
