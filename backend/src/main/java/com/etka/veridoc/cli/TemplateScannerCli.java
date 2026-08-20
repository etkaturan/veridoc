package com.etka.veridoc.cli;

import com.etka.veridoc.mrz.MrzData;
import com.etka.veridoc.mrz.MrzNormalizer;
import com.etka.veridoc.mrz.MrzVerification;
import com.etka.veridoc.mrz.Td3Parser;
import com.etka.veridoc.ocr.ImageTrimmer;
import com.etka.veridoc.ocr.LineSplitter;
import com.etka.veridoc.ocr.OcrResult;
import com.etka.veridoc.ocr.TemplateLineReader;
import com.etka.veridoc.ocr.TemplateMatchingEngine;

import javax.imageio.ImageIO;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.io.File;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Runs the pipeline using template matching instead of Tesseract. */
public final class TemplateScannerCli {

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: TemplateScannerCli <image> [font.ttf|FontName]");
            System.exit(1);
        }

        BufferedImage image = ImageIO.read(new File(args[0]));
        Font font = resolveFont(args.length > 1 ? args[1] : "Consolas");

        System.out.printf("%nFont: %s%n", font.getFamily());

        TemplateLineReader reader =
                new TemplateLineReader(new TemplateMatchingEngine(font));

        List<BufferedImage> lineImages = LineSplitter.split(image);

        // Every line of an MRZ occupies the same character grid, so the cell
        // boundaries must be derived once and shared. Trimming each line to its
        // own ink would give them different left edges — line 1 begins with a
        // letter, line 2 may not — and the arithmetic would slice between
        // characters on one of them.
        int left = Integer.MAX_VALUE;
        int right = -1;
        for (BufferedImage lineImage : lineImages) {
            int[] bounds = horizontalInkBounds(lineImage);
            if (bounds != null) {
                left = Math.min(left, bounds[0]);
                right = Math.max(right, bounds[1]);
            }
        }
        if (right < 0) {
            System.out.println("No text found in the image");
            return;
        }

        final int gridLeft = left;
        final int gridWidth = right - left + 1;

        if (System.getProperty("veridoc.grid.debug") != null) {
            System.err.printf("[grid] left=%d right=%d width=%d cellWidth=%.3f%n",
                    gridLeft, right, gridWidth, gridWidth / 44.0);

            // Locate the actual ink columns of the first line and report where
            // each character block starts, so the measured pitch can be
            // compared against the assumed even division.
            BufferedImage first = lineImages.get(0);
            boolean inInk = false;
            int blockStart = 0;
            int blocks = 0;
            for (int x = gridLeft; x <= right && blocks < 12; x++) {
                boolean hasInk = false;
                for (int y = 0; y < first.getHeight(); y++) {
                    if (((first.getRGB(x, y) >> 8) & 0xFF) < 128) {
                        hasInk = true;
                        break;
                    }
                }
                if (hasInk && !inInk) {
                    blockStart = x;
                    inInk = true;
                } else if (!hasInk && inInk) {
                    System.err.printf("[grid] block %d: %d..%d (width %d, offset %d)%n",
                            blocks, blockStart, x - 1, x - blockStart, blockStart - gridLeft);
                    blocks++;
                    inInk = false;
                }
            }
        }

        List<String> lines = new ArrayList<>();
        for (BufferedImage lineImage : lineImages) {
            BufferedImage aligned = lineImage.getSubimage(
                    gridLeft, 0, gridWidth, lineImage.getHeight());

            OcrResult result = reader.read(aligned, 44);
            System.out.printf("  confidence %.1f%n    |%s|%n",
                    result.meanConfidence(), result.text());
            lines.add(result.text());
        }

        List<String> normalised = MrzNormalizer.normalize(String.join("\n", lines));
        if (normalised.size() != 2) {
            System.out.println("\nExpected 2 lines, got " + normalised.size());
            return;
        }

        Td3Parser parser = new Td3Parser();
        MrzVerification verification = parser.verify(normalised);

        System.out.println("\nCheck digits:");
        verification.results().forEach((field, valid) ->
                System.out.printf("  %-16s %s%n", field, valid ? "pass" : "FAIL"));

        LocalDate today = LocalDate.now();
        MrzData data = parser.parse(normalised, today);
        System.out.printf("%n  %s, born %s, expires %s%n",
                data.fullName(),
                data.dateOfBirth().map(Object::toString).orElse("?"),
                data.expiryDate().map(Object::toString).orElse("?"));
    }

    /**
     * Resolves a font from a file path or an installed family name.
     *
     * <p>Fails rather than substituting. Java silently returns Dialog for an
     * unknown family name, which produces templates that match nothing and a
     * failure that looks like a recognition problem instead of a missing font.
     */



        /**
     * Finds the leftmost and rightmost columns containing ink.
     *
     * @return {@code [left, right]}, or null if the image has no ink
     */
    private static int[] horizontalInkBounds(BufferedImage image) {
        int left = image.getWidth();
        int right = -1;

        for (int x = 0; x < image.getWidth(); x++) {
            for (int y = 0; y < image.getHeight(); y++) {
                if (((image.getRGB(x, y) >> 8) & 0xFF) < 128) {
                    left = Math.min(left, x);
                    right = Math.max(right, x);
                    break;
                }
            }
        }
        return right < 0 ? null : new int[]{left, right};
    }

    private static Font resolveFont(String specification) throws Exception {
        File file = new File(specification);

        if (file.isFile()) {
            return Font.createFont(Font.TRUETYPE_FONT, file).deriveFont(Font.PLAIN, 64f);
        }
        if (specification.endsWith(".ttf") || specification.endsWith(".otf")) {
            throw new IllegalArgumentException(
                    "Font file not found: " + file.getAbsolutePath());
        }

        Font font = new Font(specification, Font.PLAIN, 64);
        if (!font.getFamily().equalsIgnoreCase(specification)) {
            throw new IllegalArgumentException(
                    "Font '%s' is not installed (Java substituted '%s')"
                            .formatted(specification, font.getFamily()));
        }
        return font;
    }
}