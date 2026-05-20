# Fix FaroErrorBoundary blank page crash

## Problem

When a React render error occurs in production, users see a blank page instead of
the error fallback ("Oops! Noe gikk galt."). The `FaroErrorBoundary` crashes
internally, which causes React to unmount the entire tree.

## Root cause

`FaroErrorBoundary` (from `@grafana/faro-react`) depends on an internal `api`
module variable that is only populated when `ReactIntegration` is registered as a
Faro instrumentation. The app initializes Faro in `frontend/client/src/observability/faro.ts`
using `@grafana/faro-web-sdk` but never registers `ReactIntegration`.

The failure sequence:

1. A render error occurs anywhere in the React tree.
2. `getDerivedStateFromError` sets `{ hasError: true, error }` -- this is fine.
3. `componentDidCatch` runs and calls `api.pushError(...)`.
4. `api` is `undefined` because `setDependencies` was never called.
5. `componentDidCatch` throws `Cannot read properties of undefined (reading 'pushError')`.
6. No parent error boundary exists to catch *this* error.
7. React unmounts the entire tree -- blank page.

## Fix

In `frontend/client/src/observability/faro.ts`, add `ReactIntegration` from
`@grafana/faro-react` to the instrumentations array:

```typescript
import { getWebInstrumentations, initializeFaro } from "@grafana/faro-web-sdk";
import { ReactIntegration } from "@grafana/faro-react";
import { TELEMETRY_COLLECTOR_URL } from "../utils/constants";

if (TELEMETRY_COLLECTOR_URL) {
  initializeFaro({
    url: TELEMETRY_COLLECTOR_URL,
    app: {
      name: "ekspertbistand-frontend",
    },
    instrumentations: [
      ...getWebInstrumentations({
        captureConsole: true,
      }),
      new ReactIntegration(),
    ],
  });
}
```

This calls `setDependencies(internalLogger, api)` during Faro initialization,
which populates the `api` variable that `FaroErrorBoundary.componentDidCatch`
depends on.

## Verification

- Trigger a render error in development (e.g., temporarily throw in a component).
- Confirm the error fallback renders instead of a blank page.
- Confirm the error is reported to Faro telemetry.

## Files to change

- `frontend/client/src/observability/faro.ts` (add ReactIntegration)
