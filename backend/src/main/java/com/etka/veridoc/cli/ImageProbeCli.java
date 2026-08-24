package com.etka.veridoc.cli;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.File;
import java.util.Iterator;

/** Reports every ImageIO reader's view of a file's dimensions, to catch cases where ImageIO picks up an embedded thumbnail rather than the full image. */
public final class ImageProbeCli {
    public static void main(String[] args) throws Exception {
        File file = new File(args[0]);
        System.out.println("File: " + file.getAbsolutePath() + " (" + file.length() + " bytes)");

        try (ImageInputStream stream = ImageIO.createImageInputStream(file)) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            int i = 0;
            while (readers.hasNext()) {
                ImageReader reader = readers.next();
                reader.setInput(stream);
                System.out.printf("Reader %d: %s%n", i, reader.getClass().getName());
                for (int idx = 0; idx < reader.getNumImages(true); idx++) {
                    System.out.printf("  image[%d]: %dx%d%n",
                            idx, reader.getWidth(idx), reader.getHeight(idx));
                }
                i++;
            }
        }

        var direct = ImageIO.read(file);
        System.out.println("ImageIO.read() result: " + direct.getWidth() + "x" + direct.getHeight());
    }
}