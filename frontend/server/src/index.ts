import express from "express";
import type { ApiHealth } from "shared";
import path from "path";
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
const staticDir = path.resolve(__dirname, "../../client/dist");

const spa = express.Router();
spa.use(express.static(staticDir, { index: false }));
spa.get("*", (_req, res) => {
  res.sendFile(path.join(staticDir, "index.html"));
});

app.use(basePath, spa);

if (process.env.NODE_ENV !== "production" && basePath !== "/") {
  app.use("/", router);
  app.use("/", spa);
}

app.listen(port, () => {
  console.log(`🚀 BFF running on http://localhost:${port} (basePath: ${basePath})`);
});
