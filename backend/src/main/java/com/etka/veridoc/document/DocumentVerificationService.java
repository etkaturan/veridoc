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
    private final VerificationRecordRepository repository;
    private final IdentityHasher hasher;

    public DocumentVerificationService(MrzParserRegistry registry,
                                       VerificationRecordRepository repository,
                                       IdentityHasher hasher) {
        this.registry = registry;
        this.repository = repository;
        this.hasher = hasher;
    }

    /**
     * Verifies a document and stores the derived result.
     *
     * <p>Persistence happens here rather than in the controller so that every
     * caller gets a record, and so the identity data never leaves this method:
     * the {@link MrzData} is hashed and reduced to booleans before anything is
     * written, and is discarded when the method returns.
     *
     * @return the outcome, with the stored record's id when one was created
     */
    @org.springframework.transaction.annotation.Transactional
    public VerificationOutcome verifyAndRecord(java.awt.image.BufferedImage image,
                                               LocalDate today) {
        VerificationOutcome outcome = verify(image, today);

        if (outcome.status() != VerificationOutcome.Status.PARSED) {
            return outcome;
        }

        MrzData data = outcome.data().orElseThrow();
        MrzVerification verification = outcome.verification().orElseThrow();

        VerificationRecord record = VerificationRecord.builder()
                .documentHash(hasher.hashDocument(data.issuingState(), data.documentNumber()))
                .nameHash(hasher.hashName(data.surname(), data.givenNames()))
                .documentFormat(outcome.format().orElseThrow().name())
                .issuingState(data.issuingState())
                .checksPassed(verification.isFullyValid())
                .failedFields(verification.failedFields().isEmpty()
                        ? null
                        : verification.failedFields().toString())
                .over18(data.isAtLeastAge(18, today))
                .over21(data.isAtLeastAge(21, today))
                .expiresOn(data.expiryDate().orElse(null))
                .build();

        return outcome.withRecordId(repository.save(record).getId());
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
        // A full document photo has the MRZ somewhere within it, not filling
        // the frame. Locate it first; if no plausible band is found, fall back
        // to treating the whole image as the band, which keeps the existing
        // behaviour for images that are already cropped to the MRZ.
        var located = com.etka.veridoc.ocr.MrzBandLocator.locate(image);
        System.err.println("[service] MrzBandLocator.locate() returned: "
                + (located.isPresent()
                        ? located.get().getWidth() + "x" + located.get().getHeight()
                        : "EMPTY"));
        BufferedImage band = located.orElse(image);

        if (true) { // always write for now — property flag has been unreliable
            java.io.File debugFile = new java.io.File("../samples/debug/located-band.png")
                    .getAbsoluteFile();
            try {
                debugFile.getParentFile().mkdirs();
                boolean written = javax.imageio.ImageIO.write(band, "png", debugFile);
                System.err.printf("[band] wrote debug image: %s (success=%s)%n",
                        debugFile, written);
            } catch (java.io.IOException e) {
                System.err.println("[band] failed to write debug image: " + e);
            }
        }

        System.err.println("[service] about to call extract() with band dimensions: "
                + band.getWidth() + "x" + band.getHeight());

        List<String> rawLines;
        try (TesseractOcrEngine engine = TesseractOcrEngine.english()) {
            rawLines = new MrzExtractor(engine).extract(band);
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