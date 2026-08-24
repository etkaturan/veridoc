import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import SubjectBinding from "./SubjectBinding";
import * as api from "../api/veridoc";

vi.mock("../api/veridoc", async () => {
  const actual = await vi.importActual<typeof api>("../api/veridoc");
  return { ...actual, bindSubject: vi.fn(), checkSubjectAge: vi.fn() };
});

describe("SubjectBinding", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("binds a subject and then reveals the age-check controls", async () => {
    vi.mocked(api.bindSubject).mockResolvedValue({
      recordId: "rec-1",
      status: "BOUND",
      subjectReference: "user-1",
    });

    const user = userEvent.setup();
    render(<SubjectBinding recordId="rec-1" />);

    await user.type(screen.getByLabelText(/subject reference/i), "user-1");
    await user.click(screen.getByRole("button", { name: /^bind$/i }));

    await waitFor(() => {
      expect(screen.getByText(/bound to/i)).toBeInTheDocument();
    });
    expect(screen.getByRole("button", { name: /check 18\+/i })).toBeInTheDocument();
  });

  it("shows the ANSWERED result after checking age", async () => {
    vi.mocked(api.bindSubject).mockResolvedValue({
      recordId: "rec-1",
      status: "BOUND",
      subjectReference: "user-1",
    });
    vi.mocked(api.checkSubjectAge).mockResolvedValue({
      status: "ANSWERED",
      meets: true,
      minimumAge: 18,
      detail: null,
    });

    const user = userEvent.setup();
    render(<SubjectBinding recordId="rec-1" />);

    await user.type(screen.getByLabelText(/subject reference/i), "user-1");
    await user.click(screen.getByRole("button", { name: /^bind$/i }));
    await waitFor(() => screen.getByRole("button", { name: /check 18\+/i }));
    await user.click(screen.getByRole("button", { name: /check 18\+/i }));

    await waitFor(() => {
      expect(screen.getByText(/meets the age requirement of 18/i)).toBeInTheDocument();
    });
  });

  it("shows the DOCUMENT_EXPIRED result distinctly from a failed check", async () => {
    vi.mocked(api.bindSubject).mockResolvedValue({
      recordId: "rec-1",
      status: "BOUND",
      subjectReference: "user-1",
    });
    vi.mocked(api.checkSubjectAge).mockResolvedValue({
      status: "DOCUMENT_EXPIRED",
      meets: null,
      minimumAge: null,
      detail: "The verified document expired on 2024-01-01",
    });

    const user = userEvent.setup();
    render(<SubjectBinding recordId="rec-1" />);

    await user.type(screen.getByLabelText(/subject reference/i), "user-1");
    await user.click(screen.getByRole("button", { name: /^bind$/i }));
    await waitFor(() => screen.getByRole("button", { name: /check 18\+/i }));
    await user.click(screen.getByRole("button", { name: /check 18\+/i }));

    await waitFor(() => {
      expect(screen.getByText(/expired on 2024-01-01/i)).toBeInTheDocument();
    });
  });
});