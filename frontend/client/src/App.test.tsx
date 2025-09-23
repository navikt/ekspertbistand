import { render, screen } from "@testing-library/react";
import App from "./App";

it("shows health", async () => {
  render(<App />);
  expect(await screen.findByText(/Health: ok/i)).toBeInTheDocument();
});
