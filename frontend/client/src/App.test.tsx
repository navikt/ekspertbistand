import { vi } from "vitest";
import { render, screen } from "@testing-library/react";
import HealthPage from "./pages/HealthPage";

vi.mock("swr", () => ({
  __esModule: true,
  default: () => ({ data: { status: "ok" } }),
}));

it("shows health", async () => {
  render(<HealthPage />);
  expect(await screen.findByText(/Health: ok/i)).toBeInTheDocument();
});
