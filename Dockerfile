# syntax=docker/dockerfile:1.7

ARG BUILDER_IMAGE=node:20
ARG RUNNER_IMAGE=cgr.dev/chainguard/node:latest
FROM ${BUILDER_IMAGE} AS builder

ENV NODE_ENV=production
WORKDIR /workspace

RUN corepack enable

COPY pnpm-lock.yaml pnpm-workspace.yaml package.json tsconfig.base.json ./.npmrc ./
COPY frontend/client/package.json frontend/client/
COPY frontend/server/package.json frontend/server/
COPY frontend/shared/package.json frontend/shared/

ARG NODE_AUTH_TOKEN

RUN NODE_AUTH_TOKEN=$NODE_AUTH_TOKEN pnpm install --frozen-lockfile

ARG BASE_PATH=/
ENV BASE_PATH=${BASE_PATH}
COPY frontend ./frontend
RUN pnpm --filter ./frontend/client build \
 && pnpm --filter ./frontend/server build

RUN pnpm --filter ./frontend/server deploy --prod --legacy /deploy/server


FROM ${RUNNER_IMAGE} AS runner
ENV NODE_ENV=production
WORKDIR /app

COPY --from=builder /deploy/server/ ./
COPY --from=builder /workspace/frontend/server/dist ./dist
COPY --from=builder /workspace/frontend/client/dist ./client/dist

USER 65532:65532
ENV PORT=4000
EXPOSE 4000

CMD ["dist/index.js"]
