export type RuntimeEnv = "prod" | "dev" | "local" | "other";

const localHosts = new Set(["localhost", "127.0.0.1", "::1"]);

export const detectEnv = (): RuntimeEnv => {
  if (typeof window === "undefined") return "other";

  const host = window.location.hostname;

  if (localHosts.has(host)) return "local";

  if (/\.intern\.dev\.nav\.no$/i.test(host) || /\.dev\.nav\.no$/i.test(host)) return "dev";
  if (/\.intern\.nav\.no$/i.test(host) || /\.nav\.no$/i.test(host)) return "prod";

  return "other";
};

export const isLocal = () => detectEnv() === "local";
export const isDev = () => detectEnv() === "dev";
export const isProd = () => detectEnv() === "prod";

export function envSwitch<T>(options: {
  prod: () => T;
  dev: () => T;
  local?: () => T;
  other?: () => T;
}): T {
  const env = detectEnv();
  if (env === "prod") return options.prod();
  if (env === "dev") return options.dev();
  if (env === "local" && options.local) return options.local();
  return (options.other ?? options.dev)();
}
