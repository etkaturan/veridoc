package com.etka.veridoc.cli;

import com.etka.veridoc.mrz.MrzData;
import com.etka.veridoc.mrz.MrzFormat;
import com.etka.veridoc.mrz.MrzNormalizer;
import com.etka.veridoc.mrz.MrzParser;
import com.etka.veridoc.mrz.MrzParserRegistry;
import com.etka.veridoc.mrz.MrzVerification;
import com.etka.veridoc.mrz.Td3Parser;
import com.etka.veridoc.ocr.OcrHints;
import com.etka.veridoc.ocr.OcrResult;
import com.etka.veridoc.ocr.TesseractOcrEngine;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

/**
 * Reads an image from disk and runs it through the whole pipeline, printing
 * each stage. Exists so the extraction chain can be exercised from a terminal
 * before any web layer is in place.
 */
public final class MrzScannerCli {

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: MrzScannerCli <image-file>");
            System.exit(1);
        }

        File file = new File(args[0]);
        if (!file.isFile()) {
            System.err.println("No such file: " + file.getAbsolutePath());
            System.exit(1);
        }

        BufferedImage image = ImageIO.read(file);
        if (image == null) {
            System.err.println("Not a readable image format: " + file.getName());
            System.exit(1);
        }

        section("INPUT");
        System.out.printf("  file        %s%n", file.getName());
        System.out.printf("  dimensions  %d x %d%n", image.getWidth(), image.getHeight());

                List<java.awt.image.BufferedImage> lineImages =
                com.etka.veridoc.ocr.LineSplitter.split(image);

        section("LINE SPLIT");
        System.out.printf("  detected    %d line(s)%n", lineImages.size());
        for (int index = 0; index < lineImages.size(); index++) {
            System.out.printf("    [%d] %d x %d%n", index + 1,
                    lineImages.get(index).getWidth(), lineImages.get(index).getHeight());
        }

                // TD3 is 44 characters per line. Once other formats are supported this
        // becomes a parameter, or a first pass that measures the character pitch.
        final int charactersPerLine = 44;

        OcrResult ocr;
        StringBuilder assembled = new StringBuilder();

        try (TesseractOcrEngine engine = TesseractOcrEngine.english()) {
            var reader = new com.etka.veridoc.ocr.FixedWidthLineReader(engine);
            long start = System.currentTimeMillis();

            section("OCR  (character cells)");

            for (int index = 0; index < lineImages.size(); index++) {
                var trimmed = com.etka.veridoc.ocr.ImageTrimmer.trim(lineImages.get(index));
                if (trimmed.isEmpty()) {
                    System.out.printf("  line %d is blank%n", index + 1);
                    continue;
                }

                OcrResult lineResult =
                        reader.read(trimmed.get(), charactersPerLine, OcrHints.forMrz());

                assembled.append(lineResult.text()).append('\n');

                System.out.printf("  line %d  confidence %.1f%n",
                        index + 1, lineResult.meanConfidence());
                System.out.println("    |" + lineResult.text() + "|");

                // Flag the least certain cells: when a check digit fails, these
                // are the characters to suspect first.
                var weak = new java.util.ArrayList<String>();
                for (int cell = 0; cell < lineResult.lineConfidences().size(); cell++) {
                    if (lineResult.lineConfidences().get(cell) < 70.0f) {
                        weak.add("%d:%c".formatted(cell, lineResult.text().charAt(cell)));
                    }
                }
                if (!weak.isEmpty()) {
                    System.out.println("    low confidence at " + String.join(", ", weak));
                }
            }

            long elapsed = System.currentTimeMillis() - start;
            System.out.printf("  elapsed     %d ms%n", elapsed);

            ocr = new OcrResult(assembled.toString(), 0.0f, List.of());
        }

        List<String> lines = MrzNormalizer.normalize(ocr.text());

        section("NORMALISED");
        if (lines.isEmpty()) {
            System.out.println("  nothing usable");
            return;
        }
        for (int index = 0; index < lines.size(); index++) {
            System.out.printf("  [%d] %2d chars  %s%n",
                    index + 1, lines.get(index).length(), lines.get(index));
        }

        section("FORMAT");
        MrzFormat.detect(lines).ifPresentOrElse(
                format -> System.out.printf("  %s (%s)%n", format, format.description()),
                () -> System.out.println("  no ICAO 9303 layout matches these dimensions"));

        MrzParserRegistry registry = new MrzParserRegistry(List.of(new Td3Parser()));

        MrzParser parser;
        try {
            parser = registry.parserFor(lines);
        } catch (RuntimeException failure) {
            System.out.println("\n  Cannot parse: " + failure.getMessage());
            System.out.println("\n  The MRZ band is probably not cropped tightly enough,");
            System.out.println("  or the image resolution is too low for a clean read.");
            return;
        }

                if (lines.stream().anyMatch(line -> line.indexOf('?') >= 0)) {
            System.out.println("\n  Some characters could not be read (shown as '?').");
            System.out.println("  Check digits cannot be evaluated until every cell reads cleanly.");
            return;
        }

        MrzVerification verification = parser.verify(lines);

        section("CHECK DIGITS");
        verification.results().forEach((field, valid) ->
                System.out.printf("  %-16s %s%n", field, valid ? "pass" : "FAIL"));
        System.out.printf("  %-16s %s%n", "OVERALL",
                verification.isFullyValid() ? "all checks passed" : "one or more failed");

        LocalDate today = LocalDate.now();
        MrzData data = parser.parse(lines, today);

        section("EXTRACTED");
        System.out.printf("  document      %s (%s)%n", data.documentCode(), data.issuingState());
        System.out.printf("  number        %s%n", data.documentNumber());
        System.out.printf("  name          %s%n", data.fullName());
        System.out.printf("  nationality   %s%n", data.nationality());
        System.out.printf("  sex           %s%n", data.sex());
        System.out.printf("  born          %s%n",
                data.dateOfBirth().map(Object::toString).orElse("unreadable"));
        System.out.printf("  expires       %s%n",
                data.expiryDate().map(Object::toString).orElse("unreadable"));

        section("DERIVED  (as of " + today + ")");
        System.out.printf("  age           %s%n",
                data.ageAt(today).map(Object::toString).orElse("unknown"));
        System.out.printf("  over 18       %s%n", data.isAtLeastAge(18, today));
        System.out.printf("  expired       %s%n", data.isExpiredAt(today));

        if (!verification.isFullyValid()) {
            System.out.println("\n  NOTE: check digits failed, so the values above may be misread.");
        }
    }

    private static void section(String title) {
        System.out.println();
        System.out.println("── " + title + " " + "─".repeat(Math.max(0, 50 - title.length())));
    }
}