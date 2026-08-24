import { describe, it, expect, vi, beforeEach } from "vitest";
import { checkSubjectAge, bindSubject, ApiError } from "./veridoc";

describe("checkSubjectAge", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", vi.fn());
  });

  it("returns the body on a 200 ANSWERED response", async () => {
    vi.mocked(fetch).mockResolvedValue(
      new Response(
        JSON.stringify({ status: "ANSWERED", meets: true, minimumAge: 18, detail: null }),
        { status: 200 },
      ),
    );

    const result = await checkSubjectAge("user-1", 18);

    expect(result.status).toBe("ANSWERED");
    expect(result.meets).toBe(true);
  });

  it("treats a 404 as a NO_VERIFICATION answer, not a thrown error", async () => {
    vi.mocked(fetch).mockResolvedValue(
      new Response(
        JSON.stringify({ status: "NO_VERIFICATION", meets: null, minimumAge: null, detail: "none" }),
        { status: 404 },
      ),
    );

    const result = await checkSubjectAge("nobody", 18);

    expect(result.status).toBe("NO_VERIFICATION");
  });

  it("treats a 410 as a DOCUMENT_EXPIRED answer, not a thrown error", async () => {
    vi.mocked(fetch).mockResolvedValue(
      new Response(
        JSON.stringify({ status: "DOCUMENT_EXPIRED", meets: null, minimumAge: null, detail: "expired" }),
        { status: 410 },
      ),
    );

    const result = await checkSubjectAge("user-1", 18);

    expect(result.status).toBe("DOCUMENT_EXPIRED");
  });

  it("throws ApiError on a genuine server error", async () => {
    vi.mocked(fetch).mockResolvedValue(
      new Response(JSON.stringify({ error: "boom" }), { status: 500 }),
    );

    await expect(checkSubjectAge("user-1", 18)).rejects.toBeInstanceOf(ApiError);
  });
});

describe("bindSubject", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", vi.fn());
  });

  it("posts JSON with the subject reference", async () => {
    vi.mocked(fetch).mockResolvedValue(
      new Response(
        JSON.stringify({ recordId: "abc", status: "BOUND", subjectReference: "user-1" }),
        { status: 200 },
      ),
    );

    await bindSubject("abc", "user-1");

    const [, init] = vi.mocked(fetch).mock.calls[0];
    expect(init?.method).toBe("POST");
    expect(JSON.parse(init?.body as string)).toEqual({ subjectReference: "user-1" });
  });

  it("throws ApiError when the record is already bound", async () => {
    vi.mocked(fetch).mockResolvedValue(
      new Response(JSON.stringify({ error: "already bound" }), { status: 409 }),
    );

    await expect(bindSubject("abc", "user-1")).rejects.toBeInstanceOf(ApiError);
  });
});