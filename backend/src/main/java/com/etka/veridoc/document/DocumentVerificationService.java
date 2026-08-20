package com.etka.veridoc.document;

import com.etka.veridoc.mrz.MrzData;
import com.etka.veridoc.mrz.MrzFormat;
import com.etka.veridoc.mrz.MrzNormalizer;
import com.etka.veridoc.mrz.MrzParser;
import com.etka.veridoc.mrz.MrzParserRegistry;
import com.etka.veridoc.mrz.MrzVerification;
import com.etka.veridoc.ocr.MrzExtractor;
import com.etka.veridoc.ocr.TesseractOcrEngine;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Orchestrates the verification pipeline: extract, normalise, detect, parse,
 * verify.
 */
@Service
public class DocumentVerificationService {

    private final MrzParserRegistry registry;

    public DocumentVerificationService(MrzParserRegistry registry) {
        this.registry = registry;
    }

    /**
     * Runs a document image through the full pipeline.
     *
     * @param image an image of the document, cropped to the MRZ band
     * @param today reference date for interpreting two-digit years
     */
    public VerificationOutcome verify(BufferedImage image, LocalDate today) {
        // A TessBaseAPI holds mutable native state and is not thread-safe, so a
        // shared singleton would corrupt results under concurrent requests. One
        // engine per call is the simple correct choice; if initialisation cost
        // becomes measurable, pool them rather than sharing one.
        List<String> rawLines;
        try (TesseractOcrEngine engine = TesseractOcrEngine.english()) {
            rawLines = new MrzExtractor(engine).extract(image);
        }

        List<String> lines = MrzNormalizer.normalize(String.join("\n", rawLines));

        if (lines.isEmpty()) {
            return VerificationOutcome.unreadable("No text could be extracted from the image");
        }

        Optional<MrzFormat> format = MrzFormat.detect(lines);
        if (format.isEmpty()) {
            return VerificationOutcome.unreadable(
                    "Extracted text does not match any ICAO 9303 layout (%d line(s), lengths %s)"
                            .formatted(lines.size(), lines.stream().map(String::length).toList()));
        }

        long unreadable = lines.stream()
                .flatMapToInt(String::chars)
                .filter(character -> character == '?')
                .count();

        if (unreadable > 0) {
            return VerificationOutcome.unreadable(
                    "%d character(s) could not be recognised. Try a sharper or better-lit image."
                            .formatted(unreadable));
        }

        Optional<MrzParser> parser = registry.parserFor(format.get());
        if (parser.isEmpty()) {
            return VerificationOutcome.unsupported(format.get());
        }

        MrzVerification verification = parser.get().verify(lines);
        MrzData data = parser.get().parse(lines, today);

        return VerificationOutcome.parsed(format.get(), data, verification);
    }
}