import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import AgeCheck from "./AgeCheck";
import * as api from "../api/veridoc";

vi.mock("../api/veridoc", async () => {
  const actual = await vi.importActual<typeof api>("../api/veridoc");
  return { ...actual, checkAge: vi.fn() };
});

// jsdom has no createObjectURL; the component only uses it for a preview
// image, which is not under test here.
beforeEach(() => {
  URL.createObjectURL = vi.fn(() => "blob:mock");
  URL.revokeObjectURL = vi.fn();
});

function makeFile(): File {
  return new File(["fake-image-bytes"], "specimen.png", { type: "image/png" });
}

describe("AgeCheck", () => {
  it("offers subject binding only when the result is trustworthy", async () => {
    vi.mocked(api.checkAge).mockResolvedValue({
      meetsRequirement: true,
      requiredAge: 18,
      trustworthy: true,
      message: null,
      recordId: "rec-1",
    });

    render(<AgeCheck />);
    const input = screen.getByLabelText(/document image/i) as HTMLInputElement;

    const { default: userEvent } = await import("@testing-library/user-event");
    const user = userEvent.setup();
    await user.upload(input, makeFile());
    await user.click(screen.getByRole("button", { name: /verify/i }));

    await waitFor(() => {
      expect(screen.getByText(/bind to a subject/i)).toBeInTheDocument();
    });
  });

  it("does not offer subject binding when the result is untrustworthy", async () => {
    vi.mocked(api.checkAge).mockResolvedValue({
      meetsRequirement: true,
      requiredAge: 18,
      trustworthy: false,
      message: null,
      recordId: "rec-2",
    });

    render(<AgeCheck />);
    const input = screen.getByLabelText(/document image/i) as HTMLInputElement;

    const { default: userEvent } = await import("@testing-library/user-event");
    const user = userEvent.setup();
    await user.upload(input, makeFile());
    await user.click(screen.getByRole("button", { name: /verify/i }));

    await waitFor(() => {
      expect(screen.getByText(/meets the age requirement/i)).toBeInTheDocument();
    });
    expect(screen.queryByText(/bind to a subject/i)).not.toBeInTheDocument();
  });
});