const ALLOWED_NAMESPACES = ["/api/soknad", "/api/tilsagndata", "/api/organisasjoner", "/api/ereg"];

// Only unreserved URI characters and "/" are accepted in the path. Percent encoding, backslashes,
// semicolons and control characters are rejected, so the backend cannot decode or normalize the
// path into something other than what is checked here.
const SAFE_PATH = /^\/[A-Za-z0-9\-._~/]*$/;

/**
 * Checks a request target relative to the backend proxy mount, e.g. `/api/soknad/v1?x=y`.
 * The query string is not inspected.
 */
export function isBackendPathAllowed(requestTarget: string): boolean {
  const queryStart = requestTarget.indexOf("?");
  const path = queryStart === -1 ? requestTarget : requestTarget.slice(0, queryStart);

  if (!SAFE_PATH.test(path) || path.startsWith("//")) return false;
  if (path.split("/").some((segment) => segment === "." || segment === "..")) return false;

  return ALLOWED_NAMESPACES.some(
    (namespace) => path === namespace || path.startsWith(`${namespace}/`)
  );
}