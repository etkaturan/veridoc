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