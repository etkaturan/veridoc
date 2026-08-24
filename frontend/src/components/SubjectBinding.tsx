import { useState } from "react";
import {
  bindSubject,
  checkSubjectAge,
  ApiError,
  type SubjectAgeResponse,
} from "../api/veridoc";

interface Props {
  /** The record id returned by a successful document verification. */
  recordId: string;
}

/**
 * Binds a verified document to a subject reference, then lets the caller
 * check that subject's age with no document involved. This is the payoff of
 * persisting verifications: verify once, answer forever from a boolean.
 */
export default function SubjectBinding({ recordId }: Props) {
  const [subjectReference, setSubjectReference] = useState("");
  const [bound, setBound] = useState<string | null>(null);
  const [ageResult, setAgeResult] = useState<SubjectAgeResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function handleBind() {
    if (!subjectReference.trim()) return;

    setBusy(true);
    setError(null);
    setAgeResult(null);

    try {
      const result = await bindSubject(recordId, subjectReference.trim());
      setBound(result.subjectReference);
    } catch (failure) {
      setError(failure instanceof ApiError ? failure.message : "Could not bind subject.");
    } finally {
      setBusy(false);
    }
  }

  async function handleAgeCheck(minimumAge: 18 | 21) {
    if (!bound) return;

    setBusy(true);
    setError(null);

    try {
      setAgeResult(await checkSubjectAge(bound, minimumAge));
    } catch (failure) {
      setError(failure instanceof ApiError ? failure.message : "Could not check age.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="panel subject-binding">
      <h2>Bind to a subject</h2>
      <p className="subtitle">
        Attach this verification to your own identifier, then check age later
        with no document required.
      </p>

      {!bound ? (
        <>
          <label className="field">
            <span>Subject reference</span>
            <input
              type="text"
              value={subjectReference}
              onChange={(e) => setSubjectReference(e.target.value)}
              placeholder="e.g. user-12345"
              maxLength={128}
            />
          </label>
          <button onClick={handleBind} disabled={!subjectReference.trim() || busy}>
            {busy ? "Binding…" : "Bind"}
          </button>
        </>
      ) : (
        <>
          <p className="bound-confirmation">
            Bound to <strong>{bound}</strong>
          </p>
          <div className="age-check-buttons">
            <button onClick={() => handleAgeCheck(18)} disabled={busy}>
              Check 18+
            </button>
            <button onClick={() => handleAgeCheck(21)} disabled={busy}>
              Check 21+
            </button>
          </div>
        </>
      )}

      {error && <div className="result error">{error}</div>}

      {ageResult && (
        <div className={`result ${resultClass(ageResult)}`}>
          {renderAgeResult(ageResult)}
        </div>
      )}
    </div>
  );
}

function resultClass(result: SubjectAgeResponse): string {
  if (result.status === "ANSWERED") return result.meets ? "pass" : "fail";
  return "warn";
}

function renderAgeResult(result: SubjectAgeResponse) {
  switch (result.status) {
    case "ANSWERED":
      return (
        <strong>
          {result.meets
            ? `Meets the age requirement of ${result.minimumAge}`
            : `Does not meet the age requirement of ${result.minimumAge}`}
        </strong>
      );
    case "NO_VERIFICATION":
      return <strong>No verification is on file for this subject.</strong>;
    case "DOCUMENT_EXPIRED":
      return <strong>{result.detail ?? "The verified document has expired."}</strong>;
  }
}