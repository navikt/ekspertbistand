import { describe, expect, it } from "vitest";

const resolveV2RelativeImport = (file: string, specifier: string) => {
  const parts = ["src", "v2", ...file.replace(/^\.\//, "").split("/").slice(0, -1)];
  for (const segment of specifier.split("/")) {
    if (segment === "..") {
      parts.pop();
    } else if (segment !== ".") {
      parts.push(segment);
    }
  }
  return parts.join("/").replace(/\.(?:ts|tsx|js|jsx)$/, "");
};

const sourcePathFromGlobKey = (file: string) =>
  `src/v2/${file.replace(/^\.\//, "").replace(/\.(?:ts|tsx)$/, "")}`;

describe("v2 application isolation", () => {
  it("resolves the v2 import graph without crossing into original business modules", () => {
    const v2SourceFiles = import.meta.glob<string>("./**/*.{ts,tsx}", {
      eager: true,
      import: "default",
      query: "?raw",
    });
    const sourceFiles = Object.entries(v2SourceFiles).filter(
      ([file]) => !/\.test\.(?:ts|tsx)$/.test(file)
    );
    const v2StyleFiles = Object.keys(import.meta.glob("./**/*.css"));
    const v2Modules = new Set([
      ...sourceFiles.map(([file]) => sourcePathFromGlobKey(file)),
      ...v2StyleFiles.map((file) => `src/v2/${file.replace(/^\.\//, "")}`),
    ]);
    // Only infrastructure is shared with v1. Copy anything else into v2 before changing it.
    const allowedSharedModules = new Set(["src/utils/api", "src/utils/env", "src/utils/http"]);
    const forbiddenImports: string[] = [];
    const unresolvedV2Imports: string[] = [];
    const legacyRoutes: string[] = [];
    const importPattern = /\b(?:from\s*|import\s*\(\s*|import\s*)["']([^"']+)["']/g;

    for (const [file, source] of sourceFiles) {
      for (const match of source.matchAll(/["'`]\/(?:skjema|soknader|tilskuddsbrev)(?=[/"'`])/g)) {
        legacyRoutes.push(`${file} -> ${match[0]}`);
      }
      for (const match of source.matchAll(importPattern)) {
        const specifier = match[1];
        if (!specifier) continue;

        if (!specifier.startsWith(".")) continue;
        const target = resolveV2RelativeImport(file, specifier);
        if (target.startsWith("src/v2/")) {
          if (!v2Modules.has(target) && !v2Modules.has(`${target}/index`)) {
            unresolvedV2Imports.push(`${file} -> ${specifier}`);
          }
        } else if (!allowedSharedModules.has(target)) {
          forbiddenImports.push(`${file} -> ${specifier}`);
        }
      }
    }

    expect(unresolvedV2Imports).toEqual([]);
    expect(forbiddenImports).toEqual([]);
    expect(legacyRoutes).toEqual([]);
  });
});
