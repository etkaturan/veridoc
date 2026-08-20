package com.etka.veridoc.cli;

import com.etka.veridoc.ocr.LineSplitter;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;

/**
 * Writes out the intermediate images of the extraction pipeline so the cell
 * geometry can be inspected directly rather than inferred from OCR output.
 *
 * <p>Produces, per line: the split band, the same band with cell boundaries
 * drawn on it, and the first twelve individual cells.
 */
public final class GridDebugCli {

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: GridDebugCli <image> [outputDir]");
            System.exit(1);
        }

        BufferedImage image = ImageIO.read(new File(args[0]));
        File outputDir = new File(args.length > 1 ? args[1] : "../samples/debug");
        outputDir.mkdirs();

        System.out.printf("Source: %d x %d, type=%d%n",
                image.getWidth(), image.getHeight(), image.getType());

        List<BufferedImage> lineImages = LineSplitter.split(image);

        int left = Integer.MAX_VALUE;
        int right = -1;
        for (BufferedImage lineImage : lineImages) {
            int[] bounds = inkBounds(lineImage);
            if (bounds != null) {
                left = Math.min(left, bounds[0]);
                right = Math.max(right, bounds[1]);
            }
        }

        int gridWidth = right - left + 1;
        double cellWidth = gridWidth / 44.0;
        System.out.printf("Grid: left=%d right=%d width=%d cell=%.3f%n",
                left, right, gridWidth, cellWidth);

        for (int lineIndex = 0; lineIndex < lineImages.size(); lineIndex++) {
            BufferedImage lineImage = lineImages.get(lineIndex);

            // Copy rather than use the subimage view directly: getSubimage
            // shares the parent's raster, and writing or further slicing a
            // shared raster is a common source of positional corruption.
            BufferedImage aligned = copy(
                    lineImage.getSubimage(left, 0, gridWidth, lineImage.getHeight()));

            write(outputDir, "line%d-aligned.png".formatted(lineIndex + 1), aligned);
            write(outputDir, "line%d-grid.png".formatted(lineIndex + 1),
                    withGridLines(aligned, cellWidth));

            for (int cell = 0; cell < 12; cell++) {
                int from = (int) Math.round(cell * cellWidth);
                int to = Math.min((int) Math.round((cell + 1) * cellWidth), aligned.getWidth());
                write(outputDir,
                        "line%d-cell%02d.png".formatted(lineIndex + 1, cell),
                        copy(aligned.getSubimage(from, 0, to - from, aligned.getHeight())));
            }
        }

        System.out.println("Wrote debug images to " + outputDir.getAbsolutePath());
    }

    /** Forces an independent pixel buffer, breaking any shared-raster aliasing. */
    private static BufferedImage copy(BufferedImage source) {
        BufferedImage result = new BufferedImage(
                source.getWidth(), source.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D graphics = result.createGraphics();
        try {
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return result;
    }

    private static BufferedImage withGridLines(BufferedImage source, double cellWidth) {
        BufferedImage marked = new BufferedImage(
                source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = marked.createGraphics();
        try {
            graphics.drawImage(source, 0, 0, null);
            graphics.setColor(Color.RED);
            for (int cell = 0; cell <= 44; cell++) {
                int x = (int) Math.round(cell * cellWidth);
                graphics.drawLine(x, 0, x, marked.getHeight());
            }
        } finally {
            graphics.dispose();
        }
        return marked;
    }

    private static int[] inkBounds(BufferedImage image) {
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

    private static void write(File dir, String name, BufferedImage image) throws Exception {
        ImageIO.write(image, "png", new File(dir, name));
    }
}