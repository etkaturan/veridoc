const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080/api";

/**
 * The server deliberately returns only whether the age requirement is met —
 * no date of birth, no name, no document number. Nothing here to store.
 */
export interface AgeCheckResponse {
  meetsRequirement: boolean | null;
  requiredAge: number;
  trustworthy: boolean;
  message: string | null;
  recordId: string | null;
}
export class ApiError extends Error {
  readonly status: number;

  constructor(message: string, status: number) {
    super(message);
    this.status = status;
    this.name = "ApiError";
  }
}

export async function checkAge(
  file: File,
  requiredAge: number,
  signal?: AbortSignal,
): Promise<AgeCheckResponse> {
  const form = new FormData();
  form.append("file", file);

  const response = await fetch(
    `${BASE_URL}/documents/age-check?requiredAge=${requiredAge}`,
    { method: "POST", body: form, signal },
  );

  const body = await response.json().catch(() => null);

  if (!response.ok) {
    throw new ApiError(
      body?.message ?? body?.error ?? `Request failed (${response.status})`,
      response.status,
    );
  }

  return body as AgeCheckResponse;
}

export interface BindResponse {
  recordId: string;
  status: string;
  subjectReference: string;
}

export type SubjectAgeStatus = "ANSWERED" | "NO_VERIFICATION" | "DOCUMENT_EXPIRED";

export interface SubjectAgeResponse {
  status: SubjectAgeStatus;
  meets: boolean | null;
  minimumAge: number | null;
  detail: string | null;
}

async function parseOrThrow<T>(response: Response): Promise<T> {
  const body = await response.json().catch(() => null);

  // 404 and 410 are meaningful answers here, not failures — the caller
  // decides how to present "no verification" or "document expired" rather
  // than treating them as errors to catch.
  if (!response.ok && response.status !== 404 && response.status !== 410) {
    throw new ApiError(
      body?.message ?? body?.error ?? `Request failed (${response.status})`,
      response.status,
    );
  }

  return body as T;
}

/** Associates a verification record with a subject reference. */
export async function bindSubject(
  recordId: string,
  subjectReference: string,
): Promise<BindResponse> {
  const response = await fetch(`${BASE_URL}/subjects/bind/${recordId}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ subjectReference }),
  });
  return parseOrThrow<BindResponse>(response);
}

/**
 * Answers an age question about a subject from previously stored data.
 * No document is involved in this call.
 */
export async function checkSubjectAge(
  subjectReference: string,
  minimumAge: 18 | 21,
): Promise<SubjectAgeResponse> {
  const response = await fetch(
    `${BASE_URL}/subjects/${encodeURIComponent(subjectReference)}/age-check?minimumAge=${minimumAge}`,
    { method: "POST" },
  );
  return parseOrThrow<SubjectAgeResponse>(response);
}