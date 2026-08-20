package com.etka.veridoc.cli;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Renders MRZ lines to a PNG, so the extraction pipeline can be exercised
 * against input whose correct answer is known in advance.
 *
 * <p>Testing against a real document tells you the pipeline failed. Testing
 * against generated input tells you <em>which character</em> it got wrong,
 * which is far more actionable while tuning OCR.
 */
public final class MrzImageGenerator {

    private static final int FONT_SIZE = 96;
    private static final int MARGIN = 40;
    private static final int LINE_SPACING = 20;

    public static void main(String[] args) throws IOException, java.awt.FontFormatException {
        if (args.length < 3) {
            System.err.println("Usage: MrzImageGenerator <font.ttf> <output.png> <line1> [line2] [line3]");
            System.exit(1);
        }

        File output = new File(args[1]);
        String[] lines = java.util.Arrays.copyOfRange(args, 2, args.length);

        Font font = loadFont(args[0]);

        // Measure first, using a throwaway image, so the real canvas fits exactly.
        BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D probeGraphics = probe.createGraphics();
        probeGraphics.setFont(font);
        var metrics = probeGraphics.getFontMetrics();

        int longest = 0;
        for (String line : lines) {
            longest = Math.max(longest, line.length());
        }
        int widest = longest * metrics.charWidth('M');
        int lineHeight = metrics.getHeight();
        probeGraphics.dispose();

        int width = widest + 2 * MARGIN;
        int height = lines.length * lineHeight
                + (lines.length - 1) * LINE_SPACING
                + 2 * MARGIN;

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, height);

            graphics.setColor(Color.BLACK);
            graphics.setFont(font);

            // Draw one character at a time at an exact pitch. drawString applies
            // the font's kerning and text-layout rules, which vary spacing
            // between glyph pairs even in a monospace font — producing an image
            // whose characters are not on a fixed grid. A real MRZ is printed at
            // a fixed pitch, so the generator must reproduce that exactly or the
            // fixed-width reader has nothing correct to align to.
            int pitch = metrics.charWidth('M');
            int baseline = MARGIN + metrics.getAscent();

            for (String line : lines) {
                for (int index = 0; index < line.length(); index++) {
                    char character = line.charAt(index);
                    // Centre each glyph within its cell, as MRZ printing does.
                    int offset = (pitch - metrics.charWidth(character)) / 2;
                    graphics.drawString(String.valueOf(character),
                            MARGIN + index * pitch + offset, baseline);
                }
                baseline += lineHeight + LINE_SPACING;
            }
        } finally {
            graphics.dispose();
        }

        ImageIO.write(image, "png", output);

        // Record the exact geometry used. The reader can then be tested against
        // known-correct cell boundaries, which separates "is recognition
        // correct" from "is grid inference correct" — two problems that are
        // impossible to debug while both are in play.
        File sidecar = new File(output.getPath().replaceAll("\\.[^.]+$", "") + ".grid");
        java.nio.file.Files.writeString(sidecar.toPath(),
                "left=%d%npitch=%d%ncount=%d%n"
                        .formatted(MARGIN, metrics.charWidth('M'), lines[0].length()));

        System.out.printf("Wrote %s (%d x %d), pitch %d px%n",
                output.getName(), width, height, metrics.charWidth('M'));
    }

    /**
     * Resolves a font from either a TTF/OTF file path or an installed font
     * family name.
     *
     * <p>MRZs are printed in OCR-B, but any monospace font is adequate for
     * pipeline testing: the point of generated input is knowing the correct
     * answer in advance, not visual fidelity to a real document.
     */
    private static Font loadFont(String specification)
            throws IOException, java.awt.FontFormatException {

        File file = new File(specification);

        if (file.isFile()) {
            return Font.createFont(Font.TRUETYPE_FONT, file)
                    .deriveFont(Font.PLAIN, (float) FONT_SIZE);
        }

        Font font = new Font(specification, Font.PLAIN, FONT_SIZE);

        // Java silently substitutes a default when a family is not installed,
        // so compare the resolved name against what was asked for.
        if (!font.getFamily().equalsIgnoreCase(specification)) {
            System.err.printf("Warning: font '%s' not found, using '%s' instead%n",
                    specification, font.getFamily());
        }
        return font;
    }
}