/// <reference types="vitest" />
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react-swc";

// https://vite.dev/config/
export default defineConfig(() => {
  const envBase = process.env.BASE_PATH || process.env.VITE_BASE_PATH;
  const base = envBase && envBase !== "/" ? (envBase.endsWith("/") ? envBase : `${envBase}/`) : "/";

  return {
    base,
    plugins: [react()],
    server: {
      proxy: {
        "/api": "http://localhost:4000",
      },
    },
    test: {
      globals: true,
      environment: "jsdom",
      setupFiles: "./src/test/setup.ts",
      css: true,
      exclude: ["e2e/**", "node_modules/**"],
      coverage: { reporter: ["text", "lcov"] },
    },
  };
});
