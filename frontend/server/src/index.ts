import express from "express";
import type { Request, Response } from "express";
import type { ApiHealth } from "shared";
import path from "path";
import fs from "fs";
import { fileURLToPath } from "url";
import { createProxyMiddleware } from "http-proxy-middleware";
import { tokenXMiddleware } from "./tokenx.js";
import { logger } from "@navikt/pino-logger";
import type { ClientRequest, IncomingMessage, ServerResponse } from "http";
import type { Socket } from "net";
import rateLimit, { ipKeyGenerator, type RateLimitInfo } from "express-rate-limit";
import { createHash } from "crypto";

const {
  PORT = "4000",
  BASE_PATH = "/",
  EKSPERTBISTAND_API_BASEURL = "http://localhost:8080",
  EKSPERTBISTAND_API_AUDIENCE,
  TOKEN_X_ISSUER,
  LOCAL_SUBJECT_TOKEN,
  STATIC_DIR,
  NODE_ENV,
  GIT_COMMIT,
  NAIS_APP_IMAGE,
} = process.env;

const port = Number(PORT);

const basePath = BASE_PATH !== "/" && BASE_PATH.endsWith("/") ? BASE_PATH.slice(0, -1) : BASE_PATH;

const tokenxEnabled = Boolean(TOKEN_X_ISSUER);
if (tokenxEnabled && !EKSPERTBISTAND_API_AUDIENCE) {
  throw new Error("Mangler EKSPERTBISTAND_API_AUDIENCE for TokenX OBO.");
}

const app = express();
const api = express.Router();
const localSessionEnabled = NODE_ENV !== "production";

const hashToken = (token: string) => createHash("sha256").update(token).digest("base64");

const createRateLimiter = () =>
  rateLimit({
    windowMs: 1000,
    limit: 100,
    message: "You have exceeded the 100 requests in 1s limit!",
    standardHeaders: true,
    legacyHeaders: false,
    keyGenerator: (req) => {
      const authHeader = req.headers?.authorization ?? "";
      if (!authHeader.startsWith("Bearer ")) {
        return ipKeyGenerator(req);
      }
      const token = authHeader.substring(7);
      return hashToken(token);
    },
    handler: (req, res, _next, options) => {
      const rateLimitInfo = (req as Request & { rateLimit?: RateLimitInfo }).rateLimit;
      if (rateLimitInfo?.remaining === 0) {
        logger.error(`Rate limit reached for client ${req.ip}`);
      }
      res.status(options.statusCode).send(options.message);
    },
  });

const apiRateLimit = createRateLimiter();
const staticRateLimit = createRateLimiter();

api.use(apiRateLimit);

api.get("/internal/isAlive", (_req: Request, res: Response) => {
  const health: ApiHealth = { status: "ok" };
  res.json(health);
});

if (localSessionEnabled) {
  api.get("/oauth2/session", (_req: Request, res: Response) => {
    res.json({
      session: { ends_in_seconds: 3600 },
      tokens: { expire_in_seconds: 3600 },
    });
  });
}

const ekspertbistandBackendProxy = createProxyMiddleware({
  target: EKSPERTBISTAND_API_BASEURL,
  changeOrigin: true,
  pathRewrite: (path) => path.replace(/^\/ekspertbistand-backend/, ""),
  on: {
    proxyReq(proxyReq: ClientRequest) {
      proxyReq.removeHeader("cookie");
    },
    error(err: Error, _req: IncomingMessage, res: ServerResponse | Socket) {
      logger.error({ err }, "Proxy error mot skjema-api");
      if ("writeHead" in res && typeof (res as ServerResponse).writeHead === "function") {
        const serverRes = res as ServerResponse;
        if (!serverRes.headersSent)
          serverRes.writeHead(502, { "Content-Type": "application/json" });
        if (!serverRes.writableEnded) {
          serverRes.end(JSON.stringify({ message: "Kunne ikke kontakte ekspertbistand-api." }));
        }
      }
    },
  },
});

const tokenX = tokenXMiddleware({
  enabled: tokenxEnabled,
  audience: EKSPERTBISTAND_API_AUDIENCE,
  localSubjectToken: LOCAL_SUBJECT_TOKEN,
});

api.use("/ekspertbistand-backend", tokenX, ekspertbistandBackendProxy);

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const resolveStaticDir = (): string => {
  const defaultDir = path.resolve(__dirname, "../../client/dist");
  const resolvedPath = path.resolve(STATIC_DIR ?? defaultDir);

  if (fs.existsSync(resolvedPath)) {
    return resolvedPath;
  }

  throw new Error(`Client build not found at ${resolvedPath}`);
};

const staticDir = resolveStaticDir();

const spa = express.Router();
const staticIndexPath = path.join(staticDir, "index.html");
spa.use(staticRateLimit);
spa.use(
  express.static(staticDir, {
    index: false,
    maxAge: "1h",
    cacheControl: true,
  })
);
spa.get("{/*splat}", (_req, res) => {
  res.setHeader("Cache-Control", "no-store");
  const etagValue = GIT_COMMIT ?? NAIS_APP_IMAGE;
  if (etagValue) {
    res.setHeader("ETag", etagValue);
  }
  res.sendFile(staticIndexPath);
});

const routes = [api, spa] as const;
const mountPaths = [basePath];

if (NODE_ENV !== "production" && basePath !== "/") {
  mountPaths.push("/");
}

for (const mountPath of mountPaths) {
  app.use(mountPath, ...routes);
}

app.listen(port, () => {
  logger.info(`🚀 Ekspertbistand BFF server started on ${port} with basepath ${basePath}`);
});
