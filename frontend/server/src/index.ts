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
import rateLimit from "express-rate-limit";

const {
  PORT = "4000",
  BASE_PATH = "/",
  EKSPERTBISTAND_API_BASEURL = "http://localhost:8080",
  EKSPERTBISTAND_API_AUDIENCE,
  TOKEN_X_ISSUER,
  LOCAL_SUBJECT_TOKEN,
  STATIC_DIR,
  NODE_ENV,
} = process.env;

const port = Number(PORT);

type Organisasjon = {
  orgnr: string;
  navn: string;
  underenheter?: Organisasjon[];
};

const basePath = BASE_PATH !== "/" && BASE_PATH.endsWith("/") ? BASE_PATH.slice(0, -1) : BASE_PATH;

const tokenxEnabled = Boolean(TOKEN_X_ISSUER);
if (tokenxEnabled && !EKSPERTBISTAND_API_AUDIENCE) {
  throw new Error("Mangler EKSPERTBISTAND_API_AUDIENCE for TokenX OBO.");
}

const app = express();
const api = express.Router();

const limiterConfig = {
  windowMs: 15 * 60 * 1000,
  limit: 100,
  standardHeaders: true,
  legacyHeaders: false,
} as const;

const apiLimiter = rateLimit(limiterConfig);
const staticLimiter = rateLimit(limiterConfig);

api.use(apiLimiter);

api.get("/api/health", (_req: Request, res: Response) => {
  const health: ApiHealth = { status: "ok" };
  res.json(health);
});

const ekspertbistandApiProxy = createProxyMiddleware({
  target: EKSPERTBISTAND_API_BASEURL,
  changeOrigin: true,
  pathRewrite: (p) => p.replace(/^\/ekspertbistand-backend/, ""),
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

const withTokenX = [
  tokenXMiddleware({
    enabled: tokenxEnabled,
    audience: EKSPERTBISTAND_API_AUDIENCE,
    localSubjectToken: LOCAL_SUBJECT_TOKEN,
  }),
];

api.get("/api/virksomheter", ...withTokenX, async (req: Request, res: Response) => {
  try {
    const authorization = Array.isArray(req.headers.authorization)
      ? req.headers.authorization[0]
      : (req.headers.authorization ?? "");

    const headers: Record<string, string> = {
      Accept: "application/json",
    };
    if (authorization) {
      headers.Authorization = authorization;
    }

    const upstream = await fetch(`${EKSPERTBISTAND_API_BASEURL}/api/organisasjoner/v1`, {
      headers,
    });

    if (!upstream.ok) {
      res.status(upstream.status).json({ message: "Kunne ikke hente organisasjoner." });
      return;
    }

    const payload = (await upstream.json()) as {
      hierarki?: Organisasjon[];
    };

    const mapOrganisasjon = (node: Organisasjon): Organisasjon => ({
      orgnr: node.orgnr,
      navn: node.navn,
      underenheter: (node.underenheter ?? []).map(mapOrganisasjon),
    });

    const organisasjoner = payload.hierarki?.map(mapOrganisasjon) ?? [];

    res.json({ organisasjoner });
  } catch (error) {
    logger.error({ error }, "Feil ved henting av organisasjoner");
    res.status(502).json({ message: "Kunne ikke kontakte ekspertbistand-api." });
  }
});

api.use("/ekspertbistand-backend", ...withTokenX, ekspertbistandApiProxy);

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const resolveStaticDir = (): string => {
  const candidates = [
    STATIC_DIR && path.resolve(STATIC_DIR),
    path.resolve(__dirname, "../client/dist"),
    path.resolve(__dirname, "../../client/dist"),
  ].filter(Boolean) as string[];

  for (const p of candidates) {
    if (fs.existsSync(p)) return p;
  }
  throw new Error(`Client build not found. Tried: ${candidates.join(", ")}`);
};

const staticDir = resolveStaticDir();

const spa = express.Router();
spa.use(staticLimiter);
spa.use(express.static(staticDir, { index: false }));
spa.get("*", (_req, res) => res.sendFile(path.join(staticDir, "index.html")));

app.use(basePath, api, spa);

if (NODE_ENV !== "production" && basePath !== "/") {
  app.use("/", api, spa);
}

app.listen(port, () => {
  logger.info(`🚀 Ekspertbistand BFF server started on ${port} with basepath ${basePath}`);
});
