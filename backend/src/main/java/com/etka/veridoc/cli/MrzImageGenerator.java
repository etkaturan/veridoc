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

        int widest = 0;
        for (String line : lines) {
            widest = Math.max(widest, metrics.stringWidth(line));
        }
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

            int baseline = MARGIN + metrics.getAscent();
            for (String line : lines) {
                graphics.drawString(line, MARGIN, baseline);
                baseline += lineHeight + LINE_SPACING;
            }
        } finally {
            graphics.dispose();
        }

        ImageIO.write(image, "png", output);
        System.out.printf("Wrote %s (%d x %d)%n", output.getName(), width, height);
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