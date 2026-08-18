package com.etka.veridoc.ocr;

/**
 * Thrown when the OCR engine itself fails — missing language data, a native
 * library problem, an unreadable image format.
 *
 * <p>This signals a broken engine, not a poor read. Text that comes back
 * garbled or empty is a normal {@link OcrResult} with low confidence, and is
 * the caller's problem to interpret.
 */
public class OcrException extends RuntimeException {

    public OcrException(String message) {
        super(message);
    }

    public OcrException(String message, Throwable cause) {
        super(message, cause);
    }
}