import js from "@eslint/js";
import globals from "globals";
import reactHooks from "eslint-plugin-react-hooks";
import reactRefresh from "eslint-plugin-react-refresh";
import tseslint from "typescript-eslint";
import prettier from "eslint-config-prettier";
import { defineConfig, globalIgnores } from "eslint/config";

export default defineConfig([
  // Ignore build artifacts and declaration files everywhere
  globalIgnores(["**/dist/**", "**/build/**", "node_modules", "backend/**", "**/*.d.ts"]),

  // Default for everything
  {
    files: ["**/*.{ts,tsx,js,jsx}"],
    ignores: ["**/dist/**", "**/build/**"],
    extends: [
      js.configs.recommended,
      tseslint.configs.recommended,
      prettier
    ],
    rules: {
      "@typescript-eslint/no-unused-vars": [
        "warn",
        { argsIgnorePattern: "^_" }
      ]
    }
  },

  // Client (React + browser)
  {
    files: ["frontend/client/**/*.{ts,tsx,js,jsx}"],
    extends: [
      reactHooks.configs["recommended-latest"],
      reactRefresh.configs.vite
    ],
    languageOptions: {
      ecmaVersion: 2020,
      globals: globals.browser
    }
  },

  // Server (Express + Node.js)
  {
    files: ["frontend/server/**/*.{ts,tsx,js,jsx}"],
    languageOptions: {
      ecmaVersion: 2020,
      globals: globals.node
    }
  },

  // Shared (pure TS, no globals needed beyond ES2020)
  {
    files: ["frontend/shared/**/*.{ts,tsx,js}"],
    languageOptions: {
      ecmaVersion: 2020,
      globals: {}
    }
  }
]);
