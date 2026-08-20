package com.etka.veridoc.ocr;

import com.etka.veridoc.mrz.MrzFormat;
import com.etka.veridoc.mrz.MrzNormalizer;
import com.etka.veridoc.mrz.Td3Parser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the full extraction pipeline against a rendered MRZ whose content
 * is known in advance, so a failure identifies the exact character that broke
 * rather than merely reporting that OCR got worse.
 */
@DisplayName("MRZ extraction, end to end")
class MrzExtractionEndToEndTest {

    private static final String LINE_1 =
            "P<UTOERIKSSON<<ANNA<MARIA" + "<".repeat(19);
    private static final String LINE_2 =
            "L898902C36UTO7408122F1204159ZE184226B<<<<<10";

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 19);

    @Test
    @DisplayName("extracts two lines of the correct length")
    void extractsCorrectLineStructure() {
        BufferedImage image = render(LINE_1, LINE_2);

        try (TesseractOcrEngine engine = TesseractOcrEngine.english()) {
            List<String> raw = new MrzExtractor(engine).extract(image);
            List<String> lines = MrzNormalizer.normalize(String.join("\n", raw));

            assertThat(lines).hasSize(2);
            assertThat(lines).allSatisfy(line -> assertThat(line).hasSize(44));
            assertThat(MrzFormat.detect(lines)).contains(MrzFormat.TD3);
        }
    }

    @Test
    @DisplayName("reads the date of birth accurately enough to pass its check digit")
    void readsBirthDateCorrectly() {
        // OCR accuracy depends on the rendering font matching the calibrated
        // ink threshold. On a machine without Consolas this asserts nothing
        // meaningful, so skip rather than report a false regression.
        org.junit.jupiter.api.Assumptions.assumeTrue(
                new Font("Consolas", Font.PLAIN, 96).getFamily().equalsIgnoreCase("Consolas"),
                "Consolas not available; OCR accuracy assertion skipped");

        BufferedImage image = render(LINE_1, LINE_2);

        try (TesseractOcrEngine engine = TesseractOcrEngine.english()) {
            List<String> raw = new MrzExtractor(engine).extract(image);
            List<String> lines = MrzNormalizer.normalize(String.join("\n", raw));

            var verification = new Td3Parser().verify(lines);

            assertThat(verification.results())
                    .containsEntry(com.etka.veridoc.mrz.MrzVerification.MrzField.DATE_OF_BIRTH, true);

            var data = new Td3Parser().parse(lines, TODAY);
            assertThat(data.dateOfBirth()).contains(LocalDate.of(1974, 8, 12));
            assertThat(data.isAtLeastAge(18, TODAY)).isTrue();
        }
    }

    /**
     * Renders MRZ lines for testing.
     *
     * <p>Uses Consolas where available, because the filler ink-density
     * threshold in {@link FixedWidthLineReader} was calibrated against it.
     * Density-based filler detection is inherently font-dependent, so
     * rendering with an uncalibrated font produces misclassified fillers and a
     * failure that says nothing about the pipeline.
     */
    private static BufferedImage render(String... lines) {
        Font font = new Font("Consolas", Font.PLAIN, 96);
        if (!font.getFamily().equalsIgnoreCase("Consolas")) {
            font = new Font(Font.MONOSPACED, Font.PLAIN, 96);
        }

        BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D probeGraphics = probe.createGraphics();
        probeGraphics.setFont(font);
        var metrics = probeGraphics.getFontMetrics();

        int widest = 0;
        for (String line : lines) {
            widest = Math.max(widest, metrics.stringWidth(line));
        }
        int lineHeight = metrics.getHeight();
        probeGraphics.dispose();

        int margin = 40;
        int spacing = 20;
        int width = widest + 2 * margin;
        int height = lines.length * lineHeight + (lines.length - 1) * spacing + 2 * margin;

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, height);
            graphics.setColor(Color.BLACK);
            graphics.setFont(font);

            int baseline = margin + metrics.getAscent();
            for (String line : lines) {
                graphics.drawString(line, margin, baseline);
                baseline += lineHeight + spacing;
            }
        } finally {
            graphics.dispose();
        }
        return image;
    }
}