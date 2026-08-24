package com.etka.veridoc.cli;

import com.etka.veridoc.mrz.MrzFormat;
import com.etka.veridoc.mrz.MrzNormalizer;
import com.etka.veridoc.mrz.MrzVerification;
import com.etka.veridoc.mrz.Td3Parser;
import com.etka.veridoc.ocr.GridInference;
import com.etka.veridoc.ocr.LineSplitter;
import com.etka.veridoc.ocr.MrzBandLocator;
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

/**
 * Runs the extraction pipeline against every image in one or more folders and
 * prints a one-line summary per image, plus per-line detail.
 *
 * <p>Tuning any splitting or margin parameter against a single photograph
 * risks fixing that photo while silently breaking another. This exists so
 * every change can be checked against the full set at once — generated
 * specimens (which must stay perfect) and real photographs (which are the
 * actual target) together.
 */
public final class BatchDiagnosticCli {

    private static final int[] CANDIDATE_WIDTHS = {44, 30};

    public static void main(String[] args) throws Exception {
        List<File> files = new ArrayList<>();
        for (String dir : args.length > 0 ? args : new String[]{"../samples", "../usable"}) {
            File folder = new File(dir);
            if (!folder.isDirectory()) {
                continue;
            }
            for (File f : folder.listFiles()) {
                String name = f.getName().toLowerCase();
                if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")) {
                    files.add(f);
                }
            }
        }
        files.sort(java.util.Comparator.comparing(File::getName));

        Font templateFont = loadFont();
        System.out.println("Template font: " + templateFont.getFontName());
        System.out.println("Files found: " + files.size());
        System.out.println("=".repeat(70));

        int fullPass = 0;
        int structurallyOk = 0;
        int failed = 0;

        for (File file : files) {
            try {
                Result r = process(file, templateFont);
                System.out.println(r.summary());
                if (r.fullyValid) fullPass++;
                else if (r.formatMatched) structurallyOk++;
                else failed++;
            } catch (Exception e) {
                System.out.printf("%-40s ERROR: %s%n", file.getName(), e);
                failed++;
            }
        }

        System.out.println("=".repeat(70));
        System.out.printf("Fully valid (all check digits pass): %d%n", fullPass);
        System.out.printf("Structurally parsed (wrong/failed digits): %d%n", structurallyOk);
        System.out.printf("Failed to extract a valid layout: %d%n", failed);
    }

    private record Result(String fileName, boolean bandLocated, int lineCount,
                          int widthUsed, boolean formatMatched, boolean fullyValid,
                          String note) {
        String summary() {
            return "%-32s band=%-5s lines=%d width=%-3s format=%-5s valid=%-5s %s"
                    .formatted(fileName, bandLocated, lineCount,
                            widthUsed == 0 ? "-" : widthUsed,
                            formatMatched, fullyValid, note);
        }
    }

    private static Result process(File file, Font templateFont) throws Exception {
        BufferedImage image = ImageIO.read(file);
        if (image == null) {
            return new Result(file.getName(), false, 0, 0, false, false, "not a decodable image");
        }

        var located = MrzBandLocator.locate(image);
        BufferedImage band = located.orElse(image);

        // Call the ACTUAL production extraction path — MrzExtractor — rather
        // than a hand-rolled parallel implementation. A batch tool testing
        // different code than the running service gives false confidence:
        // this tool previously reimplemented splitting and grid inference
        // directly, and diverged from real fixes made to MrzExtractor
        // (the confidence-driven merged-line split) without anyone noticing,
        // because it kept "passing" using its own older logic.
        try (com.etka.veridoc.ocr.TesseractOcrEngine engine =
                     com.etka.veridoc.ocr.TesseractOcrEngine.english()) {
            List<String> rawLines = new com.etka.veridoc.ocr.MrzExtractor(engine).extract(band);
            List<String> normalized = MrzNormalizer.normalize(String.join("\n", rawLines));

            var format = MrzFormat.detect(normalized);

            if (format.isPresent() && format.get() == MrzFormat.TD3) {
                var verification = new Td3Parser().verify(normalized);
                var data = new Td3Parser().parse(normalized, LocalDate.now());
                String note = verification.isFullyValid()
                        ? "name=" + data.fullName()
                        : "(" + verification.failedFields() + ")";
                if (!verification.isFullyValid()) {
                    System.err.println("  RAW LINES for " + file.getName() + ":");
                    for (String line : normalized) {
                        System.err.println("    |" + line + "|");
                    }
                }
                return new Result(file.getName(), located.isPresent(), normalized.size(),
                        44, true, verification.isFullyValid(), note);
            }
            if (format.isPresent()) {
                return new Result(file.getName(), located.isPresent(), normalized.size(),
                        0, true, false, "format=" + format.get() + " (parser not implemented)");
            }
            return new Result(file.getName(), located.isPresent(), normalized.size(),
                    0, false, false, "no width produced a valid ICAO layout (lines="
                            + normalized.size() + ")");
        }
    }

    private static Font loadFont() {
        File fontFile = new File("../samples/fonts/OCRB.ttf");
        if (fontFile.isFile()) {
            try {
                return Font.createFont(Font.TRUETYPE_FONT, fontFile).deriveFont(Font.PLAIN, 64f);
            } catch (Exception ignored) {
                // fall through to Consolas
            }
        }
        return new Font("Consolas", Font.PLAIN, 64);
    }
}