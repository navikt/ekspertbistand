import { setupServer } from "msw/node";
import { http, HttpResponse } from "msw";

export const server = setupServer(
  http.get("/api/health", () => HttpResponse.json({ status: "ok" }))
);
