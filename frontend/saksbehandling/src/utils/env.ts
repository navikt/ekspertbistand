type AppEnv = "prod" | "dev" | "local" | "other";

export function detectEnv(): AppEnv {
  const mode = import.meta.env.MODE;

  if (mode === "production") {
    return "prod";
  }
  if (mode === "development") {
    return "local";
  }
  if (mode === "test") {
    return "other";
  }
  return "dev";
}

export function envSwitch<T>(values: {
  prod?: () => T;
  dev?: () => T;
  local?: () => T;
  other?: () => T;
}): T | undefined {
  const env = detectEnv();
  return values[env]?.();
}
