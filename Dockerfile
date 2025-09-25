# syntax=docker/dockerfile:1.7

ARG NODE_IMAGE=cgr.dev/chainguard/node:20
FROM ${NODE_IMAGE} AS builder

ENV NODE_ENV=production
WORKDIR /workspace

RUN corepack enable

COPY pnpm-lock.yaml pnpm-workspace.yaml package.json ./
COPY frontend/client/package.json frontend/client/
COPY frontend/server/package.json frontend/server/
COPY frontend/shared/package.json frontend/shared/

RUN pnpm install --frozen-lockfile

ARG BASE_PATH=/
ENV BASE_PATH=${BASE_PATH}
COPY frontend ./frontend
RUN pnpm --filter ./frontend/client build \
 && pnpm --filter ./frontend/server build

RUN pnpm --filter ./frontend/server deploy --prod /deploy/server


FROM ${NODE_IMAGE} AS runner
ENV NODE_ENV=production
USER nonroot
WORKDIR /app

COPY --from=builder /deploy/server/ ./

COPY --from=builder /workspace/frontend/server/dist ./dist
COPY --from=builder /workspace/frontend/client/dist ./client/dist

ENV PORT=4000
EXPOSE 4000
CMD ["node", "dist/index.js"]

