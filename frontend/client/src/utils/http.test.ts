import { describe, expect, it } from "vitest";
import { HttpError, isUnauthorizedError, parseErrorMessage, resolveApiError } from "./http";

describe("http utils", () => {
  it("extracts a message from a JSON error response", async () => {
    const response = new Response(JSON.stringify({ message: "Something went wrong" }), {
      status: 400,
      headers: { "Content-Type": "application/json" },
    });

    await expect(parseErrorMessage(response)).resolves.toBe("Something went wrong");
  });

  it("returns null when the response body is not JSON", async () => {
    const response = new Response("not-json", { status: 500 });
    await expect(parseErrorMessage(response)).resolves.toBeNull();
  });

  it("flags unauthorized http errors", () => {
    const error = new HttpError("Unauthorized", { status: 401 });
    expect(isUnauthorizedError(error)).toBe(true);
    expect(resolveApiError(error, "Fallback")).toEqual({
      message: "Unauthorized",
      requiresLogin: true,
    });
  });
});
