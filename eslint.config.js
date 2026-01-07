import js from "@eslint/js";
import globals from "globals";
import reactHooks from "eslint-plugin-react-hooks";
import reactRefresh from "eslint-plugin-react-refresh";
import jsxA11y from "eslint-plugin-jsx-a11y";
import tseslint from "typescript-eslint";
import prettier from "eslint-config-prettier";
import { defineConfig, globalIgnores } from "eslint/config";

const reactHooksRecommended = reactHooks.configs["recommended-latest"];
const reactRefreshVite = reactRefresh.configs.vite;
const jsxA11yRecommended = jsxA11y.configs.recommended;

export default defineConfig([
  globalIgnores(["**/dist/**", "**/build/**", "node_modules", "backend/**", "**/*.d.ts"]),

  {
    files: ["**/*.{ts,tsx,js,jsx}"],
    ignores: ["**/dist/**", "**/build/**"],
    extends: [js.configs.recommended, ...tseslint.configs.recommended, prettier],
    plugins: {
      "@typescript-eslint": tseslint.plugin,
    },
    rules: {
      "@typescript-eslint/no-unused-vars": ["warn", { argsIgnorePattern: "^_" }],
    },
  },

  {
    files: ["frontend/client/**/*.{ts,tsx,js,jsx}"],
    plugins: {
      "react-hooks": reactHooks,
      "react-refresh": reactRefresh,
      "jsx-a11y": jsxA11y,
    },
    rules: {
      ...(reactHooksRecommended?.rules ?? {}),
      ...(reactRefreshVite?.rules ?? {}),
      ...(jsxA11yRecommended?.rules ?? {}),
    },
    languageOptions: {
      ecmaVersion: 2020,
      globals: globals.browser,
    },
  },

  {
    files: ["frontend/server/**/*.{ts,tsx,js,jsx}"],
    languageOptions: {
      ecmaVersion: 2020,
      globals: globals.node,
    },
  },

  {
    files: ["frontend/shared/**/*.{ts,tsx,js}"],
    languageOptions: {
      ecmaVersion: 2020,
      globals: {},
    },
  },
]);
