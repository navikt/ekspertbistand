import { render, screen } from "@testing-library/react";
import HealthPage from "./pages/HealthPage";

it("shows health", async () => {
  render(<HealthPage />);
  expect(await screen.findByText(/Health: ok/i)).toBeInTheDocument();
});
