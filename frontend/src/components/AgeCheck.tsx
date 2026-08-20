import { useRef, useState } from "react";
import { checkAge, ApiError, type AgeCheckResponse } from "../api/veridoc";

const ACCEPTED = ["image/png", "image/jpeg"];
const MAX_BYTES = 10 * 1024 * 1024;

export default function AgeCheck() {
  const [file, setFile] = useState<File | null>(null);
  const [preview, setPreview] = useState<string | null>(null);
  const [requiredAge, setRequiredAge] = useState(18);
  const [result, setResult] = useState<AgeCheckResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const previousPreview = useRef<string | null>(null);

  function selectFile(selected: File | null) {
    setResult(null);
    setError(null);

    // Object URLs hold the file in memory until explicitly revoked.
    if (previousPreview.current) {
      URL.revokeObjectURL(previousPreview.current);
      previousPreview.current = null;
    }

    if (!selected) {
      setFile(null);
      setPreview(null);
      return;
    }
    if (!ACCEPTED.includes(selected.type)) {
      setError("Please choose a PNG or JPEG image.");
      return;
    }
    if (selected.size > MAX_BYTES) {
      setError("That file is larger than 10 MB.");
      return;
    }

    const url = URL.createObjectURL(selected);
    previousPreview.current = url;
    setFile(selected);
    setPreview(url);
  }

  async function submit() {
    if (!file) return;

    setBusy(true);
    setError(null);
    setResult(null);

    try {
      setResult(await checkAge(file, requiredAge));
    } catch (failure) {
      if (failure instanceof ApiError && failure.status === 422) {
        // The document was received but could not be read. The server's message
        // names the specific stage that failed, which is actionable for the user.
        setError(`Could not read the document: ${failure.message}`);
      } else if (failure instanceof ApiError) {
        setError(failure.message);
      } else {
        setError("Could not reach the verification service.");
      }
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="panel">
      <h1>Age verification</h1>
      <p className="subtitle">
        Upload an image cropped to the machine readable zone of a passport.
      </p>

      <label className="field">
        <span>Document image</span>
        <input
          type="file"
          accept="image/png,image/jpeg"
          onChange={(e) => selectFile(e.target.files?.[0] ?? null)}
        />
      </label>

      {preview && <img className="preview" src={preview} alt="Selected document" />}

      <label className="field">
        <span>Minimum age</span>
        <input
          type="number"
          min={1}
          max={120}
          value={requiredAge}
          onChange={(e) => setRequiredAge(Number(e.target.value))}
        />
      </label>

      <button onClick={submit} disabled={!file || busy}>
        {busy ? "Verifying…" : "Verify"}
      </button>

      {error && <div className="result error">{error}</div>}

      {result && (
        <div className={`result ${result.meetsRequirement ? "pass" : "fail"}`}>
          <strong>
            {result.meetsRequirement
              ? `Meets the age requirement of ${result.requiredAge}`
              : `Does not meet the age requirement of ${result.requiredAge}`}
          </strong>
          <p className="note">
            {result.trustworthy
              ? "All check digits validated — the document is internally consistent."
              : "Some check digits failed. The reading may be inaccurate; consider a clearer image."}
          </p>
        </div>
      )}

      <p className="privacy">
        This check returns only whether the age requirement is met. No date of
        birth, name, or document number is returned or stored.
      </p>
    </div>
  );
}