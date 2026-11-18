# syntax=docker/dockerfile:1.7

FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/node:24-dev AS builder
USER root

ENV NODE_ENV=production
WORKDIR /workspace

RUN npm install --global corepack && corepack enable

COPY pnpm-lock.yaml pnpm-workspace.yaml package.json tsconfig.base.json ./.npmrc ./
COPY frontend/saksbehandling/package.json frontend/saksbehandling/
COPY frontend/saksbehandling-server/package.json frontend/saksbehandling-server/

ARG NODE_AUTH_TOKEN

RUN NODE_AUTH_TOKEN=$NODE_AUTH_TOKEN pnpm install --frozen-lockfile

ARG VITE_LOKAL_BRUKERBYTTE
ENV VITE_LOKAL_BRUKERBYTTE=${VITE_LOKAL_BRUKERBYTTE}

COPY frontend ./frontend

RUN pnpm --filter ./frontend/saksbehandling build
RUN pnpm --filter ./frontend/saksbehandling-server build
RUN pnpm --filter ./frontend/saksbehandling-server deploy --prod --legacy /deploy/server


FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/node:24-slim AS runner

ENV NODE_ENV=production
ENV STATIC_DIR=/app/client/dist

WORKDIR /app

COPY --from=builder /deploy/server/ ./
COPY --from=builder /workspace/frontend/saksbehandling-server/dist ./dist
COPY --from=builder /workspace/frontend/saksbehandling/dist ./client/dist

USER 65532:65532
ENV PORT=4000
EXPOSE 4000

CMD ["dist/index.js"]
