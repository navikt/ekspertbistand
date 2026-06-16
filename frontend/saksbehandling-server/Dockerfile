# syntax=docker/dockerfile:1.7

FROM node:22 AS builder

ENV NODE_ENV=production
WORKDIR /workspace

RUN corepack enable

COPY pnpm-lock.yaml pnpm-workspace.yaml package.json tsconfig.base.json ./.npmrc ./
COPY frontend/saksbehandling/package.json frontend/saksbehandling/
COPY frontend/saksbehandling-server/package.json frontend/saksbehandling-server/

ARG NODE_AUTH_TOKEN

RUN NODE_AUTH_TOKEN=$NODE_AUTH_TOKEN pnpm install --frozen-lockfile

ARG VITE_ENABLE_MOCKS
ENV VITE_ENABLE_MOCKS=${VITE_ENABLE_MOCKS}

COPY frontend ./frontend

RUN pnpm --filter ./frontend/saksbehandling build
RUN pnpm --filter ./frontend/saksbehandling-server build
RUN pnpm --filter ./frontend/saksbehandling-server deploy --prod --legacy /deploy/server


FROM node:22-alpine AS runner

ENV NODE_ENV=production
ENV STATIC_DIR=/app/client/dist
WORKDIR /app

COPY --from=builder /deploy/server/ ./
COPY --from=builder /workspace/frontend/saksbehandling-server/dist ./dist
COPY --from=builder /workspace/frontend/saksbehandling/dist ./client/dist

USER node
ENV PORT=4000
EXPOSE 4000

CMD ["node", "dist/index.js"]
