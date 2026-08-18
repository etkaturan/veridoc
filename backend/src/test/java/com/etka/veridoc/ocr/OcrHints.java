package com.etka.veridoc.ocr;

import java.util.Optional;

/**
 * Constraints passed to the OCR engine to improve accuracy.
 *
 * <p>Restricting the character set is by far the most effective of these for
 * MRZ work: an MRZ can only contain A–Z, 0–9 and '&lt;', so telling the engine
 * that eliminates whole categories of misread before they happen. Without it,
 * the engine is free to propose lowercase letters, punctuation and symbols
 * that cannot legally appear.
 *
 * @param characterWhitelist characters the engine may output, or empty for no restriction
 * @param segmentation       how the engine should interpret the page layout
 * @param language           tessdata language code, e.g. "eng"
 */
public record OcrHints(
        Optional<String> characterWhitelist,
        PageSegmentation segmentation,
        String language
) {

    /** Page layout modes, mapped to Tesseract's PSM values. */
    public enum PageSegmentation {
        /** Treat the image as a single uniform block of text. */
        SINGLE_BLOCK(6),
        /** Treat the image as a single text line. */
        SINGLE_LINE(7),
        /** Treat the image as a single word. */
        SINGLE_WORD(8),
        /** Full automatic page segmentation. */
        AUTOMATIC(3);

        private final int tesseractValue;

        PageSegmentation(int tesseractValue) {
            this.tesseractValue = tesseractValue;
        }

        public int tesseractValue() {
            return tesseractValue;
        }
    }

    /** Every character legal in an ICAO 9303 machine readable zone. */
    public static final String MRZ_CHARACTERS =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789<";

    /** Hints tuned for reading a cropped MRZ band. */
    public static OcrHints forMrz() {
        return new OcrHints(
                Optional.of(MRZ_CHARACTERS),
                PageSegmentation.SINGLE_BLOCK,
                "eng");
    }

    /** Hints for reading arbitrary printed text, such as the visual inspection zone. */
    public static OcrHints forGeneralText() {
        return new OcrHints(
                Optional.empty(),
                PageSegmentation.AUTOMATIC,
                "eng");
    }
}