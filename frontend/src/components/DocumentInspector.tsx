import { useState } from "react";
import { getDocumentDetail, type DocumentDetailResponse } from "../api/veridoc";

const ACCEPTED = ["image/png", "image/jpeg"];
const MAX_BYTES = 10 * 1024 * 1024;

/**
 * Uploads any document image and shows everything the pipeline extracted.
 *
 * <p>Unlike AgeCheck, this is an inspection tool: it exposes the full
 * document data for testing and demonstration, not the privacy-preserving
 * production flow.
 */
export default function DocumentInspector() {
  const [preview, setPreview] = useState<string | null>(null);
  const [result, setResult] = useState<DocumentDetailResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function handleFile(file: File | null) {
    setResult(null);
    setError(null);

    if (!file) {
      setPreview(null);
      return;
    }
    if (!ACCEPTED.includes(file.type)) {
      setError("Please choose a PNG or JPEG image.");
      return;
    }
    if (file.size > MAX_BYTES) {
      setError("That file is larger than 10 MB.");
      return;
    }

    setPreview(URL.createObjectURL(file));
    setBusy(true);

    try {
      setResult(await getDocumentDetail(file));
    } catch {
      setError("Could not reach the verification service.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="panel">
      <h1>Document inspector</h1>
      <p className="subtitle">
        Upload any ID document photo to see everything the pipeline extracts —
        for testing, not the privacy-preserving age-check flow above.
      </p>

      <label className="field">
        <span>Document image</span>
        <input
          type="file"
          accept="image/png,image/jpeg"
          onChange={(e) => handleFile(e.target.files?.[0] ?? null)}
        />
      </label>

      {preview && <img className="preview" src={preview} alt="Uploaded document" />}
      {busy && <p className="subtitle">Processing…</p>}

      {error && <div className="result error">{error}</div>}

      {result && !result.success && (
        <div className="result fail">
          <strong>Could not read this document</strong>
          <p className="note">{result.message}</p>
        </div>
      )}

      {result?.success && (
        <div className={`result ${result.trustworthy ? "pass" : "warn"}`}>
          <strong>
            {result.trustworthy
              ? "All check digits validated"
              : "Some check digits failed — data may be misread"}
          </strong>
          {!result.trustworthy && result.failedChecks.length > 0 && (
            <p className="note">Failed: {result.failedChecks.join(", ")}</p>
          )}

          <table className="detail-table">
            <tbody>
              <tr><td>Format</td><td>{result.format}</td></tr>
              <tr><td>Name</td><td>{result.givenNames.join(" ")} {result.surname}</td></tr>
              <tr><td>Document number</td><td>{result.documentNumber}</td></tr>
              <tr><td>Issuing state</td><td>{result.issuingState}</td></tr>
              <tr><td>Nationality</td><td>{result.nationality}</td></tr>
              <tr><td>Sex</td><td>{result.sex}</td></tr>
              <tr><td>Date of birth</td><td>{result.dateOfBirth}</td></tr>
              <tr><td>Age</td><td>{result.age}</td></tr>
              <tr><td>Expiry date</td><td>{result.expiryDate}</td></tr>
              <tr><td>Expired</td><td>{result.expired ? "Yes" : "No"}</td></tr>
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}