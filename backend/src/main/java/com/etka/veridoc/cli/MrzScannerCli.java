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

        OcrResult ocr;
        try (TesseractOcrEngine engine = TesseractOcrEngine.english()) {
            long start = System.currentTimeMillis();
            ocr = engine.readLines(lineImages, OcrHints.forMrz());
            long elapsed = System.currentTimeMillis() - start;

            section("OCR");
            System.out.printf("  confidence  %.1f%s%n", ocr.meanConfidence(),
                    ocr.isLowConfidence() ? "  (low)" : "");
            System.out.printf("  elapsed     %d ms%n", elapsed);
            if (!ocr.lineConfidences().isEmpty()) {
                System.out.print("  per line   ");
                ocr.lineConfidences().forEach(c -> System.out.printf(" %.1f", c));
                System.out.println();
            }
            System.out.println("  raw output:");
            ocr.text().lines().forEach(line -> System.out.println("    |" + line + "|"));
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