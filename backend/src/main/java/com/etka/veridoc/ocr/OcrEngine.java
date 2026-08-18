package com.etka.veridoc.ocr;

import java.awt.image.BufferedImage;

/**
 * Extracts text from an image.
 *
 * <p>This is the boundary between the application and whichever OCR
 * implementation is in use. Nothing outside the {@code ocr} package should
 * know that Tesseract exists — that keeps the engine replaceable, and keeps
 * the domain testable without native libraries.
 *
 * <p>Implementations are not required to be thread-safe. Callers that need
 * concurrency should obtain a separate instance per thread, or wrap access.
 */
public interface OcrEngine {

    /**
     * Reads text from an image.
     *
     * @param image  the region to read; usually a cropped, preprocessed band
     * @param hints  constraints that improve accuracy for the expected content
     * @return the recognised text, exactly as produced — no cleanup applied
     * @throws OcrException if the engine fails
     */
    OcrResult read(BufferedImage image, OcrHints hints);
}