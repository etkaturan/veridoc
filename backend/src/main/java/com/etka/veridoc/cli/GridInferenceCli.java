package com.etka.veridoc.cli;

import com.etka.veridoc.ocr.GridInference;
import com.etka.veridoc.ocr.LineSplitter;

import javax.imageio.ImageIO;
import java.io.File;

/** Prints the inferred grid for an image, for comparison against its sidecar. */
public final class GridInferenceCli {

    public static void main(String[] args) throws Exception {
        var image = ImageIO.read(new File(args[0]));
        var lines = LineSplitter.split(image);

        System.setProperty("veridoc.grid.debug", "true");

        GridInference.infer(lines, 44).ifPresentOrElse(
                grid -> System.out.printf("%nInferred: left=%d pitch=%.3f%n",
                        grid.left(), grid.pitch()),
                () -> System.out.println("Inference failed"));
    }
}