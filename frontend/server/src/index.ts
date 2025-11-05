import express from "express";
import type { NextFunction, Request, Response } from "express";
import type { ApiHealth } from "shared";
import path from "path";
import fs from "fs";
import { fileURLToPath } from "url";
import { createProxyMiddleware } from "http-proxy-middleware";
import { getToken, requestTokenxOboToken } from "@navikt/oasis";
import type { ClientRequest, IncomingMessage, ServerResponse } from "http";
import type { Socket } from "net";

const app = express();
const port = process.env.PORT || 4000;
const basePathEnv = process.env.BASE_PATH || "/";
const basePath =
  basePathEnv !== "/" && basePathEnv.endsWith("/") ? basePathEnv.slice(0, -1) : basePathEnv;

const router = express.Router();

router.get("/api/health", (_req: Request, res: Response) => {
  const health: ApiHealth = { status: "ok" };
  res.json(health);
});

const skjemaTarget = process.env.EKSPERTBISTAND_API_BASEURL || "http://localhost:8080";

const tokenxEnabled = Boolean(process.env.TOKEN_X_ISSUER);
const skjemaAudience = process.env.EKSPERTBISTAND_API_AUDIENCE;

if (tokenxEnabled && !skjemaAudience) {
  throw new Error("Mangler EKSPERTBISTAND_API_AUDIENCE for TokenX OBO.");
}

const skjemaProxy = createProxyMiddleware({
  target: skjemaTarget,
  changeOrigin: true,
  pathRewrite: (path) => path.replace(/^\/ekspertbistand-backend/, ""),
  on: {
    proxyReq(proxyReq: ClientRequest) {
      proxyReq.removeHeader("cookie");
    },
    error(err: Error, _req: IncomingMessage, res: ServerResponse | Socket) {
      console.error("Proxy error mot skjema-api:", err);
      if ("writeHead" in res && typeof res.writeHead === "function") {
        const serverRes = res as ServerResponse;
        if (!serverRes.headersSent) {
          serverRes.writeHead(502, { "Content-Type": "application/json" });
        }
        if (!serverRes.writableEnded) {
          serverRes.end(JSON.stringify({ message: "Kunne ikke kontakte skjema-tjenesten." }));
        }
      }
    },
  },
});

router.use("/ekspertbistand-backend", async (req: Request, res: Response, next: NextFunction) => {
  try {
    const subjectToken = tokenxEnabled ? getToken(req) : undefined;
    delete req.headers.cookie;

    if (!tokenxEnabled) {
      const localToken = process.env.LOCAL_SUBJECT_TOKEN || "faketoken";
      req.headers.authorization = `Bearer ${localToken}`;
      return skjemaProxy(req, res, next);
    }

    if (!subjectToken) {
      res.status(401).json({ message: "Mangler innloggings-token." });
      return;
    }

    const oboResult = await requestTokenxOboToken(subjectToken, skjemaAudience!);
    if (!oboResult.ok) {
      console.error("TokenX OBO feilet:", oboResult.error);
      res.status(401).json({ message: "Kunne ikke hente tilgangstoken." });
      return;
    }

    req.headers.authorization = `Bearer ${oboResult.token}`;
    return skjemaProxy(req, res, next);
  } catch (error) {
    console.error("Uventet feil ved TokenX OBO:", error);
    res.status(500).json({ message: "Uventet feil ved tokenutveksling." });
  }
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
