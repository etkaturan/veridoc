package com.etka.veridoc.ocr;

import java.util.List;

/**
 * What the OCR engine read, together with how confident it was.
 *
 * <p>Confidence is genuinely useful here. A low-confidence read that happens
 * to produce valid check digits is far more suspicious than a high-confidence
 * one, and a low-confidence read that fails them is almost certainly a bad
 * photo rather than a bad document — which is a much more helpful thing to
 * tell a user.
 *
 * @param text            the recognised text, unmodified
 * @param meanConfidence  0–100, the engine's average confidence across words
 * @param lineConfidences per-line confidence in the same order as the text lines
 */
public record OcrResult(
        String text,
        float meanConfidence,
        List<Float> lineConfidences
) {

    public OcrResult {
        lineConfidences = List.copyOf(lineConfidences);
    }

    /** Confidence below which a read should be treated as unreliable. */
    public static final float LOW_CONFIDENCE_THRESHOLD = 60.0f;

    public boolean isLowConfidence() {
        return meanConfidence < LOW_CONFIDENCE_THRESHOLD;
    }

    /** True if the engine produced nothing usable. */
    public boolean isEmpty() {
        return text == null || text.isBlank();
    }
}