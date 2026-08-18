package com.etka.veridoc.ocr;

import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.tesseract.TessBaseAPI;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Objects;

/**
 * OCR engine backed by Tesseract via JavaCPP.
 *
 * <p>This is the only class in the project that knows Tesseract exists.
 * Everything else talks to {@link OcrEngine}.
 *
 * <p><strong>Not thread-safe.</strong> A {@link TessBaseAPI} instance holds
 * mutable native state, so concurrent calls to {@link #read} on one instance
 * will corrupt results. Use one instance per thread.
 *
 * <p><strong>Must be closed.</strong> The native API allocates memory outside
 * the JVM heap that the garbage collector will not reclaim.
 */
public final class TesseractOcrEngine implements OcrEngine, AutoCloseable {

    private static final String TESSDATA_RESOURCE = "/tessdata/";
    private static final String WHITELIST_VARIABLE = "tessedit_char_whitelist";

    private final TessBaseAPI api;
    private final String language;

    /**
     * Creates an engine using language data bundled on the classpath.
     *
     * @param language tessdata language code, e.g. "eng"
     * @throws OcrException if the language data is missing or Tesseract fails to start
     */
    public TesseractOcrEngine(String language) {
        this.language = Objects.requireNonNull(language, "language must not be null");

        Path tessdataDirectory = extractTessdata(language);

                this.api = new TessBaseAPI();

        // These two must be set BEFORE Init(). Tesseract reads them while
        // constructing its language model and never consults them again, so
        // setting them later has no effect at all.
        api.SetVariable("load_system_dawg", "false");
        api.SetVariable("load_freq_dawg", "false");

        if (api.Init(tessdataDirectory.toString(), language) != 0) {
            api.close();
            throw new OcrException(
                    "Tesseract failed to initialise with language '%s' from %s"
                            .formatted(language, tessdataDirectory));
        }
    }

    /** Creates an engine for English. */
    public static TesseractOcrEngine english() {
        return new TesseractOcrEngine("eng");
    }

        /**
     * Reads an image that has already been split into single text lines.
     *
     * <p>Tesseract segments text by finding gaps between glyphs, which works
     * poorly on a machine readable zone: a run of twenty identical filler
     * characters offers no anchors, so the engine miscounts them and invents
     * boundaries. Reading each line independently in single-line mode removes
     * the layout decision entirely.
     *
     * @param lineImages one image per text line, in order
     * @param hints      recognition constraints
     * @return the lines joined with newlines, and the mean confidence
     */
    public OcrResult readLines(List<BufferedImage> lineImages, OcrHints hints) {
        Objects.requireNonNull(lineImages, "lineImages must not be null");

        OcrHints lineHints = new OcrHints(
                hints.characterWhitelist(),
                OcrHints.PageSegmentation.SINGLE_LINE,
                hints.language());

        StringBuilder combined = new StringBuilder();
        List<Float> confidences = new java.util.ArrayList<>();

        for (BufferedImage lineImage : lineImages) {
            OcrResult lineResult = read(lineImage, lineHints);
            combined.append(lineResult.text().strip()).append('\n');
            confidences.add(lineResult.meanConfidence());
        }

        float mean = confidences.isEmpty() ? 0.0f
                : (float) confidences.stream().mapToDouble(Float::doubleValue).average().orElse(0);

        return new OcrResult(combined.toString(), mean, confidences);
    }

    @Override
    public OcrResult read(BufferedImage image, OcrHints hints) {
        Objects.requireNonNull(image, "image must not be null");
        Objects.requireNonNull(hints, "hints must not be null");

        if (!hints.language().equals(language)) {
            throw new OcrException(
                    "Engine was initialised for '%s' but hints request '%s'"
                            .formatted(language, hints.language()));
        }

        applyHints(hints);

        BufferedImage grayscale = toGrayscale(image);
        byte[] pixels = ((DataBufferByte) grayscale.getRaster().getDataBuffer()).getData();

        // The pointer must stay alive for as long as Tesseract reads from it,
        // so it is closed only after GetUTF8Text has run.
        try (BytePointer imageData = new BytePointer(pixels)) {
            api.SetImage(
                    imageData,
                    grayscale.getWidth(),
                    grayscale.getHeight(),
                    1,                          // bytes per pixel (8-bit grayscale)
                    grayscale.getWidth());      // bytes per line

            BytePointer textPointer = api.GetUTF8Text();
            if (textPointer == null) {
                return new OcrResult("", 0.0f, List.of());
            }

            try {
                                String text = textPointer.getString();
                float confidence = api.MeanTextConf();

                if (System.getProperty("veridoc.ocr.debug") != null) {
                    System.err.printf("[ocr] psm=%d whitelist=%s confidence=%.1f%n",
                            hints.segmentation().tesseractValue(),
                            hints.characterWhitelist().orElse("(none)"),
                            confidence);
                }

                return new OcrResult(text, confidence, List.of());
            } finally {
                textPointer.deallocate();
            }
        }
    }

    private void applyHints(OcrHints hints) {
        api.SetPageSegMode(hints.segmentation().tesseractValue());

        // Setting the whitelist to an empty string clears any previous value,
        // so this branch must always run — not only when a whitelist exists.
        api.SetVariable(WHITELIST_VARIABLE,
                hints.characterWhitelist().orElse(""));
    }

    /**
     * Converts any image to 8-bit grayscale in a buffer we allocate ourselves.
     *
     * <p>Allocating the target means the scanline stride is guaranteed to equal
     * the width, which is what we pass to Tesseract as bytes-per-line. Reusing
     * a caller-supplied image would risk a padded stride and a sheared read.
     */
    private static BufferedImage toGrayscale(BufferedImage source) {
        BufferedImage grayscale = new BufferedImage(
                source.getWidth(), source.getHeight(), BufferedImage.TYPE_BYTE_GRAY);

        Graphics2D graphics = grayscale.createGraphics();
        try {
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return grayscale;
    }

    /**
     * Copies the bundled language data out of the classpath onto disk.
     *
     * <p>Tesseract is a native library and can only read a real filesystem
     * path. Once this application is packaged as a jar, the resource is inside
     * an archive and has no such path, so it must be extracted first.
     */
    private static Path extractTessdata(String language) {
        String resource = TESSDATA_RESOURCE + language + ".traineddata";

        try (InputStream source = TesseractOcrEngine.class.getResourceAsStream(resource)) {
            if (source == null) {
                throw new OcrException(
                        "Language data not found on classpath: %s".formatted(resource));
            }

            Path directory = Path.of(System.getProperty("java.io.tmpdir"), "veridoc-tessdata");
            Files.createDirectories(directory);

            Path target = directory.resolve(language + ".traineddata");
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);

            return directory;
        } catch (IOException failure) {
            throw new OcrException("Could not extract language data for " + language, failure);
        }
    }

    @Override
    public void close() {
        api.End();
        api.close();
    }
}