/// <reference types="vitest" />
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react-swc";

// https://vite.dev/config/
export default defineConfig(() => {
  const appBasePath = process.env.VITE_APP_BASE_PATH || process.env.BASE_PATH || "/";
  process.env.VITE_APP_BASE_PATH = appBasePath;
  const envBase = process.env.VITE_BASE_PATH || process.env.BASE_PATH;
  const base = envBase && envBase !== "/" ? (envBase.endsWith("/") ? envBase : `${envBase}/`) : "/";

  return {
    base,
    plugins: [react()],
    server: {
      proxy: {
        "/ekspertbistand-backend": {
          target: "http://localhost:4000",
          changeOrigin: true,
        },
      },
    },
    ssr: {
      noExternal: ["react-router", "react-router-dom"],
    },
    test: {
      globals: true,
      environment: "jsdom",
      pool: "vmThreads",
      setupFiles: ["./src/test/polyfills.ts", "./src/test/setup.ts"],
      css: true,
      deps: {
        inline: [/react-router/, /react-router-dom/],
      },
      server: {
        deps: {
          inline: ["react-router", "react-router-dom"],
        },
      },
      exclude: ["e2e/**", "node_modules/**"],
      coverage: { reporter: ["text", "lcov"] },
    },
  };
});
